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

package org.apache.ignite.portables;

import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Wrapper for portable object in portable binary format. Once an object is defined as portable,
 * GridGain will always store it in memory in the portable (i.e. binary) format.
 * User can choose to work either with the portable format or with the deserialized form
 * (assuming that class definitions are present in the classpath).
 * <p>
 * <b>NOTE:</b> user does not need to (and should not) implement this interface directly.
 * <p>
 * To work with the portable format directly, user should create a cache projection
 * over {@code GridPortableObject} class and then retrieve individual fields as needed:
 * <pre name=code class=java>
 * GridCacheProjection&lt;GridPortableObject.class, GridPortableObject.class&gt; prj =
 *     cache.projection(GridPortableObject.class, GridPortableObject.class);
 *
 * // Convert instance of MyKey to portable format.
 * // We could also use GridPortableBuilder to create
 * // the key in portable format directly.
 * GridPortableObject key = grid.portables().toPortable(new MyKey());
 *
 * GridPortableObject val = prj.get(key);
 *
 * String field = val.field("myFieldName");
 * </pre>
 * Alternatively, we could also choose a hybrid approach, where, for example,
 * the keys are concrete deserialized objects and the values are returned in portable
 * format, like so:
 * <pre name=code class=java>
 * GridCacheProjection&lt;MyKey.class, GridPortableObject.class&gt; prj =
 *     cache.projection(MyKey.class, GridPortableObject.class);
 *
 * GridPortableObject val = prj.get(new MyKey());
 *
 * String field = val.field("myFieldName");
 * </pre>
 * We could also have the values as concrete deserialized objects and the keys in portable format,
 * but such use case is a lot less common because cache keys are usually a lot smaller than values, and
 * it may be very cheap to deserialize the keys, but not the values.
 * <p>
 * And finally, if we have class definitions in the classpath, we may choose to work with deserialized
 * typed objects at all times. In this case we do incur the deserialization cost, however,
 * GridGain will only deserialize on the first access and will cache the deserialized object,
 * so it does not have to be deserialized again:
 * <pre name=code class=java>
 * GridCacheProjection&lt;MyKey.class, MyValue.class&gt; prj =
 *     cache.projection(MyKey.class, MyValue.class);
 *
 * MyValue val = prj.get(new MyKey());
 *
 * // Normal java getter.
 * String fieldVal = val.getMyFieldName();
 * </pre>
 * <h1 class="header">Working With Maps and Collections</h1>
 * All maps and collections in the portable objects are serialized automatically. When working
 * with different platforms, e.g. C++ or .NET, GridGain will automatically pick the most
 * adequate collection or map in either language. For example, {@link ArrayList} in Java will become
 * {@code List} in C#, {@link LinkedList} in Java is {@link LinkedList} in C#, {@link HashMap}
 * in Java is {@code Dictionary} in C#, and {@link TreeMap} in Java becomes {@code SortedDictionary}
 * in C#, etc.
 * <h1 class="header">Dynamic Structure Changes</h1>
 * Since objects are always cached in the portable binary format, server does not need to
 * be aware of the class definitions. Moreover, if class definitions are not present or not
 * used on the server, then clients can continuously change the structure of the portable
 * objects without having to restart the cluster. For example, if one client stores a
 * certain class with fields A and B, and another client stores the same class with
 * fields B and C, then the server-side portable object will have the fields A, B, and C.
 * As the structure of a portable object changes, the new fields become available for SQL queries
 * automatically.
 * <h1 class="header">Building Portable Objects</h1>
 * GridGain comes with {@link PortableBuilder} which allows to build portable objects dynamically:
 * <pre name=code class=java>
 * GridPortableBuilder builder = GridGain.grid().portables().builder("org.project.MyObject");
 *
 * builder.setField("fieldA", "A");
 * builder.setField("fieldB", "B");
 *
 * GridPortableObject portableObj = builder.build();
 * </pre>
 * For the cases when class definition is present
 * in the class path, it is also possible to populate a standard POJO and then
 * convert it to portable format, like so:
 * <pre name=code class=java>
 * MyObject obj = new MyObject();
 *
 * obj.setFieldA("A");
 * obj.setFieldB(123);
 *
 * GridPortableObject portableObj = GridGain.grid().portables().toPortable(obj);
 * </pre>
 * <h1 class="header">Portable Metadata</h1>
 * Even though GridGain portable protocol only works with hash codes for type and field names
 * to achieve better performance, GridGain provides metadata for all portable types which
 * can be queried ar runtime via any of the {@link org.apache.ignite.IgnitePortables#metadata(Class) GridPortables.metadata(...)}
 * methods. Having metadata also allows for proper formatting of {@code GridPortableObject.toString()} method,
 * even when portable objects are kept in binary format only, which may be necessary for audit reasons.
 */
public interface PortableObject extends Serializable, Cloneable {
    /**
     * Gets portable object type ID.
     *
     * @return Type ID.
     */
    public int typeId();

    /**
     * Gets meta data for this portable object.
     *
     * @return Meta data.
     * @throws PortableException In case of error.
     */
    @Nullable public PortableMetadata metaData() throws PortableException;

    /**
     * Gets field value.
     *
     * @param fieldName Field name.
     * @return Field value.
     * @throws PortableException In case of any other error.
     */
    @Nullable public <F> F field(String fieldName) throws PortableException;

    /**
     * Gets fully deserialized instance of portable object.
     *
     * @return Fully deserialized instance of portable object.
     * @throws PortableInvalidClassException If class doesn't exist.
     * @throws PortableException In case of any other error.
     */
    @Nullable public <T> T deserialize() throws PortableException;

    /**
     * Creates a copy of this portable object and optionally changes field values
     * if they are provided in map. If map is empty or {@code null}, clean copy
     * is created.
     *
     * @param fields Fields to modify in copy.
     * @return Copy of this portable object.
     * @throws PortableException In case of error.
     * @deprecated Use {@link PortableBuilder} instead.
     * @see PortableBuilder
     */
    @Deprecated
    public PortableObject copy(@Nullable Map<String, Object> fields) throws PortableException;

    /**
     * Copies this portable object.
     *
     * @return Copy of this portable object.
     */
    public PortableObject clone() throws CloneNotSupportedException;
}
