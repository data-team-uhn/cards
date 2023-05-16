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

package io.uhndata.cards.torch.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
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

import io.uhndata.cards.metrics.Metrics;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Query the Torch server provided for patients with appointments in the coming few days (default: 3), and stores them
 * in JCR local storage. This will overwrite existing patients/appointments with updated information, if it is
 * available, but will not remove any existing patients/appointments.
 *
 * @version $Id$
 */
@SuppressWarnings({ "checkstyle:ClassDataAbstractionCoupling" })
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

    /** List of allowed provider roles, such that providerIDs must be the given role. Optionally set. */
    private final String[] providerRoles;

    /** URL for the Torch server endpoint. */
    private final String endpointURL;

    /** List of dates to filter appointments by. May be empty if no date filtering is applied. */
    private final List<Calendar> queryDates;

    /** JWT token for querying the endpoint. */
    private final String vaultToken;

    /** Vault role name for use when logging in with the JWT. */
    private final String vaultRole;

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    private final ThreadResourceResolverProvider rrp;

    /**
     * @param resolverFactory A reference to a ResourceResolverFactory to use
     * @param authURL The URL for the Vault JWT authentication endpoint
     * @param endpointURL The URL for the Torch server endpoint
     * @param daysToQuery Number of days to query
     * @param vaultToken JWT token for querying the endpoint
     * @param clinicNames list of names of clinics to query
     * @param providerIDs list of names of providers to filter queries to
     * @param queryDates list of dates to restrict queries to, if any
     */
    @SuppressWarnings({ "checkstyle:ParameterNumber" })
    ImportTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp,
        final String authURL, final String endpointURL,
        final int daysToQuery, final String vaultToken, final String[] clinicNames, final String[] providerIDs,
        final String[] providerRoles, final String vaultRole, final String[] queryDates)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.authURL = authURL;
        this.endpointURL = endpointURL;
        this.daysToQuery = daysToQuery;
        this.vaultToken = vaultToken;
        this.clinicNames = clinicNames;
        // Parse the query dates
        this.queryDates = new LinkedList<>();
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        for (String queryDate : queryDates) {
            try {
                final Calendar date = Calendar.getInstance();
                date.setTime(formatter.parse(queryDate));
                this.queryDates.add(date);
            } catch (ParseException e) {
                LOGGER.error("Query date invalid: {}", e.getMessage(), e);
            }
        }
        // If we have no provider IDs/roles, we want an empty list instead of a list of length 1 with an empty string
        this.providerIDs = StringUtils.isAllBlank(providerIDs) ? new String[0] : providerIDs;
        this.providerRoles = StringUtils.isAllBlank(providerRoles) ? new String[0] : providerRoles;
        this.vaultRole = vaultRole;
    }

    @Override
    public void run()
    {
        final String token = loginWithJWT();
        long importedAppointmentsCount = 0;
        // Iterate over every clinic name
        for (String clinicName : this.clinicNames) {
            importedAppointmentsCount += getUpcomingAppointments(token, this.daysToQuery, clinicName);
        }
        // Update the performance counter
        Metrics.increment(this.resolverFactory,
            "ImportedAppointments", importedAppointmentsCount);
    }

    /**
     * Obtain an authentication token for use in querying the Torch server. Currently, this sends a JWT Login request to
     * Vault, which authorizes our JWT token to query the server. This should be called before performing
     * {@code getUpcomingAppointments}.
     *
     * @return an authentication token to be passed onto as a header, typically begins with "Bearer " or an empty string
     *         if no token could be obtained.
     */
    private String loginWithJWT()
    {
        String token = "Bearer " + this.vaultToken;

        // If we have no role to login to, we can skip the rest of the Vault auth process
        if (StringUtils.isBlank(this.vaultRole)) {
            return token;
        }

        final String postRequest = "{ \"role\": \"" + this.vaultRole + "\", \"jwt\":\"" + this.vaultToken + "\" }";

        try {
            getPostResponse(this.authURL, postRequest);
        } catch (final Exception e) {
            LOGGER.error("Failed to activate authentication token: {}", e.getMessage(), e);
        }

        return token;
    }

    /**
     * Get any upcoming appointments in the next few days.
     *
     * @param authToken an authentication token for use in querying the Torch server
     * @param daysToQuery the number of days to query
     * @param clinicName the clinic for which to retrieve appointments
     */
    private long getUpcomingAppointments(final String authToken, final int daysToQuery, final String clinicName)
    {
        final String postRequestTemplate = "{\"query\": \"query{"
            + "patientsByDateAndClinic(location: \\\"" + clinicName
            + "\\\", start: \\\"%s\\\", end: \\\"%s\\\", session: \\\"cards-nightlyImport-%s\\\") {"
            + "fhirID name {given family} sex mrn ohip dob emailOk com {email{home work temp mobile}} "
            + "appointments {fhirID time status location "
            + "participants {physician {name {prefix given family suffix} eID} role}} }}\"}";

        long importedAppointmentsCount = 0;
        final Calendar startDate = Calendar.getInstance();
        final Date today = new Date();
        startDate.setTime(today);
        final Calendar endDate = (Calendar) startDate.clone();
        endDate.add(Calendar.DATE, daysToQuery);
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        final String postRequest = String.format(postRequestTemplate, formatter.format(startDate.getTime()),
            formatter.format(endDate.getTime()), formatter.format(new Date()));

        // Query the torch server
        try {
            final String rawResponse = getPostResponse(this.endpointURL, postRequest, authToken);

            // Read the response into a JsonObject
            final JsonReader jsonReader = Json.createReader(new StringReader(rawResponse));
            final JsonObject response = jsonReader.readObject();

            // Create the storage object and store every patient/visit
            final JsonArray data = response.getJsonObject("data").getJsonArray("patientsByDateAndClinic");
            boolean mustPopResolver = false;
            try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(
                Map.of(ResourceResolverFactory.SUBSERVICE, "TorchImporter"))) {
                this.rrp.push(resolver);
                mustPopResolver = true;
                final PatientLocalStorage storage = new PatientLocalStorage(resolver, startDate, endDate,
                    this.providerIDs, this.providerRoles, this.queryDates);

                data.forEach(storage::store);
                importedAppointmentsCount += storage.getCountAppointmentsCreated();
            } finally {
                if (mustPopResolver) {
                    this.rrp.pop();
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to query server: {}", e.getMessage(), e);
        }
        return importedAppointmentsCount;
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
     * Place all of the data from a given input stream into a string.
     *
     * @param stream The stream to read data from
     * @return The string output from the given input stream
     */
    private static String readInputStream(InputStream stream) throws IOException
    {
        final BufferedReader br = new BufferedReader(new InputStreamReader(stream, "utf-8"));
        String responseLine = null;
        final StringBuilder retVal = new StringBuilder();
        while ((responseLine = br.readLine()) != null) {
            retVal.append(responseLine.trim());
        }
        return retVal.toString();
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
        } catch (IOException e) {
            // If we can obtain a better error message from the POST result, log it
            InputStream errorStream = http.getErrorStream();
            if (errorStream != null) {
                throw new IOException("Error during POST: " + ImportTask.readInputStream(errorStream));
            } else {
                throw e;
            }
        }

        return ImportTask.readInputStream(con.getInputStream());
    }
}
