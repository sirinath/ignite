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

package org.apache.ignite.resources;

import java.lang.annotation.*;
import java.util.*;

/**
 * Annotates a field or a setter method for injection of local node {@link UUID} resource. {@code Node UUID}
 * is a globally unique node identifier and is provided to grid via {@link org.apache.ignite.configuration.IgniteConfiguration}.
 * <p>
 * Local node ID can be injected into instances of following classes:
 * <ul>
 * <li>{@link org.apache.ignite.compute.ComputeTask}</li>
 * <li>{@link org.apache.ignite.compute.ComputeJob}</li>
 * <li>{@link org.apache.ignite.spi.IgniteSpi}</li>
 * <li>{@link org.apache.ignite.lifecycle.LifecycleBean}</li>
 * <li>{@link IgniteUserResource @GridUserResource}</li>
 * </ul>
 * <p>
 * Here is how injection would typically happen:
 * <pre name="code" class="java">
 * public class MyGridJob implements ComputeJob {
 *      ...
 *      &#64;GridLocalNodeIdResource
 *      private UUID nodeId;
 *      ...
 *  }
 * </pre>
 * or
 * <pre name="code" class="java">
 * public class MyGridJob implements ComputeJob {
 *     ...
 *     private UUID nodeId;
 *     ...
 *     &#64;GridLocalNodeIdResource
 *     public void setLocalNodeId(UUID nodeId) {
 *          this.nodeId = nodeId;
 *     }
 *     ...
 * }
 * </pre>
 * <p>
 * See {@link org.apache.ignite.configuration.IgniteConfiguration#getNodeId()} for Grid configuration details.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface IgniteLocalNodeIdResource {
    // No-op.
}
