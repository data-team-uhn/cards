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

package ca.sickkids.ccm.lfs.vocabularies.internal;

import java.io.IOException;

import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;

/**
 * A wrapper class for the metadata jsons in the EBI ontology repository. In addition to implementing
 * all of the methods defined in {@link ca.sickkids.ccm.lfs.vocabularies.spi.SourceHandler}, implements
 * extra methods for extracting data that can be found in the EBI jsons.
 *
 * @version $Id$
 */
public class EBISourceHandler extends AbstractSourceHandler
{
    /**
     * Constructor for instantiating the wrapper. Uses the constructor defined in
     * {@link ca.sickkids.ccm.lfs.vocabularies.internal.AbstractSourceHandler}, specifying that
     * the repository is EBI and there are no parameters needed.
     *
     * @param identifier identifier of the desired vocabulary
     * @throws VocabularyIndexException thrown when http request fails
     * @throws IOException thrown when http client fails to close
     */
    public EBISourceHandler(String identifier) throws VocabularyIndexException, IOException
    {
        super(identifier, "https://www.ebi.ac.uk/ols/api/ontologies/", "");
    }

    @Override
    public String getRepositoryName()
    {
        return "EBI";
    }

    @Override
    public String getIdentifier()
    {
        return this.identifier;
    }

    @Override
    public String getName()
    {
        return this.sourceJson.getJsonObject("config").getString("title", null);
    }

    @Override
    public String getSourceLocation()
    {
        return this.sourceJson.getJsonObject("config").getString("fileLocation", null);
    }

    /**
     * Returns the status of the ontology. "FAILED" generally means that the data in the json
     * is not functioning or obsolete.
     *
     * @return String declaring the status of the ontology
     */
    public String getStatus()
    {
        return this.sourceJson.getString("status", "FAILED");
    }

    /**
     * Returns the loaded ontology's version.
     *
     * @return String version of the ontology
     */
    public String getVersion()
    {
        return this.sourceJson.getJsonObject("config").getString("version", null);
    }

    /**
     * Returns the homepage website containing more information about the ontology.
     *
     * @return String url of the website
     */
    public String getWebsite()
    {
        return this.sourceJson.getJsonObject("config").getString("homepage", null);
    }

    /**
     * Returns the number of terms in the ontology.
     *
     * @return number of terms in the ontology.
     */
    public int getNumberOfTerms()
    {
        return this.sourceJson.getInt("numberOfTerms", 0);
    }

    /**
     * Returns the number of properties in the ontology.
     *
     * @return number of properties in the ontology.
     */
    public int getNumberOfProperties()
    {
        return this.sourceJson.getInt("numberOfProperties", 0);
    }
}
