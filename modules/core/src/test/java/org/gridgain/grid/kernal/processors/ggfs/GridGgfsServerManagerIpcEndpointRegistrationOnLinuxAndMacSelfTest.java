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

package org.gridgain.grid.kernal.processors.ggfs;

import org.apache.ignite.configuration.*;
import org.gridgain.grid.util.typedef.*;

import static org.apache.ignite.fs.IgniteFsConfiguration.*;

/**
 * Tests for {@link GridGgfsServer} that checks all IPC endpoint registration types
 * permitted for Linux and Mac OS.
 */
public class GridGgfsServerManagerIpcEndpointRegistrationOnLinuxAndMacSelfTest
    extends GridGgfsServerManagerIpcEndpointRegistrationAbstractSelfTest {
    /**
     * @throws Exception If failed.
     */
    public void testLoopbackAndShmemEndpointsRegistration() throws Exception {
        IgniteConfiguration cfg = gridConfiguration();

        cfg.setGgfsConfiguration(
            gridGgfsConfiguration(null), // Check null IPC endpoint config won't bring any hassles.
            gridGgfsConfiguration("{type:'tcp', port:" + (DFLT_IPC_PORT + 1) + "}"),
            gridGgfsConfiguration("{type:'shmem', port:" + (DFLT_IPC_PORT + 2) + "}"));

        G.start(cfg);

        T2<Integer, Integer> res = checkRegisteredIpcEndpoints();

        // 1 regular + 3 management TCP endpoins.
        assertEquals(4, res.get1().intValue());
        assertEquals(2, res.get2().intValue());
    }
}
