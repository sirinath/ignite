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

package org.gridgain.loadtests.mapper;

import org.apache.ignite.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.compute.*;
import org.apache.ignite.resources.*;
import org.gridgain.grid.*;
import org.gridgain.grid.util.typedef.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Test task.
 */
public class GridContinuousMapperTask2 extends ComputeTaskAdapter<int[], Integer> {
    /** Grid. */
    @IgniteInstanceResource
    private Ignite g;

    /** {@inheritDoc} */
    @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid, @Nullable int[] jobIds)
        throws IgniteCheckedException {
        Map<ComputeJob, ClusterNode> mappings = new HashMap<>(jobIds.length);

        Iterator<ClusterNode> nodeIter = g.cluster().forRemotes().nodes().iterator();

        for (int jobId : jobIds) {
            ComputeJob job = new ComputeJobAdapter(jobId) {
                @IgniteInstanceResource
                private Ignite g;

                @Override public Object execute() {
                    Integer jobId = argument(0);

                    X.println(">>> Received job for ID: " + jobId);

                    return g.cache("replicated").peek(jobId);
                }
            };

            // If only local node in the grid.
            if (g.cluster().nodes().size() == 1)
                mappings.put(job, g.cluster().localNode());
            else {
                ClusterNode n = nodeIter.hasNext() ? nodeIter.next() :
                    (nodeIter = g.cluster().forRemotes().nodes().iterator()).next();

                mappings.put(job, n);
            }
        }

        return mappings;
    }

    /** {@inheritDoc} */
    @Override public ComputeJobResultPolicy result(ComputeJobResult res, List<ComputeJobResult> rcvd) throws IgniteCheckedException {
        TestObject o = res.getData();

        X.println("Received job result from node [resId=" + o.getId() + ", node=" + res.getNode().id() + ']');

        return super.result(res, rcvd);
    }

    /** {@inheritDoc} */
    @Override public Integer reduce(List<ComputeJobResult> results) throws IgniteCheckedException {
        X.println(">>> Reducing task...");

        return null;
    }
}
