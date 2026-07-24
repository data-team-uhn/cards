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
package io.uhndata.cards.entityindex;

import java.io.IOException;

/**
 * A denormalized, entity-level search index. Unlike the standard Oak indexes, where each JCR node is a separate index
 * document and multi-property queries require expensive JOINs, this index stores a whole <em>entity</em> — a root
 * resource, e.g. a {@code cards:Form}, together with all its descendant items, e.g. its {@code cards:Answer}s — as a
 * single flattened document. Any number of constraints on different answers of the same form can therefore be
 * evaluated in a single index lookup, in milliseconds, no matter how many answers are involved.
 * <p>
 * The index is maintained near-real-time: changes to entities are picked up by an observation listener and are
 * usually searchable within a second of being saved.
 * </p>
 *
 * @version $Id$
 * @since 0.9.41
 */
public interface EntityIndexer
{
    /**
     * Add or update the index document for the entity at the given path. If the path is not an indexable entity, or
     * it cannot be read, nothing happens.
     *
     * @param entityPath the absolute JCR path of an entity root, e.g. {@code /Forms/<uuid>}
     */
    void index(String entityPath);

    /**
     * Remove the index document for the entity at the given path, if it is indexed.
     *
     * @param entityPath the absolute JCR path of an entity root, e.g. {@code /Forms/<uuid>}
     */
    void delete(String entityPath);

    /**
     * Rebuild the whole index from scratch by walking all the entities under the configured root. This is a
     * potentially long operation executed synchronously; callers may want to invoke it from a background thread.
     */
    void reindexAll();

    /**
     * The number of entities currently present in the index.
     *
     * @return a number of indexed entities, {@code -1} if the index is not available
     */
    long getIndexedEntityCount();

    /**
     * Run a search against the index.
     *
     * @param query the search criteria
     * @return the matching entity paths, in the requested order
     * @throws IOException if accessing the index fails
     * @throws IllegalArgumentException if the query cannot be parsed
     */
    SearchResults search(SearchQuery query) throws IOException;
}
