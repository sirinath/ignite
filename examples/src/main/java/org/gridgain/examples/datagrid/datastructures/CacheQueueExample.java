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

package org.gridgain.examples.datagrid.datastructures;

import org.apache.ignite.*;
import org.apache.ignite.lang.*;
import org.gridgain.examples.datagrid.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.datastructures.*;

import java.util.*;

/**
 * Grid cache distributed queue example. This example demonstrates {@code FIFO} unbounded
 * cache queue.
 * <p>
 * Remote nodes should always be started with special configuration file which
 * enables P2P class loading: {@code 'ggstart.{sh|bat} examples/config/example-cache.xml'}.
 * <p>
 * Alternatively you can run {@link CacheNodeStartup} in another JVM which will
 * start GridGain node with {@code examples/config/example-cache.xml} configuration.
 */
public class CacheQueueExample {
    /** Cache name. */
    private static final String CACHE_NAME = "partitioned_tx";

    /** Number of retries */
    private static final int RETRIES = 20;

    /** Queue instance. */
    private static GridCacheQueue<String> queue;

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     * @throws IgniteCheckedException If example execution failed.
     */
    public static void main(String[] args) throws IgniteCheckedException {
        try (Ignite g = Ignition.start("examples/config/example-cache.xml")) {
            System.out.println();
            System.out.println(">>> Cache queue example started.");

            // Make queue name.
            String queueName = UUID.randomUUID().toString();

            queue = initializeQueue(g, queueName);

            readFromQueue(g);

            writeToQueue(g);

            clearAndRemoveQueue(g);
        }

        System.out.println("Cache queue example finished.");
    }

    /**
     * Initialize queue.
     *
     * @param g Grid.
     * @param queueName Name of queue.
     * @return Queue.
     * @throws IgniteCheckedException If execution failed.
     */
    private static GridCacheQueue<String> initializeQueue(Ignite g, String queueName) throws IgniteCheckedException {
        // Initialize new FIFO queue.
        GridCacheQueue<String> queue = g.cache(CACHE_NAME).dataStructures().queue(queueName, 0, false, true);

        // Initialize queue items.
        // We will be use blocking operation and queue size must be appropriated.
        for (int i = 0; i < g.cluster().nodes().size() * RETRIES * 2; i++)
            queue.put(Integer.toString(i));

        System.out.println("Queue size after initializing: " + queue.size());

        return queue;
    }

    /**
     * Read items from head and tail of queue.
     *
     * @param g Grid.
     * @throws IgniteCheckedException If failed.
     */
    private static void readFromQueue(Ignite g) throws IgniteCheckedException {
        final String queueName = queue.name();

        // Read queue items on each node.
        g.compute().run(new QueueClosure(CACHE_NAME, queueName, false));

        System.out.println("Queue size after reading [expected=0, actual=" + queue.size() + ']');
    }

    /**
     * Write items into queue.
     *
     * @param g Grid.
     * @throws IgniteCheckedException If failed.
     */
    private static void writeToQueue(Ignite g) throws IgniteCheckedException {
        final String queueName = queue.name();

        // Write queue items on each node.
        g.compute().run(new QueueClosure(CACHE_NAME, queueName, true));

        System.out.println("Queue size after writing [expected=" + g.cluster().nodes().size() * RETRIES +
            ", actual=" + queue.size() + ']');

        System.out.println("Iterate over queue.");

        // Iterate over queue.
        for (String item : queue)
            System.out.println("Queue item: " + item);
    }

    /**
     * Clear and remove queue.
     *
     * @param g Grid.
     * @throws IgniteCheckedException If execution failed.
     */
    private static void clearAndRemoveQueue(Ignite g) throws IgniteCheckedException {
        System.out.println("Queue size before clearing: " + queue.size());

        // Clear queue.
        queue.clear();

        System.out.println("Queue size after clearing: " + queue.size());

        // Remove queue from cache.
        g.cache(CACHE_NAME).dataStructures().removeQueue(queue.name());

        // Try to work with removed queue.
        try {
            queue.poll();
        }
        catch (IgniteException expected) {
            System.out.println("Expected exception - " + expected.getMessage());
        }
    }

    /**
     * Closure to populate or poll the queue.
     */
    private static class QueueClosure implements IgniteRunnable {
        /** Cache name. */
        private final String cacheName;

        /** Queue name. */
        private final String queueName;

        /** Flag indicating whether to put or poll. */
        private final boolean put;

        /**
         * @param cacheName Cache name.
         * @param queueName Queue name.
         * @param put Flag indicating whether to put or poll.
         */
        QueueClosure(String cacheName, String queueName, boolean put) {
            this.cacheName = cacheName;
            this.queueName = queueName;
            this.put = put;
        }

        /** {@inheritDoc} */
        @Override public void run() {
            try {
                GridCacheQueue<String> queue = Ignition.ignite().cache(cacheName).dataStructures().
                    queue(queueName, 0, false, true);

                if (put) {
                    UUID locId = Ignition.ignite().cluster().localNode().id();

                    for (int i = 0; i < RETRIES; i++) {
                        String item = locId + "_" + Integer.toString(i);

                        queue.put(item);

                        System.out.println("Queue item has been added: " + item);
                    }
                }
                else {
                    // Take items from queue head.
                    for (int i = 0; i < RETRIES; i++)
                        System.out.println("Queue item has been read from queue head: " + queue.take());

                    // Take items from queue head once again.
                    for (int i = 0; i < RETRIES; i++)
                        System.out.println("Queue item has been read from queue head: " + queue.poll());
                }
            }
            catch (IgniteCheckedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
