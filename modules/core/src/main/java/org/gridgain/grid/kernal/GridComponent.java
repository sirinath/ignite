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

package org.gridgain.grid.kernal;

import org.apache.ignite.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.spi.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Interface for all main internal GridGain components (managers and processors).
 */
public interface GridComponent {
    /**
     * Starts grid component.
     *
     * @throws IgniteCheckedException Throws in case of any errors.
     */
    public void start() throws IgniteCheckedException;

    /**
     * Stops grid component.
     *
     * @param cancel If {@code true}, then all ongoing tasks or jobs for relevant
     *      components need to be cancelled.
     * @throws IgniteCheckedException Thrown in case of any errors.
     */
    public void stop(boolean cancel) throws IgniteCheckedException;

    /**
     * Callback that notifies that kernal has successfully started,
     * including all managers and processors.
     *
     * @throws IgniteCheckedException Thrown in case of any errors.
     */
    public void onKernalStart() throws IgniteCheckedException;

    /**
     * Callback to notify that kernal is about to stop.
     *
     * @param cancel Flag indicating whether jobs should be canceled.
     */
    public void onKernalStop(boolean cancel);

    /**
     * Gets discovery data object that will be sent to new node
     * during discovery process.
     *
     * @param nodeId ID of new node that joins topology.
     * @return Discovery data object or {@code null} if there is nothing
     *      to send for this component.
     */
    @Nullable public Object collectDiscoveryData(UUID nodeId);

    /**
     * Receives discovery data object from remote nodes (called
     * on new node during discovery process).
     *
     * @param data Discovery data object or {@code null} if nothing was
     *      sent for this component.
     */
    public void onDiscoveryDataReceived(Object data);

    /**
     * Prints memory statistics (sizes of internal structures, etc.).
     *
     * NOTE: this method is for testing and profiling purposes only.
     */
    public void printMemoryStats();

    /**
     * Validates that new node can join grid topology, this method is called on coordinator
     * node before new node joins topology.
     *
     * @param node Joining node.
     * @return Validation result or {@code null} in case of success.
     */
    @Nullable public IgniteSpiNodeValidationResult validateNode(ClusterNode node);
}
