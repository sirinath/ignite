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

package org.gridgain.grid.cache;

import org.apache.ignite.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.transactions.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.cache.affinity.consistenthash.*;
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.cache.store.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Main entry point for all <b>Data Grid APIs.</b> You can get a named cache by calling {@link org.apache.ignite.Ignite#cache(String)}
 * method.
 * <h1 class="header">Functionality</h1>
 * This API extends {@link GridCacheProjection} API which contains vast majority of cache functionality
 * and documentation. In addition to {@link GridCacheProjection} functionality this API provides:
 * <ul>
 * <li>
 *  Various {@code 'loadCache(..)'} methods to load cache either synchronously or asynchronously.
 *  These methods don't specify any keys to load, and leave it to the underlying storage to load cache
 *  data based on the optionally passed in arguments.
 * </li>
 * <li>
 *     Method {@link #affinity()} provides {@link GridCacheAffinityFunction} service for information on
 *     data partitioning and mapping keys to grid nodes responsible for caching those keys.
 * </li>
 * <li>
 *     Method {@link #dataStructures()} provides {@link GridCacheDataStructures} service for
 *     creating and working with distributed concurrent data structures, such as
 *     {@link GridCacheAtomicLong}, {@link GridCacheAtomicReference}, {@link GridCacheQueue}, etc.
 * </li>
 * <li>
 *  Methods like {@code 'tx{Un}Synchronize(..)'} witch allow to get notifications for transaction state changes.
 *  This feature is very useful when integrating cache transactions with some other in-house transactions.
 * </li>
 * <li>Method {@link #metrics()} to provide metrics for the whole cache.</li>
 * <li>Method {@link #configuration()} to provide cache configuration bean.</li>
 * </ul>
 *
 * @param <K> Cache key type.
 * @param <V> Cache value type.
 */
public interface GridCache<K, V> extends GridCacheProjection<K, V> {
    /**
     * Gets configuration bean for this cache.
     *
     * @return Configuration bean for this cache.
     */
    public GridCacheConfiguration configuration();

    /**
     * Registers transactions synchronizations for all transactions started by this cache.
     * Use it whenever you need to get notifications on transaction lifecycle and possibly change
     * its course. It is also particularly useful when integrating cache transactions
     * with some other in-house transactions.
     *
     * @param syncs Transaction synchronizations to register.
     */
    public void txSynchronize(@Nullable IgniteTxSynchronization syncs);

    /**
     * Removes transaction synchronizations.
     *
     * @param syncs Transactions synchronizations to remove.
     * @see #txSynchronize(IgniteTxSynchronization)
     */
    public void txUnsynchronize(@Nullable IgniteTxSynchronization syncs);

    /**
     * Gets registered transaction synchronizations.
     *
     * @return Registered transaction synchronizations.
     * @see #txSynchronize(IgniteTxSynchronization)
     */
    public Collection<IgniteTxSynchronization> txSynchronizations();

    /**
     * Gets affinity service to provide information about data partitioning
     * and distribution.
     *
     * @return Cache data affinity service.
     */
    public GridCacheAffinity<K> affinity();

    /**
     * Gets data structures service to provide a gateway for creating various
     * distributed data structures similar in APIs to {@code java.util.concurrent} package.
     *
     * @return Cache data structures service.
     */
    public GridCacheDataStructures dataStructures();

    /**
     * Gets metrics (statistics) for this cache.
     *
     * @return Cache metrics.
     */
    public GridCacheMetrics metrics();

    /**
     * Gets size (in bytes) of all entries swapped to disk.
     *
     * @return Size (in bytes) of all entries swapped to disk.
     * @throws IgniteCheckedException In case of error.
     */
    public long overflowSize() throws IgniteCheckedException;

    /**
     * Gets number of cache entries stored in off-heap memory.
     *
     * @return Number of cache entries stored in off-heap memory.
     */
    public long offHeapEntriesCount();

    /**
     * Gets memory size allocated in off-heap.
     *
     * @return Allocated memory size.
     */
    public long offHeapAllocatedSize();

    /**
     * Gets size in bytes for swap space.
     *
     * @return Size in bytes.
     * @throws IgniteCheckedException If failed.
     */
    public long swapSize() throws IgniteCheckedException;

    /**
     * Gets number of swap entries (keys).
     *
     * @return Number of entries stored in swap.
     * @throws IgniteCheckedException If failed.
     */
    public long swapKeys() throws IgniteCheckedException;

    /**
     * Gets iterator over keys and values belonging to this cache swap space on local node. This
     * iterator is thread-safe, which means that cache (and therefore its swap space)
     * may be modified concurrently with iteration over swap.
     * <p>
     * Returned iterator supports {@code remove} operation which delegates to
     * {@link #removex(Object, org.apache.ignite.lang.IgnitePredicate[])} method.
     * <h2 class="header">Cache Flags</h2>
     * This method is not available if any of the following flags are set on projection:
     * {@link GridCacheFlag#SKIP_SWAP}.
     *
     * @return Iterator over keys.
     * @throws IgniteCheckedException If failed.
     * @see #promote(Object)
     */
    public Iterator<Map.Entry<K, V>> swapIterator() throws IgniteCheckedException;

    /**
     * Gets iterator over keys and values belonging to this cache off-heap memory on local node. This
     * iterator is thread-safe, which means that cache (and therefore its off-heap memory)
     * may be modified concurrently with iteration over off-heap. To achieve better performance
     * the keys and values deserialized on demand, whenever accessed.
     * <p>
     * Returned iterator supports {@code remove} operation which delegates to
     * {@link #removex(Object, org.apache.ignite.lang.IgnitePredicate[])} method.
     *
     * @return Iterator over keys.
     * @throws IgniteCheckedException If failed.
     */
    public Iterator<Map.Entry<K, V>> offHeapIterator() throws IgniteCheckedException;

    /**
     * Delegates to {@link GridCacheStore#loadCache(org.apache.ignite.lang.IgniteBiInClosure,Object...)} method
     * to load state from the underlying persistent storage. The loaded values
     * will then be given to the optionally passed in predicate, and, if the predicate returns
     * {@code true}, will be stored in cache. If predicate is {@code null}, then
     * all loaded values will be stored in cache.
     * <p>
     * Note that this method does not receive keys as a parameter, so it is up to
     * {@link GridCacheStore} implementation to provide all the data to be loaded.
     * <p>
     * This method is not transactional and may end up loading a stale value into
     * cache if another thread has updated the value immediately after it has been
     * loaded. It is mostly useful when pre-loading the cache from underlying
     * data store before start, or for read-only caches.
     *
     * @param p Optional predicate (may be {@code null}). If provided, will be used to
     *      filter values to be put into cache.
     * @param ttl Time to live for loaded entries ({@code 0} for infinity).
     * @param args Optional user arguments to be passed into
     *      {@link GridCacheStore#loadCache(org.apache.ignite.lang.IgniteBiInClosure, Object...)} method.
     * @throws IgniteCheckedException If loading failed.
     */
    public void loadCache(@Nullable IgniteBiPredicate<K, V> p, long ttl, @Nullable Object... args) throws IgniteCheckedException;

    /**
     * Asynchronously delegates to {@link GridCacheStore#loadCache(org.apache.ignite.lang.IgniteBiInClosure, Object...)} method
     * to reload state from the underlying persistent storage. The reloaded values
     * will then be given to the optionally passed in predicate, and if the predicate returns
     * {@code true}, will be stored in cache. If predicate is {@code null}, then
     * all reloaded values will be stored in cache.
     * <p>
     * Note that this method does not receive keys as a parameter, so it is up to
     * {@link GridCacheStore} implementation to provide all the data to be loaded.
     * <p>
     * This method is not transactional and may end up loading a stale value into
     * cache if another thread has updated the value immediately after it has been
     * loaded. It is mostly useful when pre-loading the cache from underlying
     * data store before start, or for read-only caches.
     *
     * @param p Optional predicate (may be {@code null}). If provided, will be used to
     *      filter values to be put into cache.
     * @param ttl Time to live for loaded entries ({@code 0} for infinity).
     * @param args Optional user arguments to be passed into
     *      {@link GridCacheStore#loadCache(org.apache.ignite.lang.IgniteBiInClosure,Object...)} method.
     * @return Future to be completed whenever loading completes.
     */
    public IgniteFuture<?> loadCacheAsync(@Nullable IgniteBiPredicate<K, V> p, long ttl, @Nullable Object... args);

    /**
     * Gets a random entry out of cache. In the worst cache scenario this method
     * has complexity of <pre>O(S * N/64)</pre> where {@code N} is the size of internal hash
     * table and {@code S} is the number of hash table buckets to sample, which is {@code 5}
     * by default. However, if the table is pretty dense, with density factor of {@code N/64},
     * which is true for near fully populated caches, this method will generally perform significantly
     * faster with complexity of O(S) where {@code S = 5}.
     * <p>
     * Note that this method is not available on {@link GridCacheProjection} API since it is
     * impossible (or very hard) to deterministically return a number value when pre-filtering
     * and post-filtering is involved (e.g. projection level predicate filters).
     *
     * @return Random entry, or {@code null} if cache is empty.
     */
    @Nullable public GridCacheEntry<K, V> randomEntry();

    /**
     * Forces this cache node to re-balance its partitions. This method is usually used when
     * {@link GridCacheConfiguration#getPreloadPartitionedDelay()} configuration parameter has non-zero value.
     * When many nodes are started or stopped almost concurrently, it is more efficient to delay
     * preloading until the node topology is stable to make sure that no redundant re-partitioning
     * happens.
     * <p>
     * In case of{@link GridCacheMode#PARTITIONED} caches, for better efficiency user should
     * usually make sure that new nodes get placed on the same place of consistent hash ring as
     * the left nodes, and that nodes are restarted before
     * {@link GridCacheConfiguration#getPreloadPartitionedDelay() preloadDelay} expires. To place nodes
     * on the same place in consistent hash ring, use
     * {@link GridCacheConsistentHashAffinityFunction#setHashIdResolver(GridCacheAffinityNodeHashResolver)} to make sure that
     * a node maps to the same hash ID if re-started.
     * <p>
     * See {@link GridCacheConfiguration#getPreloadPartitionedDelay()} for more information on how to configure
     * preload re-partition delay.
     * <p>
     * @return Future that will be completed when preloading is finished.
     */
    public IgniteFuture<?> forceRepartition();

    /**
     * Resets metrics for current cache.
     */
    public void resetMetrics();
}
