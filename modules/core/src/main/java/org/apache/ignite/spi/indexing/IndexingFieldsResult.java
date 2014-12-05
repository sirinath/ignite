/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.apache.ignite.spi.indexing;

import org.apache.ignite.spi.*;

import java.util.*;

/**
 * Field query result. It is composed of
 * fields metadata and iterator over queried fields.
 * See also {@link IndexingSpi#queryFields(String, String, Collection, IndexingQueryFilter)}.
 */
public interface IndexingFieldsResult {
    /**
     * Gets metadata for queried fields.
     *
     * @return Meta data for queried fields.
     */
    List<IndexingFieldMetadata> metaData();

    /**
     * Gets iterator over queried fields.
     *
     * @return Iterator over queried fields.
     */
    IgniteSpiCloseableIterator<List<IndexingEntity<?>>> iterator();
}