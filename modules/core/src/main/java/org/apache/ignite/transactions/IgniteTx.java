/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.apache.ignite.transactions;

import org.apache.ignite.*;
import org.apache.ignite.lang.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;

import java.util.*;

/**
 * Grid cache transaction. Cache transactions have a default 2PC (two-phase-commit) behavior and
 * can be plugged into ongoing {@code JTA} transaction by properly implementing
 * {@gglink org.gridgain.grid.cache.jta.GridCacheTmLookup}
 * interface. Cache transactions can also be started explicitly directly from {@link GridCacheProjection} API
 * via any of the {@code 'GridCacheProjection.txStart(..)'} methods.
 * <p>
 * Cache transactions support the following isolation levels:
 * <ul>
 * <li>
 *  {@link IgniteTxIsolation#READ_COMMITTED} isolation level means that always a committed value
 *  will be provided for read operations. With this isolation level values are always read
 *  from cache global memory or persistent store every time a value is accessed. In other words,
 *  if the same key is accessed more than once within the same transaction, it may have different
 *  value every time since global cache memory may be updated concurrently by other threads.
 * </li>
 * <li>
 *  {@link IgniteTxIsolation#REPEATABLE_READ} isolation level means that if a value was read once
 *  within transaction, then all consecutive reads will provide the same in-transaction value. With
 *  this isolation level accessed values are stored within in-transaction memory, so consecutive access
 *  to the same key within the same transaction will always return the value that was previously read or
 *  updated within this transaction. If concurrency is {@link IgniteTxConcurrency#PESSIMISTIC}, then a lock
 *  on the key will be acquired prior to accessing the value.
 * </li>
 * <li>
 *  {@link IgniteTxIsolation#SERIALIZABLE} isolation level means that all transactions occur in a completely
 *  isolated fashion, as if all transactions in the system had executed serially, one after the other.
 *  Read access with this level happens the same way as with {@link IgniteTxIsolation#REPEATABLE_READ} level.
 *  However, in {@link IgniteTxConcurrency#OPTIMISTIC} mode, if some transactions cannot be serially isolated
 *  from each other, then one winner will be picked and the other transactions in conflict will result in
 * {@link IgniteTxOptimisticException} being thrown.
 * </li>
 * </ul>
 * <p>
 * Cache transactions support the following concurrency models:
 * <ul>
 * <li>
 *  {@link IgniteTxConcurrency#OPTIMISTIC} - in this mode all cache operations are not distributed to other
 *  nodes until {@link #commit()} is called. In this mode one {@code 'PREPARE'}
 *  message will be sent to participating cache nodes to start acquiring per-transaction locks, and once
 *  all nodes reply {@code 'OK'} (i.e. {@code Phase 1} completes successfully), a one-way' {@code 'COMMIT'}
 *  message is sent without waiting for reply. If it is necessary to know whenever remote nodes have committed
 *  as well, synchronous commit or synchronous rollback should be enabled via
 *  {@link GridCacheConfiguration#setWriteSynchronizationMode}
 *  or by setting proper flags on cache projection, such as {@link GridCacheFlag#SYNC_COMMIT}.
 *  <p>
 *  Note that in this mode, optimistic failures are only possible in conjunction with
 *  {@link IgniteTxIsolation#SERIALIZABLE} isolation level. In all other cases, optimistic
 *  transactions will never fail optimistically and will always be identically ordered on all participating
 *  grid nodes.
 * </li>
 * <li>
 *  {@link IgniteTxConcurrency#PESSIMISTIC} - in this mode a lock is acquired on all cache operations
 *  with exception of read operations in {@link IgniteTxIsolation#READ_COMMITTED} mode. All optional filters
 *  passed into cache operations will be evaluated after successful lock acquisition. Whenever
 *  {@link #commit()} is called, a single one-way {@code 'COMMIT'} message
 *  is sent to participating cache nodes without waiting for reply. Note that there is no reason for
 *  distributed 'PREPARE' step, as all locks have been already acquired. Just like with optimistic mode,
 *  it is possible to configure synchronous commit or rollback and wait till transaction commits on
 *  all participating remote nodes.
 * </li>
 * </ul>
 * <p>
 * <h1 class="header">Cache Atomicity Mode</h1>
 * In addition to standard {@link GridCacheAtomicityMode#TRANSACTIONAL} behavior, GridGain also supports
 * a lighter {@link GridCacheAtomicityMode#ATOMIC} mode as well. In this mode distributed transactions
 * and distributed locking are not supported. Disabling transactions and locking allows to achieve much higher
 * performance and throughput ratios. It is recommended that {@link GridCacheAtomicityMode#ATOMIC} mode
 * is used whenever full {@code ACID}-compliant transactions are not needed.
 * <p>
 * <h1 class="header">Usage</h1>
 * You can use cache transactions as follows:
 * <pre name="code" class="java">
 * GridCache&lt;String, Integer&gt; cache = GridGain.grid().cache();
 *
 * try (GridCacheTx tx = cache.txStart()) {
 *     // Perform transactional operations.
 *     Integer v1 = cache.get("k1");
 *
 *     // Check if v1 satisfies some condition before doing a put.
 *     if (v1 != null && v1 > 0)
 *         cache.putx("k1", 2);
 *
 *     cache.removex("k2");
 *
 *     // Commit the transaction.
 *     tx.commit();
 * }
 * </pre>
 */
