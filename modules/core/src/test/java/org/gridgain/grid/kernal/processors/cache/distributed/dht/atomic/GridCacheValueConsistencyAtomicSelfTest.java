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

package org.gridgain.grid.kernal.processors.cache.distributed.dht.atomic;

import org.apache.ignite.*;
import org.apache.ignite.lang.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.testframework.*;
import org.jdk8.backport.*;

import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheAtomicityMode.*;

/**
 * Tests cache value consistency for ATOMIC mode.
 */
public class GridCacheValueConsistencyAtomicSelfTest extends GridCacheValueConsistencyAbstractSelfTest {
    /** {@inheritDoc} */
    @Override protected GridCacheAtomicityMode atomicityMode() {
        return ATOMIC;
    }

    /** {@inheritDoc} */
    @Override protected int iterationCount() {
        return 400_000;
    }

    /**
     * @throws Exception If failed.
     */
    public void testForceTransformBackupConsistency() throws Exception {
        U.sleep(1000);

        int keyCnt = iterationCount() / 10;

        int threadCnt = 8;

        final int range = keyCnt / threadCnt;

        for (int r = 1; r < 5; r++) {
            final AtomicInteger rangeIdx = new AtomicInteger();

            info(">>>>>> Running iteration: " + r);

            GridTestUtils.runMultiThreaded(new Runnable() {
                @Override public void run() {
                    try {
                        int rangeStart = rangeIdx.getAndIncrement() * range;

                        info("Got range [" + rangeStart + ", " + (rangeStart + range) + ")");

                        for (int i = rangeStart; i < rangeStart + range; i++) {
                            int idx = ThreadLocalRandom8.current().nextInt(gridCount());

                            GridCacheProjection<Integer, Integer> cache = grid(idx).cache(null);

                            cache = cache.flagsOn(GridCacheFlag.FORCE_TRANSFORM_BACKUP);

                            cache.transform(i, new Transformer(i));
                        }
                    }
                    catch (IgniteCheckedException e) {
                        throw new IgniteException(e);
                    }
                }
            }, threadCnt, "runner");

            info("Finished run, checking values.");

            U.sleep(500);

            int total = 0;

            for (int idx = 0; idx < gridCount(); idx++) {
                GridCache<Integer, Integer> cache = grid(idx).cache(null);

                for (int i = 0; i < keyCnt; i++) {
                    Integer val = cache.peek(i);

                    if (val != null) {
                        assertEquals("Invalid value for key: " + i, (Integer)r, val);

                        total++;
                    }
                }
            }

            assertTrue("Total keys: " + total, total >= keyCnt * 2); // 1 backup.
        }
    }

    /**
     *
     */
    private static class Transformer implements IgniteClosure<Integer, Integer> {
        private int key;

        private Transformer(int key) {
            this.key = key;
        }

        /** {@inheritDoc} */
        @Override public Integer apply(Integer old) {
            if (key < 5)
                System.err.println(Thread.currentThread().getName() + " <> Transforming value [key=" + key +
                    ", val=" + old + ']');

            return old == null ? 1 : old + 1;
        }
    }
}
