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

package org.gridgain.grid.kernal.processors.ggfs;

import org.apache.ignite.*;
import org.gridgain.grid.*;

import java.util.concurrent.atomic.*;

/**
 * Abstract class for GGFS managers.
 */
public abstract class GridGgfsManager {
    /** GGFS context. */
    protected GridGgfsContext ggfsCtx;

    /** Logger. */
    protected IgniteLogger log;

    /** Starting flag. */
    private AtomicBoolean starting = new AtomicBoolean();

    /**
     * Called when GGFS processor is started.
     *
     * @param ggfsCtx GGFS context.
     */
    public void start(GridGgfsContext ggfsCtx) throws IgniteCheckedException {
        if (!starting.compareAndSet(false, true))
            assert false : "Method start is called more than once for manager: " + this;

        assert ggfsCtx != null;

        this.ggfsCtx = ggfsCtx;

        log = ggfsCtx.kernalContext().log(getClass());

        start0();

        if (log != null && log.isDebugEnabled())
            log.debug(startInfo());
    }

    /**
     * Stops manager.
     *
     * @param cancel Cancel flag.
     */
    public final void stop(boolean cancel) {
        if (!starting.get())
            // Ignoring attempt to stop manager that has never been started.
            return;

        stop0(cancel);

        if (log != null && log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public final void onKernalStart() throws IgniteCheckedException {
        onKernalStart0();

        if (log != null && log.isDebugEnabled())
            log.debug(kernalStartInfo());
    }

    /**
     * @param cancel Cancel flag.
     */
    public final void onKernalStop(boolean cancel) {
        if (!starting.get())
            // Ignoring attempt to stop manager that has never been started.
            return;

        onKernalStop0(cancel);

        if (log != null && log.isDebugEnabled())
            log.debug(kernalStopInfo());
    }

    /**
     * Start manager implementation.
     */
    protected void start0() throws IgniteCheckedException {
        // No-op by default.
    }

    /**
     * Stop manager implementation.
     *
     * @param cancel Cancel flag.
     */
    protected void stop0(boolean cancel) {
        // No-op by default.
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    protected void onKernalStart0() throws IgniteCheckedException {
        // No-op.
    }

    /**
     *
     */
    protected void onKernalStop0(boolean cancel) {
        // No-op.
    }

    /**
     * @return Start info.
     */
    protected String startInfo() {
        return "Cache manager started: " + getClass().getSimpleName();
    }

    /**
     * @return Stop info.
     */
    protected String stopInfo() {
        return "Cache manager stopped: " + getClass().getSimpleName();
    }

    /**
     * @return Start info.
     */
    protected String kernalStartInfo() {
        return "Cache manager received onKernalStart() callback: " + getClass().getSimpleName();
    }

    /**
     * @return Stop info.
     */
    protected String kernalStopInfo() {
        return "Cache manager received onKernalStop() callback: " + getClass().getSimpleName();
    }
}
