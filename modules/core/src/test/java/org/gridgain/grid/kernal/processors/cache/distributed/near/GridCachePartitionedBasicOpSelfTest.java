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

import org.apache.ignite.configuration.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;

import static org.gridgain.grid.cache.GridCacheAtomicityMode.*;
import static org.gridgain.grid.cache.GridCacheMode.*;

/**
 * Simple cache test.
 */
public class GridCachePartitionedBasicOpSelfTest extends GridCacheBasicOpAbstractTest {
    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(gridName);

        GridCacheConfiguration cc = defaultCacheConfiguration();

        cc.setCacheMode(PARTITIONED);
        cc.setWriteSynchronizationMode(GridCacheWriteSynchronizationMode.FULL_SYNC);
        cc.setBackups(2);
        cc.setPreloadMode(GridCachePreloadMode.SYNC);
        cc.setAtomicityMode(TRANSACTIONAL);

        c.setCacheConfiguration(cc);

        return c;
    }

    /** {@inheritDoc} */
    @Override public void testBasicOps() throws Exception {
        super.testBasicOps();
    }

    /** {@inheritDoc} */
    @Override public void testBasicOpsAsync() throws Exception {
        super.testBasicOpsAsync();
    }

    /** {@inheritDoc} */
    @Override public void testOptimisticTransaction() throws Exception {
        super.testOptimisticTransaction();
    }

    /** {@inheritDoc} */
    @Override public void testPutWithExpiration() throws Exception {
        super.testPutWithExpiration();
    }
}
