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

package org.gridgain.grid.kernal.visor.ggfs;

import org.apache.ignite.fs.*;
import org.gridgain.grid.util.typedef.*;

import java.util.*;

/**
 * Various global constants for GGFS profiler.
 */
public class VisorGgfsProfiler {
    /** Default file block size to calculate uniformity. */
    public static final int UNIFORMITY_DFLT_BLOCK_SIZE = 4096;

    /** Default number of blocks to split file for uniformity calculations. */
    public static final int UNIFORMITY_BLOCKS = 100;

    /**
     * Aggregate GGFS profiler entries.
     *
     * @param entries Entries to sum.
     * @return Single aggregated entry.
     */
    public static VisorGgfsProfilerEntry aggregateGgfsProfilerEntries(List<VisorGgfsProfilerEntry> entries) {
        assert !F.isEmpty(entries);

        if (entries.size() == 1)
            return entries.get(0); // No need to aggregate.
        else {
            String path = entries.get(0).path();

            Collections.sort(entries, VisorGgfsProfilerEntry.ENTRY_TIMESTAMP_COMPARATOR);

            long timestamp = 0;
            long size = 0;
            long bytesRead = 0;
            long readTime = 0;
            long userReadTime = 0;
            long bytesWritten = 0;
            long writeTime = 0;
            long userWriteTime = 0;
            IgniteFsMode mode = null;
            VisorGgfsProfilerUniformityCounters counters = new VisorGgfsProfilerUniformityCounters();

            for (VisorGgfsProfilerEntry entry : entries) {
                // Take last timestamp.
                timestamp = entry.timestamp();

                // Take last size.
                size = entry.size();

                // Take last mode.
                mode = entry.mode();

                // Aggregate metrics.
                bytesRead += entry.bytesRead();
                readTime += entry.readTime();
                userReadTime += entry.userReadTime();
                bytesWritten += entry.bytesWritten();
                writeTime += entry.writeTime();
                userWriteTime += entry.userWriteTime();

                counters.aggregate(entry.counters());
            }

            return new VisorGgfsProfilerEntry(path, timestamp, mode, size, bytesRead, readTime, userReadTime,
                bytesWritten, writeTime, userWriteTime, counters);
        }
    }

}
