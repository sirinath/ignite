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

/**
 * Annotates a field or a setter method for injection of {@link org.apache.ignite.IgniteLogger}. Grid logger is provided to grid
 * via {@link org.apache.ignite.configuration.IgniteConfiguration}.
 * <p>
 * Logger can be injected into instances of following classes:
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
 *      &#64;GridLoggerResource
 *      private GridLogger log;
 *      ...
 *  }
 * </pre>
 * or
 * <pre name="code" class="java">
 * public class MyGridJob implements ComputeJob {
 *     ...
 *     private GridLogger log;
 *     ...
 *     &#64;GridLoggerResource
 *     public void setGridLogger(GridLogger log) {
 *          this.log = log;
 *     }
 *     ...
 * }
 * </pre>
 * <p>
 * See {@link org.apache.ignite.configuration.IgniteConfiguration#getGridLogger()} for Grid configuration details.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface IgniteLoggerResource {
    /**
     * Optional log category class. If not provided (i.e. by default
     * {@link Void} class is returned), then the category will
     * be the class into which resource is assigned.
     * <p>
     * Either {@code categoryClass} or {@link #categoryName()} can be provided,
     * by not both.
     *
     * @return Category class of the injected logger.
     */
    public Class categoryClass() default Void.class;

    /**
     * Optional log category name. If not provided, then {@link #categoryClass()}
     * value will be used.
     * <p>
     * Either {@code categoryName} or {@link #categoryClass()} can be provided,
     * by not both.
     *
     * @return Category name for the injected logger.
     */
    public String categoryName() default "";
}
