/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gridgain.grid.p2p;

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.events.*;
import org.apache.ignite.lang.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.testframework.*;
import org.gridgain.testframework.config.*;
import org.gridgain.testframework.junits.common.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.*;
import static org.apache.ignite.events.IgniteEventType.*;

/**
 * The test does the following:
 *
 * 1. The test should execute a task in SHARED_DEPLOY mode, restart a node with new version and make sure that a
 *      new class loader is created on remote node.
 * 2. The test should execute a task in SHARED_DEPLOY mode, restart a node with same version and make sure
 *      that the same class loader is created on remote node.
 * 3. The test should execute a task in SHARED_UNDEPLOY mode, restart a node with same version and
 *      make sure that a new class loader is created on remote node.
 */
@SuppressWarnings({"ProhibitedExceptionDeclared", "ObjectEquality", "unchecked"})
public class GridP2PUserVersionChangeSelfTest extends GridCommonAbstractTest {
    /** Current deployment mode. */
    private IgniteDeploymentMode depMode;

    /** Test task class name. */
    private static final String TEST_TASK_NAME = "org.gridgain.grid.tests.p2p.GridP2PTestTaskExternalPath1";

    /** Test resource class name. */
    private static final String TEST_RCRS_NAME = "org.gridgain.grid.tests.p2p.GridTestUserResource";

    /** IP finder. */
    private final TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** */
    public GridP2PUserVersionChangeSelfTest() {
        super(/*start grid*/false);
    }

    /** {@inheritDoc} */
    @Override protected long getTestTimeout() {
        return 30 * 1000;
    }

    /**
     * @return Timeout for condition waits.
     */
    private long getConditionTimeout() {
        return getTestTimeout() > 10000 ? getTestTimeout() - 10000 : getTestTimeout();
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setDeploymentMode(depMode);
        cfg.setNetworkTimeout(10000);

        TcpDiscoverySpi discoSpi = new TcpDiscoverySpi();

        discoSpi.setIpFinder(ipFinder);

        cfg.setDiscoverySpi(discoSpi);

        if (gridName.contains("testCacheRedeployVersionChangeContinuousMode")) {
            GridCacheConfiguration cacheCfg = new GridCacheConfiguration();

            cacheCfg.setCacheMode(GridCacheMode.REPLICATED);

            cfg.setCacheConfiguration(cacheCfg);
        }
        else
            cfg.setCacheConfiguration();

        return cfg;
    }

    /**
     * @throws Exception If test failed.
     */
    public void testRedeployVersionChangeContinuousMode() throws Exception {
        depMode = IgniteDeploymentMode.CONTINUOUS;

        checkRedeployVersionChange();
    }

    /**
     * @throws Exception If test failed.
     */
    public void testRedeployVersionChangeSharedMode() throws Exception {
        depMode = IgniteDeploymentMode.SHARED;

        checkRedeployVersionChange();
    }

