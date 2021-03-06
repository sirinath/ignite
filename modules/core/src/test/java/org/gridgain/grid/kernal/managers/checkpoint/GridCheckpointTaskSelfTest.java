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

package org.gridgain.grid.kernal.managers.checkpoint;

import org.apache.ignite.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.compute.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.resources.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.apache.ignite.spi.checkpoint.*;
import org.apache.ignite.spi.checkpoint.cache.*;
import org.apache.ignite.spi.discovery.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.testframework.junits.common.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCacheWriteSynchronizationMode.*;

/**
 * Checkpoint tests.
 */
public class GridCheckpointTaskSelfTest extends GridCommonAbstractTest {
    /** IP finder. */
    private static final TcpDiscoveryIpFinder IP_FINDER = new TcpDiscoveryVmIpFinder(true);

    /** Checkpoints cache name. */
    private static final String CACHE_NAME = "checkpoints.cache";

    /** Checkpoint key. */
    private static final String CP_KEY = "test.checkpoint.key." + System.currentTimeMillis();

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setCacheConfiguration(cacheConfiguration());
        cfg.setCheckpointSpi(checkpointSpi());
        cfg.setDiscoverySpi(discoverySpi());

        return cfg;
    }

    /**
     * @return Cache configuration.
     */
    private GridCacheConfiguration cacheConfiguration() {
        GridCacheConfiguration cfg = defaultCacheConfiguration();

        cfg.setName(CACHE_NAME);
        cfg.setCacheMode(REPLICATED);
        cfg.setWriteSynchronizationMode(FULL_SYNC);

        return cfg;
    }

    /**
     * @return Checkpoint SPI.
     */
    private CheckpointSpi checkpointSpi() {
        CacheCheckpointSpi spi = new CacheCheckpointSpi();

        spi.setCacheName(CACHE_NAME);

        return spi;
    }

    /**
     * @return Discovery SPI.
     */
    private DiscoverySpi discoverySpi() {
        TcpDiscoverySpi spi = new TcpDiscoverySpi();

        spi.setIpFinder(IP_FINDER);

        return spi;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        startGrid(1);
        startGrid(2);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        assert grid(1).cache(CACHE_NAME).isEmpty() : grid(1).cache(CACHE_NAME).entrySet();
        assert grid(2).cache(CACHE_NAME).isEmpty() : grid(2).cache(CACHE_NAME).entrySet();

        stopAllGrids();
    }

    /**
     * @throws Exception If failed.
     */
    public void testFailover() throws Exception {
        grid(1).compute().execute(FailoverTestTask.class, null);
    }

    /**
     * @throws Exception If failed.
     */
    public void testReduce() throws Exception {
        grid(1).compute().execute(ReduceTestTask.class, null);
    }

    /**
     * Failover test task.
     */
    @ComputeTaskSessionFullSupport
    private static class FailoverTestTask extends ComputeTaskAdapter<Void, Void> {
        /** Grid. */
        @IgniteInstanceResource
        private Ignite ignite;

        /** Task session. */
        @IgniteTaskSessionResource
        private ComputeTaskSession ses;

        /** {@inheritDoc} */
        @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
            @Nullable Void arg) throws IgniteCheckedException {
            assert ignite.cluster().nodes().size() == 2;

            ClusterNode rmt = F.first(ignite.cluster().forRemotes().nodes());

            ses.saveCheckpoint(CP_KEY, true);

            return F.asMap(
                new ComputeJobAdapter() {
                    @IgniteLocalNodeIdResource
                    private UUID nodeId;

                    @IgniteTaskSessionResource
                    private ComputeTaskSession ses;

                    @Override public Object execute() throws IgniteCheckedException {
                        X.println("Executing FailoverTestTask job on node " + nodeId);

                        Boolean cpVal = ses.loadCheckpoint(CP_KEY);

                        assert cpVal != null;

                        if (cpVal) {
                            ses.saveCheckpoint(CP_KEY, false);

                            throw new ComputeExecutionRejectedException("Failing over the job.");
                        }

                        return null;
                    }
                },
                rmt
            );
        }

        /** {@inheritDoc} */
        @Nullable @Override public Void reduce(List<ComputeJobResult> results) throws IgniteCheckedException {
            return null;
        }
    }

    /**
     * Failover test task.
     */
    @ComputeTaskSessionFullSupport
    private static class ReduceTestTask extends ComputeTaskAdapter<Void, Void> {
        /** Grid. */
        @IgniteInstanceResource
        private Ignite ignite;

        /** Task session. */
        @IgniteTaskSessionResource
        private ComputeTaskSession ses;

        /** {@inheritDoc} */
        @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
            @Nullable Void arg) throws IgniteCheckedException {
            assert ignite.cluster().nodes().size() == 2;

            ClusterNode rmt = F.first(ignite.cluster().forRemotes().nodes());

            return F.asMap(
                new ComputeJobAdapter() {
                    @IgniteLocalNodeIdResource
                    private UUID nodeId;

                    @IgniteTaskSessionResource
                    private ComputeTaskSession ses;

                    @Override public Object execute() throws IgniteCheckedException {
                        X.println("Executing ReduceTestTask job on node " + nodeId);

                        ses.saveCheckpoint(CP_KEY, true);

                        return null;
                    }
                },
                rmt
            );
        }

        /** {@inheritDoc} */
        @Override public Void reduce(List<ComputeJobResult> results) throws IgniteCheckedException {
            assert ses.loadCheckpoint(CP_KEY) != null;

            return null;
        }
    }
}
