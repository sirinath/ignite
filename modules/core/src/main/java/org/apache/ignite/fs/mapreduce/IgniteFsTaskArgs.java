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

package org.apache.ignite.fs.mapreduce;

import org.apache.ignite.fs.*;

import java.util.*;

/**
 * GGFS task arguments. When you initiate new GGFS task execution using one of {@code GridGgfs.execute(...)} methods,
 * all passed parameters are encapsulated in a single {@code GridGgfsTaskArgs} object. Later on this object is
 * passed to {@link IgniteFsTask#createJob(org.apache.ignite.fs.IgniteFsPath, IgniteFsFileRange, IgniteFsTaskArgs)} method.
 * <p>
 * Task arguments encapsulates the following data:
 * <ul>
 *     <li>GGFS name</li>
 *     <li>File paths passed to {@code GridGgfs.execute()} method</li>
 *     <li>{@link IgniteFsRecordResolver} for that task</li>
 *     <li>Flag indicating whether to skip non-existent file paths or throw an exception</li>
 *     <li>User-defined task argument</li>
 *     <li>Maximum file range length for that task (see {@link org.apache.ignite.fs.IgniteFsConfiguration#getMaximumTaskRangeLength()})</li>
 * </ul>
 */
public interface IgniteFsTaskArgs<T> {
    /**
     * Gets GGFS name.
     *
     * @return GGFS name.
     */
    public String ggfsName();

    /**
     * Gets file paths to process.
     *
     * @return File paths to process.
     */
    public Collection<IgniteFsPath> paths();

    /**
     * Gets record resolver for the task.
     *
     * @return Record resolver.
     */
    public IgniteFsRecordResolver recordResolver();

    /**
     * Flag indicating whether to fail or simply skip non-existent files.
     *
     * @return {@code True} if non-existent files should be skipped.
     */
    public boolean skipNonExistentFiles();

    /**
     * User argument provided for task execution.
     *
     * @return User argument.
     */
    public T userArgument();

    /**
     * Optional maximum allowed range length, {@code 0} by default. If not specified, full range including
     * all consecutive blocks will be used without any limitations.
     *
     * @return Maximum range length.
     */
    public long maxRangeLength();
}
