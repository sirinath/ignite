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
import org.gridgain.grid.kernal.processors.cache.*;

import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCacheWriteSynchronizationMode.*;

/**
 * Test basic cache operations in transactions.
 */
public class GridCachePartitionedTxMultiNodeSelfTest extends IgniteTxMultiNodeAbstractTest {
    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        // Default cache configuration.
        GridCacheConfiguration ccfg = defaultCacheConfiguration();

        ccfg.setCacheMode(PARTITIONED);
        ccfg.setWriteSynchronizationMode(FULL_SYNC);
        ccfg.setBackups(backups);

        cfg.setCacheConfiguration(ccfg);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override public void testPutOneEntryInTx() throws Exception {
        super.testPutOneEntryInTx();
    }

    /** {@inheritDoc} */
    @Override public void testPutOneEntryInTxMultiThreaded() throws Exception {
        super.testPutOneEntryInTxMultiThreaded();
    }

    /** {@inheritDoc} */
    @Override public void testPutTwoEntriesInTx() throws Exception {
        super.testPutTwoEntriesInTx();
    }

    /** {@inheritDoc} */
    @Override public void testPutTwoEntryInTxMultiThreaded() throws Exception {
        super.testPutTwoEntryInTxMultiThreaded();
    }

    /** {@inheritDoc} */
    @Override public void testRemoveInTxQueried() throws Exception {
        super.testRemoveInTxQueried();
    }

    /** {@inheritDoc} */
    @Override public void testRemoveInTxQueriedMultiThreaded() throws Exception {
        super.testRemoveInTxQueriedMultiThreaded();
    }

    /** {@inheritDoc} */
    @Override public void testRemoveInTxSimple() throws Exception {
        super.testRemoveInTxSimple();
    }
}
