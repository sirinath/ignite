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

package org.gridgain.grid.kernal.processors.cache.jta;

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.transactions.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.jta.*;
import org.gridgain.grid.kernal.processors.cache.transactions.*;
import org.jetbrains.annotations.*;

import javax.transaction.*;

/**
 * Implementation of {@link GridCacheJtaManagerAdapter}.
 */
public class GridCacheJtaManager<K, V> extends GridCacheJtaManagerAdapter<K, V> {
    /** */
    private final ThreadLocal<GridCacheXAResource> xaRsrc = new ThreadLocal<>();

    /** */
    private TransactionManager jtaTm;

    /** */
    private GridCacheTmLookup tmLookup;

    /** {@inheritDoc} */
    @Override public void createTmLookup(GridCacheConfiguration ccfg) throws IgniteCheckedException {
        assert ccfg.getTransactionManagerLookupClassName() != null;

        try {
            Class<?> cls = Class.forName(ccfg.getTransactionManagerLookupClassName());

            tmLookup = (GridCacheTmLookup)cls.newInstance();
        }
        catch (Exception e) {
            throw new IgniteCheckedException("Failed to instantiate transaction manager lookup.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public void checkJta() throws IgniteCheckedException {
        if (jtaTm == null)
            jtaTm = tmLookup.getTm();

        if (jtaTm != null) {
            GridCacheXAResource rsrc = xaRsrc.get();

            if (rsrc == null || rsrc.isFinished()) {
                try {
                    Transaction jtaTx = jtaTm.getTransaction();

                    if (jtaTx != null) {
                        IgniteTx tx = cctx.tm().userTx();

                        if (tx == null) {
                            TransactionsConfiguration tCfg = cctx.kernalContext().config()
                                .getTransactionsConfiguration();

                            tx = cctx.tm().newTx(
                                /*implicit*/false,
                                /*implicit single*/false,
                                /*system*/false,
                                tCfg.getDefaultTxConcurrency(),
                                tCfg.getDefaultTxIsolation(),
                                tCfg.getDefaultTxTimeout(),
                                /*invalidate*/false,
                                /*store enabled*/true,
                                /*tx size*/0,
                                /*group lock keys*/null,
                                /*partition lock*/false
                            );
                        }

                        rsrc = new GridCacheXAResource((IgniteTxEx)tx, cctx);

                        if (!jtaTx.enlistResource(rsrc))
                            throw new IgniteCheckedException("Failed to enlist XA resource to JTA user transaction.");

                        xaRsrc.set(rsrc);
                    }
                }
                catch (SystemException e) {
                    throw new IgniteCheckedException("Failed to obtain JTA transaction.", e);
                }
                catch (RollbackException e) {
                    throw new IgniteCheckedException("Failed to enlist XAResource to JTA transaction.", e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public Object tmLookup() {
        return tmLookup;
    }
}
