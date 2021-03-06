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

package org.gridgain.grid.kernal.visor.node;

import org.apache.ignite.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.compute.*;
import org.apache.ignite.events.*;
import org.apache.ignite.lang.*;
import org.gridgain.grid.kernal.processors.task.*;
import org.gridgain.grid.kernal.visor.*;
import org.gridgain.grid.kernal.visor.event.*;
import org.gridgain.grid.kernal.visor.util.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static org.apache.ignite.events.IgniteEventType.*;

/**
 * Task that runs on specified node and returns events data.
 */
@GridInternal
public class VisorNodeEventsCollectorTask extends VisorMultiNodeTask<VisorNodeEventsCollectorTask.VisorNodeEventsCollectorTaskArg,
    Iterable<? extends VisorGridEvent>, Collection<? extends VisorGridEvent>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorEventsCollectJob job(VisorNodeEventsCollectorTaskArg arg) {
        return new VisorEventsCollectJob(arg, debug);
    }

    /** {@inheritDoc} */
    @Override protected Iterable<? extends VisorGridEvent> reduce0(
        List<ComputeJobResult> results) throws IgniteCheckedException {

        Collection<VisorGridEvent> allEvents = new ArrayList<>();

        for (ComputeJobResult r : results) {
            if (r.getException() == null)
                allEvents.addAll((Collection<VisorGridEvent>) r.getData());
        }

        return allEvents.isEmpty() ? Collections.<VisorGridEvent>emptyList() : allEvents;
    }

    /**
     * Argument for task returns events data.
     */
    @SuppressWarnings("PublicInnerClass")
    public static class VisorNodeEventsCollectorTaskArg implements Serializable {
        /** */
        private static final long serialVersionUID = 0L;

        /** Node local storage key. */
        private final String keyOrder;

        /** Arguments for type filter. */
        private final int[] typeArg;

        /** Arguments for time filter. */
        private final Long timeArg;

        /** Task or job events with task name contains. */
        private final String taskName;

        /** Task or job events with session. */
        private final IgniteUuid taskSessionId;

        /**
         * @param keyOrder Arguments for node local storage key.
         * @param typeArg Arguments for type filter.
         * @param timeArg Arguments for time filter.
         * @param taskName Arguments for task name filter.
         * @param taskSessionId Arguments for task session filter.
         */
        public VisorNodeEventsCollectorTaskArg(@Nullable String keyOrder, @Nullable int[] typeArg,
            @Nullable Long timeArg,
            @Nullable String taskName, @Nullable IgniteUuid taskSessionId) {
            this.keyOrder = keyOrder;
            this.typeArg = typeArg;
            this.timeArg = timeArg;
            this.taskName = taskName;
            this.taskSessionId = taskSessionId;
        }

        /**
         * @param typeArg Arguments for type filter.
         * @param timeArg Arguments for time filter.
         */
        public static VisorNodeEventsCollectorTaskArg createEventsArg(@Nullable int[] typeArg, @Nullable Long timeArg) {
            return new VisorNodeEventsCollectorTaskArg(null, typeArg, timeArg, null, null);
        }

        /**
         * @param timeArg Arguments for time filter.
         * @param taskName Arguments for task name filter.
         * @param taskSessionId Arguments for task session filter.
         */
        public static VisorNodeEventsCollectorTaskArg createTasksArg(@Nullable Long timeArg, @Nullable String taskName,
            @Nullable IgniteUuid taskSessionId) {
            return new VisorNodeEventsCollectorTaskArg(null,
                VisorTaskUtils.concat(EVTS_JOB_EXECUTION, EVTS_TASK_EXECUTION, EVTS_AUTHENTICATION, EVTS_AUTHORIZATION,
                    EVTS_SECURE_SESSION),
                timeArg, taskName, taskSessionId);
        }

        /**
         * @param keyOrder Arguments for node local storage key.
         * @param typeArg Arguments for type filter.
         */
        public static VisorNodeEventsCollectorTaskArg createLogArg(@Nullable String keyOrder, @Nullable int[] typeArg) {
            return new VisorNodeEventsCollectorTaskArg(keyOrder, typeArg, null, null, null);
        }

        /**
         * @return Node local storage key.
         */
        @Nullable public String keyOrder() {
            return keyOrder;
        }

        /**
         * @return Arguments for type filter.
         */
        public int[] typeArgument() {
            return typeArg;
        }

        /**
         * @return Arguments for time filter.
         */
        public Long timeArgument() {
            return timeArg;
        }

        /**
         * @return Task or job events with task name contains.
         */
        public String taskName() {
            return taskName;
        }

        /**
         * @return Task or job events with session.
         */
        public IgniteUuid taskSessionId() {
            return taskSessionId;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorNodeEventsCollectorTaskArg.class, this);
        }
    }

    /**
     * Job for task returns events data.
     */
    private static class VisorEventsCollectJob extends VisorJob<VisorNodeEventsCollectorTaskArg,
            Collection<? extends VisorGridEvent>> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job with specified argument.
         *
         * @param arg Job argument.
         * @param debug Debug flag.
         */
        private VisorEventsCollectJob(VisorNodeEventsCollectorTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /**
         * Tests whether or not this task has specified substring in its name.
         *
         * @param taskName Task name to check.
         * @param taskClsName Task class name to check.
         * @param s Substring to check.
         */
        private boolean containsInTaskName(String taskName, String taskClsName, String s) {
            assert taskName != null;
            assert taskClsName != null;

            if (taskName.equals(taskClsName)) {
                int idx = taskName.lastIndexOf('.');

                return ((idx >= 0) ? taskName.substring(idx + 1) : taskName).toLowerCase().contains(s);
            }

            return taskName.toLowerCase().contains(s);
        }

        /**
         * Filter events containing visor in it's name.
         *
         * @param e Event
         * @return {@code true} if not contains {@code visor} in task name.
         */
        private boolean filterByTaskName(IgniteEvent e, String taskName) {
            if (e.getClass().equals(IgniteTaskEvent.class)) {
                IgniteTaskEvent te = (IgniteTaskEvent)e;

                return containsInTaskName(te.taskName(), te.taskClassName(), taskName);
            }

            if (e.getClass().equals(IgniteJobEvent.class)) {
                IgniteJobEvent je = (IgniteJobEvent)e;

                return containsInTaskName(je.taskName(), je.taskName(), taskName);
            }

            if (e.getClass().equals(IgniteDeploymentEvent.class)) {
                IgniteDeploymentEvent de = (IgniteDeploymentEvent)e;

                return de.alias().toLowerCase().contains(taskName);
            }

            return true;
        }

        /**
         * Filter events containing visor in it's name.
         *
         * @param e Event
         * @return {@code true} if not contains {@code visor} in task name.
         */
        private boolean filterByTaskSessionId(IgniteEvent e, IgniteUuid taskSessionId) {
            if (e.getClass().equals(IgniteTaskEvent.class)) {
                IgniteTaskEvent te = (IgniteTaskEvent)e;

                return te.taskSessionId().equals(taskSessionId);
            }

            if (e.getClass().equals(IgniteJobEvent.class)) {
                IgniteJobEvent je = (IgniteJobEvent)e;

                return je.taskSessionId().equals(taskSessionId);
            }

            return true;
        }

        /** {@inheritDoc} */
        @Override protected Collection<? extends VisorGridEvent> run(final VisorNodeEventsCollectorTaskArg arg)
            throws IgniteCheckedException {
            final long startEvtTime = arg.timeArgument() == null ? 0L : System.currentTimeMillis() - arg.timeArgument();

            final ClusterNodeLocalMap<String, Long> nl = g.nodeLocalMap();

            final Long startEvtOrder = arg.keyOrder() != null && nl.containsKey(arg.keyOrder()) ?
                nl.get(arg.keyOrder()) : -1L;

            Collection<IgniteEvent> evts = g.events().localQuery(new IgnitePredicate<IgniteEvent>() {
                @Override public boolean apply(IgniteEvent event) {
                    return event.localOrder() > startEvtOrder &&
                        (arg.typeArgument() == null || F.contains(arg.typeArgument(), event.type())) &&
                        event.timestamp() >= startEvtTime &&
                        (arg.taskName() == null || filterByTaskName(event, arg.taskName())) &&
                        (arg.taskSessionId() == null || filterByTaskSessionId(event, arg.taskSessionId()));
                }
            });

            Collection<VisorGridEvent> res = new ArrayList<>(evts.size());

            Long maxOrder = startEvtOrder;

            for (IgniteEvent e : evts) {
                int tid = e.type();
                IgniteUuid id = e.id();
                String name = e.name();
                UUID nid = e.node().id();
                long t = e.timestamp();
                String msg = e.message();
                String shortDisplay = e.shortDisplay();

                maxOrder = Math.max(maxOrder, e.localOrder());

                if (e instanceof IgniteTaskEvent) {
                    IgniteTaskEvent te = (IgniteTaskEvent)e;

                    res.add(new VisorGridTaskEvent(tid, id, name, nid, t, msg, shortDisplay,
                        te.taskName(), te.taskClassName(), te.taskSessionId(), te.internal()));
                }
                else if (e instanceof IgniteJobEvent) {
                    IgniteJobEvent je = (IgniteJobEvent)e;

                    res.add(new VisorGridJobEvent(tid, id, name, nid, t, msg, shortDisplay,
                        je.taskName(), je.taskClassName(), je.taskSessionId(), je.jobId()));
                }
                else if (e instanceof IgniteDeploymentEvent) {
                    IgniteDeploymentEvent de = (IgniteDeploymentEvent)e;

                    res.add(new VisorGridDeploymentEvent(tid, id, name, nid, t, msg, shortDisplay, de.alias()));
                }
                else if (e instanceof IgniteLicenseEvent) {
                    IgniteLicenseEvent le = (IgniteLicenseEvent)e;

                    res.add(new VisorGridLicenseEvent(tid, id, name, nid, t, msg, shortDisplay, le.licenseId()));
                }
                else if (e instanceof IgniteDiscoveryEvent) {
                    IgniteDiscoveryEvent de = (IgniteDiscoveryEvent)e;

                    ClusterNode node = de.eventNode();

                    String addr = F.first(node.addresses());

                    res.add(new VisorGridDiscoveryEvent(tid, id, name, nid, t, msg, shortDisplay,
                        node.id(), addr, node.isDaemon()));
                }
                else if (e instanceof IgniteAuthenticationEvent) {
                    IgniteAuthenticationEvent ae = (IgniteAuthenticationEvent)e;

                    res.add(new VisorGridAuthenticationEvent(tid, id, name, nid, t, msg, shortDisplay, ae.subjectType(),
                        ae.subjectId(), ae.login()));
                }
                else if (e instanceof IgniteAuthorizationEvent) {
                    IgniteAuthorizationEvent ae = (IgniteAuthorizationEvent)e;

                    res.add(new VisorGridAuthorizationEvent(tid, id, name, nid, t, msg, shortDisplay, ae.operation(),
                        ae.subject()));
                }
                else if (e instanceof IgniteSecureSessionEvent) {
                    IgniteSecureSessionEvent se = (IgniteSecureSessionEvent) e;

                    res.add(new VisorGridSecuritySessionEvent(tid, id, name, nid, t, msg, shortDisplay, se.subjectType(),
                        se.subjectId()));
                }
                else
                    res.add(new VisorGridEvent(tid, id, name, nid, t, msg, shortDisplay));
            }

            // Update latest order in node local, if not empty.
            if (arg.keyOrder() != null && !res.isEmpty())
                nl.put(arg.keyOrder(), maxOrder);

            return res;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorEventsCollectJob.class, this);
        }
    }
}
