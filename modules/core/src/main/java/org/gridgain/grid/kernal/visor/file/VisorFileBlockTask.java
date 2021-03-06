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

package org.gridgain.grid.kernal.visor.file;

import org.apache.ignite.*;
import org.apache.ignite.lang.*;
import org.gridgain.grid.kernal.processors.task.*;
import org.gridgain.grid.kernal.visor.*;
import org.gridgain.grid.util.typedef.internal.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;

import static org.gridgain.grid.kernal.visor.util.VisorTaskUtils.*;

/**
 * Task to read file block.
 */
@GridInternal
public class VisorFileBlockTask extends VisorOneNodeTask<VisorFileBlockTask.VisorFileBlockArg,
    IgniteBiTuple<? extends IOException, VisorFileBlock>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorFileBlockJob job(VisorFileBlockArg arg) {
        return new VisorFileBlockJob(arg, debug);
    }

    /**
     * Arguments for {@link VisorFileBlockTask}
     */
    @SuppressWarnings("PublicInnerClass")
    public static class VisorFileBlockArg implements Serializable {
        /** */
        private static final long serialVersionUID = 0L;

        /** Log file path. */
        private final String path;

        /** Log file offset. */
        private final long offset;

        /** Block size. */
        private final int blockSz;

        /** Log file last modified timestamp. */
        private final long lastModified;

        /**
         * @param path Log file path.
         * @param offset Offset in file.
         * @param blockSz Block size.
         * @param lastModified Log file last modified timestamp.
         */
        public VisorFileBlockArg(String path, long offset, int blockSz, long lastModified) {
            this.path = path;
            this.offset = offset;
            this.blockSz = blockSz;
            this.lastModified = lastModified;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorFileBlockArg.class, this);
        }
    }

    /**
     * Job that read file block on node.
     */
    private static class VisorFileBlockJob
        extends VisorJob<VisorFileBlockArg, IgniteBiTuple<? extends IOException, VisorFileBlock>> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * @param arg Descriptor of file block to read.
         * @param debug Debug flag.
         */
        private VisorFileBlockJob(VisorFileBlockArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected IgniteBiTuple<? extends IOException, VisorFileBlock> run(VisorFileBlockArg arg) throws IgniteCheckedException {
            try {
                URL url = U.resolveGridGainUrl(arg.path);

                if (url == null)
                    return new IgniteBiTuple<>(new NoSuchFileException("File path not found: " + arg.path), null);

                VisorFileBlock block = readBlock(new File(url.toURI()), arg.offset, arg.blockSz, arg.lastModified);

                return new IgniteBiTuple<>(null, block);
            }
            catch (IOException e) {
                return new IgniteBiTuple<>(e, null);
            }
            catch (URISyntaxException ignored) {
                return new IgniteBiTuple<>(new NoSuchFileException("File path not found: " + arg.path), null);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorFileBlockJob.class, this);
        }
    }
}
