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

package org.apache.ignite.streamer;

import org.apache.ignite.mbean.*;

/**
 * Streamer stage MBean.
 */
@IgniteMBeanDescription("MBean that provides access to streamer stage description and metrics.")
public interface StreamerStageMBean {
    /**
     * Gets stage name.
     *
     * @return Stage name.
     */
    @IgniteMBeanDescription("Stage name.")
    public String getName();

    /**
     * Gets stage class name.
     *
     * @return Stage class name.
     */
    @IgniteMBeanDescription("Stage class name.")
    public String getStageClassName();

    /**
     * Gets stage minimum execution time.
     *
     * @return Stage minimum execution time.
     */
    @IgniteMBeanDescription("Stage minimum execution time.")
    public long getMinimumExecutionTime();

    /**
     * Gets stage maximum execution time.
     *
     * @return Stage maximum execution time.
     */
    @IgniteMBeanDescription("Stage maximum execution time.")
    public long getMaximumExecutionTime();

    /**
     * Gets stage average execution time.
     *
     * @return Stage average execution time.
     */
    @IgniteMBeanDescription("Stage average execution time.")
    public long getAverageExecutionTime();

    /**
     * Gets stage minimum waiting time.
     *
     * @return Stage minimum waiting time.
     */
    @IgniteMBeanDescription("Stage minimum waiting time.")
    public long getMinimumWaitingTime();

    /**
     * Gets stage maximum waiting time.
     *
     * @return Stage maximum waiting time.
     */
    @IgniteMBeanDescription("Stage maximum waiting time.")
    public long getMaximumWaitingTime();

    /**
     * Stage average waiting time.
     *
     * @return Stage average waiting time.
     */
    @IgniteMBeanDescription("Stage average waiting time.")
    public long getAverageWaitingTime();

    /**
     * Gets total stage execution count since last reset.
     *
     * @return Number of times this stage was executed.
     */
    @IgniteMBeanDescription("Number of times this stage was executed.")
    public long getTotalExecutionCount();

    /**
     * Gets stage failure count.
     *
     * @return Stage failure count.
     */
    @IgniteMBeanDescription("Stage failure count.")
    public int getFailuresCount();

    /**
     * Gets flag indicating if stage is being currently executed by at least one thread on current node.
     *
     * @return {@code True} if stage is executing now.
     */
    @IgniteMBeanDescription("Whether stage is currently being executed.")
    public boolean isExecuting();
}
