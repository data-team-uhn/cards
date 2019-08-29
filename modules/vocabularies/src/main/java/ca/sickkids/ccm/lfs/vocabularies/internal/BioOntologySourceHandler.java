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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;

/**
 * A wrapper class for the metadata jsons contained in the Bioontology ontology repository. In addition to implementing
 * all of the methods defined in {@link ca.sickkids.ccm.lfs.vocabularies.spi.SourceHandler}, it implements
 * methods for extracting data that can be found in Bioontology jsons.
 *
 * @version $Id$
 */
public class BioOntologySourceHandler extends AbstractSourceHandler
{
    protected JsonObject latestSubmission;
    protected boolean successfulLatestSubmission;
    protected boolean loadedLatestSubmission;

    /**
     * Constructor for instantiating the wrapper. Uses the constructor defined in
     * {@link ca.sickkids.ccm.lfs.vocabularies.internal.AbstractSourceHandler}, specifying that
     * the repository is Bioontology and there are no parameters needed.
     * @param identifier the identifier of the ontology to use
     * @throws VocabularyIndexException thrown on failure of http request to get metadata json
     * @throws IOException thrown on failure of http client to close
     */
    public BioOntologySourceHandler(String identifier) throws VocabularyIndexException, IOException
    {
        super(identifier, "https://data.bioontology.org/ontologies/", "?apikey=8ac0298d-99f4-4793-8c70-fb7d3400f279");
        this.successfulLatestSubmission = false;
        this.loadedLatestSubmission = false;
    }

    /**
     * Implementation of {@link ca.sickkids.ccm.lfs.vocabularies.spi.SourceHandler.getRepositoryName}
     * @return String name of the ontology repository used ("Biolontology")
     */
    public String getRepositoryName()
    {
        return "Bioontology";
    }

    /**
     * Implementation of {@link ca.sickkids.ccm.lfs.vocabularies.spi.SourceHandler.getIdentifier}
     * @return String identifier code of the ontology in the repository
     */
    public String getIdentifier()
    {
        return this.identifier;
    }

    /**
     * Implementation of {@link ca.sickkids.ccm.lfs.vocabularies.spi.SourceHandler.getName}.
     * @return String long-form name of the ontology
     */
    public String getName()
    {
        return this.sourceJson.getString("name", null);
    }

    /**
     * Implementation of {@link ca.sickkids.ccm.lfs.vocabularies.spi.SourceHandler.getSourceLocation}.
     * @return String url location of the ontology file to get
     */
    public String getSourceLocation()
    {
        return this.sourceJson.getJsonObject("links").getString("download", null);
    }

    /**
     * Load the latest bioontology submission.
     */
    public void loadLatestSubmission()
    {
        // Build the HTTP request
        String latestSubmissionURL = "https://data.bioontology.org/ontologies/" + identifier + "/latestSubmission"
            + "?apikey=8ac0298d-99f4-4793-8c70-fb7d3400f279";
        HttpGet httpget = new HttpGet(latestSubmissionURL);
        httpget.setHeader("Content-Type", "application/json");
        CloseableHttpClient client = HttpClientBuilder.create().build();

        CloseableHttpResponse httpresponse;
        try {
            httpresponse = client.execute(httpget);

            if (httpresponse.getStatusLine().getStatusCode() < 400) {
                // Request successful, set the latestSubmission JsonObject
                JsonReader reader = Json.createReader(httpresponse.getEntity().getContent());
                this.latestSubmission = reader.readObject();

                this.successfulLatestSubmission = true;
            } else {
                // Close the response and log result
                httpresponse.close();
                this.successfulLatestSubmission = false;
            }
        } catch (ClientProtocolException e) {
            this.successfulLatestSubmission = false;
        } catch (IOException e) {
            this.successfulLatestSubmission = false;
        } finally {
            // Close http client
            try {
                client.close();
            } catch (IOException e) {
                this.successfulLatestSubmission = false;
            }
        }
    }

    /**
     * Returns the homepage of the currently loaded bioontology submission.
     * Loads the latest bioontology submission, if not already loaded.
     * If the bioontology ontology failed to load, returns null.
     * @return The homepage of the currently loaded bioontology submission, or null if it failed to load
     */
    public String getWebsite()
    {
        if (!this.loadedLatestSubmission) {
            loadLatestSubmission();
        }

        if (this.successfulLatestSubmission) {
            return this.latestSubmission.getString("homepage", null);
        } else {
            return null;
        }

    }

    /**
     * Returns the version of the currently loaded bioontology submission.
     * Loads the latest bioontology submission, if not already loaded.
     * If the bioontology ontology failed to load, returns null.
     * If the version is missing, it returns the creation date, or release date if that is also missing.
     * @return The version of the currently loaded bioontology submission, or null if it failed to load
     */
    public String getVersion()
    {
        if (!this.loadedLatestSubmission) {
            loadLatestSubmission();
        }

        if (this.successfulLatestSubmission) {
            String creationDate = this.latestSubmission.getString("creationDate", null);
            String released = this.latestSubmission.getString("released", null);
            String version = this.latestSubmission.getString("version", null);

            if (version != null) {
                return version;
            } else if (creationDate != null) {
                return creationDate;
            } else {
                return released;
            }
        } else {
            return null;
        }
    }
}
