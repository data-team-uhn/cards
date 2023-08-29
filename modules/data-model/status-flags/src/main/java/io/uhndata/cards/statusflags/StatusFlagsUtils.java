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
package io.uhndata.cards.statusflags;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
public class StatusFlagsUtils
{
    public static final String PROPERTY_NAME = "statusFlags";

    private static final Logger LOGGER = LoggerFactory.getLogger(StatusFlagsUtils.class);

    private static final String[] STRING_ARRAY = new String[0];

    public Set<String> getFlags(final Node node)
    {
        try {
            if (node != null && node.hasProperty(PROPERTY_NAME) && node.getProperty(PROPERTY_NAME).isMultiple()) {
                Set<String> result = new LinkedHashSet<>();
                for (Value v : node.getProperty(PROPERTY_NAME).getValues()) {
                    result.add(v.getString());
                }
                return Collections.unmodifiableSet(result);
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to read status flags for {}: {}", node, e.getMessage(), e);
        }
        return Collections.emptySet();
    }

    public boolean hasFlag(final Node node, final String flag)
    {
        return getFlags(node).contains(flag);
    }

    public boolean addFlag(final Node node, final String flag)
    {
        Set<String> newFlags = new LinkedHashSet<>(getFlags(node));
        boolean result = newFlags.add(flag);
        if (result) {
            try {
                node.setProperty(PROPERTY_NAME, newFlags.toArray(STRING_ARRAY));
            } catch (RepositoryException e) {
                LOGGER.error("Failed to add status flag {} on {}: {}", flag, node, e.getMessage(), e);
                return false;
            }
        }
        return result;
    }

    public boolean addFlags(final Node node, final String... flags)
    {
        Set<String> newFlags = new LinkedHashSet<>(getFlags(node));
        boolean result = false;
        for (String flag : flags) {
            result |= newFlags.add(flag);
        }
        if (result) {
            try {
                node.setProperty(PROPERTY_NAME, newFlags.toArray(STRING_ARRAY));
            } catch (RepositoryException e) {
                LOGGER.error("Failed to add status flags on {}: {}", node, e.getMessage(), e);
                return false;
            }
        }
        return result;
    }

    public boolean removeFlag(final Node node, final String flag)
    {
        Set<String> newFlags = new LinkedHashSet<>(getFlags(node));
        boolean result = newFlags.remove(flag);
        if (result) {
            try {
                node.setProperty(PROPERTY_NAME, newFlags.toArray(STRING_ARRAY));
            } catch (RepositoryException e) {
                LOGGER.error("Failed to remove status flag {} from {}: {}", flag, node, e.getMessage(), e);
                return false;
            }
        }
        return result;
    }
}
