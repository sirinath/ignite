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

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.discovery.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.transactions.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.store.*;
import org.gridgain.testframework.junits.common.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCacheWriteSynchronizationMode.*;

/**
 * Test that persistent store is not used when loading invalidated entry from backup node.
 */
public class GridPartitionedBackupLoadSelfTest extends GridCommonAbstractTest {
    /** */
    private static final TcpDiscoveryIpFinder IP_FINDER = new TcpDiscoveryVmIpFinder(true);

    /** */
    private static final int GRID_CNT = 3;

    /** */
    private final TestStore store = new TestStore();

    /** */
    private final AtomicInteger cnt = new AtomicInteger();

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setDiscoverySpi(discoverySpi());
        cfg.setCacheConfiguration(cacheConfiguration());

        return cfg;
    }

    /**
     * @return Discovery SPI.
     */
    private DiscoverySpi discoverySpi() {
        TcpDiscoverySpi spi = new TcpDiscoverySpi();

        spi.setIpFinder(IP_FINDER);

        return spi;
    }

    /**
     * @return Cache configuration.
     */
    private GridCacheConfiguration cacheConfiguration() {
        GridCacheConfiguration cfg = defaultCacheConfiguration();

        cfg.setCacheMode(PARTITIONED);
        cfg.setBackups(1);
        cfg.setStore(store);
        cfg.setWriteSynchronizationMode(FULL_SYNC);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        startGridsMultiThreaded(GRID_CNT);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /**
     * @throws Exception If failed.
     */
    public void testBackupLoad() throws Exception {
        assert grid(0).cache(null).putx(1, 1);

        assert store.get(1) == 1;

        for (int i = 0; i < GRID_CNT; i++) {
            GridCache<Integer, Integer> cache = cache(i);

            GridCacheEntry<Integer, Integer> entry = cache.entry(1);

            if (entry.backup()) {
                assert entry.peek() == 1;

                assert entry.clear();

                assert entry.peek() == null;

                // Store is called in putx method, so we reset counter here.
                cnt.set(0);

                assert entry.get() == 1;

                assert cnt.get() == 0;
            }
        }
    }

    /**
     * Test store.
     */
    private class TestStore extends GridCacheStoreAdapter<Integer, Integer> {
        /** */
        private Map<Integer, Integer> map = new ConcurrentHashMap<>();

        /** {@inheritDoc} */
        @Override public Integer load(@Nullable IgniteTx tx, Integer key)
            throws IgniteCheckedException {
            cnt.incrementAndGet();

            return null;
        }

        /** {@inheritDoc} */
        @Override public void put(IgniteTx tx, Integer key, @Nullable Integer val)
            throws IgniteCheckedException {
            map.put(key, val);
        }

        /** {@inheritDoc} */
        @Override public void remove(IgniteTx tx, Integer key) throws IgniteCheckedException {
            // No-op
        }

        /**
         * @param key Key.
         * @return Value.
         */
        public Integer get(Integer key) {
            return map.get(key);
        }
    }
}
