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

package org.gridgain.loadtests.colocation;

import org.apache.ignite.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.resources.*;
import org.apache.ignite.transactions.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.store.*;
import org.jdk8.backport.*;

import java.util.concurrent.*;

/**
 * Accenture cache store.
 */
public class GridTestCacheStore extends GridCacheStoreAdapter<GridTestKey, Long> {
    @IgniteInstanceResource
    private Ignite ignite;

    @IgniteLoggerResource
    private IgniteLogger log;

    /**
     * Preload data from store. In this case we just auto-generate random values.
     *
     * @param clo Callback for every key.
     * @param args Optional arguments.
     * @throws IgniteCheckedException If failed.
     */
    @Override public void loadCache(final IgniteBiInClosure<GridTestKey, Long> clo,
        Object... args) throws IgniteCheckedException {
        // Number of threads is passed in as argument by caller.
        final int numThreads = (Integer)args[0];
        int entryCnt = (Integer)args[1];

        log.info("Number of load threads: " + numThreads);
        log.info("Number of cache entries to load: " + entryCnt);

        ExecutorService execSvc = Executors.newFixedThreadPool(numThreads);

        try {
            ExecutorCompletionService<Object> completeSvc = new ExecutorCompletionService<>(execSvc);

            GridCache<GridTestKey, Long> cache = ignite.cache("partitioned");

            assert cache != null;

            // Get projection just to check affinity for Integer.
            final GridCacheProjection<Integer, Long> prj = cache.projection(Integer.class, Long.class);

            final LongAdder adder = new LongAdder();

            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;

                final int perThreadKeys = entryCnt / numThreads;

                final int mod = entryCnt % numThreads;

                completeSvc.submit(new Callable<Object>() {
                    @Override public Object call() throws Exception {
                        int start = threadId * perThreadKeys;
                        int end = start + perThreadKeys;

                        if (threadId + 1 == numThreads)
                            end += mod;

                        for (long i = start; i < end; i++) {
                            if (prj.cache().affinity().mapKeyToNode(GridTestKey.affinityKey(i)).isLocal()) { // Only add if key is local.
                                clo.apply(new GridTestKey(i), i);

                                adder.increment();
                            }

                            if (i % 10000 == 0)
                                log.info("Loaded " + adder.intValue() + " keys.");
                        }

                        return null;
                    }
                });
            }

            // Wait for threads to complete.
            for (int i = 0; i < numThreads; i++) {
                try {
                    completeSvc.take().get();
                }
                catch (InterruptedException | ExecutionException e) {
                    throw new IgniteCheckedException(e);
                }
            }

            // Final print out.
            log.info("Loaded " + adder.intValue() + " keys.");
        }
        finally {
            execSvc.shutdown();
        }
    }

    @Override public Long load(IgniteTx tx, GridTestKey key) throws IgniteCheckedException {
        return null; // No-op.
    }

    @Override public void put(IgniteTx tx, GridTestKey key, Long val) throws IgniteCheckedException {
        // No-op.
    }

    @Override public void remove(IgniteTx tx, GridTestKey key) throws IgniteCheckedException {
        // No-op.
    }
}
