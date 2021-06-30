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

package io.uhndata.cards.vocabularies.internal;

import java.io.File;
import java.io.IOException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.vocabularies.spi.RepositoryHandler;
import io.uhndata.cards.vocabularies.spi.VocabularyDescription;
import io.uhndata.cards.vocabularies.spi.VocabularyDescriptionBuilder;

/**
 * Interfaces with the <a href="https://www.ebi.ac.uk/ols/">EMBL-EBI Ontology Lookup Service</a>.
 *
 * @version $Id$
 */
@Component(
    service = RepositoryHandler.class,
    name = "RepositoryHandler.ebi")
public class EbiRepositoryHandler implements RepositoryHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EbiRepositoryHandler.class);

    @Override
    public String getRepositoryName()
    {
        return "EMBL-EBI Ontology Lookup Service";
    }

    @Override
    public String getRepositoryDescription()
    {
        return "A repository for biomedical ontologies "
            + "that aims to provide a single point of access to the latest ontology versions.";
    }

    @Override
    public VocabularyDescription getVocabularyDescription(String identifier, String version)
        throws IllegalArgumentException, IOException
    {
        final String ontologyURL = "https://www.ebi.ac.uk/ols/api/ontologies/" + identifier;
        HttpGet httpget = new HttpGet(ontologyURL);
        httpget.setHeader("Accept", "application/json");
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
            CloseableHttpResponse httpresponse = httpclient.execute(httpget)) {

            if (httpresponse.getStatusLine().getStatusCode() < 400) {
                // If the HTTP request is successful, parse a VocabularyDescription from the response

                JsonReader parser = Json.createReader(httpresponse.getEntity().getContent());
                JsonObject ontologyJson = parser.readObject();
                JsonObject configJson = ontologyJson.getJsonObject("config");

                VocabularyDescriptionBuilder desc = new VocabularyDescriptionBuilder();
                desc.withIdentifier(identifier)
                    // The only case when the version is null is when the source isn't valid anyway
                    .withVersion(configJson.getString("version", null))
                    .withName(configJson.getString("title", null))
                    .withDescription(configJson.getString("description", null))
                    .withWebsite(configJson.getString("homepage", null))
                    .withSource(configJson.getString("fileLocation", null))
                    .withSourceFormat(OntologyFormatDetection.getSourceFormat(
                        configJson.getString("fileLocation", null)));
                return desc.build();
            } else {
                // If the HTTP request is not successful, throw an exception
                String message = "Failed to access vocabulary [" + identifier + "]: "
                    + httpresponse.getStatusLine().getStatusCode()
                    + " http error";
                LOGGER.warn(message);
                throw new IllegalArgumentException(message);
            }
        } catch (IOException e) {
            String message = "Unexpected IO error while accessing the description of vocabulary [" + identifier + "]: "
                + e.getMessage();
            LOGGER.warn(message, e);
            throw new IOException(message, e);
        }
    }

    @Override
    public File downloadVocabularySource(VocabularyDescription vocabulary) throws IllegalArgumentException, IOException
    {
        // Throw an exception if the vocabulary description isn't valid
        if (vocabulary == null || StringUtils.isAnyBlank(vocabulary.getIdentifier(), vocabulary.getSource())) {
            throw new IllegalArgumentException("Invalid vocabulary description, no source to download");
        }

        String identifier = vocabulary.getIdentifier();

        // Create a temporary file where the source will be stored
        final File temporaryFile = File.createTempFile(this.getRepositoryName() + "-" + identifier, "");

        // Download the source
        HttpGet httpget = new HttpGet(vocabulary.getSource());

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build();
            CloseableHttpResponse httpResponse = httpClient.execute(httpget)) {

            if (httpResponse.getStatusLine().getStatusCode() < 400) {
                // If the HTTP request is successful, write all of the contents of the response to the temporary file
                FileUtils.copyInputStreamToFile(httpResponse.getEntity().getContent(), temporaryFile);
            } else {
                // If the HTTP request is not successful, throw an exception
                String message = "Failed to download the source for vocabulary [" + identifier + "] from ["
                    + this.getRepositoryName() + "]: " + httpResponse.getStatusLine().getStatusCode()
                    + " http error";
                LOGGER.warn(message);
                throw new IllegalArgumentException(message);
            }
            return temporaryFile;
        } catch (IOException e) {
            String message = "Unexpected IO error while accessing vocabulary [" + identifier + "]: "
                + e.getMessage();
            LOGGER.warn(message, e);
            throw new IOException(message, e);
        }
    }
}
