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

package org.apache.ignite.internal.processors.hadoop.jobtracker;

import org.apache.ignite.*;
import org.apache.ignite.cache.*;
import org.apache.ignite.cache.query.*;
import org.apache.ignite.events.*;
import org.apache.ignite.events.EventType;
import org.apache.ignite.internal.*;
import org.apache.ignite.internal.managers.eventstorage.*;
import org.apache.ignite.internal.processors.cache.*;
import org.apache.ignite.internal.processors.hadoop.*;
import org.apache.ignite.internal.processors.hadoop.counter.*;
import org.apache.ignite.internal.processors.hadoop.taskexecutor.*;
import org.apache.ignite.internal.processors.hadoop.taskexecutor.external.*;
import org.apache.ignite.internal.util.*;
import org.apache.ignite.internal.util.future.*;
import org.apache.ignite.internal.util.typedef.*;
import org.apache.ignite.internal.util.typedef.internal.*;
import org.apache.ignite.lang.*;
import org.jdk8.backport.*;
import org.jetbrains.annotations.*;

import javax.cache.event.*;
import javax.cache.event.CacheEntryEvent;
import javax.cache.expiry.*;
import javax.cache.processor.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.*;
import static org.apache.ignite.internal.processors.hadoop.GridHadoopJobPhase.*;
import static org.apache.ignite.internal.processors.hadoop.GridHadoopTaskType.*;
import static org.apache.ignite.internal.processors.hadoop.taskexecutor.GridHadoopTaskState.*;

/**
 * Hadoop job tracker.
 */
public class GridHadoopJobTracker extends GridHadoopComponent {
    /** */
    private final GridMutex mux = new GridMutex();

    /** */
    private volatile GridCacheProjectionEx<GridHadoopJobId, GridHadoopJobMetadata> jobMetaPrj;

    /** Projection with expiry policy for finished job updates. */
    private volatile GridCacheProjectionEx<GridHadoopJobId, GridHadoopJobMetadata> finishedJobMetaPrj;

    /** Map-reduce execution planner. */
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private GridHadoopMapReducePlanner mrPlanner;

    /** All the known jobs. */
    private final ConcurrentMap<GridHadoopJobId, GridFutureAdapterEx<GridHadoopJob>> jobs = new ConcurrentHashMap8<>();

    /** Locally active jobs. */
    private final ConcurrentMap<GridHadoopJobId, JobLocalState> activeJobs = new ConcurrentHashMap8<>();

    /** Locally requested finish futures. */
    private final ConcurrentMap<GridHadoopJobId, GridFutureAdapter<GridHadoopJobId>> activeFinishFuts =
        new ConcurrentHashMap8<>();

    /** Event processing service. */
    private ExecutorService evtProcSvc;

    /** Component busy lock. */
    private GridSpinReadWriteLock busyLock;

    /** Closure to check result of async transform of system cache. */
    private final IgniteInClosure<IgniteInternalFuture<?>> failsLog = new CI1<IgniteInternalFuture<?>>() {
        @Override public void apply(IgniteInternalFuture<?> gridFut) {
            try {
                gridFut.get();
            }
            catch (IgniteCheckedException e) {
                U.error(log, "Failed to transform system cache.", e);
            }
        }
    };

    /** {@inheritDoc} */
    @Override public void start(GridHadoopContext ctx) throws IgniteCheckedException {
        super.start(ctx);

        busyLock = new GridSpinReadWriteLock();

        evtProcSvc = Executors.newFixedThreadPool(1);
    }

    /**
     * @return Job meta projection.
     */
    @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
    private GridCacheProjectionEx<GridHadoopJobId, GridHadoopJobMetadata> jobMetaCache() {
        GridCacheProjectionEx<GridHadoopJobId, GridHadoopJobMetadata> prj = jobMetaPrj;

        if (prj == null) {
            synchronized (mux) {
                if ((prj = jobMetaPrj) == null) {
                    CacheProjection<Object, Object> sysCache = ctx.kernalContext().cache()
                        .cache(CU.SYS_CACHE_HADOOP_MR);

                    assert sysCache != null;

                    mrPlanner = ctx.planner();

                    try {
                        ctx.kernalContext().resource().injectGeneric(mrPlanner);
                    }
                    catch (IgniteCheckedException e) { // Must not happen.
                        U.error(log, "Failed to inject resources.", e);

                        throw new IllegalStateException(e);
                    }

                    jobMetaPrj = prj = (GridCacheProjectionEx<GridHadoopJobId, GridHadoopJobMetadata>)
                        sysCache.projection(GridHadoopJobId.class, GridHadoopJobMetadata.class);

                    if (ctx.configuration().getFinishedJobInfoTtl() > 0) {
                        ExpiryPolicy finishedJobPlc = new ModifiedExpiryPolicy(
                            new Duration(MILLISECONDS, ctx.configuration().getFinishedJobInfoTtl()));

                        finishedJobMetaPrj = ((GridCacheProjectionEx<GridHadoopJobId, GridHadoopJobMetadata>)prj).
                            withExpiryPolicy(finishedJobPlc);
                    }
                    else
                        finishedJobMetaPrj = jobMetaPrj;
                }
            }
        }

        return prj;
    }

