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

package org.gridgain.grid.kernal.processors.cache.distributed;

import org.apache.ignite.configuration.*;
import org.apache.ignite.lifecycle.*;
import org.apache.ignite.marshaller.optimized.*;
import org.apache.ignite.thread.*;
import org.gridgain.grid.cache.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.testframework.junits.common.*;

import java.io.*;
import java.util.concurrent.*;

import static org.apache.ignite.events.IgniteEventType.*;
import static org.gridgain.grid.cache.GridCachePreloadMode.*;

/**
 * Tests for cache preloader.
 */
@SuppressWarnings({"PublicInnerClass"})
public abstract class GridCachePreloadLifecycleAbstractTest extends GridCommonAbstractTest {
    /** */
    protected static final String TEST_STRING = "ABC";

    /** */
    protected static final GridCachePreloadMode DFLT_PRELOAD_MODE = SYNC;

    /** */
    protected GridCachePreloadMode preloadMode = DFLT_PRELOAD_MODE;

    /** */
    protected LifecycleBean lifecycleBean;

    /** */
    private TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** Default keys. */
    protected static final String[] DFLT_KEYS = new String[] {
        "Branches",
        "CurrencyCurvesAssign",
        "CurRefIndex",
        "MaturityClasses",
        "Folders",
        "FloatingRates",
        "Swap",
        "Portfolios"
    };


    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(gridName);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(ipFinder);

        c.setDiscoverySpi(disco);

        c.setIncludeEventTypes(EVT_TASK_FAILED, EVT_TASK_FINISHED, EVT_JOB_MAPPED);
        c.setIncludeProperties();
        c.setDeploymentMode(IgniteDeploymentMode.SHARED);
        c.setNetworkTimeout(10000);
        c.setRestEnabled(false);
        c.setMarshaller(new IgniteOptimizedMarshaller(false));

//        c.setPeerClassLoadingLocalClassPathExclude(GridCachePreloadLifecycleAbstractTest.class.getName(),
//            MyValue.class.getName());

        c.setExecutorService(new IgniteThreadPoolExecutor(10, 10, 0, new LinkedBlockingQueue<Runnable>()));
        c.setSystemExecutorService(new IgniteThreadPoolExecutor(10, 10, 0, new LinkedBlockingQueue<Runnable>()));
        c.setPeerClassLoadingExecutorService(new IgniteThreadPoolExecutor(3, 3, 0, new LinkedBlockingQueue<Runnable>()));

        c.setLifecycleBeans(lifecycleBean);

        return c;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        preloadMode = DFLT_PRELOAD_MODE;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        lifecycleBean = null;

        stopAllGrids();
    }
    /** {@inheritDoc} */
    @Override protected long getTestTimeout() {
        return 4 * 60 * 1000; // 4 min.
    }

    /**
     * @param key Key.
     * @return Value.
     */
    protected String value(Object key) {
        return TEST_STRING + '-' + key;
    }

    /**
     * @param plain Flag to use plain strings.
     * @param cnt Number of keys to gen.
     * @param lookup Optional key lookup array.
     * @return Generated keys.
     */
    @SuppressWarnings("IfMayBeConditional")
    protected Object[] keys(boolean plain, int cnt, String... lookup) {
        Object[] arr = new Object[cnt];

        for (int i = 0; i < cnt; i++)
            if (plain)
                arr[i] = i < lookup.length ? lookup[i] : "str-" + i;
            else
                arr[i] = i < lookup.length ? new MyStringKey(lookup[i]) : new MyStringKey("str-" + i);

        return arr;
    }

    /**
     *
     */
    public static class MyStringKey implements Serializable {
        /** Key. */
        private final String str;

        /**
         * @param str Key.
         */
        public MyStringKey(String str) {
            this.str = str;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return 31 + ((str == null) ? 0 : str.hashCode());
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object obj) {
//            if (this == obj)
//                return true;
//
//            if (obj == null)
//                return false;
//
//            if (getClass() != obj.getClass())
//                return false;
//
//            MyStringKey other = (MyStringKey) obj;
//
//            if (str == null) {
//                if (other.str != null)
//                    return false;
//            }
//            else if (!str.equals(other.str))
//                return false;
//
//            return true;
            return toString().equals(obj.toString());
        }

        /** {@inheritDoc} */
        @Override public String toString() {
//            return str;
            return S.toString(MyStringKey.class, this, "clsLdr", getClass().getClassLoader());
        }
    }

    /**
     *
     */
    public static class MyValue implements Serializable {
        /** Data. */
        private final String data;

        /**
         * @param data Data.
         */
        public MyValue(String data) {
            assert data != null;

            this.data = data;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object obj) {
            if (obj == this)
                return true;

            if (obj instanceof MyValue) {
                MyValue myObj = (MyValue) obj;

                return data.equals(myObj.data);
            }

            return false;
            // return data.equals(obj.toString());
        }

        /** {@inheritDoc} */
        @Override public String toString() {
//            return data;
            return S.toString(MyValue.class, this, "clsLdr", getClass().getClassLoader());
        }
    }
}
