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

package org.gridgain.grid.kernal.processors.task;

import org.gridgain.grid.kernal.*;
import java.util.*;

/**
 * Listener for task events.
 */
interface GridTaskEventListener extends EventListener {
    /**
     * @param worker Started grid task worker.
     */
    public void onTaskStarted(GridTaskWorker<?, ?> worker);

    /**
     * @param worker Grid task worker.
     * @param sib Job sibling.
     */
    public void onJobSend(GridTaskWorker<?, ?> worker, GridJobSiblingImpl sib);

    /**
     * @param worker Grid task worker.
     * @param sib Job sibling.
     * @param nodeId Failover node ID.
     */
    public void onJobFailover(GridTaskWorker<?, ?> worker, GridJobSiblingImpl sib, UUID nodeId);

    /**
     * @param worker Grid task worker.
     * @param sib Job sibling.
     */
    public void onJobFinished(GridTaskWorker<?, ?> worker, GridJobSiblingImpl sib);

    /**
     * @param worker Task worker for finished grid task.
     */
    public void onTaskFinished(GridTaskWorker<?, ?> worker);
}
