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
package io.uhndata.cards.export.spi;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

/**
 * An export job involves 3 main steps: finding data to export, serializing the data, and storing the generate files.
 * Each of these steps is a {@code DataPipelineStep}. This is just a utility interface that makes it easier to pass
 * around any of the three steps and to reuse common code.
 *
 * @version $Id$
 * @since 0.9.26
 */
public interface DataPipelineStep
{
    /** Regular expression used for escaping dots in the resource selectors. */
    String DOT = "\\.";

    /**
     * The {@link DataRetriever} does not retrieve actual data, but finds the resources that need to be exported. A
     * {@code ResourceIdentifier} simply identifies one of these resources, to be passed from the data retriever to the
     * data formatter.
     */
    final class ResourceIdentifier
    {
        private String path;

        private String id;

        private String exportPath;

        public ResourceIdentifier(String path, String identifier, String exportPath)
        {
            this.path = path;
            this.id = identifier;
            this.exportPath = exportPath;
        }

        /**
         * This is the bare JCR path to the resource to be exported.
         *
         * @return a simple JCR path, for example {@code /Subjects/123} or {@code /Questionnaires/OAIP}
         */
        public String getPath()
        {
            return this.path;
        }

        /**
         * This is an identifier for the resource itself, like the name (ID) of a Subject or the Questionnaire name.
         *
         * @return a short name
         */
        public String getIdentifier()
        {
            return this.id;
        }

        /**
         * This is the path that should be used to serialize the resource, a JCR path with extra selectors that specify
         * how it should be serialized.
         *
         * @return a JCR path with optional selectors, for example
         *         {@code /Subjects/123.data.deep.dataFilter:modifiedAfter=2025-01-01.dataFilter:status=SUBMITTED}
         */
        public String getExportPath()
        {
            return this.exportPath;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.path, this.id, this.exportPath);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null || this.getClass() != obj.getClass()) {
                return false;
            }
            ResourceIdentifier other = (ResourceIdentifier) obj;
            return Objects.equals(this.path, other.getPath())
                && Objects.equals(this.id, other.getIdentifier())
                && Objects.equals(this.exportPath, other.getExportPath());
        }

        @Override
        public String toString()
        {
            return String.format("{path:\"%s\",id:\"%s\",exportPath:\"%s\"}", this.path, this.id, this.exportPath);
        }
    }

    /**
     * This holds the data representation and is passed from the data formatter to the data exporter.
     */
    final class ResourceRepresentation
    {
        private final ResourceIdentifier identifier;

        private final InputStream data;

        private final long size;

        private final List<String> dataContents;

        private final String mimeType;

        /**
         * Constructor passing in all the needed data.
         *
         * @param identifier the identifier of the exported data
         * @param data an input stream with the actual resource representation
         * @param size the size of the data, if known, or {@code -1} if the size is unknown
         * @param mimeType the MIME type of the representation
         * @param dataContents a listing of the actual data contained within the representation, if known, for example a
         *            list of Forms within a Questionnaire's CSV export or a Subject's JSON
         */
        public ResourceRepresentation(final ResourceIdentifier identifier,
            final InputStream data,
            final long size,
            final String mimeType,
            final List<String> dataContents)
        {
            this.identifier = identifier;
            this.data = data;
            this.size = size;
            this.mimeType = mimeType;
            this.dataContents = dataContents;
        }

        /**
         * Get the identifier of the resource being exported.
         *
         * @return the identifier of the exported data
         */
        public ResourceIdentifier getIdentifier()
        {
            return this.identifier;
        }

        /**
         * Get the actual resource serialization to be stored. This method should only be called once, since the same
         * input stream is returned, and reading from it permanently consumes the representation.
         *
         * @return an input stream for the data representation, same for all calls of the method
         */
        public InputStream getRepresentation()
        {
            return this.data;
        }

        /**
         * Get the size of the data representation, if known.
         *
         * @return a non-negative number if the size of the data stream (in bytes) is known, a negative number otherwise
         */
        public long getRepresentationSize()
        {
            return this.size;
        }

        /**
         * Get a listing of the actual data contained within the representation, if known, for example a list of Forms
         * within a Questionnaire's CSV export or a Subject's JSON.
         *
         * @return a list of JCR paths to secondary resources contained within the representation
         */
        public List<String> getDataContents()
        {
            return this.dataContents;
        }

        /**
         * Get the MIME type of the resource representation.
         *
         * @return a valid MIME type, for example {@code text/csv} or {@code application/json}
         */
        public String getMimeType()
        {
            return this.mimeType;
        }
    }

    /**
     * Escape special characters in a string so that it can be used as a single selector in a resource path.
     *
     * @param input a string, may be {@code null}
     * @return an escaped string safe to use as a resource selector, {@code null} if the input string is {@code null}
     */
    default String escapeForDataUrl(String input)
    {
        return input == null ? null : input.replaceAll(DOT, Matcher.quoteReplacement(DOT));
    }

    /**
     * Retrieve the value of a named parameter from the configuration. OSGi configurations for a data pipeline element
     * consist of an array of {@code key=value} strings, and this helper method finds the first entry with the right key
     * and returns the value. If there are more than one values for the same key, the first one is returned. If there
     * aren't any values for the given key configured, an empty string is returned.
     *
     * @param parameters the array of configurations to look into
     * @param parameterName the name of the parameter to retrieve
     * @return a string, empty if no configuration for the given name exists
     */
    default String getNamedParameter(final String[] parameters, final String parameterName)
    {
        if (parameters != null && parameters.length > 0) {
            for (String parameter : parameters) {
                if (parameter.startsWith(parameterName + "=")) {
                    return parameter.substring(parameterName.length() + 1);
                }
            }
        }
        return "";
    }

    /**
     * Retrieve the value of a named parameter from the configuration, with fallback to the given default if not
     * configured. OSGi configurations for a data pipeline element consist of an array of {@code key=value} strings, and
     * this helper method finds the first entry with the right key and returns the value. If there are more than one
     * values for the same key, the first one is returned. If there aren't any values for the given key configured, the
     * specified default value is returned.
     *
     * @param parameters the array of configurations to look into
     * @param parameterName the name of the parameter to retrieve
     * @param defaultValue the value to use if nothing is configured for the given parameter name
     * @return a string, empty if no configuration for the given name exists
     */
    default String getNamedParameter(final String[] parameters, final String parameterName, final String defaultValue)
    {
        if (parameters != null && parameters.length > 0) {
            for (String parameter : parameters) {
                if (parameter.startsWith(parameterName + "=")) {
                    return parameter.substring(parameterName.length() + 1);
                }
            }
        }
        return defaultValue;
    }

    /**
     * Retrieve all the values for a named parameter from the configuration. OSGi configurations for a data pipeline
     * element consist of an array of {@code key=value} strings, and this helper method finds the entries with the right
     * key and returns all their values. If there are one or more values for the same key, they are all returned in a
     * list, in the order they are specified. If there aren't any values for the given key configured, an empty list is
     * returned.
     *
     * @param parameters the array of configurations to look into
     * @param parameterName the name of the parameter to retrieve
     * @return a list of strings, empty if no configuration for the given name exists
     */
    default List<String> getNamedParameters(final String[] parameters, final String parameterName)
    {
        final List<String> result = new LinkedList<>();
        if (parameters != null && parameters.length > 0) {
            for (String parameter : parameters) {
                if (parameter.startsWith(parameterName + "=")) {
                    result.add(parameter.substring(parameterName.length() + 1));
                }
            }
        }
        return result;
    }

    /**
     * The name of this data pipeline element.
     *
     * @return a short name
     */
    String getName();
}
