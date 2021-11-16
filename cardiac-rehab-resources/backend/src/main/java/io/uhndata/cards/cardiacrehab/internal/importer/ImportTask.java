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

package io.uhndata.cards.cardiacrehab.internal.importer;

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
import java.util.HashMap;

import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportTask.class);

    private static final String URL = "https://localhost:8080/endpoint";

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    ImportTask(final ResourceResolverFactory resolverFactory)
    {
        this.resolverFactory = resolverFactory;
    }

    @Override
    public void run()
    {
        getUpcomingAppointments();
    }

    private void getUpcomingAppointments()
    {

        // Since we can't query more than one date at a time, query three dates
        String postRequestTemplate = "{\"query\": \"query{"
            + "patientsByDateAndClinic(clinic: \"PMH 8 Palliative Care Oncology Clinic\", date: \"%s\") {"
            + "name {given family}\nsex\nmrn\nohip\ndob\nemailOk\ncom {email {home work}}\n"
            + "nextAppointment {id time status} }}\"}";
        Calendar dateToQuery = Calendar.getInstance();
        Date today = new Date();
        dateToQuery.setTime(today);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < 3; i++)
        {
            String postRequest = String.format(postRequestTemplate, formatter.format(dateToQuery));
            // Query the torch server
            try {
                URL url = new URL(ImportTask.URL);
                URLConnection con = url.openConnection();
                HttpURLConnection http = (HttpURLConnection) con;

                http.setRequestMethod("POST");
                http.setRequestProperty("Content-Type", "application/json");
                http.setRequestProperty("Authorization", System.getenv("PROM_AUTH_TOKEN"));
                http.setDoOutput(true);

                // Get the next date
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = postRequest.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Get the response from the server
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"));
                String responseLine = null;
                StringBuilder rawResponse = new StringBuilder();
                while ((responseLine = br.readLine()) != null) {
                    rawResponse.append(responseLine.trim());
                }

                // Read the response into a JsonObject
                JsonReader jsonReader = Json.createReader(new StringReader(rawResponse.toString()));
                JsonObject response = jsonReader.readObject();

                JsonArray data = response.getJsonObject("data").getJsonArray("patientsByDateAndClinic");
                for (int j = 0; j < data.size(); j++) {
                    StorePatient(data.getJsonObject(j));
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to query server: {}", e.getMessage(), e);
            }
            dateToQuery.add(Calendar.DATE, 1);
        }
    }

    /**
     * Store information about a patient from a JsonObject
     * @param patient
     */
    void StorePatient(JsonObject patient) throws LoginException, IOException, RepositoryException {
        ResourceResolver resolver = this.resolverFactory.getResourceResolver(null);
        Resource parentResource = resolver.getResource("/Patients/");
        Resource patientFormType = resolver.getResource("/Questionnaires/Patient information/");
        // Determine aspects of the patient that we want
        resolver.create(parentResource, patient.getString("mrn"), new HashMap<String, Object>() {
            
        });
    }

    /**
     * Store information about an appointment from a JsonObject
     * @param appointment
     */
    void StoreAppointment(JsonObject appointment) {

    }
}
