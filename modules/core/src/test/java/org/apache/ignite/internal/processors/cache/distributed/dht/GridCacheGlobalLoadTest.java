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

package org.apache.ignite.internal.processors.cache.distributed.dht;

import org.apache.ignite.*;
import org.apache.ignite.cache.*;
import org.apache.ignite.cache.store.*;
import org.apache.ignite.internal.processors.cache.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.resources.*;
import org.jdk8.backport.*;
import org.jetbrains.annotations.*;
import org.junit.*;

import javax.cache.*;
import java.util.concurrent.*;

import static org.apache.ignite.cache.GridCacheAtomicityMode.*;
import static org.apache.ignite.cache.GridCacheDistributionMode.*;
import static org.apache.ignite.cache.GridCacheMode.*;

/**
 * Load cache test.
 */
public class GridCacheGlobalLoadTest extends IgniteCacheAbstractTest {
    /** */
    private static ConcurrentMap<String, Object[]> map;

    /** {@inheritDoc} */
    @Override protected int gridCount() {
        return 3;
    }

    /** {@inheritDoc} */
    @Override protected GridCacheMode cacheMode() {
        return PARTITIONED;
    }

    /** {@inheritDoc} */
    @Override protected GridCacheAtomicityMode atomicityMode() {
        return TRANSACTIONAL;
    }

    /** {@inheritDoc} */
    @Override protected GridCacheDistributionMode distributionMode() {
        return NEAR_PARTITIONED;
    }

    /**
     * @throws Exception If failed.
     */
    public void testLoadCache() throws Exception {
        IgniteCache<Integer, Integer> cache = jcache();

        map = new ConcurrentHashMap8<>();

        cache.loadCache(null, 1, 2, 3);

        assertEquals(3, map.size());

        Object[] expArgs = {1, 2, 3};

        for (int i = 0; i < gridCount(); i++) {
            Object[] args = map.get(getTestGridName(i));

            Assert.assertArrayEquals(expArgs, args);
        }

        assertEquals(cache.get(1), (Integer)1);
        assertEquals(cache.get(2), (Integer)2);
        assertEquals(cache.get(3), (Integer)3);

        map = new ConcurrentHashMap8<>();

        cache.loadCache(new IgniteBiPredicate<Integer, Integer>() {
            @Override public boolean apply(Integer key, Integer val) {
                assertNotNull(key);
                assertNotNull(val);

                return key % 2 == 0;
            }
        }, 1, 2, 3, 4, 5, 6);

        assertEquals(3, map.size());

        expArgs = new Object[]{1, 2, 3, 4, 5, 6};

        for (int i = 0; i < gridCount(); i++) {
            Object[] args = map.get(getTestGridName(i));

            Assert.assertArrayEquals(expArgs, args);
        }

        assertEquals(cache.get(1), (Integer)1);
        assertEquals(cache.get(2), (Integer)2);
        assertEquals(cache.get(3), (Integer)3);
        assertEquals(cache.get(4), (Integer)4);
        assertEquals(cache.get(6), (Integer)6);
        assertNull(cache.get(5));
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        map = null;
    }

    /** {@inheritDoc} */
    @Override protected CacheStore<?, ?> cacheStore() {
        return new TestStore();
    }

    /**
     * Test store.
     */
    private static class TestStore extends CacheStoreAdapter<Integer, Integer> {
        /** */
        @IgniteInstanceResource
        private Ignite ignite;

        /** {@inheritDoc} */
        @Override public void loadCache(IgniteBiInClosure<Integer, Integer> clo,
            @Nullable Object... args) {
            assertNotNull(ignite);
            assertNotNull(clo);
            assertNotNull(map);
            assertNotNull(args);

            assertNull(map.put(ignite.name(), args));

            for (Object arg : args) {
                Integer key = (Integer)arg;

                clo.apply(key, key);
            }
        }

        /** {@inheritDoc} */
        @Override public Integer load(Integer key) {
            assertEquals((Integer)5, key);

            return null;
        }

        /** {@inheritDoc} */
        @Override public void write(Cache.Entry<? extends Integer, ? extends Integer> e) {
            fail();
        }

        /** {@inheritDoc} */
        @Override public void delete(Object key) {
            fail();
        }
    }
}