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

package org.gridgain.grid.kernal.processors.ggfs;

import org.apache.ignite.fs.*;
import org.apache.ignite.lang.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * File or directory information.
 */
public final class IgniteFsFileImpl implements IgniteFsFile, Externalizable {
    /** */
    private static final long serialVersionUID = 0L;

    /** Path to this file. */
    private IgniteFsPath path;

    /** File id. */
    private IgniteUuid fileId;

    /** Block size. */
    private int blockSize;

    /** Group block size. */
    private long grpBlockSize;

    /** File length. */
    private long len;

    /** Last access time. */
    private long accessTime;

    /** Last modification time. */
    private long modificationTime;

    /** Properties. */
    private Map<String, String> props;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public IgniteFsFileImpl() {
        // No-op.
    }

    /**
     * Constructs directory info.
     *
     * @param path Path.
     */
    public IgniteFsFileImpl(IgniteFsPath path, GridGgfsFileInfo info, long globalGrpBlockSize) {
        A.notNull(path, "path");
        A.notNull(info, "info");

        this.path = path;
        fileId = info.id();

        if (info.isFile()) {
            blockSize = info.blockSize();
            len = info.length();

            grpBlockSize = info.affinityKey() == null ? globalGrpBlockSize :
                info.length() == 0 ? globalGrpBlockSize : info.length();
        }

        props = info.properties();

        if (props == null)
            props = Collections.emptyMap();

        accessTime = info.accessTime();
        modificationTime = info.modificationTime();
    }

    /**
     * Constructs file instance.
     *
     * @param path Path.
     * @param entry Listing entry.
     */
    public IgniteFsFileImpl(IgniteFsPath path, GridGgfsListingEntry entry, long globalGrpSize) {
        A.notNull(path, "path");
        A.notNull(entry, "entry");

        this.path = path;
        fileId = entry.fileId();

        blockSize = entry.blockSize();

        grpBlockSize = entry.affinityKey() == null ? globalGrpSize :
            entry.length() == 0 ? globalGrpSize : entry.length();

        len = entry.length();
        props = entry.properties();

        accessTime = entry.accessTime();
        modificationTime = entry.modificationTime();
    }

    /** {@inheritDoc} */
    @Override public IgniteFsPath path() {
        return path;
    }

    /**
     * @return File ID.
     */
    public IgniteUuid fileId() {
        return fileId;
    }

    /** {@inheritDoc} */
    @Override public boolean isFile() {
        return blockSize > 0;
    }

    /** {@inheritDoc} */
    @Override public boolean isDirectory() {
        return blockSize == 0;
    }

    /** {@inheritDoc} */
    @Override public long length() {
        return len;
    }

    /** {@inheritDoc} */
    @Override public int blockSize() {
        return blockSize;
    }

    /** {@inheritDoc} */
    @Override public long groupBlockSize() {
        return grpBlockSize;
    }

    /** {@inheritDoc} */
    @Override public long accessTime() {
        return accessTime;
    }

    /** {@inheritDoc} */
    @Override public long modificationTime() {
        return modificationTime;
    }

    /** {@inheritDoc} */
    @Override public String property(String name) throws IllegalArgumentException {
        String val = props.get(name);

        if (val ==  null)
            throw new IllegalArgumentException("File property not found [path=" + path + ", name=" + name + ']');

        return val;
    }

    /** {@inheritDoc} */
    @Override public String property(String name, @Nullable String dfltVal) {
        String val = props.get(name);

        return val == null ? dfltVal : val;
    }

    /** {@inheritDoc} */
    @Override public Map<String, String> properties() {
        return props;
    }

    /**
     * Writes object to data output.
     *
     * @param out Data output.
     */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        path.writeExternal(out);

        out.writeInt(blockSize);
        out.writeLong(grpBlockSize);
        out.writeLong(len);
        U.writeStringMap(out, props);
        out.writeLong(accessTime);
        out.writeLong(modificationTime);
    }

    /**
     * Reads object from data input.
     *
     * @param in Data input.
     */
    @Override public void readExternal(ObjectInput in) throws IOException {
        path = new IgniteFsPath();

        path.readExternal(in);

        blockSize = in.readInt();
        grpBlockSize = in.readLong();
        len = in.readLong();
        props = U.readStringMap(in);
        accessTime = in.readLong();
        modificationTime = in.readLong();
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return path.hashCode();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (o == this)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        IgniteFsFileImpl that = (IgniteFsFileImpl)o;

        return path.equals(that.path);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(IgniteFsFileImpl.class, this);
    }
}
