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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public class ImportTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportTask.class);

    private static final String URL = "https://prom.dev.uhn.io/graphql";

    private static final String JWT_URL = "https://vault.dev.uhn.io/v1/auth/jwt/login";

    private static final String PRIMARY_TYPE = "jcr:primaryType";
    private static final String JCR_SQL = "JCR-SQL2";
    private static final String QUESTION_FIELD = "question";
    private static final String VALUE_FIELD = "value";
    private static final int DAYS_TO_QUERY = 3;

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    ImportTask(final ResourceResolverFactory resolverFactory)
    {
        this.resolverFactory = resolverFactory;
    }

    @Override
    public void run()
    {
        String token = getAuthToken();
        getUpcomingAppointments(token, DAYS_TO_QUERY);
    }

    /**
     * Obtain an authentication token for use in querying the Torch server.
     * @return an authentication token to be passed onto as a header, typically begins with "Bearer " or an empty
     *         string if no token could be obtained.
     */
    private String getAuthToken()
    {
        String token = "";
        String postRequest = "{ \"role\": \"prom_role\", \"jwt\":\"" + System.getenv("PROM_AUTH_TOKEN") + "\" }";

        try {
            String rawResponse = getPostResponse(ImportTask.JWT_URL, postRequest);
            JsonReader jsonReader = Json.createReader(new StringReader(rawResponse.toString()));
            token = "Bearer " + System.getenv("PROM_AUTH_TOKEN");
        } catch (Exception e) {
            LOGGER.error("Failed to retrieve authentication token: {}", e.getMessage(), e);
        }

        return token;
    }

    /**
     * Get any upcoming appointments in the next few days.
     * @param authToken an authentication token for use in querying the Torch server
     */
    private void getUpcomingAppointments(String authToken, int daysToQuery)
    {
        // Since we can't query more than one date at a time, query three dates
        String postRequestTemplate = "{\"query\": \"query{"
            + "patientsByDateAndClinic(clinic: \\\"PMH 8 Palliative Care Oncology Clinic\\\", date: \\\"%s\\\") {"
            + "name {given family} sex mrn ohip dob emailOk com {email} "
            + "nextAppointment {fhirID time status attending {name {family}}} }}\"}";
        Calendar dateToQuery = Calendar.getInstance();
        Date today = new Date();
        dateToQuery.setTime(today);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < daysToQuery; i++)
        {
            String postRequest = String.format(postRequestTemplate, formatter.format(dateToQuery.getTime()));
            // Query the torch server
            try {
                String rawResponse = getPostResponse(ImportTask.URL, postRequest, authToken);

                // Read the response into a JsonObject
                JsonReader jsonReader = Json.createReader(new StringReader(rawResponse));
                JsonObject response = jsonReader.readObject();

                JsonArray data = response.getJsonObject("data").getJsonArray("patientsByDateAndClinic");
                for (int j = 0; j < data.size(); j++) {
                    storePatient(data.getJsonObject(j));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to query server: {}", e.getMessage(), e);
            }
            dateToQuery.add(Calendar.DATE, 1);
        }
    }

    /***
     * Get the response from a URL after submitting a POST request.
     * @param url The URL to send a POST request to
     * @param data The payload to POST
     * @return The response from the server
     */
    String getPostResponse(String url, String data) throws IOException
    {
        return getPostResponse(url, data, "");
    }

    /***
     * Get the response from a URL after submitting a POST request.
     * @param url The URL to send a POST request to
     * @param data The payload to POST
     * @param token an optional token to send as a request property
     * @return The response from the server
     */
    String getPostResponse(String url, String data, String token) throws IOException
    {
        URLConnection con = new URL(url).openConnection();
        HttpURLConnection http = (HttpURLConnection) con;

        http.setRequestMethod("POST");
        http.setRequestProperty("Content-Type", "application/json");
        if (!"".equals(token)) {
            http.setRequestProperty("Authorization", token);
        }
        http.setDoOutput(true);

        // Get the next date
        try (OutputStream os = con.getOutputStream()) {
            byte[] input = data.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Get the response from the server
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"));
        String responseLine = null;
        StringBuilder rawResponse = new StringBuilder();
        while ((responseLine = br.readLine()) != null) {
            rawResponse.append(responseLine.trim());
        }
        return rawResponse.toString();
    }

    /**
     * Store information about a patient from a JsonObject.
     * @param patient The patient object to add
     */
    void storePatient(JsonObject patientDetails)
    {
        String mrn = patientDetails.getString("mrn");
        try {
            ResourceResolver resolver = this.resolverFactory.getResourceResolver(null);

            // Update information about the patient
            Resource patient = getOrCreateSubject(mrn, "/SubjectTypes/Patient", null, resolver);
            Resource patientInfo = getOrCreateForm(patient, "/Questionnaires/Patient information/", resolver);
            updatePatientInformationForm(patientInfo, patientDetails, resolver);

            // Update information about the visit ("appointment" is used interchangably here)
            JsonObject appointmentDetails = patientDetails.getJsonObject("nextAppointment");
            Resource visit = getOrCreateSubject(appointmentDetails.getString("fhirID"),
                "/SubjectTypes/Patient/Visit/", patient, resolver);
            Resource visitInfo = getOrCreateForm(patient, "/Questionnaires/Visit information/", resolver);
            updateVisitInformationForm(visitInfo, appointmentDetails, resolver);

            final Session session = resolver.adaptTo(Session.class);
            session.save();
        } catch (LoginException e) {
            LOGGER.error("Could not save patient {}: {}", mrn, e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.error("Could not save patient {}: {}", mrn, e.getMessage(), e);
        } catch (RepositoryException e) {
            LOGGER.error("Could not save patient {}: {}", mrn, e.getMessage(), e);
        }
    }

    /**
     * Grab a subject of the specified type, or create it if it doesn't exist.
     * @param identifier Identifier to use for the subject
     * @param parentTypePath path to a SubjectType node for this subject
     * @param parent parent Resource if this is a child of that resource, or null
     * @param resolver ResourceResolver to use when searching for/creating the node
     * @return A Subject resource
     */
    Resource getOrCreateSubject(String identifier, String parentTypePath, Resource parent, ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        Iterator<Resource> patientResource = resolver.findResources(String.format(
            "SELECT * FROM [cards:Subject] WHERE identifier = \"%s\"", identifier), ImportTask.JCR_SQL);
        if (patientResource.hasNext()) {
            return patientResource.next();
        } else {
            Resource parentResource = parent;
            if (parentResource == null) {
                parentResource = resolver.getResource("/Subjects/");
            }
            Resource patientType = resolver.getResource(parentTypePath);
            Resource newSubject = resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                ImportTask.PRIMARY_TYPE, "cards:Subject",
                "identifier", identifier,
                "fullidentifier", identifier,
                "type", patientType.adaptTo(Node.class)
            ));
            return newSubject;
        }
    }

    /**
     * Grab a form of the specified type, or create it if it doesn't exist.
     * @param patient Resource of the subject node for the form
     * @param resolver ResourceResolver to use when searching for/creating the node
     * @return A Form resource
     */
    Resource getOrCreateForm(Resource subject, String questionnairePath, ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        Resource formType = resolver.getResource(questionnairePath);
        Node subjectNode = subject.adaptTo(Node.class);
        Iterator<Resource> formResource = resolver.findResources(String.format(
            "SELECT * FROM [cards:Form] WHERE subject = \"%s\" AND questionnaire=\"%s\"",
            subjectNode.getProperty("jcr:uuid").getString(),
            formType.adaptTo(Node.class).getProperty("jcr:uuid").getString()), ImportTask.JCR_SQL);
        if (formResource.hasNext()) {
            return formResource.next();
        } else {
            Resource parentResource = resolver.getResource("/Forms/");
            Resource newSubject = resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                ImportTask.PRIMARY_TYPE, "cards:Form",
                "questionnaire", formType.adaptTo(Node.class),
                "relatedSubjects", subjectNode,
                "subject", subjectNode
            ));
            return newSubject;
        }
    }

    interface JsonStringGetter
    {
        String get(JsonObject in);
    }

    /**
     * Get a string from a JSONObject using the given JsonStringGetter function, returning an empty
     * string if the value does not exist.
     * @param valueGetter The JsonStringGetter to use when obtaining a value from a JsonObject
     * @param info The JsonObject to apply valueGetter to.
     * @return The string, or an empty string if it does not exist
     */
    String safelyGetValue(JsonStringGetter valueGetter, JsonObject info)
    {
        try {
            return valueGetter.get(info);
        } catch (ClassCastException e) {
            // Class cast exceptions represent a value of "null", which could not be obtained
            return "";
        }
    }

    void updateForm(Resource form, JsonObject info, String parentQuestionnaire,
        Map<String, JsonStringGetter> mapping, Map<String, String> dateFields, ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        // Run through the children of the node, seeing what exists
        Set<String> seenNodes = new HashSet<String>();
        Map<String, Node> dateNodes = new HashMap<String, Node>();
        for (Resource existingAnswer : form.getChildren()) {
            Node answerNode = existingAnswer.adaptTo(Node.class);
            Node questionNode = answerNode.getProperty(ImportTask.QUESTION_FIELD).getNode();

            // Make note of the date node, since we handle it differently
            if (dateFields.containsKey(questionNode.getName())) {
                dateNodes.put(questionNode.getName(), answerNode);
            }

            // Do nothing if we don't know how to update this question
            if (!mapping.containsKey(questionNode.getName())) {
                continue;
            }

            seenNodes.add(questionNode.getName());
            answerNode.setProperty(ImportTask.VALUE_FIELD,
                safelyGetValue(mapping.get(questionNode.getName()), info));
        }

        // For each node that does not exist, create a new answer
        String formPath = form.getPath();
        for (Map.Entry<String, JsonStringGetter> entry : mapping.entrySet()) {
            if (seenNodes.contains(entry.getKey())) {
                continue;
            }

            resolver.create(form, UUID.randomUUID().toString(), Map.of(
                ImportTask.QUESTION_FIELD, resolver.getResource(parentQuestionnaire + "/"
                    + entry.getKey()).adaptTo(Node.class),
                ImportTask.VALUE_FIELD, safelyGetValue(entry.getValue(), info),
                ImportTask.PRIMARY_TYPE, "cards:TextAnswer"
                ));
        }

        // Finally, do the same with date fields, which have different parameters
        for (Map.Entry<String, String> entry : dateFields.entrySet()) {
            try {
                Calendar entryDate = Calendar.getInstance();
                entryDate.setTime(new SimpleDateFormat("yyyy-MM-dd").parse(info.getString(entry.getValue())));
                if (dateNodes.containsKey(entry.getKey())) {
                    dateNodes.get(entry.getKey()).setProperty(ImportTask.VALUE_FIELD, entryDate);
                } else {
                    resolver.create(form, UUID.randomUUID().toString(), Map.of(
                        ImportTask.QUESTION_FIELD,
                        resolver.getResource(parentQuestionnaire + "/" + entry.getKey()).adaptTo(Node.class),
                        ImportTask.VALUE_FIELD, entryDate,
                        ImportTask.PRIMARY_TYPE, "cards:DateAnswer"
                        ));
                }
            } catch (ParseException e) {
                //TODO: handle exception
                LOGGER.error("Error occurred while parsing {} for {}, {}", entry, info.toString(), e);
            }
        }
    }

    /**
     * Update the patient information form with values from the given JsonObject.
     * @param info The JsonObject representing a patient returned from Torch
     * @param form The Patient Information form to update
     * @param resolver The ResourceResolver to use when creating new nodes
     */
    void updatePatientInformationForm(Resource form, JsonObject info, ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        // Map of Patient information question node name => Function to get JSON value
        Map<String, JsonStringGetter> formMapping = Map.of(
            "mrn", obj -> obj.getString("mrn"),
            "health_card", obj -> obj.getString("ohip"),
            "sex", obj -> obj.getString("sex"),
            "first_name", obj -> obj.getJsonObject("name").getJsonArray("given").getString(0),
            "last_name", obj -> obj.getJsonObject("name").getString("family"),
            "email", obj -> obj.getJsonObject("com").getString("email")
        );

        Map<String, String> dateMapping = Map.of(
            "date_of_birth", "dob"
        );

        updateForm(form, info, "/Questionnaires/Patient information", formMapping, dateMapping, resolver);
    }

    /**
     * Update the visit information form with values from the given JsonObject.
     * @param info The JsonObject representing a visit returned from Torch
     * @param form The Visit Information form to update
     * @param resolver The ResourceResolver to use when creating new nodes
     */
    void updateVisitInformationForm(Resource form, JsonObject info, ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        Map<String, JsonStringGetter> formMapping = Map.of(
            "fhir_id", obj -> obj.getString("fhirID"),
            "status", obj -> obj.getString("status"),
            "provider", obj -> obj.getJsonObject("attending").getJsonObject("name").getString("family")
        );

        Map<String, String> dateMapping = Map.of(
            "time", "time"
        );

        updateForm(form, info, "/Questionnaires/Visit information", formMapping, dateMapping, resolver);
    }
}
