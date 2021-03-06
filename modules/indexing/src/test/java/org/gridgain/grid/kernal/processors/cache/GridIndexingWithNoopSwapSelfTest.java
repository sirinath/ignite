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

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.spi.swapspace.noop.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.eviction.fifo.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.kernal.processors.cache.GridCacheAbstractQuerySelfTest.*;
import org.gridgain.testframework.junits.common.*;

import java.util.*;

import static org.gridgain.grid.cache.GridCacheAtomicityMode.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCachePreloadMode.*;

/**
 * GG-4368
 */
public class GridIndexingWithNoopSwapSelfTest extends GridCommonAbstractTest {
    /** */
    private static final TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** */
    protected Ignite ignite;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(gridName);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(ipFinder);

        c.setDiscoverySpi(disco);

        c.setSwapSpaceSpi(new NoopSwapSpaceSpi());

        GridCacheConfiguration cc = defaultCacheConfiguration();

        cc.setCacheMode(PARTITIONED);
        cc.setWriteSynchronizationMode(GridCacheWriteSynchronizationMode.FULL_SYNC);
        cc.setPreloadMode(SYNC);
        cc.setSwapEnabled(true);
        cc.setDistributionMode(GridCacheDistributionMode.NEAR_PARTITIONED);
        cc.setEvictNearSynchronized(false);
        cc.setEvictionPolicy(new GridCacheFifoEvictionPolicy(1000));
        cc.setBackups(1);
        cc.setAtomicityMode(TRANSACTIONAL);

        GridCacheQueryConfiguration qcfg = new GridCacheQueryConfiguration();

        qcfg.setIndexPrimitiveKey(true);

        cc.setQueryConfiguration(qcfg);

        c.setCacheConfiguration(cc);

        return c;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        ignite = startGrid();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();

        ignite = null;
    }

    /** @throws Exception If failed. */
    public void testQuery() throws Exception {
        GridCache<Integer, ObjectValue> cache = ignite.cache(null);

        int cnt = 10;

        for (int i = 0; i < cnt; i++)
            cache.putx(i, new ObjectValue("test" + i, i));

        for (int i = 0; i < cnt; i++) {
            assertNotNull(cache.peek(i));

            cache.evict(i); // Swap.
        }

        GridCacheQuery<Map.Entry<Integer, ObjectValue>> qry =
            cache.queries().createSqlQuery(ObjectValue.class, "intVal >= ? order by intVal");

        qry.enableDedup(true);

        assertEquals(0, qry.execute(0).get().size());
    }
}