public interface IgniteTx extends GridMetadataAware, AutoCloseable, IgniteAsyncSupport {
    /**
     * Gets unique identifier for this transaction.
     *
     * @return Transaction UID.
     */
    public IgniteUuid xid();

    /**
     * ID of the node on which this transaction started.
     *
     * @return Originating node ID.
     */
    public UUID nodeId();

    /**
     * ID of the thread in which this transaction started.
     *
     * @return Thread ID.
     */
    public long threadId();

    /**
     * Start time of this transaction.
     *
     * @return Start time of this transaction on this node.
     */
    public long startTime();

    /**
     * Cache transaction isolation level.
     *
     * @return Isolation level.
     */
    public IgniteTxIsolation isolation();

    /**
     * Cache transaction concurrency mode.
     *
     * @return Concurrency mode.
     */
    public IgniteTxConcurrency concurrency();

    /**
     * Flag indicating whether transaction was started automatically by the
     * system or not. System will start transactions implicitly whenever
     * any cache {@code put(..)} or {@code remove(..)} operation is invoked
     * outside of transaction.
     *
     * @return {@code True} if transaction was started implicitly.
     */
    public boolean implicit();

    /**
     * Get invalidation flag for this transaction. If set to {@code true}, then
     * remote values will be {@code invalidated} (set to {@code null}) instead
     * of updated.
     * <p>
     * Invalidation messages don't carry new values, so they are a lot lighter
     * than update messages. However, when a value is accessed on a node after
     * it's been invalidated, it must be loaded from persistent store.
     *
     * @return Invalidation flag.
     */
    public boolean isInvalidate();

    /**
     * Gets current transaction state value.
     *
     * @return Current transaction state.
     */
    public IgniteTxState state();

    /**
     * Gets timeout value in milliseconds for this transaction. If transaction times
     * out prior to it's completion, {@link IgniteTxTimeoutException} will be thrown.
     *
     * @return Transaction timeout value.
     */
    public long timeout();

    /**
     * Sets transaction timeout value. This value can be set only before a first operation
     * on transaction has been performed.
     *
     * @param timeout Transaction timeout value.
     * @return Previous timeout.
     */
    public long timeout(long timeout);

    /**
     * Modify the transaction associated with the current thread such that the
     * only possible outcome of the transaction is to roll back the
     * transaction.
     *
     * @return {@code True} if rollback-only flag was set as a result of this operation,
     *      {@code false} if it was already set prior to this call or could not be set
     *      because transaction is already finishing up committing or rolling back.
     */
    public boolean setRollbackOnly();

    /**
     * If transaction was marked as rollback-only.
     *
     * @return {@code True} if transaction can only be rolled back.
     */
    public boolean isRollbackOnly();

    /**
     * Commits this transaction by initiating {@code two-phase-commit} process.
     *
     * @throws IgniteCheckedException If commit failed.
     */
    @IgniteAsyncSupported
    public void commit() throws IgniteCheckedException;

    /**
     * Ends the transaction. Transaction will be rolled back if it has not been committed.
     *
     * @throws IgniteCheckedException If transaction could not be gracefully ended.
     */
    @Override public void close() throws IgniteCheckedException;

    /**
     * Rolls back this transaction.
     *
     * @throws IgniteCheckedException If rollback failed.
     */
    @IgniteAsyncSupported
    public void rollback() throws IgniteCheckedException;
}