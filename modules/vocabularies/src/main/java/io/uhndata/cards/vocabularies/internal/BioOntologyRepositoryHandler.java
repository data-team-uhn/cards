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
import java.util.Collections;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.vocabularies.BioPortalApiKeyManager;
import io.uhndata.cards.vocabularies.spi.RepositoryHandler;
import io.uhndata.cards.vocabularies.spi.VocabularyDescription;
import io.uhndata.cards.vocabularies.spi.VocabularyDescriptionBuilder;

/**
 * Interfaces with the <a href="http://data.bioontology.org/">BioOntology</a> portal. BioOntology is a RESTfull server
 * serving a large collection of vocabularies, along with meta-information.
 *
 * @version $Id$
 */
@Component(
    service = RepositoryHandler.class,
    name = "RepositoryHandler.bioontology")
public class BioOntologyRepositoryHandler implements RepositoryHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BioOntologyRepositoryHandler.class);

    /** Extra query parameters to send to authenticate the request and make the response more compact. */
    private static final String REQUEST_CONFIGURATION =
        "?display_context=false&display_links=false&apikey=";

    /** The list of vocabulary format names that may be available in the supported RDF format,
        but where the default download may be in an unsupported format. Such vocabularies
        should be explicitly requested to be downloaded in RDF format. */
    private static final Set<String> RDF_VOCABULARY_FORMATS =
        Collections.singleton("OWL");

    /** Extra query parameter to request a vocabulary in RDF format (that is supported by our parser).
        See http://data.bioontology.org/documentation for full API documentation. */
    private static final String REQUEST_DOWNLOAD_FORMAT_RDF =
        "&download_format=rdf";

    @Reference
    private BioPortalApiKeyManager apiKeyManager;

    @Override
    public String getRepositoryName()
    {
        return "BioOntology";
    }

    @Override
    public String getRepositoryDescription()
    {
        return "BioPortal: The world’s most comprehensive repository of biomedical ontologies. "
            + "A service provided by the National Center for Biomedical Ontology.";
    }

    @Override
    public VocabularyDescription getVocabularyDescription(String identifier, String version)
        throws IllegalArgumentException, IOException
    {
        String resourceConfiguration = this.getRequestConfiguration();
        final String submissionsURL = "https://data.bioontology.org/ontologies/" + identifier
            + "/submissions" + resourceConfiguration;
        HttpGet httpget = new HttpGet(submissionsURL);
        httpget.setHeader("Accept", "application/json");
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
            CloseableHttpResponse httpresponse = httpclient.execute(httpget)) {

            if (httpresponse.getStatusLine().getStatusCode() < 400) {
                // If the HTTP request is successful, find the requested (or latest) submission,
                // and parse a VocabularyDescription from it

                JsonReader parser = Json.createReader(httpresponse.getEntity().getContent());
                JsonArray submissions = parser.readArray();
                JsonObject submission = submissions.stream()
                    .map(i -> (JsonObject) i)
                    .filter(i -> StringUtils.isBlank(version) ? true : version.equals(i.getString("version")))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Failed to find the requested version [" + version + "] of vocabulary [" + identifier + "]"));

                String ontologyLanguage = submission.getString("hasOntologyLanguage", null);
                Boolean requireRDFDownload = (ontologyLanguage == null)
                    ? false
                    : RDF_VOCABULARY_FORMATS.contains(ontologyLanguage.toUpperCase());

                String sourceConfiguration = resourceConfiguration
                    + (requireRDFDownload ? REQUEST_DOWNLOAD_FORMAT_RDF : "");

                VocabularyDescriptionBuilder desc = new VocabularyDescriptionBuilder();
                desc.withIdentifier(identifier)
                    .withVersion(getVersion(submission))
                    .withName(submission.getJsonObject("ontology").getString("name", null))
                    .withDescription(submission.getString("description", null))
                    .withWebsite(submission.getString("homepage", null))
                    .withCitation(submission.getString("publication", null))
                    .withSource(submission.getString("@id") + "/download" + sourceConfiguration)
                    .withSourceFormat(ontologyLanguage);
                return desc.build();
            } else {
                // If the HTTP request is not successful, throw an exception
                String message = "Failed to access submissions for vocabulary [" + identifier + "]: "
                    + httpresponse.getStatusLine().getStatusCode()
                    + " http error";
                LOGGER.warn(message);
                throw new IllegalArgumentException(message);
            }
        } catch (IOException e) {
            String message = "Unexpected IO error while accessing submissions for vocabulary [" + identifier + "]: "
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

    /**
     * Retrieves a version identifier from the submission. Although each release should have a proper version
     * identifier, sometimes it is missing and must be computed from other sources.
     *
     * @param submission the JSON describing the submission
     * @return a version identifier
     */
    private String getVersion(JsonObject submission)
    {
        // "version" is the official release name of the submission, so it is the preferred version string.
        // However, quite often the version is not defined, so we must fallback to other information.
        String version = submission.getString("version", null);
        // This is the date of the release, so in the absence of an official version name or number, it is a good
        // version identifier
        String released = submission.getString("released", null);
        // Rarely, not even the release date is known, so we have to fall back on the date when the release was added to
        // the BioOntology repository
        String creationDate = submission.getString("creationDate", null);

        if (StringUtils.isNotBlank(version) && !StringUtils.equals("unknown", version)) {
            return version;
        } else if (StringUtils.isNotBlank(released)) {
            return released;
        }
        return creationDate;
    }

    /**
     * Returns extra query parameters completed with the current API key.
     *
     * @return extra query parameters completed with the latest API key
     */
    private String getRequestConfiguration()
    {
        String key = this.apiKeyManager.getAPIKey();
        return REQUEST_CONFIGURATION + key;
    }
}
