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

package org.gridgain.grid.cache.store.jdbc;

import org.apache.ignite.configuration.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.transactions.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.store.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.testframework.junits.common.*;
import org.jdk8.backport.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.cache.GridCacheAtomicityMode.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCacheDistributionMode.*;
import static org.gridgain.grid.cache.GridCacheWriteSynchronizationMode.*;
import static org.gridgain.testframework.GridTestUtils.*;

/**
 *
 */
public class GridCacheJdbcBlobStoreMultithreadedSelfTest extends GridCommonAbstractTest {
    /** IP finder. */
    private static final TcpDiscoveryIpFinder IP_FINDER = new TcpDiscoveryVmIpFinder(true);

    /** Number of grids to start. */
    private static final int GRID_CNT = 5;

    /** Number of transactions. */
    private static final int TX_CNT = 1000;

    /** Cache store. */
    private static GridCacheStore<Integer, String> store;

    /** Distribution mode. */
    private GridCacheDistributionMode mode;

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        store = store();

        mode = NEAR_PARTITIONED;

        startGridsMultiThreaded(GRID_CNT - 2);

        mode = NEAR_ONLY;

        startGrid(GRID_CNT - 2);

        mode = CLIENT_ONLY;

        startGrid(GRID_CNT - 1);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /** {@inheritDoc} */
    @Override protected final IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(gridName);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(IP_FINDER);

        c.setDiscoverySpi(disco);

        GridCacheConfiguration cc = defaultCacheConfiguration();

        cc.setCacheMode(PARTITIONED);
        cc.setWriteSynchronizationMode(FULL_SYNC);
        cc.setSwapEnabled(false);
        cc.setAtomicityMode(TRANSACTIONAL);
        cc.setBackups(1);
        cc.setDistributionMode(mode);

        cc.setStore(store);

        c.setCacheConfiguration(cc);

        return c;
    }

    /**
     * @throws Exception If failed.
     */
    public void testMultithreadedPut() throws Exception {
        IgniteFuture<?> fut1 = runMultiThreadedAsync(new Callable<Object>() {
            private final Random rnd = new Random();

            @Override public Object call() throws Exception {
                for (int i = 0; i < TX_CNT; i++) {
                    GridCache<Integer, String> cache = cache(rnd.nextInt(GRID_CNT));

                    cache.put(rnd.nextInt(1000), "value");
                }

                return null;
            }
        }, 4, "put");

        IgniteFuture<?> fut2 = runMultiThreadedAsync(new Callable<Object>() {
            private final Random rnd = new Random();

            @Override public Object call() throws Exception {
                for (int i = 0; i < TX_CNT; i++) {
                    GridCache<Integer, String> cache = cache(rnd.nextInt(GRID_CNT));

                    cache.putIfAbsent(rnd.nextInt(1000), "value");
                }

                return null;
            }
        }, 4, "putIfAbsent");

        fut1.get();
        fut2.get();

        long opened = ((LongAdder)U.field(store, "opened")).sum();
        long closed = ((LongAdder)U.field(store, "closed")).sum();

        assert opened > 0;
        assert closed > 0;

        assertEquals(opened, closed);
    }

    /**
     * @throws Exception If failed.
     */
    public void testMultithreadedPutAll() throws Exception {
        runMultiThreaded(new Callable<Object>() {
            private final Random rnd = new Random();

            @Override public Object call() throws Exception {
                for (int i = 0; i < TX_CNT; i++) {
                    Map<Integer, String> map = new TreeMap<>();

                    for (int j = 0; j < 10; j++)
                        map.put(rnd.nextInt(1000), "value");

                    GridCache<Integer, String> cache = cache(rnd.nextInt(GRID_CNT));

                    cache.putAll(map);
                }

                return null;
            }
        }, 8, "putAll");

        long opened = ((LongAdder)U.field(store, "opened")).sum();
        long closed = ((LongAdder)U.field(store, "closed")).sum();

        assert opened > 0;
        assert closed > 0;

        assertEquals(opened, closed);
    }

    /**
     * @throws Exception If failed.
     */
    public void testMultithreadedExplicitTx() throws Exception {
        runMultiThreaded(new Callable<Object>() {
            private final Random rnd = new Random();

            @Override public Object call() throws Exception {
                for (int i = 0; i < TX_CNT; i++) {
                    GridCache<Integer, String> cache = cache(rnd.nextInt(GRID_CNT));

                    try (IgniteTx tx = cache.txStart()) {
                        cache.put(1, "value");
                        cache.put(2, "value");
                        cache.put(3, "value");

                        cache.get(1);
                        cache.get(4);

                        Map<Integer, String> map = new TreeMap<>();

                        map.put(5, "value");
                        map.put(6, "value");

                        cache.putAll(map);

                        tx.commit();
                    }
                }

                return null;
            }
        }, 8, "tx");

        long opened = ((LongAdder)U.field(store, "opened")).sum();
        long closed = ((LongAdder)U.field(store, "closed")).sum();

        assert opened > 0;
        assert closed > 0;

        assertEquals(opened, closed);
    }

    /**
     * @return New store.
     * @throws Exception In case of error.
     */
    private GridCacheStore<Integer, String> store() throws Exception {
        GridCacheStore<Integer, String> store = new GridCacheJdbcBlobStore<>();

        Field f = store.getClass().getDeclaredField("testMode");

        f.setAccessible(true);

        f.set(store, true);

        return store;
    }
}
