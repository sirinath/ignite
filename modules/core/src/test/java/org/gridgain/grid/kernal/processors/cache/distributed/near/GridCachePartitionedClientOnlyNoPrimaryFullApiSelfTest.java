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

import org.apache.ignite.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.lang.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.util.typedef.*;

import java.util.*;

/**
 * Tests for local cache.
 */
public class GridCachePartitionedClientOnlyNoPrimaryFullApiSelfTest extends GridCachePartitionedFullApiSelfTest {
    /** {@inheritDoc} */
    @Override protected GridCacheDistributionMode distributionMode() {
        return GridCacheDistributionMode.CLIENT_ONLY;
    }

    /**
     *
     */
    public void testMapKeysToNodes() {
        cache().affinity().mapKeysToNodes(Arrays.asList("1", "2"));
    }

    /**
     *
     */
    public void testMapKeyToNode() {
        assert cache().affinity().mapKeyToNode("1") == null;
    }

    /**
     * @return Handler that discards grid exceptions.
     */
    @Override protected IgniteClosure<Throwable, Throwable> errorHandler() {
        return new IgniteClosure<Throwable, Throwable>() {
            @Override public Throwable apply(Throwable e) {
                if (e instanceof IgniteCheckedException || X.hasCause(e, ClusterTopologyException.class)) {
                    info("Discarding exception: " + e);

                    return null;
                }
                else
                    return e;
            }
        };
    }
}
