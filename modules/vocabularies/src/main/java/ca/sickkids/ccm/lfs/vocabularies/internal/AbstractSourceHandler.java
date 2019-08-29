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

import ca.sickkids.ccm.lfs.vocabularies.spi.SourceHandler;
import ca.sickkids.ccm.lfs.vocabularies.spi.VocabularyIndexException;

/**
 * Abstract implementation of {@link ca.sickkids.ccm.lfs.vocabularies.spi.SourceHandler}. This class
 * simply defines an inheritable constructor for subclasses to use when instantiating the JsonObject
 * representing the json holding metadata about the ontology.
 *
 * @version $Id$
 */
public abstract class AbstractSourceHandler implements SourceHandler
{
    /**
     * JsonObject representing the json carrying metadata about the ontology. This is protected
     * as it must be accessible by methods implemented in subclasses.
     */
    protected JsonObject sourceJson;

    /**
     * String representing the identifier code. This must be accessible to subclasses for it to
     * be returned in methods.
     */
    protected String identifier;

    /**
     * Constructor which instantiates the sourceJson instance variable by sending a http request
     * for the corresponding metdata json in the ontology repository specified in the repository
     * parameter. The identifier instance variable is also instantiated.
     * @param identifier identifier of the desired ontology
     * @param repository the url to the ontology repository being used
     * @param parameters parameters needed for the http request sent to get the json
     * @throws VocabularyIndexException thrown on failure of http request to get metadata json
     * @throws IOException thrown on failure of http client to close
     */
    public AbstractSourceHandler(String identifier, String repository, String parameters)
        throws VocabularyIndexException, IOException
    {
        // Identifier of the ontology
        this.identifier = identifier;

        // url for http request to access the metadata json
        String jsonSource = repository + identifier + parameters;

        // Instantiate http GET request
        HttpGet httpget = new HttpGet(jsonSource);
        httpget.setHeader("Content-Type", "application/json");
        CloseableHttpClient client = HttpClientBuilder.create().build();

        CloseableHttpResponse httpresponse;
        try {
            // Execute GET request
            httpresponse = client.execute(httpget);

            if (httpresponse.getStatusLine().getStatusCode() < 400) {
                // If request successful

                // Set the sourceJson JsonObject using the data from the http request
                JsonReader reader = Json.createReader(httpresponse.getEntity().getContent());
                this.sourceJson = reader.readObject();
            } else {
                // If request unsuccessful

                // Close the response and throw exception
                httpresponse.close();
                throw new VocabularyIndexException("Could not create source handler: "
                + httpresponse.getStatusLine().getStatusCode() + " Error.");
            }
        } catch (ClientProtocolException e) {
            throw new VocabularyIndexException("Could not create source handler: " + e.getMessage());
        } catch (IOException e) {
            throw new VocabularyIndexException("Could not create source handler: " + e.getMessage());
        } finally {
            // Close http client
            client.close();
        }
    }
}
