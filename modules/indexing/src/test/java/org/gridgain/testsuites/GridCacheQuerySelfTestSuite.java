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

package org.gridgain.testsuites;

import junit.framework.*;
import org.apache.ignite.spi.communication.tcp.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.kernal.processors.cache.distributed.replicated.*;
import org.gridgain.grid.kernal.processors.cache.local.*;
import org.gridgain.grid.kernal.processors.cache.query.*;
import org.gridgain.grid.kernal.processors.cache.query.continuous.*;
import org.gridgain.grid.kernal.processors.cache.query.reducefields.*;

/**
 * Test suite for cache queries.
 */
public class GridCacheQuerySelfTestSuite extends TestSuite {
    /**
     * @return Test suite.
     * @throws Exception If failed.
     */
    public static TestSuite suite() throws Exception {
        TestSuite suite = new TestSuite("Gridgain Cache Queries Test Suite");

        // Queries tests.
        suite.addTestSuite(GridCacheQueryLoadSelfTest.class);
        suite.addTestSuite(GridCacheQueryMetricsSelfTest.class);
        suite.addTestSuite(GridCacheQueryUserResourceSelfTest.class);
        suite.addTestSuite(GridCacheLocalQuerySelfTest.class);
        suite.addTestSuite(GridCacheLocalAtomicQuerySelfTest.class);
        suite.addTestSuite(GridCacheReplicatedQuerySelfTest.class);
        suite.addTestSuite(GridCacheReplicatedQueryP2PDisabledSelfTest.class);
        suite.addTestSuite(GridCachePartitionedQuerySelfTest.class);
        suite.addTestSuite(GridCacheAtomicQuerySelfTest.class);
        suite.addTestSuite(GridCacheAtomicNearEnabledQuerySelfTest.class);
        suite.addTestSuite(GridCachePartitionedQueryP2PDisabledSelfTest.class);
        suite.addTestSuite(GridCachePartitionedQueryMultiThreadedSelfTest.class);
        suite.addTestSuite(GridCacheQueryIndexSelfTest.class);
        suite.addTestSuite(GridCacheQueryInternalKeysSelfTest.class);
        suite.addTestSuite(GridCacheQueryMultiThreadedSelfTest.class);
        suite.addTestSuite(GridCacheQueryEvictsMultiThreadedSelfTest.class);
        suite.addTestSuite(GridCacheQueryOffheapMultiThreadedSelfTest.class);
        suite.addTestSuite(GridCacheQueryOffheapEvictsMultiThreadedSelfTest.class);
        suite.addTestSuite(GridCacheQueryNodeRestartSelfTest.class);
        suite.addTestSuite(GridCacheReduceQueryMultithreadedSelfTest.class);
        suite.addTestSuite(GridCacheCrossCacheQuerySelfTest.class);
        suite.addTestSuite(GridCacheSqlQueryMultiThreadedSelfTest.class);

        // Fields queries.
        suite.addTestSuite(GridCacheLocalFieldsQuerySelfTest.class);
        suite.addTestSuite(GridCacheReplicatedFieldsQuerySelfTest.class);
        suite.addTestSuite(GridCacheReplicatedFieldsQueryP2PDisabledSelfTest.class);
        suite.addTestSuite(GridCachePartitionedFieldsQuerySelfTest.class);
        suite.addTestSuite(GridCacheAtomicFieldsQuerySelfTest.class);
        suite.addTestSuite(GridCacheAtomicNearEnabledFieldsQuerySelfTest.class);
        suite.addTestSuite(GridCachePartitionedFieldsQueryP2PDisabledSelfTest.class);
        suite.addTestSuite(GridCacheFieldsQueryNoDataSelfTest.class);

        // Continuous queries.
        suite.addTestSuite(GridCacheContinuousQueryLocalSelfTest.class);
        suite.addTestSuite(GridCacheContinuousQueryLocalAtomicSelfTest.class);
        suite.addTestSuite(GridCacheContinuousQueryReplicatedSelfTest.class);
        suite.addTestSuite(GridCacheContinuousQueryReplicatedAtomicSelfTest.class);
        suite.addTestSuite(GridCacheContinuousQueryReplicatedP2PDisabledSelfTest.class);
        suite.addTestSuite(GridCacheContinuousQueryPartitionedSelfTest.class);
        suite.addTestSuite(GridCacheContinuousQueryPartitionedOnlySelfTest.class);
        suite.addTestSuite(GridCacheContinuousQueryPartitionedP2PDisabledSelfTest.class);
        suite.addTestSuite(GridCacheContinuousQueryAtomicSelfTest.class);
        suite.addTestSuite(GridCacheContinuousQueryAtomicNearEnabledSelfTest.class);
        suite.addTestSuite(GridCacheContinuousQueryAtomicP2PDisabledSelfTest.class);

        // Reduce fields queries.
        suite.addTestSuite(GridCacheReduceFieldsQueryLocalSelfTest.class);
        suite.addTestSuite(GridCacheReduceFieldsQueryPartitionedSelfTest.class);
        suite.addTestSuite(GridCacheReduceFieldsQueryAtomicSelfTest.class);
        suite.addTestSuite(GridCacheReduceFieldsQueryReplicatedSelfTest.class);

        suite.addTestSuite(GridCacheQueryIndexingDisabledSelfTest.class);

        suite.addTestSuite(GridCacheSwapScanQuerySelfTest.class);

        suite.addTestSuite(GridOrderedMessageCancelSelfTest.class);

        return suite;
    }
}
