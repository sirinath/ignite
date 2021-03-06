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

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.apache.ignite.*;
import org.apache.ignite.transactions.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.util.typedef.*;

import java.util.*;

import static org.apache.ignite.transactions.IgniteTxConcurrency.*;
import static org.apache.ignite.transactions.IgniteTxIsolation.*;

/**
 * Multi-node test for group locking.
 */
public abstract class GridCacheGroupLockPartitionedMultiNodeAbstractSelfTest extends
    GridCacheGroupLockPartitionedAbstractSelfTest {
    /** {@inheritDoc} */
    @Override protected int gridCount() {
        return 3;
    }

    /**
     * @throws Exception If failed.
     */
    public void testNonLocalKeyOptimistic() throws Exception {
        checkNonLocalKey(OPTIMISTIC);
    }

    /**
     * @throws Exception If failed.
     */
    public void testNonLocalKeyPessimistic() throws Exception {
        checkNonLocalKey(PESSIMISTIC);
    }

    /**
     * @throws Exception If failed.
     */
    private void checkNonLocalKey(IgniteTxConcurrency concurrency) throws Exception {
        final UUID key = primaryKeyForCache(grid(1));

        GridCache<Object, Object> cache = grid(0).cache(null);

        IgniteTx tx = null;
        try {
            tx = cache.txStartAffinity(key, concurrency, READ_COMMITTED, 0, 2);

            cache.put(new GridCacheAffinityKey<>("1", key), "2");

            tx.commit();

            fail("Exception should be thrown.");
        }
        catch (IgniteCheckedException ignored) {
            // Expected exception.
        }
        finally {
            if (tx != null)
                tx.close();

            assertNull(cache.tx());
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testNearReadersUpdateWithAffinityReaderOptimistic() throws Exception {
        checkNearReadersUpdate(true, OPTIMISTIC);
    }

    /**
     * @throws Exception If failed.
     */
    public void testNearReadersUpdateWithAffinityReaderPessimistic() throws Exception {
        checkNearReadersUpdate(true, PESSIMISTIC);
    }

    /**
     * @throws Exception If failed.
     */
    public void testNearReaderUpdateWithoutAffinityReaderOptimistic() throws Exception {
        checkNearReadersUpdate(false, OPTIMISTIC);
    }

    /**
     * @throws Exception If failed.
     */
    public void testNearReaderUpdateWithoutAffinityReaderPessimistic() throws Exception {
        checkNearReadersUpdate(false, PESSIMISTIC);
    }

    /**
     * @throws Exception If failed.
     */
    private void checkNearReadersUpdate(boolean touchAffKey, IgniteTxConcurrency concurrency) throws Exception {
        UUID affinityKey = primaryKeyForCache(grid(0));

        GridCacheAffinityKey<String> key1 = new GridCacheAffinityKey<>("key1", affinityKey);
        GridCacheAffinityKey<String> key2 = new GridCacheAffinityKey<>("key2", affinityKey);
        GridCacheAffinityKey<String> key3 = new GridCacheAffinityKey<>("key3", affinityKey);

        grid(0).cache(null).put(affinityKey, "aff");

        GridCache<GridCacheAffinityKey<String>, String> cache = grid(0).cache(null);

        cache.putAll(F.asMap(
            key1, "val1",
            key2, "val2",
            key3, "val3")
        );

        Ignite reader = null;

        for (int i = 0; i < gridCount(); i++) {
            if (!grid(i).cache(null).affinity().isPrimaryOrBackup(grid(i).localNode(), affinityKey))
                reader = grid(i);
        }

        assert reader != null;

        info(">>> Reader is " + reader.cluster().localNode().id());

        // Add reader.
        if (touchAffKey)
            assertEquals("aff", reader.cache(null).get(affinityKey));

        assertEquals("val1", reader.cache(null).get(key1));
        assertEquals("val2", reader.cache(null).get(key2));
        assertEquals("val3", reader.cache(null).get(key3));

        if (nearEnabled()) {
            assertEquals("val1", reader.cache(null).peek(key1));
            assertEquals("val2", reader.cache(null).peek(key2));
            assertEquals("val3", reader.cache(null).peek(key3));
        }

        try (IgniteTx tx = cache.txStartAffinity(affinityKey, concurrency, READ_COMMITTED, 0, 3)) {
            cache.putAll(F.asMap(
                key1, "val01",
                key2, "val02",
                key3, "val03")
            );

            tx.commit();
        }

        if (nearEnabled()) {
            assertEquals("val01", reader.cache(null).peek(key1));
            assertEquals("val02", reader.cache(null).peek(key2));
            assertEquals("val03", reader.cache(null).peek(key3));
        }
    }
}
