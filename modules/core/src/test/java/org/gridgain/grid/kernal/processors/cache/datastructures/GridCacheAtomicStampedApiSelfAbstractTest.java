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

package org.gridgain.grid.kernal.processors.cache.datastructures;

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.testframework.junits.common.*;

import java.util.*;

/**
 * Basic tests for atomic stamped.
 */
public abstract class GridCacheAtomicStampedApiSelfAbstractTest extends GridCommonAbstractTest {
    /** */
    protected static TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /**
     * Constructs a test.
     */
    protected GridCacheAtomicStampedApiSelfAbstractTest() {
        super(true /* start grid. */);
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration() throws Exception {
        IgniteConfiguration cfg = super.getConfiguration();

        TcpDiscoverySpi spi = new TcpDiscoverySpi();

        spi.setIpFinder(ipFinder);

        cfg.setDiscoverySpi(spi);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        stopAllGrids();
    }

    /**
     * JUnit.
     *
     * @throws Exception If failed.
     */
    public void testPrepareAtomicStamped() throws Exception {
        /** Name of first atomic. */
        String atomicName1 = UUID.randomUUID().toString();

        String initVal = "1";
        String initStamp = "2";

        GridCacheAtomicStamped<String, String> atomic1 = grid().cache(null).dataStructures()
            .atomicStamped(atomicName1, initVal, initStamp, true);
        GridCacheAtomicStamped<String, String> atomic2 = grid().cache(null).dataStructures()
            .atomicStamped(atomicName1, null, null, true);

        assertNotNull(atomic1);
        assertNotNull(atomic2);
        assert atomic1.equals(atomic2);
        assert atomic2.equals(atomic1);

        assert initVal.equals(atomic2.value());
        assert initStamp.equals(atomic2.stamp());

        assert grid().cache(null).dataStructures().removeAtomicStamped(atomicName1);
        assert !grid().cache(null).dataStructures().removeAtomicStamped(atomicName1);

        try {
            atomic1.get();
            fail();
        }
        catch (IgniteCheckedException e) {
            info("Caught expected exception: " + e.getMessage());
        }
    }

    /**
     * JUnit.
     *
     * @throws Exception If failed.
     */
    public void testSetAndGet() throws Exception {
        String atomicName = UUID.randomUUID().toString();

        String initVal = "qwerty";
        String initStamp = "asdfgh";

        GridCacheAtomicStamped<String, String> atomic = grid().cache(null).dataStructures()
            .atomicStamped(atomicName, initVal, initStamp, true);

        assertEquals(initVal, atomic.value());
        assertEquals(initStamp, atomic.stamp());
        assertEquals(initVal, atomic.get().get1());
        assertEquals(initStamp, atomic.get().get2());

        atomic.set(null, null);

        assertEquals(null, atomic.value());
        assertEquals(null, atomic.stamp());
    }

    /**
     * JUnit.
     *
     * @throws Exception If failed.
     */
    public void testCompareAndSetSimpleValue() throws Exception {
        String atomicName = UUID.randomUUID().toString();

        String initVal = "qwerty";
        String initStamp = "asdfgh";

        GridCacheAtomicStamped<String, String> atomic = grid().cache(null).dataStructures()
            .atomicStamped(atomicName, initVal, initStamp, true);

        assertEquals(initVal, atomic.value());
        assertEquals(initStamp, atomic.stamp());
        assertEquals(initVal, atomic.get().get1());
        assertEquals(initStamp, atomic.get().get2());

        atomic.compareAndSet("a", "b", "c", "d");

        assertEquals(initVal, atomic.value());
        assertEquals(initStamp, atomic.stamp());

        atomic.compareAndSet(initVal, null, initStamp, null);

        assertEquals(null, atomic.value());
        assertEquals(null, atomic.stamp());
    }
}
