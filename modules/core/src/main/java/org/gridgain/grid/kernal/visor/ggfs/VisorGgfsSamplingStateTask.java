/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.visor.ggfs;

import org.apache.ignite.lang.*;
import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.ggfs.*;
import org.gridgain.grid.kernal.processors.task.*;
import org.gridgain.grid.kernal.visor.*;
import org.gridgain.grid.util.typedef.internal.*;

/**
 * Task to set GGFS instance sampling state.
 */
@GridInternal
public class VisorGgfsSamplingStateTask extends VisorOneNodeTask<IgniteBiTuple<String, Boolean>, Void> {
    /** */
    private static final long serialVersionUID = 0L;

    /**
     * Job that perform parsing of GGFS profiler logs.
     */
    private static class VisorGgfsSamplingStateJob extends VisorJob<IgniteBiTuple<String, Boolean>, Void> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job with given argument.
         *
         * @param arg Job argument.
         */
        public VisorGgfsSamplingStateJob(IgniteBiTuple<String, Boolean> arg) {
            super(arg);
        }

        /** {@inheritDoc} */
        @Override protected Void run(IgniteBiTuple<String, Boolean> arg) throws GridException {
            try {
                ((GridGgfsEx) g.ggfs(arg.get1())).globalSampling(arg.get2());

                return null;
            }
            catch (IllegalArgumentException iae) {
                throw new GridException("Failed to set sampling state for GGFS: " + arg.get1(), iae);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorGgfsSamplingStateJob.class, this);
        }
    }

    /** {@inheritDoc} */
    @Override protected VisorGgfsSamplingStateJob job(IgniteBiTuple<String, Boolean> arg) {
        return new VisorGgfsSamplingStateJob(arg);
    }
}