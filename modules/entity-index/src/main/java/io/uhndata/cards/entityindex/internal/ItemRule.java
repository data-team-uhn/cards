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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One rule of the indexing schema: which descendant node type to index, which of its properties give the item its
 * identity and its values. Parsed from a compact configuration string of the form
 * {@code nodeType;key=referenceProperty;values=prop1,prop2;note=noteProperty}, for example the default rule for
 * Forms, {@code cards:Answer;key=question;values=value;note=note}, or a hypothetical rule for entities with plain
 * named children and several interesting properties, {@code iap:Reviewer;values=assignee,status,decision}.
 * <p>
 * The {@code key} is the property referencing the node that names the index field; when omitted, the item's own path
 * relative to the entity gives the field name. The first {@code values} property is the item's primary value,
 * indexed directly under the field name; every listed property {@code p} is also indexed under
 * {@code <field>@<p>}.
 * </p>
 *
 * @version $Id$
 * @since 0.9.41
 */
final class ItemRule
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemRule.class);

    private final String nodeType;

    private final String keyProperty;

    private final List<String> valueProperties;

    private final String noteProperty;

    private ItemRule(final String nodeType, final String keyProperty, final List<String> valueProperties,
        final String noteProperty)
    {
        this.nodeType = nodeType;
        this.keyProperty = keyProperty;
        this.valueProperties = valueProperties;
        this.noteProperty = noteProperty;
    }

    /**
     * Parse a list of configured rule definitions, skipping invalid ones.
     *
     * @param definitions rule definitions in the {@code nodeType;key=k;values=v1,v2;note=n} format
     * @return the parsed rules, may be empty
     */
    static List<ItemRule> parseAll(final String[] definitions)
    {
        final List<ItemRule> result = new ArrayList<>();
        for (final String definition : definitions == null ? new String[0] : definitions) {
            final ItemRule rule = parse(definition);
            if (rule != null) {
                result.add(rule);
            }
        }
        return result;
    }

    private static ItemRule parse(final String definition)
    {
        if (StringUtils.isBlank(definition)) {
            return null;
        }
        final String[] parts = definition.split(";");
        final String type = parts[0].trim();
        if (type.isEmpty()) {
            LOGGER.warn("Ignoring item rule without a node type: [{}]", definition);
            return null;
        }
        String key = null;
        List<String> values = List.of("value");
        String note = null;
        for (final String part : Arrays.asList(parts).subList(1, parts.length)) {
            final String[] setting = part.split("=", 2);
            final String value = setting.length > 1 ? setting[1].trim() : "";
            switch (setting[0].trim()) {
                case "key" -> key = StringUtils.defaultIfBlank(value, null);
                case "values" -> values = Arrays.stream(value.split(","))
                    .map(String::trim).filter(StringUtils::isNotBlank).toList();
                case "note" -> note = StringUtils.defaultIfBlank(value, null);
                case null, default ->
                    LOGGER.warn("Ignoring unknown item rule setting [{}] in [{}]", part, definition);
            }
        }
        if (values.isEmpty()) {
            LOGGER.warn("Ignoring item rule without value properties: [{}]", definition);
            return null;
        }
        return new ItemRule(type, key, values, note);
    }

    String getNodeType()
    {
        return this.nodeType;
    }

    /**
     * The property referencing the node that names the index field.
     *
     * @return a property name, or {@code null} when the item's own path names the field
     */
    String getKeyProperty()
    {
        return this.keyProperty;
    }

    List<String> getValueProperties()
    {
        return this.valueProperties;
    }

    /**
     * The primary value property, indexed directly under the field name.
     *
     * @return a property name
     */
    String getPrimaryValueProperty()
    {
        return this.valueProperties.get(0);
    }

    String getNoteProperty()
    {
        return this.noteProperty;
    }

    /**
     * A canonical form of this rule, used to detect schema changes requiring a reindex.
     *
     * @return a stable string representation
     */
    String canonical()
    {
        return this.nodeType + ";key=" + StringUtils.defaultString(this.keyProperty)
            + ";values=" + String.join(",", this.valueProperties)
            + ";note=" + StringUtils.defaultString(this.noteProperty);
    }
}
