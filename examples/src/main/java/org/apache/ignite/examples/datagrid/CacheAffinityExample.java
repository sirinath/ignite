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

package org.apache.ignite.examples.datagrid;

import org.apache.ignite.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.lang.*;

import java.util.*;

/**
 * This example demonstrates the simplest code that populates the distributed cache
 * and co-locates simple closure execution with each key. The goal of this particular
 * example is to provide the simplest code example of this logic.
 * <p>
 * Remote nodes should always be started with special configuration file which
 * enables P2P class loading: {@code 'ignite.{sh|bat} examples/config/example-cache.xml'}.
 * <p>
 * Alternatively you can run {@link CacheNodeStartup} in another JVM which will
 * start node with {@code examples/config/example-cache.xml} configuration.
 */
public final class CacheAffinityExample {
    /** Cache name. */
    private static final String CACHE_NAME = "partitioned";

    /** Number of keys. */
    private static final int KEY_CNT = 20;

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     * @throws IgniteException If example execution failed.
     */
    public static void main(String[] args) throws IgniteException {
        try (Ignite ignite = Ignition.start("examples/config/example-cache.xml")) {
            System.out.println();
            System.out.println(">>> Cache affinity example started.");

            IgniteCache<Integer, String> cache = ignite.jcache(CACHE_NAME);

            // Clean up caches on all nodes before run.
            cache.clear();

            for (int i = 0; i < KEY_CNT; i++)
                cache.put(i, Integer.toString(i));

            // Co-locates jobs with data using IgniteCompute.affinityRun(...) method.
            visitUsingAffinityRun();

            // Co-locates jobs with data using IgniteCluster.mapKeysToNodes(...) method.
            visitUsingMapKeysToNodes();
        }
    }

    /**
     * Collocates jobs with keys they need to work on using
     * {@link IgniteCompute#affinityRun(String, Object, IgniteRunnable)} method.
     */
    private static void visitUsingAffinityRun() {
        Ignite ignite = Ignition.ignite();

        final IgniteCache<Integer, String> cache = ignite.jcache(CACHE_NAME);

        for (int i = 0; i < KEY_CNT; i++) {
            final int key = i;

            // This runnable will execute on the remote node where
            // data with the given key is located. Since it will be co-located
            // we can use local 'peek' operation safely.
            ignite.compute().affinityRun(CACHE_NAME, key, new IgniteRunnable() {
                @Override public void run() {
                    // Peek is a local memory lookup, however, value should never be 'null'
                    // as we are co-located with node that has a given key.
                    System.out.println("Co-located using affinityRun [key= " + key + ", value=" + cache.localPeek(key) + ']');
                }
            });
        }
    }

    /**
     * Collocates jobs with keys they need to work on using {@link IgniteCluster#mapKeysToNodes(String, Collection)}
     * method. The difference from {@code affinityRun(...)} method is that here we process multiple keys
     * in a single job.
     */
    private static void visitUsingMapKeysToNodes() {
        final Ignite ignite = Ignition.ignite();

        Collection<Integer> keys = new ArrayList<>(KEY_CNT);

        for (int i = 0; i < KEY_CNT; i++)
            keys.add(i);

        // Map all keys to nodes.
        Map<ClusterNode, Collection<Integer>> mappings = ignite.cluster().mapKeysToNodes(CACHE_NAME, keys);

        for (Map.Entry<ClusterNode, Collection<Integer>> mapping : mappings.entrySet()) {
            ClusterNode node = mapping.getKey();

            final Collection<Integer> mappedKeys = mapping.getValue();

            if (node != null) {
                // Bring computations to the nodes where the data resides (i.e. collocation).
                ignite.compute(ignite.cluster().forNode(node)).run(new IgniteRunnable() {
                    @Override public void run() {
                        IgniteCache<Integer, String> cache = ignite.jcache(CACHE_NAME);

                        // Peek is a local memory lookup, however, value should never be 'null'
                        // as we are co-located with node that has a given key.
                        for (Integer key : mappedKeys)
                            System.out.println("Co-located using mapKeysToNodes [key= " + key +
                                ", value=" + cache.localPeek(key) + ']');
                    }
                });
            }
        }
    }
}