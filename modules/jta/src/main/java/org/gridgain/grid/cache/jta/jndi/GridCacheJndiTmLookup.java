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

package org.gridgain.grid.cache.jta.jndi;

import org.apache.ignite.*;
import org.gridgain.grid.cache.jta.*;
import org.jetbrains.annotations.*;

import javax.naming.*;
import javax.transaction.*;
import java.util.*;

/**
 * Implementation of {@link GridCacheTmLookup} interface that is using list of JNDI names to find TM.
 */
public class GridCacheJndiTmLookup implements GridCacheTmLookup {
    /** */
    private List<String> jndiNames;

    /**
     * Gets a list of JNDI names.
     * 
     * @return List of JNDI names that is used to find TM.
     */
    public List<String> getJndiNames() {
        return jndiNames;
    }

    /**
     * Sets a list of JNDI names used by this TM.
     * 
     * @param jndiNames List of JNDI names that is used to find TM.
     */
    public void setJndiNames(List<String> jndiNames) {
        this.jndiNames = jndiNames;
    }

    /** {@inheritDoc} */
    @Nullable @Override public TransactionManager getTm() throws IgniteCheckedException {
        assert jndiNames != null;
        assert !jndiNames.isEmpty();

        try {
            InitialContext ctx = new InitialContext();

            for (String s : jndiNames) {
                Object obj = ctx.lookup(s);

                if (obj != null && obj instanceof TransactionManager)
                    return (TransactionManager) obj;
            }
        }
        catch (NamingException e) {
            throw new IgniteCheckedException("Unable to lookup TM by: " + jndiNames, e);
        }

        return null;
    }
}