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

package org.gridgain.grid.kernal.processors.cache;

import org.apache.ignite.lang.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.typedef.*;

import java.util.concurrent.atomic.*;

/**
 * Checks that number of concurrent asynchronous operations is limited when configuration parameter is set.
 */
public class GridCacheAsyncOperationsLimitSelfTest extends GridCacheAbstractSelfTest {
    /** */
    public static final int MAX_CONCURRENT_ASYNC_OPS = 50;

    /** {@inheritDoc} */
    @Override protected int gridCount() {
        return 3;
    }

    /** {@inheritDoc} */
    @Override protected GridCacheConfiguration cacheConfiguration(String gridName) throws Exception {
        GridCacheConfiguration cCfg = super.cacheConfiguration(gridName);

        cCfg.setMaxConcurrentAsyncOperations(MAX_CONCURRENT_ASYNC_OPS);

        return cCfg;
    }

    /**
     * @throws Exception If failed.
     */
    public void testAsyncOps() throws Exception {
        final AtomicInteger cnt = new AtomicInteger();
        final GridAtomicInteger max = new GridAtomicInteger();

        for (int i = 0; i < 5000; i++) {
            final int i0 = i;

            cnt.incrementAndGet();

            IgniteFuture<Boolean> fut = cache().putxAsync("key" + i, i);

            fut.listenAsync(new CI1<IgniteFuture<Boolean>>() {
                @Override public void apply(IgniteFuture<Boolean> t) {
                    cnt.decrementAndGet();

                    max.setIfGreater(cnt.get());

                    if (i0 > 0 && i0 % 100 == 0)
                        info("cnt: " + cnt.get());
                }
            });

            assertTrue("Maximum number of permits exceeded: " + max.get(),  max.get() <= 51);
        }
    }
}
