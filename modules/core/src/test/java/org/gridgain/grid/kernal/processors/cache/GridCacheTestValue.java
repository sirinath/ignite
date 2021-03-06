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

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.util.typedef.internal.*;
import java.io.*;

/**
 * Test value.
 */
public class GridCacheTestValue implements Serializable, Cloneable {
    /** */
    @GridCacheQuerySqlField(unique = true)
    private String val;

    /**
     *
     */
    public GridCacheTestValue() {
        /* No-op. */
    }

    /**
     *
     * @param val Value.
     */
    public GridCacheTestValue(String val) {
        this.val = val;
    }

    /**
     * @return Value.
     */
    public String getValue() {
        return val;
    }

    /**
     *
     * @param val Value.
     */
    public void setValue(String val) {
        this.val = val;
    }

    /** {@inheritDoc} */
    @Override protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        return this == o || !(o == null || getClass() != o.getClass())
            && val != null && val.equals(((GridCacheTestValue)o).val);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheTestValue.class, this);
    }
}