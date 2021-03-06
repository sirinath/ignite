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

package org.apache.ignite.spi.deployment.uri.tasks;

import org.apache.ignite.*;
import org.apache.ignite.compute.*;
import org.gridgain.grid.*;

import java.util.*;

/**
 * URI deployment test task with name.
 */
@ComputeTaskName("GridUriDeploymentTestWithNameTask0")
public class GridUriDeploymentTestWithNameTask0 extends ComputeTaskSplitAdapter<Object, Object> {
    /**
     * {@inheritDoc}
     */
    @Override public Collection<? extends ComputeJob> split(int gridSize, Object arg) throws IgniteCheckedException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override public Object reduce(List<ComputeJobResult> results) throws IgniteCheckedException {
        return null;
    }
}
