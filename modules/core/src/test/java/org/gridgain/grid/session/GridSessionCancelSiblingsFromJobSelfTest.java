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

package org.gridgain.grid.session;

import org.apache.ignite.*;
import org.apache.ignite.compute.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.resources.*;
import org.gridgain.grid.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.testframework.*;
import org.gridgain.testframework.junits.common.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Session cancellation tests.
 */
@SuppressWarnings({"CatchGenericClass, PublicInnerClass"})
@GridCommonTest(group = "Task Session")
public class GridSessionCancelSiblingsFromJobSelfTest extends GridCommonAbstractTest {
    /** */
    private static final int WAIT_TIME = 20000;

    /** */
    public static final int SPLIT_COUNT = 5;

    /** */
    public static final int EXEC_COUNT = 5;

    /** */
    private static AtomicInteger[] interruptCnt;

    /** */
    private static CountDownLatch[] startSignal;

    /** */
    private static CountDownLatch[] stopSignal;

    /** */
    public GridSessionCancelSiblingsFromJobSelfTest() {
        super(true);
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(gridName);

        TcpDiscoverySpi discoSpi = new TcpDiscoverySpi();

        discoSpi.setIpFinder(new TcpDiscoveryVmIpFinder(true));

        c.setDiscoverySpi(discoSpi);

        c.setExecutorService(
            new ThreadPoolExecutor(
                SPLIT_COUNT * EXEC_COUNT,
                SPLIT_COUNT * EXEC_COUNT,
                0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>()));

        c.setExecutorServiceShutdown(true);

        return c;
    }

    /**
     * @throws Exception If failed.
     */
    public void testCancelSiblings() throws Exception {
        refreshInitialData();

        for (int i = 0; i < EXEC_COUNT; i++)
            checkTask(i);
    }

    /**
     * @throws Exception If failed.
     */
    public void testMultiThreaded() throws Exception {
        refreshInitialData();

        final GridThreadSerialNumber sNum = new GridThreadSerialNumber();

        final AtomicBoolean failed = new AtomicBoolean(false);

        GridTestUtils.runMultiThreaded(new Runnable() {
            @Override public void run() {
                int num = sNum.get();

                try {
                    checkTask(num);
                }
                catch (Throwable e) {
                    error("Failed to execute task.", e);

                    failed.set(true);
                }
            }
        }, EXEC_COUNT, "grid-session-test");

        if (failed.get())
            fail();
    }

    /**
     * @param num Task number.
     * @throws InterruptedException If interrupted.
     * @throws IgniteCheckedException If failed.
     */
    private void checkTask(int num) throws InterruptedException, IgniteCheckedException {
        Ignite ignite = G.ignite(getTestGridName());

        ComputeTaskFuture<?> fut = executeAsync(ignite.compute(), GridTaskSessionTestTask.class, num);

        boolean await = startSignal[num].await(WAIT_TIME, TimeUnit.MILLISECONDS);

        // Wait until jobs begin execution.
        assert await : "Jobs did not start.";

        assert fut != null;

        Object res = fut.get();

        assert "interrupt-task-data".equals(res) : "Invalid task result: " + res;

        await = stopSignal[num].await(WAIT_TIME, TimeUnit.MILLISECONDS);

        // Wait for all jobs to finish.
        assert await :
            "Jobs did not cancel [interruptCount=" + Arrays.toString(interruptCnt) + ']';

        int cnt = interruptCnt[num].get();

        assert cnt == SPLIT_COUNT - 1 : "Invalid interrupt count value: " + cnt;
    }

    /** */
    private void refreshInitialData() {
        interruptCnt = new AtomicInteger[EXEC_COUNT];
        startSignal = new CountDownLatch[EXEC_COUNT];
        stopSignal = new CountDownLatch[EXEC_COUNT];

        for(int i=0 ; i < EXEC_COUNT; i++){
            interruptCnt[i] = new AtomicInteger(0);

            startSignal[i] = new CountDownLatch(SPLIT_COUNT);

            // Wait only for cancelled jobs.
            stopSignal[i] = new CountDownLatch(SPLIT_COUNT - 1);
        }
    }

    /**
     *
     */
    public static class GridTaskSessionTestTask extends ComputeTaskSplitAdapter<Serializable, String> {
        /** */
        @IgniteLoggerResource
        private IgniteLogger log;

        /** */
        @IgniteTaskSessionResource
        private ComputeTaskSession taskSes;

        /** */
        private volatile int taskNum = -1;

        /** {@inheritDoc} */
        @Override protected Collection<? extends ComputeJob> split(int gridSize, Serializable arg) throws IgniteCheckedException {
            if (log.isInfoEnabled())
                log.info("Splitting job [task=" + this + ", gridSize=" + gridSize + ", arg=" + arg + ']');

            assert arg != null;

            taskNum = (Integer)arg;

            assert taskNum != -1;

            Collection<ComputeJob> jobs = new ArrayList<>(SPLIT_COUNT);

            for (int i = 1; i <= SPLIT_COUNT; i++) {
                jobs.add(new ComputeJobAdapter(i) {
                    /** */
                    private volatile Thread thread;

                    /** */
                    @IgniteJobContextResource
                    private ComputeJobContext jobCtx;

                    /** {@inheritDoc} */
                    @SuppressWarnings({"BusyWait"})
                    @Override public Object execute() throws IgniteCheckedException {
                        assert taskSes != null;

                        thread = Thread.currentThread();

                        if (log.isInfoEnabled())
                            log.info("Computing job [job=" + this + ", arg=" + argument(0) + ']');

                        startSignal[taskNum].countDown();

                        try {
                            if (!startSignal[taskNum].await(WAIT_TIME, TimeUnit.MILLISECONDS))
                                fail();

                            if (this.<Integer>argument(0) == 1) {
                                IgniteUuid jobId = jobCtx.getJobId();

                                if (log.isInfoEnabled())
                                    log.info("Job one is proceeding [jobId=" + jobId + ']');

                                assert jobId != null;

                                Collection<ComputeJobSibling> jobSiblings = taskSes.getJobSiblings();

                                // Cancel all jobs except first job with argument 1.
                                for (ComputeJobSibling jobSibling : jobSiblings) {
                                    if (!jobId.equals(jobSibling.getJobId()))
                                        jobSibling.cancel();
                                }

                            }
                            else
                                Thread.sleep(WAIT_TIME);
                        }
                        catch (InterruptedException e) {
                            if (log.isInfoEnabled())
                                log.info("Job got interrupted [arg=" + argument(0) + ", e=" + e + ']');

                            return "interrupt-job-data";
                        }

                        if (log.isInfoEnabled())
                            log.info("Completing job: " + taskSes);

                        return argument(0);
                    }

                    /** {@inheritDoc} */
                    @Override public void cancel() {
                        assert thread != null;

                        interruptCnt[taskNum].incrementAndGet();

                        stopSignal[taskNum].countDown();
                    }
                });
            }

            return jobs;
        }

        /** {@inheritDoc} */
        @Override public ComputeJobResultPolicy result(ComputeJobResult result, List<ComputeJobResult> received)
            throws IgniteCheckedException {
            return received.size() == SPLIT_COUNT ? ComputeJobResultPolicy.REDUCE : ComputeJobResultPolicy.WAIT;
        }

        /** {@inheritDoc} */
        @Override public String reduce(List<ComputeJobResult> results) throws IgniteCheckedException {
            if (log.isInfoEnabled())
                log.info("Aggregating job [job=" + this + ", results=" + results + ']');

            if (results.size() != SPLIT_COUNT)
                fail("Invalid results size.");

            return "interrupt-task-data";
        }
    }
}
