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

package org.gridgain.grid.util.offheap.unsafe;

/**
 * Compound memory object. Contains one or more memory regions.
 */
public interface GridUnsafeCompoundMemory {
    /**
     * Deallocates this compound memory object.
     */
    public void deallocate();

    /**
     * Merges another compound memory object with this one.
     *
     * @param compound Compound memory.
     */
    public void merge(GridUnsafeCompoundMemory compound);
}
