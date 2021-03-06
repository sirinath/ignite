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

package org.gridgain.grid.kernal.processors.cache.eviction;

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.transactions.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.eviction.GridCacheEvictionPolicy;
import org.gridgain.grid.cache.eviction.fifo.GridCacheFifoEvictionPolicy;
import org.gridgain.grid.cache.store.GridCacheStore;
import org.gridgain.grid.cache.store.GridCacheStoreAdapter;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.U;
import org.gridgain.testframework.junits.common.GridCommonAbstractTest;
import org.jetbrains.annotations.Nullable;

import static org.gridgain.grid.cache.GridCacheAtomicityMode.*;

/**
 * Tests that cache handles {@code setAllowEmptyEntries} flag correctly.
 */
public abstract class GridCacheEmptyEntriesAbstractSelfTest extends GridCommonAbstractTest {
    /** IP finder. */
    private static final TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** */
    private GridCacheEvictionPolicy<?, ?> plc;

    /** */
    private GridCacheEvictionPolicy<?, ?> nearPlc;

    /** Test store. */
    private GridCacheStore<String, String> testStore;

    /** Tx concurrency to use. */
    private IgniteTxConcurrency txConcurrency;

    /** Tx isolation to use. */
    private IgniteTxIsolation txIsolation;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(gridName);

        TransactionsConfiguration txCfg = c.getTransactionsConfiguration();

        txCfg.setDefaultTxConcurrency(txConcurrency);
        txCfg.setDefaultTxIsolation(txIsolation);
        txCfg.setTxSerializableEnabled(true);

        GridCacheConfiguration cc = defaultCacheConfiguration();

        cc.setCacheMode(cacheMode());
        cc.setAtomicityMode(TRANSACTIONAL);

        cc.setSwapEnabled(false);

        cc.setWriteSynchronizationMode(GridCacheWriteSynchronizationMode.FULL_SYNC);
        cc.setDistributionMode(GridCacheDistributionMode.PARTITIONED_ONLY);

        cc.setEvictionPolicy(plc);
        cc.setNearEvictionPolicy(nearPlc);
        cc.setEvictSynchronizedKeyBufferSize(1);

        cc.setEvictNearSynchronized(true);
        cc.setEvictSynchronized(true);

        cc.setStore(testStore);

        c.setCacheConfiguration(cc);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(ipFinder);

        c.setDiscoverySpi(disco);

        return c;
    }

    /**
     * Starts grids depending on testing cache.
     *
     * @return First grid node.
     * @throws Exception If failed.
     */
    protected abstract Ignite startGrids() throws Exception;

    /** @return Cache mode for particular test. */
    protected abstract GridCacheMode cacheMode();

    /**
     * Tests FIFO eviction policy.
     *
     * @throws Exception If failed.
     */
    public void testFifo() throws Exception {
        plc = new GridCacheFifoEvictionPolicy(50);
        nearPlc = new GridCacheFifoEvictionPolicy(50);

        checkPolicy();
    }

    /**
     * Checks policy with and without store set.
     *
     * @throws Exception If failed.
     */
    private void checkPolicy() throws Exception {
        testStore = null;

        checkPolicy0();

        testStore = new GridCacheStoreAdapter<String, String>() {
            @Override public String load(@Nullable IgniteTx tx, String key) {
                return null;
            }

            @Override public void put(@Nullable IgniteTx tx, String key,
                @Nullable String val) {
                // No-op.
            }

            @Override public void remove(@Nullable IgniteTx tx, String key) {
                // No-op.
            }
        };

        checkPolicy0();
    }

    /**
     * Tests preset eviction policy.
     *
     * @throws Exception If failed.
     */
    private void checkPolicy0() throws Exception {
        for (IgniteTxConcurrency concurrency : IgniteTxConcurrency.values()) {
            txConcurrency = concurrency;

            for (IgniteTxIsolation isolation : IgniteTxIsolation.values()) {
                txIsolation = isolation;

                Ignite g = startGrids();

                GridCache<String, String> cache = g.cache(null);

                try {
                    info(">>> Checking policy [txConcurrency=" + txConcurrency + ", txIsolation=" + txIsolation +
                        ", plc=" + plc + ", nearPlc=" + nearPlc + ']');

                    checkExplicitTx(cache);

                    checkImplicitTx(cache);
                }
                finally {
                    stopAllGrids();
                }
            }
        }
    }

    /**
     * Checks that gets work for implicit txs.
     *
     * @param cache Cache to test.
     * @throws Exception If failed.
     */
    private void checkImplicitTx(GridCache<String, String> cache) throws Exception {
        assertNull(cache.get("key1"));
        assertNull(cache.getAsync("key2").get());

        assertTrue(cache.getAll(F.asList("key3", "key4")).isEmpty());
        assertTrue(cache.getAllAsync(F.asList("key5", "key6")).get().isEmpty());

        cache.put("key7", "key7");
        cache.remove("key7", "key7");
        assertNull(cache.get("key7"));

        checkEmpty(cache);
    }

    /**
     * Checks that gets work for implicit txs.
     *
     * @param cache Cache to test.
     * @throws Exception If failed.
     */
    private void checkExplicitTx(GridCache<String, String> cache) throws Exception {
        IgniteTx tx = cache.txStart();

        try {
            assertNull(cache.get("key1"));

            tx.commit();
        }
        finally {
            tx.close();
        }

        tx = cache.txStart();

        try {
            assertNull(cache.getAsync("key2").get());

            tx.commit();
        }
        finally {
            tx.close();
        }

        tx = cache.txStart();

        try {
            assertTrue(cache.getAll(F.asList("key3", "key4")).isEmpty());

            tx.commit();
        }
        finally {
            tx.close();
        }

        tx = cache.txStart();

        try {
            assertTrue(cache.getAllAsync(F.asList("key5", "key6")).get().isEmpty());

            tx.commit();
        }
        finally {
            tx.close();
        }

        tx = cache.txStart();

        try {
            cache.put("key7", "key7");

            cache.remove("key7");

            assertNull(cache.get("key7"));

            tx.commit();
        }
        finally {
            tx.close();
        }

        checkEmpty(cache);
    }

    /**
     * Checks that cache is empty.
     *
     * @param cache Cache to check.
     * @throws GridInterruptedException If interrupted while sleeping.
     */
    @SuppressWarnings({"ErrorNotRethrown", "TypeMayBeWeakened"})
    private void checkEmpty(GridCache<String, String> cache) throws GridInterruptedException {
        for (int i = 0; i < 3; i++) {
            try {
                assertTrue(cache.entrySet().toString(), cache.entrySet().isEmpty());

                break;
            }
            catch (AssertionError e) {
                if (i == 2)
                    throw e;

                info(">>> Cache is not empty, flushing evictions.");

                U.sleep(1000);
            }
        }
    }
}
