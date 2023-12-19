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

import java.util.Map;

import io.uhndata.cards.clarity.importer.ClarityImportTask;

/**
 * Service interface for processing the data received from Clarity. Implementations of this interface will be invoked by
 * {@link ClarityImportTask} when importing a row from Clarity into a collection of subjects and forms.
 * <p>
 * The processors will be called one after another, in ascending order of their {@link #getPriority() priority},
 * receiving the serialized value computed by the previous processor, starting with the raw results from the SQL query.
 * If any of the processors return {@code null}, then the SQL row is ignored and no data will be created for it.
 * </p>
 *
 * @version $Id$
 */
public interface ClarityDataProcessor extends Comparable<ClarityDataProcessor>
{
    /**
     * Called at the start of a new import job to allow a processor to initialize any needed state.
     */
    default void start()
    {
        // Nothing to do by default
    }

    /**
     * Process a row before importing it, by changing values, adding new columns, removing columns, or skipping the
     * entire row completely.
     *
     * @param input the row of data that is to be imported
     * @return the same row, a modified row, or {@code null} to cause this row to be ignored
     */
    Map<String, String> processEntry(Map<String, String> input);

    /**
     * Called at the end of an import job to allow a processor to cleanup any temporary state.
     */
    default void end()
    {
        // Nothing to do by default
    }

    /**
     * The priority of this processor. Processors with higher numbers are invoked after those with lower numbers,
     * receiving their output as an input. Priority {@code 0} is considered the base priority.
     *
     * @return the priority of this processor, can be any number
     */
    int getPriority();

    /**
     * A processor can either be active for all imports, or just a subset of them. Each import has a specific type, and
     * each processor can support specific types. This method check if an import type is supported. By default,
     * processors are active for all imports, override this method to restrict it.
     *
     * @param type a short label identifying the import process
     * @return {@code true} if this processor should be used for the specified import type, {@code false} otherwise
     */
    default boolean supportsImportType(String type)
    {
        return true;
    }

    @Override
    default int compareTo(ClarityDataProcessor other)
    {
        return this.getPriority() != other.getPriority()
            ? this.getPriority() - other.getPriority()
            : this.getClass().getName().compareTo(other.getClass().getName());
    }
}
