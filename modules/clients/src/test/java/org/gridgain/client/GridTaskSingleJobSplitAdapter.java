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

package org.gridgain.client;

import org.apache.ignite.*;
import org.apache.ignite.compute.*;

import java.util.*;

/**
 * Adapter for {@link org.apache.ignite.compute.ComputeTaskSplitAdapter}
 * overriding {@code split(...)} method to return singleton with self instance.
 * This adapter should be used for tasks that always splits to a single task.
 * @param <T> Type of the task execution argument.
 * @param <R> Type of the task result returning from {@link org.apache.ignite.compute.ComputeTask#reduce(List)} method.
 */
public abstract class GridTaskSingleJobSplitAdapter<T, R> extends ComputeTaskSplitAdapter<T, R> {
    /** Empty constructor. */
    protected GridTaskSingleJobSplitAdapter() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override protected Collection<? extends ComputeJob> split(final int gridSize, final T arg) throws IgniteCheckedException {
        return Collections.singleton(new ComputeJobAdapter() {
            @Override public Object execute() throws IgniteCheckedException {
                return executeJob(gridSize, arg);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public R reduce(List<ComputeJobResult> results) throws IgniteCheckedException {
        assert results.size() == 1;

        ComputeJobResult res = results.get(0);

        if (res.isCancelled())
            throw new IgniteCheckedException("Reduce receives failed job.");

        return res.getData();
    }

    /**
     * Executes this task's job.
     *
     * @param gridSize Number of available grid nodes. Note that returned number of
     *      jobs can be less, equal or greater than this grid size.
     * @param arg Task execution argument. Can be {@code null}.
     * @return Job execution result (possibly {@code null}). This result will be returned
     *      in {@link org.apache.ignite.compute.ComputeJobResult#getData()} method passed into
     *      {@link org.apache.ignite.compute.ComputeTask#result(org.apache.ignite.compute.ComputeJobResult, List)} method into task on caller node.
     * @throws IgniteCheckedException If job execution caused an exception. This exception will be
     *      returned in {@link org.apache.ignite.compute.ComputeJobResult#getException()} method passed into
     *      {@link org.apache.ignite.compute.ComputeTask#result(org.apache.ignite.compute.ComputeJobResult, List)} method into task on caller node.
     *      If execution produces a {@link RuntimeException} or {@link Error}, then
     *      it will be wrapped into {@link IgniteCheckedException}.
     */
    protected abstract Object executeJob(int gridSize, T arg) throws IgniteCheckedException;
}
