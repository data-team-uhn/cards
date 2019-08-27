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

import javax.jcr.Node;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.osgi.service.log.LogService;

/**
 * Interface which defines methods required from classes that parse vocabularies.
 * @version $Id$
 */
public interface VocabularyParser
{
    /**
     * General method for handling vocabulary parsing.
     * @param request - http request from the VocabularyIndexerServlet
     * @param response - http response from the VocabularyIndexerServlet
     * @param logger - logger from the VocabularyIndexerServlet to log exceptions caught
     * @throws IOException thrown when response Json cannot be written
    */
    void parseVocabulary(SlingHttpServletRequest request, SlingHttpServletResponse response, LogService logger)
        throws IOException;

    /**
     * Remove any previous instances of the vocabulary which is to be parsed and indexed in the JCR repository.
     * @param homepage - VocabulariesHomepage node instance serving as root for Vocabulary nodes
     * @param name - identifier of the vocabulary which will become its node name
     * @throws VocabularyIndexException thrown when the node cannot be removed
     */
    void clearVocabularyNode(Node homepage, String name)
        throws VocabularyIndexException;

    /**
     * Writes a json to the http response consisting of two entries, "isSuccessful" which indicates if the
     * parsing attempt was successful or not, and "error" which is the error message in case of an error.
     * @param request - http request for the VocabularyIndexerServlet
     * @param response - http response for the VocabularyIndexerServlet
     * @param isSuccessful - boolean variable which when true indicates success and when false indicates failure
     * @param error - error message to display if error occurs
     * @throws IOException - thrown when the json cannot be written
     */
    void writeStatusJson(SlingHttpServletRequest request, SlingHttpServletResponse response,
        boolean isSuccessful, String error)
        throws IOException;
}
