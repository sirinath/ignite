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

package org.gridgain.grid.kernal.processors.cache.distributed.replicated;

import org.apache.ignite.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.events.*;
import org.apache.ignite.lang.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.query.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.testframework.*;

import java.util.*;
import java.util.concurrent.*;

import static org.apache.ignite.events.IgniteEventType.*;
import static org.gridgain.grid.cache.GridCacheMode.*;

/**
 * Tests for fields queries.
 */
public class GridCacheReplicatedFieldsQuerySelfTest extends GridCacheAbstractFieldsQuerySelfTest {
    /** {@inheritDoc} */
    @Override protected GridCacheMode cacheMode() {
        return REPLICATED;
    }

    /** {@inheritDoc} */
    @Override protected int gridCount() {
        return 3;
    }

    /**
     * @throws Exception If failed.
     */
    public void testNodeLeft() throws Exception {
        hasCache = true;

        try {
            final Map<UUID, Map<Long, GridFutureAdapter<GridQueryFieldsResult>>> map =
                U.field(((GridKernal)grid(0)).internalCache().context().queries(), "fieldsQryRes");

            // Ensure that iterators map empty.
            map.clear();

            Ignite g = startGrid();

            GridCache<Integer, Integer> cache = g.cache(null);

            GridCacheQuery<List<?>> q = cache.queries().createSqlFieldsQuery("select _key from Integer where _key >= " +
                "0 order by _key");

            q.pageSize(50);

            ClusterGroup prj = g.cluster().forNodes(Arrays.asList(g.cluster().localNode(), grid(0).localNode()));

            q = q.projection(prj);

            GridCacheQueryFuture<List<?>> fut = q.execute();

            assertEquals(0, fut.next().get(0));

            // fut.nextX() does not guarantee the request has completed on remote node
            // (we could receive page from local one), so we need to wait.
            assertTrue(GridTestUtils.waitForCondition(new PA() {
                @Override public boolean apply() {
                    return map.size() == 1;
                }
            }, getTestTimeout()));

            Map<Long, GridFutureAdapter<GridQueryFieldsResult>> futs = map.get(g.cluster().localNode().id());

            assertEquals(1, futs.size());

            final UUID nodeId = g.cluster().localNode().id();
            final CountDownLatch latch = new CountDownLatch(1);

            grid(0).events().localListen(new IgnitePredicate<IgniteEvent>() {
                @Override public boolean apply(IgniteEvent evt) {
                    if (((IgniteDiscoveryEvent) evt).eventNode().id().equals(nodeId))
                        latch.countDown();

                    return true;
                }
            }, EVT_NODE_LEFT);

            stopGrid();

            latch.await();

            assertEquals(0, map.size());
        }
        finally {
            // Ensure that additional node is stopped.
            stopGrid();
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testLostIterator() throws Exception {
        GridCache<Integer, Integer> cache = grid(0).cache(null);

        GridCacheQueryFuture<List<?>> fut = null;

        for (int i = 0; i < cache.configuration().getMaximumQueryIteratorCount() + 1; i++) {
            GridCacheQuery<List<?>> q = cache.queries().createSqlFieldsQuery(
                "select _key from Integer where _key >= 0 order by _key").projection(grid(0));

            q.pageSize(50);

            GridCacheQueryFuture<List<?>> f = q.execute();

            assertEquals(0, f.next().get(0));

            if (fut == null)
                fut = f;
        }

        final GridCacheQueryFuture<List<?>> fut0 = fut;

        GridTestUtils.assertThrows(log, new Callable<Object>() {
            @Override public Object call() throws Exception {
                int i = 0;

                List<?> next;

                while ((next = fut0.next()) != null)
                    assertEquals(++i % 50, next.get(0));

                return null;
            }
        }, IgniteException.class, null);
    }
}