    /**
     * @return Projection with expiry policy for finished job updates.
     */
    private GridCacheProjectionEx<GridHadoopJobId, GridHadoopJobMetadata> finishedJobMetaCache() {
        GridCacheProjectionEx<GridHadoopJobId, GridHadoopJobMetadata> prj = finishedJobMetaPrj;

        if (prj == null) {
            jobMetaCache();

            prj = finishedJobMetaPrj;

            assert prj != null;
        }

        return prj;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public void onKernalStart() throws IgniteCheckedException {
        super.onKernalStart();

        ContinuousQuery<GridHadoopJobId, GridHadoopJobMetadata> qry = Query.continuous();

        qry.setLocalListener(new CacheEntryUpdatedListener<GridHadoopJobId, GridHadoopJobMetadata>() {
            @Override public void onUpdated(final Iterable<CacheEntryEvent<? extends GridHadoopJobId,
                ? extends GridHadoopJobMetadata>> evts) {
                if (!busyLock.tryReadLock())
                    return;

                try {
                    // Must process query callback in a separate thread to avoid deadlocks.
                    evtProcSvc.submit(new EventHandler() {
                        @Override protected void body() throws IgniteCheckedException {
                            processJobMetadataUpdates(evts);
                        }
                    });
                }
                finally {
                    busyLock.readUnlock();
                }
            }
        });

        // TODO: Filter by type
        ctx.kernalContext().cache().utilityJCache().localQuery(qry);

        ctx.kernalContext().event().addLocalEventListener(new GridLocalEventListener() {
            @Override public void onEvent(final Event evt) {
                if (!busyLock.tryReadLock())
                    return;

                try {
                    // Must process discovery callback in a separate thread to avoid deadlock.
                    evtProcSvc.submit(new EventHandler() {
                        @Override protected void body() {
                            processNodeLeft((DiscoveryEvent)evt);
                        }
                    });
                }
                finally {
                    busyLock.readUnlock();
                }
            }
        }, EventType.EVT_NODE_FAILED, EventType.EVT_NODE_LEFT);
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop(boolean cancel) {
        super.onKernalStop(cancel);

        busyLock.writeLock();

        evtProcSvc.shutdown();

        // Fail all pending futures.
        for (GridFutureAdapter<GridHadoopJobId> fut : activeFinishFuts.values())
            fut.onDone(new IgniteCheckedException("Failed to execute Hadoop map-reduce job (grid is stopping)."));
    }

    /**
     * Submits execution of Hadoop job to grid.
     *
     * @param jobId Job ID.
     * @param info Job info.
     * @return Job completion future.
     */
    @SuppressWarnings("unchecked")
    public IgniteInternalFuture<GridHadoopJobId> submit(GridHadoopJobId jobId, GridHadoopJobInfo info) {
        if (!busyLock.tryReadLock()) {
            return new GridFinishedFutureEx<>(new IgniteCheckedException("Failed to execute map-reduce job " +
                "(grid is stopping): " + info));
        }

        try {
            long jobPrepare = U.currentTimeMillis();

            if (jobs.containsKey(jobId) || jobMetaCache().containsKey(jobId))
                throw new IgniteCheckedException("Failed to submit job. Job with the same ID already exists: " + jobId);

            GridHadoopJob job = job(jobId, info);

            GridHadoopMapReducePlan mrPlan = mrPlanner.preparePlan(job, ctx.nodes(), null);

            GridHadoopJobMetadata meta = new GridHadoopJobMetadata(ctx.localNodeId(), jobId, info);

            meta.mapReducePlan(mrPlan);

            meta.pendingSplits(allSplits(mrPlan));
            meta.pendingReducers(allReducers(mrPlan));

            GridFutureAdapter<GridHadoopJobId> completeFut = new GridFutureAdapter<>();

            GridFutureAdapter<GridHadoopJobId> old = activeFinishFuts.put(jobId, completeFut);

            assert old == null : "Duplicate completion future [jobId=" + jobId + ", old=" + old + ']';

            if (log.isDebugEnabled())
                log.debug("Submitting job metadata [jobId=" + jobId + ", meta=" + meta + ']');

            long jobStart = U.currentTimeMillis();

            GridHadoopPerformanceCounter perfCntr = GridHadoopPerformanceCounter.getCounter(meta.counters(),
                ctx.localNodeId());

            perfCntr.clientSubmissionEvents(info);
            perfCntr.onJobPrepare(jobPrepare);
            perfCntr.onJobStart(jobStart);

            if (jobMetaCache().putIfAbsent(jobId, meta) != null)
                throw new IgniteCheckedException("Failed to submit job. Job with the same ID already exists: " + jobId);

            return completeFut;
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to submit job: " + jobId, e);

            return new GridFinishedFutureEx<>(e);
        }
        finally {
            busyLock.readUnlock();
        }
    }

    /**
     * Convert Hadoop job metadata to job status.
     *
     * @param meta Metadata.
     * @return Status.
     */
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public static GridHadoopJobStatus status(GridHadoopJobMetadata meta) {
        GridHadoopJobInfo jobInfo = meta.jobInfo();

        return new GridHadoopJobStatus(
            meta.jobId(),
            jobInfo.jobName(),
            jobInfo.user(),
            meta.pendingSplits() != null ? meta.pendingSplits().size() : 0,
            meta.pendingReducers() != null ? meta.pendingReducers().size() : 0,
            meta.mapReducePlan().mappers(),
            meta.mapReducePlan().reducers(),
            meta.phase(),
            meta.failCause() != null,
            meta.version()
        );
    }

    /**
     * Gets hadoop job status for given job ID.
     *
     * @param jobId Job ID to get status for.
     * @return Job status for given job ID or {@code null} if job was not found.
     */
    @Nullable public GridHadoopJobStatus status(GridHadoopJobId jobId) throws IgniteCheckedException {
        if (!busyLock.tryReadLock())
            return null; // Grid is stopping.

        try {
            GridHadoopJobMetadata meta = jobMetaCache().get(jobId);

            return meta != null ? status(meta) : null;
        }
        finally {
            busyLock.readUnlock();
        }
    }

    /**
     * Gets job finish future.
     *
     * @param jobId Job ID.
     * @return Finish future or {@code null}.
     * @throws IgniteCheckedException If failed.
     */
    @Nullable public IgniteInternalFuture<?> finishFuture(GridHadoopJobId jobId) throws IgniteCheckedException {
        if (!busyLock.tryReadLock())
            return null; // Grid is stopping.

        try {
            GridHadoopJobMetadata meta = jobMetaCache().get(jobId);

            if (meta == null)
                return null;

            if (log.isTraceEnabled())
                log.trace("Got job metadata for status check [locNodeId=" + ctx.localNodeId() + ", meta=" + meta + ']');

            if (meta.phase() == PHASE_COMPLETE) {
                if (log.isTraceEnabled())
                    log.trace("Job is complete, returning finished future: " + jobId);

                return new GridFinishedFutureEx<>(jobId, meta.failCause());
            }

            GridFutureAdapter<GridHadoopJobId> fut = F.addIfAbsent(activeFinishFuts, jobId,
                new GridFutureAdapter<GridHadoopJobId>());

            // Get meta from cache one more time to close the window.
            meta = jobMetaCache().get(jobId);

            if (log.isTraceEnabled())
                log.trace("Re-checking job metadata [locNodeId=" + ctx.localNodeId() + ", meta=" + meta + ']');

            if (meta == null) {
                fut.onDone();

                activeFinishFuts.remove(jobId , fut);
            }
            else if (meta.phase() == PHASE_COMPLETE) {
                fut.onDone(jobId, meta.failCause());

                activeFinishFuts.remove(jobId , fut);
            }

            return fut;
        }
        finally {
            busyLock.readUnlock();
        }
    }

    /**
     * Gets job plan by job ID.
     *
     * @param jobId Job ID.
     * @return Job plan.
     * @throws IgniteCheckedException If failed.
     */
    public GridHadoopMapReducePlan plan(GridHadoopJobId jobId) throws IgniteCheckedException {
        if (!busyLock.tryReadLock())
            return null;

        try {
            GridHadoopJobMetadata meta = jobMetaCache().get(jobId);

            if (meta != null)
                return meta.mapReducePlan();

            return null;
        }
        finally {
            busyLock.readUnlock();
        }
    }

    /**
     * Callback from task executor invoked when a task has been finished.
     *
     * @param info Task info.
     * @param status Task status.
     */
    @SuppressWarnings({"ConstantConditions", "ThrowableResultOfMethodCallIgnored"})
    public void onTaskFinished(GridHadoopTaskInfo info, GridHadoopTaskStatus status) {
        if (!busyLock.tryReadLock())
            return;

        try {
            assert status.state() != RUNNING;

            if (log.isDebugEnabled())
                log.debug("Received task finished callback [info=" + info + ", status=" + status + ']');

            JobLocalState state = activeJobs.get(info.jobId());

            // Task CRASHes with null fail cause.
            assert (status.state() != FAILED) || status.failCause() != null :
                "Invalid task status [info=" + info + ", status=" + status + ']';

            assert state != null || (ctx.jobUpdateLeader() && (info.type() == COMMIT || info.type() == ABORT)):
                "Missing local state for finished task [info=" + info + ", status=" + status + ']';

            StackedProcessor incrCntrs = null;

            if (status.state() == COMPLETED)
                incrCntrs = new IncrementCountersProcessor(null, status.counters());

            switch (info.type()) {
                case SETUP: {
                    state.onSetupFinished(info, status, incrCntrs);

                    break;
                }

                case MAP: {
                    state.onMapFinished(info, status, incrCntrs);

                    break;
                }

                case REDUCE: {
                    state.onReduceFinished(info, status, incrCntrs);

                    break;
                }

                case COMBINE: {
                    state.onCombineFinished(info, status, incrCntrs);

                    break;
                }

                case COMMIT:
                case ABORT: {
                    GridCacheProjectionEx<GridHadoopJobId, GridHadoopJobMetadata> cache = finishedJobMetaCache();

                    cache.invokeAsync(info.jobId(), new UpdatePhaseProcessor(incrCntrs, PHASE_COMPLETE)).
                        listenAsync(failsLog);

                    break;
                }
            }
        }
        finally {
            busyLock.readUnlock();
        }
    }

    /**
     * @param jobId Job id.
     * @param c Closure of operation.
     */
    private void transform(GridHadoopJobId jobId, EntryProcessor<GridHadoopJobId, GridHadoopJobMetadata, Void> c) {
        jobMetaCache().invokeAsync(jobId, c).listenAsync(failsLog);
    }

    /**
     * Callback from task executor called when process is ready to received shuffle messages.
     *
     * @param jobId Job ID.
     * @param reducers Reducers.
     * @param desc Process descriptor.
     */
    public void onExternalMappersInitialized(GridHadoopJobId jobId, Collection<Integer> reducers,
        GridHadoopProcessDescriptor desc) {
        transform(jobId, new InitializeReducersProcessor(null, reducers, desc));
    }

    /**
     * Gets all input splits for given hadoop map-reduce plan.
     *
     * @param plan Map-reduce plan.
     * @return Collection of all input splits that should be processed.
     */
    @SuppressWarnings("ConstantConditions")
    private Map<GridHadoopInputSplit, Integer> allSplits(GridHadoopMapReducePlan plan) {
        Map<GridHadoopInputSplit, Integer> res = new HashMap<>();

        int taskNum = 0;

        for (UUID nodeId : plan.mapperNodeIds()) {
            for (GridHadoopInputSplit split : plan.mappers(nodeId)) {
                if (res.put(split, taskNum++) != null)
                    throw new IllegalStateException("Split duplicate.");
            }
        }

        return res;
    }

    /**
     * Gets all reducers for this job.
     *
     * @param plan Map-reduce plan.
     * @return Collection of reducers.
     */
    private Collection<Integer> allReducers(GridHadoopMapReducePlan plan) {
        Collection<Integer> res = new HashSet<>();

        for (int i = 0; i < plan.reducers(); i++)
            res.add(i);

        return res;
    }

    /**
     * Processes node leave (or fail) event.
     *
     * @param evt Discovery event.
     */
    @SuppressWarnings("ConstantConditions")
    private void processNodeLeft(DiscoveryEvent evt) {
        if (log.isDebugEnabled())
            log.debug("Processing discovery event [locNodeId=" + ctx.localNodeId() + ", evt=" + evt + ']');

        // Check only if this node is responsible for job status updates.
        if (ctx.jobUpdateLeader()) {
            boolean checkSetup = evt.eventNode().order() < ctx.localNodeOrder();

            // Iteration over all local entries is correct since system cache is REPLICATED.
            for (Object metaObj : jobMetaCache().values()) {
                GridHadoopJobMetadata meta = (GridHadoopJobMetadata)metaObj;

                GridHadoopJobId jobId = meta.jobId();

                GridHadoopMapReducePlan plan = meta.mapReducePlan();

                GridHadoopJobPhase phase = meta.phase();

                try {
                    if (checkSetup && phase == PHASE_SETUP && !activeJobs.containsKey(jobId)) {
                        // Failover setup task.
                        GridHadoopJob job = job(jobId, meta.jobInfo());

                        Collection<GridHadoopTaskInfo> setupTask = setupTask(jobId);

                        assert setupTask != null;

                        ctx.taskExecutor().run(job, setupTask);
                    }
                    else if (phase == PHASE_MAP || phase == PHASE_REDUCE) {
                        // Must check all nodes, even that are not event node ID due to
                        // multiple node failure possibility.
                        Collection<GridHadoopInputSplit> cancelSplits = null;

                        for (UUID nodeId : plan.mapperNodeIds()) {
                            if (ctx.kernalContext().discovery().node(nodeId) == null) {
                                // Node has left the grid.
                                Collection<GridHadoopInputSplit> mappers = plan.mappers(nodeId);

                                if (cancelSplits == null)
                                    cancelSplits = new HashSet<>();

                                cancelSplits.addAll(mappers);
                            }
                        }

                        Collection<Integer> cancelReducers = null;

                        for (UUID nodeId : plan.reducerNodeIds()) {
                            if (ctx.kernalContext().discovery().node(nodeId) == null) {
                                // Node has left the grid.
                                int[] reducers = plan.reducers(nodeId);

                                if (cancelReducers == null)
                                    cancelReducers = new HashSet<>();

                                for (int rdc : reducers)
                                    cancelReducers.add(rdc);
                            }
                        }

                        if (cancelSplits != null || cancelReducers != null)
                            jobMetaCache().invoke(meta.jobId(), new CancelJobProcessor(null, new IgniteCheckedException(
                                "One or more nodes participating in map-reduce job execution failed."), cancelSplits,
                                cancelReducers));
                    }
                }
                catch (IgniteCheckedException e) {
                    U.error(log, "Failed to cancel job: " + meta, e);
                }
            }
        }
    }

    /**
     * @param updated Updated cache entries.
     * @throws IgniteCheckedException If failed.
     */
    private void processJobMetadataUpdates(
        Iterable<CacheEntryEvent<? extends GridHadoopJobId, ? extends GridHadoopJobMetadata>> updated)
        throws IgniteCheckedException {
        UUID locNodeId = ctx.localNodeId();

        for (CacheEntryEvent<? extends GridHadoopJobId, ? extends GridHadoopJobMetadata> entry : updated) {
            GridHadoopJobId jobId = entry.getKey();
            GridHadoopJobMetadata meta = entry.getValue();

            if (meta == null || !ctx.isParticipating(meta))
                continue;

            if (log.isDebugEnabled())
                log.debug("Processing job metadata update callback [locNodeId=" + locNodeId +
                    ", meta=" + meta + ']');

            try {
                ctx.taskExecutor().onJobStateChanged(meta);
            }
            catch (IgniteCheckedException e) {
                U.error(log, "Failed to process job state changed callback (will fail the job) " +
                    "[locNodeId=" + locNodeId + ", jobId=" + jobId + ", meta=" + meta + ']', e);

                transform(jobId, new CancelJobProcessor(null, e));

                continue;
            }

            processJobMetaUpdate(jobId, meta, locNodeId);
        }
    }

    /**
     * @param jobId  Job ID.
     * @param plan Map-reduce plan.
     */
    private void printPlan(GridHadoopJobId jobId, GridHadoopMapReducePlan plan) {
        log.info("Plan for " + jobId);

        SB b = new SB();

        b.a("   Map: ");

        for (UUID nodeId : plan.mapperNodeIds())
            b.a(nodeId).a("=").a(plan.mappers(nodeId).size()).a(' ');

        log.info(b.toString());

        b = new SB();

        b.a("   Reduce: ");

        for (UUID nodeId : plan.reducerNodeIds())
            b.a(nodeId).a("=").a(Arrays.toString(plan.reducers(nodeId))).a(' ');

        log.info(b.toString());
    }

    /**
     * @param jobId Job ID.
     * @param meta Job metadata.
     * @param locNodeId Local node ID.
     * @throws IgniteCheckedException If failed.
     */
    private void processJobMetaUpdate(GridHadoopJobId jobId, GridHadoopJobMetadata meta, UUID locNodeId)
        throws IgniteCheckedException {
        JobLocalState state = activeJobs.get(jobId);

        GridHadoopJob job = job(jobId, meta.jobInfo());

        GridHadoopMapReducePlan plan = meta.mapReducePlan();

        switch (meta.phase()) {
            case PHASE_SETUP: {
                if (ctx.jobUpdateLeader()) {
                    Collection<GridHadoopTaskInfo> setupTask = setupTask(jobId);

                    if (setupTask != null)
                        ctx.taskExecutor().run(job, setupTask);
                }

                break;
            }

            case PHASE_MAP: {
                // Check if we should initiate new task on local node.
                Collection<GridHadoopTaskInfo> tasks = mapperTasks(plan.mappers(locNodeId), meta);

                if (tasks != null)
                    ctx.taskExecutor().run(job, tasks);

                break;
            }

            case PHASE_REDUCE: {
                if (meta.pendingReducers().isEmpty() && ctx.jobUpdateLeader()) {
                    GridHadoopTaskInfo info = new GridHadoopTaskInfo(COMMIT, jobId, 0, 0, null);

                    if (log.isDebugEnabled())
                        log.debug("Submitting COMMIT task for execution [locNodeId=" + locNodeId +
                                ", jobId=" + jobId + ']');

                    ctx.taskExecutor().run(job, Collections.singletonList(info));

                    break;
                }

                Collection<GridHadoopTaskInfo> tasks = reducerTasks(plan.reducers(locNodeId), job);

                if (tasks != null)
                    ctx.taskExecutor().run(job, tasks);

                break;
            }

            case PHASE_CANCELLING: {
                // Prevent multiple task executor notification.
                if (state != null && state.onCancel()) {
                    if (log.isDebugEnabled())
                        log.debug("Cancelling local task execution for job: " + meta);

                    ctx.taskExecutor().cancelTasks(jobId);
                }

                if (meta.pendingSplits().isEmpty() && meta.pendingReducers().isEmpty()) {
                    if (ctx.jobUpdateLeader()) {
                        if (state == null)
                            state = initState(jobId);

                        // Prevent running multiple abort tasks.
                        if (state.onAborted()) {
                            GridHadoopTaskInfo info = new GridHadoopTaskInfo(ABORT, jobId, 0, 0, null);

                            if (log.isDebugEnabled())
                                log.debug("Submitting ABORT task for execution [locNodeId=" + locNodeId +
                                        ", jobId=" + jobId + ']');

                            ctx.taskExecutor().run(job, Collections.singletonList(info));
                        }
                    }

                    break;
                }
                else {
                    // Check if there are unscheduled mappers or reducers.
                    Collection<GridHadoopInputSplit> cancelMappers = new ArrayList<>();
                    Collection<Integer> cancelReducers = new ArrayList<>();

                    Collection<GridHadoopInputSplit> mappers = plan.mappers(ctx.localNodeId());

                    if (mappers != null) {
                        for (GridHadoopInputSplit b : mappers) {
                            if (state == null || !state.mapperScheduled(b))
                                cancelMappers.add(b);
                        }
                    }

                    int[] rdc = plan.reducers(ctx.localNodeId());

                    if (rdc != null) {
                        for (int r : rdc) {
                            if (state == null || !state.reducerScheduled(r))
                                cancelReducers.add(r);
                        }
                    }

                    if (!cancelMappers.isEmpty() || !cancelReducers.isEmpty())
                        transform(jobId, new CancelJobProcessor(null, cancelMappers, cancelReducers));
                }

                break;
            }

            case PHASE_COMPLETE: {
                if (log.isDebugEnabled())
                    log.debug("Job execution is complete, will remove local state from active jobs " +
                        "[jobId=" + jobId + ", meta=" + meta + ']');

                if (state != null) {
                    state = activeJobs.remove(jobId);

                    assert state != null;

                    ctx.shuffle().jobFinished(jobId);
                }

                GridFutureAdapter<GridHadoopJobId> finishFut = activeFinishFuts.remove(jobId);

                if (finishFut != null) {
                    if (log.isDebugEnabled())
                        log.debug("Completing job future [locNodeId=" + locNodeId + ", meta=" + meta + ']');

                    finishFut.onDone(jobId, meta.failCause());
                }

                if (ctx.jobUpdateLeader())
                    job.cleanupStagingDirectory();

                jobs.remove(jobId);

                job.dispose(false);

                if (ctx.jobUpdateLeader()) {
                    ClassLoader ldr = job.getClass().getClassLoader();

                    try {
                        String statWriterClsName = job.info().property(GridHadoopUtils.JOB_COUNTER_WRITER_PROPERTY);

                        if (statWriterClsName != null) {
                            Class<?> cls = ldr.loadClass(statWriterClsName);

                            GridHadoopCounterWriter writer = (GridHadoopCounterWriter)cls.newInstance();

                            GridHadoopCounters cntrs = meta.counters();

                            writer.write(job.info(), jobId, cntrs);
                        }
                    }
                    catch (Exception e) {
                        log.error("Can't write statistic due to: ", e);
                    }
                }

                break;
            }

            default:
                throw new IllegalStateException("Unknown phase: " + meta.phase());
        }
    }

    /**
     * Creates setup task based on job information.
     *
     * @param jobId Job ID.
     * @return Setup task wrapped in collection.
     */
    @Nullable private Collection<GridHadoopTaskInfo> setupTask(GridHadoopJobId jobId) {
        if (activeJobs.containsKey(jobId))
            return null;
        else {
            initState(jobId);

            return Collections.singleton(new GridHadoopTaskInfo(SETUP, jobId, 0, 0, null));
        }
    }

    /**
     * Creates mapper tasks based on job information.
     *
     * @param mappers Mapper blocks.
     * @param meta Job metadata.
     * @return Collection of created task infos or {@code null} if no mapper tasks scheduled for local node.
     */
    private Collection<GridHadoopTaskInfo> mapperTasks(Iterable<GridHadoopInputSplit> mappers, GridHadoopJobMetadata meta) {
        UUID locNodeId = ctx.localNodeId();
        GridHadoopJobId jobId = meta.jobId();

        JobLocalState state = activeJobs.get(jobId);

        Collection<GridHadoopTaskInfo> tasks = null;

        if (mappers != null) {
            if (state == null)
                state = initState(jobId);

            for (GridHadoopInputSplit split : mappers) {
                if (state.addMapper(split)) {
                    if (log.isDebugEnabled())
                        log.debug("Submitting MAP task for execution [locNodeId=" + locNodeId +
                            ", split=" + split + ']');

                    GridHadoopTaskInfo taskInfo = new GridHadoopTaskInfo(MAP, jobId, meta.taskNumber(split), 0, split);

                    if (tasks == null)
                        tasks = new ArrayList<>();

                    tasks.add(taskInfo);
                }
            }
        }

        return tasks;
    }

    /**
     * Creates reducer tasks based on job information.
     *
     * @param reducers Reducers (may be {@code null}).
     * @param job Job instance.
     * @return Collection of task infos.
     */
    private Collection<GridHadoopTaskInfo> reducerTasks(int[] reducers, GridHadoopJob job) {
        UUID locNodeId = ctx.localNodeId();
        GridHadoopJobId jobId = job.id();

        JobLocalState state = activeJobs.get(jobId);

        Collection<GridHadoopTaskInfo> tasks = null;

        if (reducers != null) {
            if (state == null)
                state = initState(job.id());

            for (int rdc : reducers) {
                if (state.addReducer(rdc)) {
                    if (log.isDebugEnabled())
                        log.debug("Submitting REDUCE task for execution [locNodeId=" + locNodeId +
                            ", rdc=" + rdc + ']');

                    GridHadoopTaskInfo taskInfo = new GridHadoopTaskInfo(REDUCE, jobId, rdc, 0, null);

                    if (tasks == null)
                        tasks = new ArrayList<>();

                    tasks.add(taskInfo);
                }
            }
        }

        return tasks;
    }

    /**
     * Initializes local state for given job metadata.
     *
     * @param jobId Job ID.
     * @return Local state.
     */
    private JobLocalState initState(GridHadoopJobId jobId) {
        return F.addIfAbsent(activeJobs, jobId, new JobLocalState());
    }

    /**
     * Gets or creates job instance.
     *
     * @param jobId Job ID.
     * @param jobInfo Job info.
     * @return Job.
     * @throws IgniteCheckedException If failed.
     */
    @Nullable public GridHadoopJob job(GridHadoopJobId jobId, @Nullable GridHadoopJobInfo jobInfo) throws IgniteCheckedException {
        GridFutureAdapterEx<GridHadoopJob> fut = jobs.get(jobId);

        if (fut != null || (fut = jobs.putIfAbsent(jobId, new GridFutureAdapterEx<GridHadoopJob>())) != null)
            return fut.get();

        fut = jobs.get(jobId);

        GridHadoopJob job = null;

        try {
            if (jobInfo == null) {
                GridHadoopJobMetadata meta = jobMetaCache().get(jobId);

                if (meta == null)
                    throw new IgniteCheckedException("Failed to find job metadata for ID: " + jobId);

                jobInfo = meta.jobInfo();
            }

            job = jobInfo.createJob(jobId, log);

            job.initialize(false, ctx.localNodeId());

            fut.onDone(job);

            return job;
        }
        catch (IgniteCheckedException e) {
            fut.onDone(e);

            jobs.remove(jobId, fut);

            if (job != null) {
                try {
                    job.dispose(false);
                }
                catch (IgniteCheckedException e0) {
                    U.error(log, "Failed to dispose job: " + jobId, e0);
                }
            }

            throw e;
        }
    }

    /**
     * Kills job.
     *
     * @param jobId Job ID.
     * @return {@code True} if job was killed.
     * @throws IgniteCheckedException If failed.
     */
    public boolean killJob(GridHadoopJobId jobId) throws IgniteCheckedException {
        if (!busyLock.tryReadLock())
            return false; // Grid is stopping.

        try {
            GridHadoopJobMetadata meta = jobMetaCache().get(jobId);

            if (meta != null && meta.phase() != PHASE_COMPLETE && meta.phase() != PHASE_CANCELLING) {
                GridHadoopTaskCancelledException err = new GridHadoopTaskCancelledException("Job cancelled.");

                jobMetaCache().invoke(jobId, new CancelJobProcessor(null, err));
            }
        }
        finally {
            busyLock.readUnlock();
        }

        IgniteInternalFuture<?> fut = finishFuture(jobId);

        if (fut != null) {
            try {
                fut.get();
            }
            catch (Throwable e) {
                if (e.getCause() instanceof GridHadoopTaskCancelledException)
                    return true;
            }
        }

        return false;
    }

    /**
     * Returns job counters.
     *
     * @param jobId Job identifier.
     * @return Job counters or {@code null} if job cannot be found.
     * @throws IgniteCheckedException If failed.
     */
    @Nullable public GridHadoopCounters jobCounters(GridHadoopJobId jobId) throws IgniteCheckedException {
        if (!busyLock.tryReadLock())
            return null;

        try {
            final GridHadoopJobMetadata meta = jobMetaCache().get(jobId);

            return meta != null ? meta.counters() : null;
        }
        finally {
            busyLock.readUnlock();
        }
    }

    /**
     * Event handler protected by busy lock.
     */
    private abstract class EventHandler implements Runnable {
        /** {@inheritDoc} */
        @Override public void run() {
            if (!busyLock.tryReadLock())
                return;

            try {
                body();
            }
            catch (Throwable e) {
                U.error(log, "Unhandled exception while processing event.", e);
            }
            finally {
                busyLock.readUnlock();
            }
        }

        /**
         * Handler body.
         */
        protected abstract void body() throws Exception;
    }

    /**
     *
     */
    private class JobLocalState {
        /** Mappers. */
        private final Collection<GridHadoopInputSplit> currMappers = new HashSet<>();

        /** Reducers. */
        private final Collection<Integer> currReducers = new HashSet<>();

        /** Number of completed mappers. */
        private final AtomicInteger completedMappersCnt = new AtomicInteger();

        /** Cancelled flag. */
        private boolean cancelled;

        /** Aborted flag. */
        private boolean aborted;

        /**
         * @param mapSplit Map split to add.
         * @return {@code True} if mapper was added.
         */
        private boolean addMapper(GridHadoopInputSplit mapSplit) {
            return currMappers.add(mapSplit);
        }

        /**
         * @param rdc Reducer number to add.
         * @return {@code True} if reducer was added.
         */
        private boolean addReducer(int rdc) {
            return currReducers.add(rdc);
        }

        /**
         * Checks whether this split was scheduled for given attempt.
         *
         * @param mapSplit Map split to check.
         * @return {@code True} if mapper was scheduled.
         */
        public boolean mapperScheduled(GridHadoopInputSplit mapSplit) {
            return currMappers.contains(mapSplit);
        }

        /**
         * Checks whether this split was scheduled for given attempt.
         *
         * @param rdc Reducer number to check.
         * @return {@code True} if reducer was scheduled.
         */
        public boolean reducerScheduled(int rdc) {
            return currReducers.contains(rdc);
        }

        /**
         * @param taskInfo Task info.
         * @param status Task status.
         * @param prev Previous closure.
         */
        private void onSetupFinished(final GridHadoopTaskInfo taskInfo, GridHadoopTaskStatus status, StackedProcessor prev) {
            final GridHadoopJobId jobId = taskInfo.jobId();

            if (status.state() == FAILED || status.state() == CRASHED)
                transform(jobId, new CancelJobProcessor(prev, status.failCause()));
            else
                transform(jobId, new UpdatePhaseProcessor(prev, PHASE_MAP));
        }

        /**
         * @param taskInfo Task info.
         * @param status Task status.
         * @param prev Previous closure.
         */
        private void onMapFinished(final GridHadoopTaskInfo taskInfo, GridHadoopTaskStatus status,
            final StackedProcessor prev) {
            final GridHadoopJobId jobId = taskInfo.jobId();

            boolean lastMapperFinished = completedMappersCnt.incrementAndGet() == currMappers.size();

            if (status.state() == FAILED || status.state() == CRASHED) {
                // Fail the whole job.
                transform(jobId, new RemoveMappersProcessor(prev, taskInfo.inputSplit(), status.failCause()));

                return;
            }

            IgniteInClosure<IgniteInternalFuture<?>> cacheUpdater = new CIX1<IgniteInternalFuture<?>>() {
                @Override public void applyx(IgniteInternalFuture<?> f) {
                    Throwable err = null;

                    if (f != null) {
                        try {
                            f.get();
                        }
                        catch (IgniteCheckedException e) {
                            err = e;
                        }
                    }

                    transform(jobId, new RemoveMappersProcessor(prev, taskInfo.inputSplit(), err));
                }
            };

            if (lastMapperFinished)
                ctx.shuffle().flush(jobId).listenAsync(cacheUpdater);
            else
                cacheUpdater.apply(null);
        }

        /**
         * @param taskInfo Task info.
         * @param status Task status.
         * @param prev Previous closure.
         */
        private void onReduceFinished(GridHadoopTaskInfo taskInfo, GridHadoopTaskStatus status, StackedProcessor prev) {
            GridHadoopJobId jobId = taskInfo.jobId();
            if (status.state() == FAILED || status.state() == CRASHED)
                // Fail the whole job.
                transform(jobId, new RemoveReducerProcessor(prev, taskInfo.taskNumber(), status.failCause()));
            else
                transform(jobId, new RemoveReducerProcessor(prev, taskInfo.taskNumber()));
        }

        /**
         * @param taskInfo Task info.
         * @param status Task status.
         * @param prev Previous closure.
         */
        private void onCombineFinished(GridHadoopTaskInfo taskInfo, GridHadoopTaskStatus status,
            final StackedProcessor prev) {
            final GridHadoopJobId jobId = taskInfo.jobId();

            if (status.state() == FAILED || status.state() == CRASHED)
                // Fail the whole job.
                transform(jobId, new RemoveMappersProcessor(prev, currMappers, status.failCause()));
            else {
                ctx.shuffle().flush(jobId).listenAsync(new CIX1<IgniteInternalFuture<?>>() {
                    @Override public void applyx(IgniteInternalFuture<?> f) {
                        Throwable err = null;

                        if (f != null) {
                            try {
                                f.get();
                            }
                            catch (IgniteCheckedException e) {
                                err = e;
                            }
                        }

                        transform(jobId, new RemoveMappersProcessor(prev, currMappers, err));
                    }
                });
            }
        }

        /**
         * @return {@code True} if job was cancelled by this (first) call.
         */
        public boolean onCancel() {
            if (!cancelled && !aborted) {
                cancelled = true;

                return true;
            }

            return false;
        }

        /**
         * @return {@code True} if job was aborted this (first) call.
         */
        public boolean onAborted() {
            if (!aborted) {
                aborted = true;

                return true;
            }

            return false;
        }
    }

    /**
     * Update job phase transform closure.
     */
    private static class UpdatePhaseProcessor extends StackedProcessor {
        /** */
        private static final long serialVersionUID = 0L;

        /** Phase to update. */
        private final GridHadoopJobPhase phase;

        /**
         * @param prev Previous closure.
         * @param phase Phase to update.
         */
        private UpdatePhaseProcessor(@Nullable StackedProcessor prev, GridHadoopJobPhase phase) {
            super(prev);

            this.phase = phase;
        }

        /** {@inheritDoc} */
        @Override protected void update(GridHadoopJobMetadata meta, GridHadoopJobMetadata cp) {
            cp.phase(phase);
        }
    }

    /**
     * Remove mapper transform closure.
     */
    private static class RemoveMappersProcessor extends StackedProcessor {
        /** */
        private static final long serialVersionUID = 0L;

        /** Mapper split to remove. */
        private final Collection<GridHadoopInputSplit> splits;

        /** Error. */
        private final Throwable err;

        /**
         * @param prev Previous closure.
         * @param split Mapper split to remove.
         * @param err Error.
         */
        private RemoveMappersProcessor(@Nullable StackedProcessor prev, GridHadoopInputSplit split, Throwable err) {
            this(prev, Collections.singletonList(split), err);
        }

        /**
         * @param prev Previous closure.
         * @param splits Mapper splits to remove.
         * @param err Error.
         */
        private RemoveMappersProcessor(@Nullable StackedProcessor prev, Collection<GridHadoopInputSplit> splits,
            Throwable err) {
            super(prev);

            this.splits = splits;
            this.err = err;
        }

        /** {@inheritDoc} */
        @Override protected void update(GridHadoopJobMetadata meta, GridHadoopJobMetadata cp) {
            Map<GridHadoopInputSplit, Integer> splitsCp = new HashMap<>(cp.pendingSplits());

            for (GridHadoopInputSplit s : splits)
                splitsCp.remove(s);

            cp.pendingSplits(splitsCp);

            if (cp.phase() != PHASE_CANCELLING && err != null)
                cp.failCause(err);

            if (err != null)
                cp.phase(PHASE_CANCELLING);

            if (splitsCp.isEmpty()) {
                if (cp.phase() != PHASE_CANCELLING)
                    cp.phase(PHASE_REDUCE);
            }
        }
    }

    /**
     * Remove reducer transform closure.
     */
    private static class RemoveReducerProcessor extends StackedProcessor {
        /** */
        private static final long serialVersionUID = 0L;

        /** Mapper split to remove. */
        private final int rdc;

        /** Error. */
        private Throwable err;

        /**
         * @param prev Previous closure.
         * @param rdc Reducer to remove.
         */
        private RemoveReducerProcessor(@Nullable StackedProcessor prev, int rdc) {
            super(prev);

            this.rdc = rdc;
        }

        /**
         * @param prev Previous closure.
         * @param rdc Reducer to remove.
         * @param err Error.
         */
        private RemoveReducerProcessor(@Nullable StackedProcessor prev, int rdc, Throwable err) {
            super(prev);

            this.rdc = rdc;
            this.err = err;
        }

        /** {@inheritDoc} */
        @Override protected void update(GridHadoopJobMetadata meta, GridHadoopJobMetadata cp) {
            Collection<Integer> rdcCp = new HashSet<>(cp.pendingReducers());

            rdcCp.remove(rdc);

            cp.pendingReducers(rdcCp);

            if (err != null) {
                cp.phase(PHASE_CANCELLING);
                cp.failCause(err);
            }
        }
    }

    /**
     * Initialize reducers.
     */
    private static class InitializeReducersProcessor extends StackedProcessor {
        /** */
        private static final long serialVersionUID = 0L;

        /** Reducers. */
        private final Collection<Integer> rdc;

        /** Process descriptor for reducers. */
        private final GridHadoopProcessDescriptor desc;

        /**
         * @param prev Previous closure.
         * @param rdc Reducers to initialize.
         * @param desc External process descriptor.
         */
        private InitializeReducersProcessor(@Nullable StackedProcessor prev,
            Collection<Integer> rdc,
            GridHadoopProcessDescriptor desc) {
            super(prev);

            assert !F.isEmpty(rdc);
            assert desc != null;

            this.rdc = rdc;
            this.desc = desc;
        }

        /** {@inheritDoc} */
        @Override protected void update(GridHadoopJobMetadata meta, GridHadoopJobMetadata cp) {
            Map<Integer, GridHadoopProcessDescriptor> oldMap = meta.reducersAddresses();

            Map<Integer, GridHadoopProcessDescriptor> rdcMap = oldMap == null ?
                new HashMap<Integer, GridHadoopProcessDescriptor>() : new HashMap<>(oldMap);

            for (Integer r : rdc)
                rdcMap.put(r, desc);

            cp.reducersAddresses(rdcMap);
        }
    }

    /**
     * Remove reducer transform closure.
     */
    private static class CancelJobProcessor extends StackedProcessor {
        /** */
        private static final long serialVersionUID = 0L;

        /** Mapper split to remove. */
        private final Collection<GridHadoopInputSplit> splits;

        /** Reducers to remove. */
        private final Collection<Integer> rdc;

        /** Error. */
        private final Throwable err;

        /**
         * @param prev Previous closure.
         * @param err Fail cause.
         */
        private CancelJobProcessor(@Nullable StackedProcessor prev, Throwable err) {
            this(prev, err, null, null);
        }

        /**
         * @param prev Previous closure.
         * @param splits Splits to remove.
         * @param rdc Reducers to remove.
         */
        private CancelJobProcessor(@Nullable StackedProcessor prev,
            Collection<GridHadoopInputSplit> splits,
            Collection<Integer> rdc) {
            this(prev, null, splits, rdc);
        }

        /**
         * @param prev Previous closure.
         * @param err Error.
         * @param splits Splits to remove.
         * @param rdc Reducers to remove.
         */
        private CancelJobProcessor(@Nullable StackedProcessor prev,
            Throwable err,
            Collection<GridHadoopInputSplit> splits,
            Collection<Integer> rdc) {
            super(prev);

            this.splits = splits;
            this.rdc = rdc;
            this.err = err;
        }

        /** {@inheritDoc} */
        @Override protected void update(GridHadoopJobMetadata meta, GridHadoopJobMetadata cp) {
            assert meta.phase() == PHASE_CANCELLING || err != null: "Invalid phase for cancel: " + meta;

            Collection<Integer> rdcCp = new HashSet<>(cp.pendingReducers());

            if (rdc != null)
                rdcCp.removeAll(rdc);

            cp.pendingReducers(rdcCp);

            Map<GridHadoopInputSplit, Integer> splitsCp = new HashMap<>(cp.pendingSplits());

            if (splits != null) {
                for (GridHadoopInputSplit s : splits)
                    splitsCp.remove(s);
            }

            cp.pendingSplits(splitsCp);

            cp.phase(PHASE_CANCELLING);

            if (err != null)
                cp.failCause(err);
        }
    }

    /**
     * Increment counter values closure.
     */
    private static class IncrementCountersProcessor extends StackedProcessor {
        /** */
        private static final long serialVersionUID = 0L;

        /** */
        private final GridHadoopCounters counters;

        /**
         * @param prev Previous closure.
         * @param counters Task counters to add into job counters.
         */
        private IncrementCountersProcessor(@Nullable StackedProcessor prev, GridHadoopCounters counters) {
            super(prev);

            assert counters != null;

            this.counters = counters;
        }

        /** {@inheritDoc} */
        @Override protected void update(GridHadoopJobMetadata meta, GridHadoopJobMetadata cp) {
            GridHadoopCounters cntrs = new GridHadoopCountersImpl(cp.counters());

            cntrs.merge(counters);

            cp.counters(cntrs);
        }
    }

    /**
     * Abstract stacked closure.
     */
    private abstract static class StackedProcessor implements
        EntryProcessor<GridHadoopJobId, GridHadoopJobMetadata, Void>, Serializable {
        /** */
        private static final long serialVersionUID = 0L;

        /** */
        private final StackedProcessor prev;

        /**
         * @param prev Previous closure.
         */
        private StackedProcessor(@Nullable StackedProcessor prev) {
            this.prev = prev;
        }

        /** {@inheritDoc} */
        @Override public Void process(MutableEntry<GridHadoopJobId, GridHadoopJobMetadata> e, Object... args) {
            GridHadoopJobMetadata val = apply(e.getValue());

            if (val != null)
                e.setValue(val);
            else
                e.remove();;

            return null;
        }

        /**
         * @param meta Old value.
         * @return New value.
         */
        private GridHadoopJobMetadata apply(GridHadoopJobMetadata meta) {
            if (meta == null)
                return null;

            GridHadoopJobMetadata cp = prev != null ? prev.apply(meta) : new GridHadoopJobMetadata(meta);

            update(meta, cp);

            return cp;
        }

        /**
         * Update given job metadata object.
         *
         * @param meta Initial job metadata.
         * @param cp Copy.
         */
        protected abstract void update(GridHadoopJobMetadata meta, GridHadoopJobMetadata cp);
    }
}
