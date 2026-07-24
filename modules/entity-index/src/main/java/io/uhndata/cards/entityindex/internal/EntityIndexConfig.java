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
package io.uhndata.cards.entityindex.internal;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The indexing schema of the {@link EntityIndexManager entity index}: which entities to index, how to find the
 * indexable items inside an entity, and how to name the resulting index fields. The defaults index Forms with their
 * Answers, keyed by the answered Question.
 *
 * @version $Id$
 * @since 0.9.41
 */
@ObjectClassDefinition(name = "Entity index",
    description = "A denormalized index storing each entity (e.g. a whole Form) as a single flattened document")
public @interface EntityIndexConfig
{
    /**
     * Whether the index is maintained at all.
     *
     * @return {@code true} if the index is enabled
     */
    @AttributeDefinition(name = "Enabled", description = "Whether the index is maintained at all")
    boolean enabled() default true;

    /**
     * Filesystem location of the index.
     *
     * @return an absolute filesystem path, or empty to use a directory inside the Sling home
     */
    @AttributeDefinition(name = "Index location",
        description = "Filesystem directory holding the index; by default 'entity-index' inside the Sling home")
    String index_path() default "";

    /**
     * The JCR path under which the indexed entities reside.
     *
     * @return an absolute JCR path
     */
    @AttributeDefinition(name = "Entity root", description = "The JCR path holding the entities to index")
    String entity_root() default "/Forms";

    /**
     * The node type of the entity roots.
     *
     * @return a JCR node type name
     */
    @AttributeDefinition(name = "Entity node type", description = "The node type of the entities to index")
    String entity_type() default "cards:Form";

    /**
     * The rules describing which descendant items to index and how, one rule per indexable node type, in the compact
     * format documented in {@link ItemRule}.
     *
     * @return rule definitions
     */
    @AttributeDefinition(name = "Item rules",
        description = "One rule per indexable descendant node type, in the format"
            + " 'nodeType;key=referenceProperty;values=prop1,prop2;note=noteProperty'; key may be omitted to name"
            + " fields after the item's own path inside the entity; values defaults to 'value'")
    String[] item_rules() default { "cards:Answer;key=question;values=value;note=note" };

    /**
     * The node types of intermediate containers to recurse into when looking for items.
     *
     * @return JCR node type names
     */
    @AttributeDefinition(name = "Container node types",
        description = "The node types of intermediate containers between the entity and its items")
    String[] container_types() default { "cards:AnswerSection" };

    /**
     * The prefix stripped from key node paths to form the human-friendly field name alias.
     *
     * @return a JCR path prefix, including the trailing slash
     */
    @AttributeDefinition(name = "Key alias prefix",
        description = "The path prefix stripped from key nodes to form human-friendly field names")
    String key_alias_prefix() default "/Questionnaires/";

    /**
     * How often the index searcher is refreshed to make recent changes visible.
     *
     * @return a number of seconds
     */
    @AttributeDefinition(name = "Refresh interval",
        description = "How often recent changes become searchable, in seconds")
    int refresh_seconds() default 1;

    /**
     * How often pending changes are durably committed to storage.
     *
     * @return a number of seconds
     */
    @AttributeDefinition(name = "Commit interval",
        description = "How often pending changes are durably persisted, in seconds")
    int commit_seconds() default 30;
}
