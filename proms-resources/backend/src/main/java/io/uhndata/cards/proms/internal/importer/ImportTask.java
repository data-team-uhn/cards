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

package io.uhndata.cards.proms.internal.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query the Torch server provided for patients with appointments in the coming few days (default: 3), and stores them
 * in JCR local storage. This will overwrite existing patients/appointments with updated information, if it is
 * available, but will not remove any existing patients/appointments.
 *
 * @version $Id$
 */
public class ImportTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportTask.class);

    /** Number of days to query. */
    private final int daysToQuery;

    /** URL for the Vault JWT authentication endpoint. */
    private final String authURL;

    /** Pipe-delimited list of names of clinics to query. */
    private final String[] clinicNames;

    /** List of providers to query. If empty, all providers' appointments are used. */
    private final String[] providerIDs;

    /** URL for the Torch server endpoint. */
    private final String endpointURL;

    /** JWT token for querying the endpoint. */
    private final String vaultToken;

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    /**
     * @param resolverFactory A reference to a ResourceResolverFactory to use
     * @param authURL The URL for the Vault JWT authentication endpoint
     * @param endpointURL The URL for the Torch server endpoint
     * @param daysToQuery Number of days to query
     * @param vaultToken JWT token for querying the endpoint
     * @param clinicNames Pipe-delimited list of names of clinics to query
     * @param providerIDs Pipe-delimited list of names of providers to filter queries to
     */
    ImportTask(final ResourceResolverFactory resolverFactory, final String authURL, final String endpointURL,
        final int daysToQuery, final String vaultToken, final String[] clinicNames, final String[] providerIDs)
    {
        this.resolverFactory = resolverFactory;
        this.authURL = authURL;
        this.endpointURL = endpointURL;
        this.daysToQuery = daysToQuery;
        this.vaultToken = vaultToken;
        this.clinicNames = clinicNames;
        // If we have no provider IDs, we want an empty list instead of a list of length 1 with an empty string
        this.providerIDs = StringUtils.isAllBlank(providerIDs) ? new String[0] : providerIDs;
    }

    @Override
    public void run()
    {
        final String token = "Bearer " + this.vaultToken;
        // Iterate over every clinic name
        for (int i = 0; i < this.clinicNames.length; i++) {
            getUpcomingAppointments(token, this.daysToQuery, this.clinicNames[i]);
        }
    }

    /**
     * Get any upcoming appointments in the next few days.
     *
     * @param authToken an authentication token for use in querying the Torch server
     * @param daysToQuery the number of days to query
     * @param clinicName the clinic for which to retrieve appointments
     */
    private void getUpcomingAppointments(final String authToken, final int daysToQuery, final String clinicName)
    {
        final String postRequestTemplate = "{\"query\": \"query{"
            + "patientsByDateAndClinic(location: \\\"" + clinicName + "\\\", start: \\\"%s\\\", end: \\\"%s\\\") {"
            + "fhirID name {given family} sex mrn ohip dob emailOk com {email} "
            + "appointments {fhirID time status location attending {name {prefix given family suffix} eID}} }}\"}";

        final Calendar startDate = Calendar.getInstance();
        final Date today = new Date();
        startDate.setTime(today);
        final Calendar endDate = (Calendar) startDate.clone();
        endDate.add(Calendar.DATE, daysToQuery);
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        final String postRequest = String.format(postRequestTemplate, formatter.format(startDate.getTime()),
            formatter.format(endDate.getTime()));

        // Query the torch server
        try {
            final String rawResponse = getPostResponse(this.endpointURL, postRequest, authToken);

            // Read the response into a JsonObject
            final JsonReader jsonReader = Json.createReader(new StringReader(rawResponse));
            final JsonObject response = jsonReader.readObject();

            // Create the storage object and store every patient/visit
            final JsonArray data = response.getJsonObject("data").getJsonArray("patientsByDateAndClinic");
            final ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, "TorchImporter"));
            final PatientLocalStorage storage = new PatientLocalStorage(resolver, startDate, endDate,
                this.providerIDs);

            data.forEach(storage::store);
            resolver.close();
        } catch (final Exception e) {
            LOGGER.error("Failed to query server: {}", e.getMessage(), e);
        }
    }

    /***
     * Get the response from a URL after submitting a POST request.
     *
     * @param url The URL to send a POST request to
     * @param data The payload to POST
     * @return The response from the server
     */
    String getPostResponse(final String url, final String data) throws IOException
    {
        return getPostResponse(url, data, "");
    }

    /***
     * Get the response from a URL after submitting a POST request.
     *
     * @param url The URL to send a POST request to
     * @param data The payload to POST
     * @param token an optional token to send as a request property
     * @return The response from the server
     */
    String getPostResponse(final String url, final String data, final String token) throws IOException
    {
        final URLConnection con = new URL(url).openConnection();
        final HttpURLConnection http = (HttpURLConnection) con;

        http.setRequestMethod("POST");
        http.setRequestProperty("Content-Type", "application/json");
        if (!"".equals(token)) {
            http.setRequestProperty("Authorization", token);
        }
        http.setDoOutput(true);

        // Write our POST data to the server
        try (OutputStream os = con.getOutputStream()) {
            final byte[] input = data.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Get the response from the server
        final BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"));
        String responseLine = null;
        final StringBuilder rawResponse = new StringBuilder();
        while ((responseLine = br.readLine()) != null) {
            rawResponse.append(responseLine.trim());
        }
        return rawResponse.toString();
    }
}
