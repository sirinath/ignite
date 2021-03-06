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

package org.gridgain.grid.kernal.processors.job;

import org.apache.ignite.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.compute.*;
import org.apache.ignite.events.*;
import org.apache.ignite.fs.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.marshaller.*;
import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.processors.task.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.apache.ignite.events.IgniteEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;

/**
 * Job worker.
 */
public class GridJobWorker extends GridWorker implements GridTimeoutObject {
    /** Per-thread held flag. */
    private static final ThreadLocal<Boolean> HOLD = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() {
            return false;
        }
    };

    /** Static logger to avoid re-creation. */
    private static final AtomicReference<IgniteLogger> logRef = new AtomicReference<>();

    /** */
    private final long createTime;

    /** */
    private volatile long startTime;

    /** */
    private volatile long finishTime;

    /** */
    private final GridKernalContext ctx;

    /** */
    private final Object jobTopic;

    /** */
    private final Object taskTopic;

    /** */
    private byte[] jobBytes;

    /** Task originating node. */
    private final ClusterNode taskNode;

    /** Flag set when visor or internal task is running. */
    private final boolean internal;

    /** */
    private final IgniteLogger log;

    /** */
    private final IgniteMarshaller marsh;

    /** */
    private final GridJobSessionImpl ses;

    /** */
    private final GridJobContextImpl jobCtx;

    /** */
    private final GridJobEventListener evtLsnr;

    /** Deployment. */
    private final GridDeployment dep;

    /** */
    private final AtomicBoolean finishing = new AtomicBoolean();

    /** Guard ensuring that master-leave callback is not execute more than once. */
    private final AtomicBoolean masterLeaveGuard = new AtomicBoolean();

    /** */
    private volatile boolean timedOut;

    /** */
    private volatile boolean sysCancelled;

    /** */
    private volatile boolean sysStopping;

    /** */
    private volatile boolean isStarted;

    /** Deployed job. */
    private ComputeJob job;

    /** Halted flag (if greater than 0, job is halted). */
    private final AtomicInteger held = new AtomicInteger();

    /** Hold/unhold listener to notify job processor. */
    private final GridJobHoldListener holdLsnr;

    /**
     * @param ctx Kernal context.
     * @param dep Grid deployment.
     * @param createTime Create time.
     * @param ses Grid task session.
     * @param jobCtx Job context.
     * @param jobBytes Grid job bytes.
     * @param job Job.
     * @param taskNode Grid task node.
     * @param internal Whether or not task was marked with {@link GridInternal}
     * @param evtLsnr Job event listener.
     * @param holdLsnr Hold listener.
     */
    GridJobWorker(
        GridKernalContext ctx,
        GridDeployment dep,
        long createTime,
        GridJobSessionImpl ses,
        GridJobContextImpl jobCtx,
        byte[] jobBytes,
        ComputeJob job,
        ClusterNode taskNode,
        boolean internal,
        GridJobEventListener evtLsnr,
        GridJobHoldListener holdLsnr) {
        super(ctx.gridName(), "grid-job-worker", ctx.log());

        assert ctx != null;
        assert ses != null;
        assert jobCtx != null;
        assert taskNode != null;
        assert evtLsnr != null;
        assert dep != null;
        assert holdLsnr != null;

        this.ctx = ctx;
        this.createTime = createTime;
        this.evtLsnr = evtLsnr;
        this.dep = dep;
        this.ses = ses;
        this.jobCtx = jobCtx;
        this.jobBytes = jobBytes;
        this.taskNode = taskNode;
        this.internal = internal;
        this.holdLsnr = holdLsnr;

        if (job != null)
            this.job = job;

        log = U.logger(ctx, logRef, this);

        marsh = ctx.config().getMarshaller();

        UUID locNodeId = ctx.discovery().localNode().id();

        jobTopic = TOPIC_JOB.topic(ses.getJobId(), locNodeId);
        taskTopic = TOPIC_TASK.topic(ses.getJobId(), locNodeId);
    }

    /**
     * Gets deployed job or {@code null} of job could not be deployed.
     *
     * @return Deployed job.
     */
    @Nullable public ComputeJob getJob() {
        return job;
    }

    /**
     * @return Deployed task.
     */
    public GridDeployment getDeployment() {
        return dep;
    }

    /**
     * Returns {@code True} if job was cancelled by the system.
     *
     * @return {@code True} if job was cancelled by the system.
     */
    boolean isSystemCanceled() {
        return sysCancelled;
    }

    /**
     * @return Create time.
     */
    long getCreateTime() {
        return createTime;
    }

    /**
     * @return Unique job ID.
     */
    public IgniteUuid getJobId() {
        IgniteUuid jobId = ses.getJobId();

        assert jobId != null;

        return jobId;
    }

    /**
     * @return Job context.
     */
    public ComputeJobContext getJobContext() {
        return jobCtx;
    }

    /**
     * @return Job communication topic.
     */
    Object getJobTopic() {
        return jobTopic;
    }

    /**
     * @return Task communication topic.
     */
    Object getTaskTopic() {
        return taskTopic;
    }

    /**
     * @return Session.
     */
    public GridJobSessionImpl getSession() {
        return ses;
    }

    /**
     * Gets job finishing state.
     *
     * @return {@code true} if job is being finished after execution
     *      and {@code false} otherwise.
     */
    boolean isFinishing() {
        return finishing.get();
    }

    /**
     * @return Parent task node ID.
     */
    ClusterNode getTaskNode() {
        return taskNode;
    }

    /**
     * @return Job execution time.
     */
    long getExecuteTime() {
        long startTime0 = startTime;
        long finishTime0 = finishTime;

        return startTime0 == 0 ? 0 : finishTime0 == 0 ?
            U.currentTimeMillis() - startTime0 : finishTime0 - startTime0;
    }

    /**
     * @return Time job spent on waiting queue.
     */
    long getQueuedTime() {
        long startTime0 = startTime;

        return startTime0 == 0 ? U.currentTimeMillis() - createTime : startTime0 - createTime;
    }

    /** {@inheritDoc} */
    @Override public long endTime() {
        return ses.getEndTime();
    }

    /** {@inheritDoc} */
    @Override public IgniteUuid timeoutId() {
        IgniteUuid jobId = ses.getJobId();

        assert jobId != null;

        return jobId;
    }

    /**
     * @return {@code True} if job is timed out.
     */
    boolean isTimedOut() {
        return timedOut;
    }

    /**
     * @return {@code True} if parent task is internal or Visor-related.
     */
    public boolean isInternal() {
        return internal;
    }

    /** {@inheritDoc} */
    @Override public void onTimeout() {
        if (finishing.get())
            return;

        timedOut = true;

        U.warn(log, "Job has timed out: " + ses);

        cancel();

        if (!internal && ctx.event().isRecordable(EVT_JOB_TIMEDOUT))
            recordEvent(EVT_JOB_TIMEDOUT, "Job has timed out: " + job);
    }

    /**
     * Callback for whenever grid is stopping.
     */
    public void onStopping() {
        sysStopping = true;
    }

    /**
     * @return {@code True} if job was halted.
     */
    public boolean held() {
        return held.get() > 0;
    }

    /**
     * Sets halt flags.
     */
    public void hold() {
        held.incrementAndGet();

        HOLD.set(true);

        holdLsnr.onHold(this);
    }

    /**
     * Initializes job. Handles deployments and event recording.
     *
     * @param dep Job deployed task.
     * @param taskCls Task class.
     * @return {@code True} if job was successfully initialized.
     */
    boolean initialize(GridDeployment dep, Class<?> taskCls) {
        assert dep != null;

        IgniteCheckedException ex = null;

        try {
            if (job == null) {
                job = marsh.unmarshal(jobBytes, dep.classLoader());

                // No need to hold reference any more.
                jobBytes = null;
            }

            // Inject resources.
            ctx.resource().inject(dep, taskCls, job, ses, jobCtx);

            if (!internal && ctx.event().isRecordable(EVT_JOB_QUEUED))
                recordEvent(EVT_JOB_QUEUED, "Job got queued for computation.");
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to initialize job [jobId=" + ses.getJobId() + ", ses=" + ses + ']', e);

            ex = e;
        }
        catch (Throwable e) {
            ex = handleThrowable(e);

            assert ex != null;
        }
        finally {
            if (ex != null)
                finishJob(null, ex, true);
        }

        return ex == null;
    }

    /** {@inheritDoc} */
    @Override protected void body() {
        assert job != null;

        startTime = U.currentTimeMillis();

        isStarted = true;

        // Event notification.
        evtLsnr.onJobStarted(this);

        if (!internal && ctx.event().isRecordable(EVT_JOB_STARTED))
            recordEvent(EVT_JOB_STARTED, /*no message for success*/null);

        execute0(true);
    }

    /**
     * Executes the job.
     */
    public void execute() {
        execute0(false);
    }

    /**
     * @param skipNtf {@code True} to skip job processor {@code onUnhold()}
     *      notification (only from {@link #body()}).
     */
    private void execute0(boolean skipNtf) {
        // Make sure flag is not set for current thread.
        HOLD.set(false);

        if (isCancelled())
            // If job was cancelled prior to assigning runner to it?
            super.cancel();

        if (!skipNtf) {
            holdLsnr.onUnhold(this);

            int c = held.decrementAndGet();

            if (c > 0) {
                if (log.isDebugEnabled())
                    log.debug("Ignoring job execution (job was held several times) [c=" + c + ']');

                return;
            }
        }

        boolean sndRes = true;

        Object res = null;

        IgniteCheckedException ex = null;

        try {
            ctx.job().currentTaskSession(ses);

            // If job has timed out, then
            // avoid computation altogether.
            if (isTimedOut())
                sndRes = false;
            else {
                res = U.wrapThreadLoader(dep.classLoader(), new Callable<Object>() {
                    @Nullable @Override public Object call() throws IgniteCheckedException {
                        try {
                            if (internal && ctx.config().isPeerClassLoadingEnabled())
                                ctx.job().internal(true);

                            return job.execute();
                        }
                        finally {
                            if (internal && ctx.config().isPeerClassLoadingEnabled())
                                ctx.job().internal(false);
                        }
                    }
                });

                if (log.isDebugEnabled())
                    log.debug("Job execution has successfully finished [job=" + job + ", res=" + res + ']');
            }
        }
        catch (IgniteCheckedException e) {
            if (sysStopping && e.hasCause(GridInterruptedException.class, InterruptedException.class)) {
                ex = handleThrowable(e);

                assert ex != null;
            }
            else {
                if (X.hasCause(e, GridInternalException.class) || X.hasCause(e, IgniteFsOutOfSpaceException.class)) {
                    // Print exception for internal errors only if debug is enabled.
                    if (log.isDebugEnabled())
                        U.error(log, "Failed to execute job [jobId=" + ses.getJobId() + ", ses=" + ses + ']', e);
                }
                else if (X.hasCause(e, InterruptedException.class)) {
                    String msg = "Job was cancelled [jobId=" + ses.getJobId() + ", ses=" + ses + ']';

                    if (log.isDebugEnabled())
                        U.error(log, msg, e);
                    else
                        U.warn(log, msg);
                }
                else
                    U.error(log, "Failed to execute job [jobId=" + ses.getJobId() + ", ses=" + ses + ']', e);

                ex = e;
            }
        }
        // Catch Throwable to protect against bad user code except
        // InterruptedException if job is being cancelled.
        catch (Throwable e) {
            ex = handleThrowable(e);

            assert ex != null;
        }
        finally {
            // Finish here only if not held by this thread.
            if (!HOLD.get())
                finishJob(res, ex, sndRes);

            ctx.job().currentTaskSession(null);
        }
    }

    /**
     * Handles {@link Throwable} generic exception for task
     * deployment and execution.
     *
     * @param e Exception.
     * @return Wrapped exception.
     */
    private IgniteCheckedException handleThrowable(Throwable e) {
        String msg = null;

        IgniteCheckedException ex = null;

        // Special handling for weird interrupted exception which
        // happens due to JDk 1.5 bug.
        if (e instanceof InterruptedException && !sysStopping) {
            msg = "Failed to execute job due to interrupted exception.";

            // Turn interrupted exception into checked exception.
            ex = new IgniteCheckedException(msg, e);
        }
        // Special 'NoClassDefFoundError' handling if P2P is on. We had many questions
        // about this exception and decided to change error message.
        else if ((e instanceof NoClassDefFoundError || e instanceof ClassNotFoundException)
            && ctx.config().isPeerClassLoadingEnabled()) {
            msg = "Failed to execute job due to class or resource loading exception (make sure that task " +
                "originating node is still in grid and requested class is in the task class path) [jobId=" +
                ses.getJobId() + ", ses=" + ses + ']';

            ex = new ComputeUserUndeclaredException(msg, e);
        }
        else if (sysStopping && X.hasCause(e, InterruptedException.class, GridInterruptedException.class)) {
            msg = "Job got interrupted due to system stop (will attempt failover).";

            ex = new ComputeExecutionRejectedException(e);
        }

        if (msg == null) {
            msg = "Failed to execute job due to unexpected runtime exception [jobId=" + ses.getJobId() +
                ", ses=" + ses + ']';

            ex = new ComputeUserUndeclaredException(msg, e);
        }

        assert msg != null;
        assert ex != null;

        U.error(log, msg, e);

        return ex;
    }

    /** {@inheritDoc} */
    @Override public void cancel() {
        cancel(false);
    }

    /**
     * @param sys System flag.
     */
    public void cancel(boolean sys) {
        try {
            super.cancel();

            final ComputeJob job0 = job;

            if (sys)
                sysCancelled = true;

            if (job0 != null) {
                if (log.isDebugEnabled())
                    log.debug("Cancelling job: " + ses);

                U.wrapThreadLoader(dep.classLoader(), new IgniteRunnable() {
                    @Override public void run() {
                        job0.cancel();
                    }
                });
            }

            if (!internal && ctx.event().isRecordable(EVT_JOB_CANCELLED))
                recordEvent(EVT_JOB_CANCELLED, "Job was cancelled: " + job0);
        }
        // Catch throwable to protect against bad user code.
        catch (Throwable e) {
            U.error(log, "Failed to cancel job due to undeclared user exception [jobId=" + ses.getJobId() +
                ", ses=" + ses + ']', e);
        }
    }

    /**
     * @param evtType Event type.
     * @param msg Message.
     */
    private void recordEvent(int evtType, @Nullable String msg) {
        assert ctx.event().isRecordable(evtType);
        assert !internal;

        IgniteJobEvent evt = new IgniteJobEvent();

        evt.jobId(ses.getJobId());
        evt.message(msg);
        evt.node(ctx.discovery().localNode());
        evt.taskName(ses.getTaskName());
        evt.taskClassName(ses.getTaskClassName());
        evt.taskSessionId(ses.getId());
        evt.type(evtType);
        evt.taskNode(taskNode);
        evt.taskSubjectId(ses.subjectId());

        ctx.event().record(evt);
    }

    /**
     * @param res Result.
     * @param ex Error.
     * @param sndReply If {@code true}, reply will be sent.
     */
    void finishJob(@Nullable Object res, @Nullable IgniteCheckedException ex, boolean sndReply) {
        // Avoid finishing a job more than once from different threads.
        if (!finishing.compareAndSet(false, true))
            return;

        // Do not send reply if job has been cancelled from system.
        if (sndReply)
            sndReply = !sysCancelled;

        // We should save message ID here since listener callback will reset sequence.
        ClusterNode sndNode = ctx.discovery().node(taskNode.id());

        long msgId = sndNode != null && ses.isFullSupport() ?
            ctx.io().nextMessageId(taskTopic, sndNode.id()) : -1;

        finishTime = U.currentTimeMillis();

        Collection<IgniteBiTuple<Integer, String>> evts = null;

        try {
            if (ses.isFullSupport())
                evtLsnr.onBeforeJobResponseSent(this);

            // Send response back only if job has not timed out.
            if (!isTimedOut()) {
                if (sndReply) {
                    if (sndNode == null) {
                        onMasterNodeLeft();

                        U.warn(log, "Failed to reply to sender node because it left grid [nodeId=" + taskNode.id() +
                            ", ses=" + ses + ", jobId=" + ses.getJobId() + ", job=" + job + ']');

                        // Record job reply failure.
                        if (!internal && ctx.event().isRecordable(EVT_JOB_FAILED))
                            evts = addEvent(evts, EVT_JOB_FAILED, "Job reply failed (task node left grid): " + job);
                    }
                    else {
                        try {
                            if (ex != null) {
                                if (isStarted) {
                                    // Job failed.
                                    if (!internal && ctx.event().isRecordable(EVT_JOB_FAILED))
                                        evts = addEvent(evts, EVT_JOB_FAILED, "Job failed due to exception [ex=" +
                                            ex + ", job=" + job + ']');
                                }
                                else if (!internal && ctx.event().isRecordable(EVT_JOB_REJECTED))
                                    evts = addEvent(evts, EVT_JOB_REJECTED, "Job has not been started " +
                                        "[ex=" + ex + ", job=" + job + ']');
                            }
                            else if (!internal && ctx.event().isRecordable(EVT_JOB_FINISHED))
                                evts = addEvent(evts, EVT_JOB_FINISHED, /*no message for success. */null);

                            boolean loc = ctx.localNodeId().equals(sndNode.id()) && !ctx.config().isMarshalLocalJobs();

                            Map<Object, Object> attrs = jobCtx.getAttributes();

                            GridJobExecuteResponse jobRes = new GridJobExecuteResponse(
                                ctx.localNodeId(),
                                ses.getId(),
                                ses.getJobId(),
                                loc ? null : marsh.marshal(ex),
                                loc ? ex : null,
                                loc ? null: marsh.marshal(res),
                                loc ? res : null,
                                loc ? null : marsh.marshal(attrs),
                                loc ? attrs : null,
                                isCancelled());

                            long timeout = ses.getEndTime() - U.currentTimeMillis();

                            if (timeout <= 0)
                                // Ignore the actual timeout and send response anyway.
                                timeout = 1;

                            if (ses.isFullSupport()) {
                                // Send response to designated job topic.
                                // Always go through communication to preserve order,
                                // if attributes are enabled.
                                assert msgId > 0;

                                ctx.io().sendOrderedMessage(
                                    sndNode,
                                    taskTopic,
                                    msgId,
                                    jobRes,
                                    internal ? MANAGEMENT_POOL : SYSTEM_POOL,
                                    timeout,
                                    false);
                            }
                            else if (ctx.localNodeId().equals(sndNode.id()))
                                ctx.task().processJobExecuteResponse(ctx.localNodeId(), jobRes);
                            else
                                // Send response to common topic as unordered message.
                                ctx.io().send(sndNode, TOPIC_TASK, jobRes, internal ? MANAGEMENT_POOL : SYSTEM_POOL);
                        }
                        catch (IgniteCheckedException e) {
                            // Log and invoke the master-leave callback.
                            if (isDeadNode(taskNode.id())) {
                                onMasterNodeLeft();

                                // Avoid stack trace for left nodes.
                                U.warn(log, "Failed to reply to sender node because it left grid " +
                                    "[nodeId=" + taskNode.id() + ", jobId=" + ses.getJobId() +
                                    ", ses=" + ses + ", job=" + job + ']');
                            }
                            else
                                U.error(log, "Error sending reply for job [nodeId=" + sndNode.id() + ", jobId=" +
                                    ses.getJobId() + ", ses=" + ses + ", job=" + job + ']', e);

                            if (!internal && ctx.event().isRecordable(EVT_JOB_FAILED))
                                evts = addEvent(evts, EVT_JOB_FAILED, "Failed to send reply for job [nodeId=" +
                                    taskNode.id() + ", job=" + job + ']');
                        }
                        // Catching interrupted exception because
                        // it gets thrown for some reason.
                        catch (Exception e) {
                            String msg = "Failed to send reply for job [nodeId=" + taskNode.id() + ", job=" + job + ']';

                            U.error(log, msg, e);

                            if (!internal && ctx.event().isRecordable(EVT_JOB_FAILED))
                                evts = addEvent(evts, EVT_JOB_FAILED, msg);
                        }
                    }
                }
                else {
                    if (ex != null) {
                        if (isStarted) {
                            if (!internal && ctx.event().isRecordable(EVT_JOB_FAILED))
                                evts = addEvent(evts, EVT_JOB_FAILED, "Job failed due to exception [ex=" + ex +
                                    ", job=" + job + ']');
                        }
                        else if (!internal && ctx.event().isRecordable(EVT_JOB_REJECTED))
                            evts = addEvent(evts, EVT_JOB_REJECTED, "Job has not been started [ex=" + ex +
                                ", job=" + job + ']');
                    }
                    else if (!internal && ctx.event().isRecordable(EVT_JOB_FINISHED))
                        evts = addEvent(evts, EVT_JOB_FINISHED, /*no message for success. */null);
                }
            }
            // Job timed out.
            else if (!internal && ctx.event().isRecordable(EVT_JOB_FAILED))
                evts = addEvent(evts, EVT_JOB_FAILED, "Job failed due to timeout: " + job);
        }
        finally {
            if (evts != null) {
                for (IgniteBiTuple<Integer, String> t : evts)
                    recordEvent(t.get1(), t.get2());
            }

            // Listener callback.
            evtLsnr.onJobFinished(this);
        }
    }

    /**
     * If the job implements {@link org.apache.ignite.compute.ComputeJobMasterLeaveAware#onMasterNodeLeft} interface then invoke
     * {@link org.apache.ignite.compute.ComputeJobMasterLeaveAware#onMasterNodeLeft(org.apache.ignite.compute.ComputeTaskSession)} method.
     *
     * @return {@code True} if master leave has been handled (either by this call or before).
     */
    boolean onMasterNodeLeft() {
        if (job instanceof ComputeJobMasterLeaveAware) {
            if (masterLeaveGuard.compareAndSet(false, true)) {
                try {
                    ((ComputeJobMasterLeaveAware)job).onMasterNodeLeft(ses.session());

                    if (log.isDebugEnabled())
                        log.debug("Successfully executed GridComputeJobMasterLeaveAware.onMasterNodeLeft() callback " +
                            "[nodeId=" + taskNode.id() + ", jobId=" + ses.getJobId() + ", job=" + job + ']');
                }
                catch (IgniteCheckedException e) {
                    U.error(log, "Failed to execute GridComputeJobMasterLeaveAware.onMasterNodeLeft() callback " +
                        "[nodeId=" + taskNode.id() + ", jobId=" + ses.getJobId() + ", job=" + job + ']', e);
                }
            }

            return true;
        }

        return false;
    }

    /**
     * @param evts Collection (created if {@code null}).
     * @param evt Event.
     * @param msg Message (optional).
     * @return Collection with event added.
     */
    Collection<IgniteBiTuple<Integer, String>> addEvent(@Nullable Collection<IgniteBiTuple<Integer, String>> evts,
        Integer evt, @Nullable String msg) {
        assert ctx.event().isRecordable(evt);
        assert !internal;

        if (evts == null)
            evts = new ArrayList<>();

        evts.add(F.t(evt, msg));

        return evts;
    }

    /**
     * Checks whether node is alive or dead.
     *
     * @param uid UID of node to check.
     * @return {@code true} if node is dead, {@code false} is node is alive.
     */
    private boolean isDeadNode(UUID uid) {
        return ctx.discovery().node(uid) == null || !ctx.discovery().pingNode(uid);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null)
            return false;

        assert obj instanceof GridJobWorker;

        IgniteUuid jobId1 = ses.getJobId();
        IgniteUuid jobId2 = ((GridJobWorker)obj).ses.getJobId();

        assert jobId1 != null;
        assert jobId2 != null;

        return jobId1.equals(jobId2);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        IgniteUuid jobId = ses.getJobId();

        assert jobId != null;

        return jobId.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobWorker.class, this);
    }
}
