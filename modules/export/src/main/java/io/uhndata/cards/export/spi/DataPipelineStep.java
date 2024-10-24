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

public interface DataPipelineStep
{
    /** Regular expression used for escaping dots in the resource selectors. */
    String DOT = "\\.";

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

        public String getPath()
        {
            return this.path;
        }

        public String getIdentifier()
        {
            return this.id;
        }

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

    final class ResourceRepresentation
    {
        private final ResourceIdentifier identifier;

        private final InputStream data;

        private final List<String> dataContents;

        private final String mimeType;

        public ResourceRepresentation(final ResourceIdentifier identifier,
            final InputStream data,
            final String mimeType,
            final List<String> dataContents)
        {
            this.identifier = identifier;
            this.data = data;
            this.mimeType = mimeType;
            this.dataContents = dataContents;
        }

        public ResourceIdentifier getIdentifier()
        {
            return this.identifier;
        }

        public InputStream getRepresentation()
        {
            return this.data;
        }

        public List<String> getDataContents()
        {
            return this.dataContents;
        }

        public String getMimeType()
        {
            return this.mimeType;
        }
    }

    default String escapeForDataUrl(String input)
    {
        return input == null ? null : input.replaceAll(DOT, Matcher.quoteReplacement(DOT));
    }

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

    String getName();
}
