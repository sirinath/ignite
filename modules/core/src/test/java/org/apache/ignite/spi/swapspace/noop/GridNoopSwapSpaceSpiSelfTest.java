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

package org.apache.ignite.spi.swapspace.noop;

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.spi.swapspace.*;
import org.gridgain.testframework.junits.common.*;

/**
 * Tests for "noop" realization of {@link org.apache.ignite.spi.swapspace.SwapSpaceSpi}.
 */
public class GridNoopSwapSpaceSpiSelfTest extends GridCommonAbstractTest {
    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(new TcpDiscoveryVmIpFinder(true));

        cfg.setDiscoverySpi(disco);

        return cfg;
    }

    /**
     * @throws Exception If test failed.
     */
    public void testWithoutCacheUseNoopSwapSapce() throws Exception {
        try {
            Ignite ignite = startGrid(1);

            SwapSpaceSpi spi = ignite.configuration().getSwapSpaceSpi();

            assertNotNull(spi);

            assertTrue(spi instanceof NoopSwapSpaceSpi);
        }
        finally {
            stopAllGrids();
        }
    }
}