    /**
     * @throws Exception If failed.
     */
    private void checkRedeployVersionChange() throws Exception {
        try {
            Ignite ignite1 = startGrid(1);
            Ignite ignite2 = startGrid(2);

            GridTestExternalClassLoader ldr = new GridTestExternalClassLoader(
                new URL[] { new URL(GridTestProperties.getProperty("p2p.uri.cls")) },
                Collections.singletonMap("META-INF/gridgain.xml", makeUserVersion("1").getBytes()));

            Class task1 = ldr.loadClass(TEST_TASK_NAME);

            final CountDownLatch undeployed = new CountDownLatch(1);

            ignite2.events().localListen(new IgnitePredicate<IgniteEvent>() {
                @Override public boolean apply(IgniteEvent evt) {
                    if (evt.type() == EVT_TASK_UNDEPLOYED &&
                        ((IgniteDeploymentEvent) evt).alias().equals(TEST_TASK_NAME))
                        undeployed.countDown();

                    return true;
                }
            }, EVT_TASK_UNDEPLOYED);

            int[] res1 = (int[]) ignite1.compute().execute(task1, ignite2.cluster().localNode().id());

            stopGrid(1);

            ldr.setResourceMap(Collections.singletonMap("META-INF/gridgain.xml", makeUserVersion("2").getBytes()));

            ignite1 = startGrid(1);

            int[] res2 = (int[]) ignite1.compute().execute(task1, ignite2.cluster().localNode().id());

            assert res1[0] != res2[0];
            assert res1[1] != res2[1];

            // Allow P2P timeout to expire.
            assert undeployed.await(30000, MILLISECONDS);
        }
        finally {
            stopGrid(1);
            stopGrid(2);
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testRedeployOnNodeRestartContinuousMode() throws Exception {
        depMode = IgniteDeploymentMode.CONTINUOUS;

        try {
            Ignite ignite1 = startGrid(1);
            Ignite ignite2 = startGrid(2);

            GridTestExternalClassLoader ldr = new GridTestExternalClassLoader(
                new URL[] { new URL(GridTestProperties.getProperty("p2p.uri.cls")) });

            Class task1 = ldr.loadClass(TEST_TASK_NAME);

            final CountDownLatch undeployed = new CountDownLatch(1);

            ignite2.events().localListen(new IgnitePredicate<IgniteEvent>() {
                @Override public boolean apply(IgniteEvent evt) {
                    if (evt.type() == EVT_TASK_UNDEPLOYED &&
                        ((IgniteDeploymentEvent) evt).alias().equals(TEST_TASK_NAME))
                        undeployed.countDown();

                    return true;
                }
            }, EVT_TASK_UNDEPLOYED);

            int[] res1 = (int[]) ignite1.compute().execute(task1, ignite2.cluster().localNode().id());

            stopGrid(1);

            ignite1 = startGrid(1);

            int[] res2 = (int[]) ignite1.compute().execute(task1, ignite2.cluster().localNode().id());

            assert !undeployed.await(3000, MILLISECONDS);

            assert res1[0] == res2[0];
            assert res1[1] == res2[1];
        }
        finally {
            stopGrid(1);
            stopGrid(2);
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testRedeployOnNodeRestartSharedMode() throws Exception {
        depMode = IgniteDeploymentMode.SHARED;

        try {
            Ignite ignite1 = startGrid(1);
            Ignite ignite2 = startGrid(2);

            GridTestExternalClassLoader ldr = new GridTestExternalClassLoader(
                new URL[] { new URL(GridTestProperties.getProperty("p2p.uri.cls")) });

            Class task1 = ldr.loadClass(TEST_TASK_NAME);

            final CountDownLatch undeployed = new CountDownLatch(1);

            ignite2.events().localListen(new IgnitePredicate<IgniteEvent>() {
                @Override public boolean apply(IgniteEvent evt) {
                    if (evt.type() == EVT_TASK_UNDEPLOYED &&
                        ((IgniteDeploymentEvent) evt).alias().equals(TEST_TASK_NAME))
                        undeployed.countDown();

                    return true;
                }
            }, EVT_TASK_UNDEPLOYED);

            final CountDownLatch discoLatch = new CountDownLatch(1);

            ignite2.events().localListen(new IgnitePredicate<IgniteEvent>() {
                @Override public boolean apply(IgniteEvent evt) {
                    if (evt.type() == EVT_NODE_LEFT)
                        discoLatch.countDown();

                    return true;
                }
            }, EVT_NODE_LEFT);

            int[] res1 = (int[]) ignite1.compute().execute(task1, ignite2.cluster().localNode().id());

            stopGrid(1);

            assert discoLatch.await(1000, MILLISECONDS);

            assert undeployed.await(1000, MILLISECONDS);

            ignite1 = startGrid(1);

            int[] res2 = (int[]) ignite1.compute().execute(task1, ignite2.cluster().localNode().id());

            assert res1[0] != res2[0];
            assert res1[1] != res2[1];
        }
        finally {
            stopGrid(1);
            stopGrid(2);
        }
    }

    /**
     * @throws Exception If failed.
     */
    // TODO: GG-5678 Uncomment when fix
    public void _testCacheRedeployVersionChangeContinuousMode() throws Exception {
        depMode = IgniteDeploymentMode.CONTINUOUS;

        try {
            Ignite ignite1 = startGrid("testCacheRedeployVersionChangeContinuousMode1");
            Ignite ignite2 = startGrid("testCacheRedeployVersionChangeContinuousMode2");

            GridTestExternalClassLoader ldr = new GridTestExternalClassLoader(
                new URL[] { new URL(GridTestProperties.getProperty("p2p.uri.cls")) },
                Collections.singletonMap("META-INF/gridgain.xml", makeUserVersion("1").getBytes()));

            Class rcrsCls = ldr.loadClass(TEST_RCRS_NAME);

            GridCache<Long, Object> cache1 = ignite1.cache(null);

            assertNotNull(cache1);

            cache1.put(1L, rcrsCls.newInstance());

            final GridCache<Long, Object> cache2 = ignite2.cache(null);

            assertNotNull(cache2);

            // The entry should propagate to grid2, because the
            // cache is REPLICATED. This happens asynchronously, we
            // need to use condition wait.
            assert GridTestUtils.waitForCondition(new PAX() {
                @Override public boolean applyx() throws IgniteCheckedException {
                    return cache2.get(1L) != null;
                }
            }, getConditionTimeout());

            stopGrid("testCacheRedeployVersionChangeContinuousMode1");

            // Increase the user version of the test class.
            ldr.setResourceMap(Collections.singletonMap("META-INF/gridgain.xml", makeUserVersion("2").getBytes()));

            ignite1 = startGrid("testCacheRedeployVersionChangeContinuousMode1");

            cache1 = ignite1.cache(null);

            assertNotNull(cache1);

            // Put an entry with a new user version.
            cache1.put(2L, rcrsCls.newInstance());

            // At this point, old version of test resource should be undeployed
            // and removed from cache asynchronously.
            assert GridTestUtils.waitForCondition(new PAX() {
                @Override public boolean applyx() throws IgniteCheckedException {
                    return cache2.get(1L) == null;
                }
            }, getConditionTimeout()) : "2nd condition failed [entries1=" + cache1.entrySet() +
                ", entries2=" + cache2.entrySet() + ']';
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * Creates content of META-INF/gridgain.xml for specified user version.
     *
     * @param userVer Version to create.
     * @return content of META-INF/gridgain.xml.
     */
    private String makeUserVersion(String userVer) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?> " +
            "<beans xmlns=\"http://www.springframework.org/schema/beans\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:util=\"http://www.springframework.org/schema/util\" " +
            "xsi:schemaLocation=\"http://www.springframework.org/schema/beans " +
            "http://www.springframework.org/schema/beans/spring-beans.xsd " +
            "http://www.springframework.org/schema/util " +
            "http://www.springframework.org/schema/util/spring-util.xsd\"> " +
            "<bean id=\"userVersion\" class=\"java.lang.String\"><constructor-arg value=\"" + userVer + "\"/></bean> " +
            "</beans>";
    }
}
