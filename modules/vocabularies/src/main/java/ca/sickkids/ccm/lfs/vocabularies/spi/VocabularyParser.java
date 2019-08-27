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
package ca.sickkids.ccm.lfs.vocabularies.spi;

import java.io.IOException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;

/**
 * Interface which defines methods required from classes that parse vocabularies.
 *
 * @version $Id$
 */
@SuppressWarnings("checkstyle:RedundantModifier")
public interface VocabularyParser
{
    /**
     * Checks whether the given source is parsable by this vocabulary. This only checks the format of the source value,
     * for a theoretical ability to parse the data. It does not check the actual data indicated by the source, so even
     * if this method returns {@code true}, the actual data may be corrupted, or in a newer or older incompatible format
     * not supported. If this method returns {@code true} but the data cannot be actually parsed, then
     * {@link #parse(String, SlingHttpServletRequest, SlingHttpServletResponse)} will throw a
     * {@link VocabularyIndexException}, which will cause
     * {@link ca.sickkids.ccm.lfs.vocabularies.VocabularyIndexerServlet} to try the next available parser.
     *
     * @param source the source parameter passed in the request, usually a URL or an identifier
     * @return {@code true} if the source is known to be parsable by this vocabulary parser
     */
    boolean canParse(String source);

    /**
     * Main method for handling vocabulary parsing.
     *
     * @param source the source to parse from, the value of the mandatory {@code source} request parameter
     * @param request HTTP request the original request that trigger the parse, may contain additional parameters that
     *            customize the parsing process
     * @param response HTTP response where the status should be written to
     * @throws IOException when writing json response fails
     * @throws VocabularyIndexException when parsing the vocabulary or storing the parsed data fail
     */
    void parse(final String source, final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException, VocabularyIndexException;
}
