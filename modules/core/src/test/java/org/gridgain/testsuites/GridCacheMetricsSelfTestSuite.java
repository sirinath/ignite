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
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.kernal.processors.cache.distributed.replicated.*;
import org.gridgain.grid.kernal.processors.cache.local.*;

/**
 * Test suite for cache metrics.
 */
public class GridCacheMetricsSelfTestSuite extends TestSuite {
    /**
     * @return Cache metrics test suite.
     * @throws Exception If failed.
     */
    public static TestSuite suite() throws Exception {
        TestSuite suite = new TestSuite("Gridgain Cache Metrics Test Suite");

//        suite.addTest(new TestSuite(GridCacheLocalMetricsSelfTest.class));
        suite.addTest(new TestSuite(GridCacheNearMetricsSelfTest.class));
        suite.addTest(new TestSuite(GridCacheReplicatedMetricsSelfTest.class));
        suite.addTest(new TestSuite(GridCachePartitionedMetricsSelfTest.class));

        // Atomic cache.
        suite.addTest(new TestSuite(GridCacheAtomicLocalMetricsSelfTest.class));
        suite.addTest(new TestSuite(GridCacheAtomicLocalMetricsNoStoreSelfTest.class));
        suite.addTest(new TestSuite(GridCacheAtomicReplicatedMetricsSelfTest.class));
        suite.addTest(new TestSuite(GridCacheAtomicPartitionedMetricsSelfTest.class));

        return suite;
    }
}
