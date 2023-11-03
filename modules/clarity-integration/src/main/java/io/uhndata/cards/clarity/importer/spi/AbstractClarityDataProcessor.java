/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.uhndata.cards.clarity.importer.spi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Base class for implementing Clarity import processors. Handles the {@link #getPriority()} and
 * {@link #supportsImportType(String)} methods.
 *
 * @version $Id$
 */
public abstract class AbstractClarityDataProcessor implements ClarityDataProcessor
{
    private final boolean enabled;

    private final int priority;

    private final List<String> supportedTypes;

    /**
     * Base constructor.
     *
     * @param enabled whether this processor is enabled or not; if this is {@code false}, then
     *            {@link #supportsImportType} will return {@code false} as well, regardless of the passed import type
     * @param types the list of supported import types; if this is empty or {@code null}, then the processor is
     *            considered to support all import types
     * @param priority the priority of this processor, used as the return value of {@link #getPriority()}
     */
    protected AbstractClarityDataProcessor(final boolean enabled, final String[] types, final int priority)
    {
        this.enabled = enabled;
        this.supportedTypes = types == null ? Collections.emptyList() : Arrays.asList(types);
        this.priority = priority;
    }

    @Override
    public int getPriority()
    {
        return this.priority;
    }

    @Override
    public boolean supportsImportType(String type)
    {
        return this.enabled && (this.supportedTypes.contains(type) || this.supportedTypes.isEmpty());
    }
}
