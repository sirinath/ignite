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
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Test task.
 */
@SuppressWarnings("TransientFieldNotInitialized")
@ComputeTaskNoResultCache
public class GridContinuousMapperTask1 extends ComputeTaskAdapter<Integer, Integer> {
    /** Job ID generator. */
    private final transient AtomicInteger jobIdGen = new AtomicInteger();

    /** Mapper. */
    @IgniteTaskContinuousMapperResource
    private ComputeTaskContinuousMapper mapper;

    /** Grid. */
    @IgniteInstanceResource
    private Ignite g;

    /** Blocking queue. */
    private final transient LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(10);

    /** Sent jobs count. */
    private final transient AtomicInteger sentJobs = new AtomicInteger();

    /** Maximum number of executions. */
    private transient int maxExecs;

    /** Worker thread. */
    private transient Thread t = new Thread("mapper-worker") {
        @Override public void run() {
            try {
                while (!Thread.currentThread().isInterrupted())
                    queue.put(jobIdGen.getAndIncrement());
            }
            catch (InterruptedException ignore) {
                // No-op.
            }
        }
    };

    /**
     * Sends job to node.
     *
     * @param n Node.
     * @throws IgniteCheckedException If failed.
     */
    private void sendJob(ClusterNode n) throws IgniteCheckedException {
        try {
            int jobId = queue.take();

            sentJobs.incrementAndGet();

            mapper.send(new ComputeJobAdapter(jobId) {
                @IgniteInstanceResource
                private Ignite g;

                @Override public Object execute() {
                    Integer jobId = argument(0);

                    X.println(">>> Received job for ID: " + jobId);

                    return g.cache("replicated").peek(jobId);
                }
            }, n);
        }
        catch (InterruptedException e) {
            throw new IgniteCheckedException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid, @Nullable Integer arg)
        throws IgniteCheckedException {
        maxExecs = arg;

        // Start worker thread.
        t.start();

        if (g.cluster().nodes().size() == 1)
            sendJob(g.cluster().localNode());
        else
            for (ClusterNode n : g.cluster().forRemotes().nodes())
                sendJob(n);

        return null;
    }

    /** {@inheritDoc} */
    @Override public ComputeJobResultPolicy result(ComputeJobResult res, List<ComputeJobResult> rcvd) throws IgniteCheckedException {
        if (res.getException() != null)
            throw new IgniteCheckedException(res.getException());

        TestObject o = res.getData();

        assert o != null;

        X.println("Received job result from node [resId=" + o.getId() + ", node=" + res.getNode().id() + ']');

        if (sentJobs.get() < maxExecs)
            sendJob(res.getNode());

        return ComputeJobResultPolicy.WAIT;
    }

    /** {@inheritDoc} */
    @Override public Integer reduce(List<ComputeJobResult> results) throws IgniteCheckedException {
        X.println(">>> Reducing task...");

        t.interrupt();

        try {
            t.join();
        }
        catch (InterruptedException e) {
            throw new IgniteCheckedException(e);
        }

        return null;
    }
}
