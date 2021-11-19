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

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    ImportTask(final ResourceResolverFactory resolverFactory)
    {
        this.resolverFactory = resolverFactory;
    }

    @Override
    public void run()
    {
        LOGGER.warn("Running import task");
        String token = getAuthToken();
        getUpcomingAppointments(token);
    }

    private String getAuthToken()
    {
        String token = "";
        String postRequest = "{ \"role\": \"prom_role\", \"jwt\":\"" + System.getenv("PROM_AUTH_TOKEN") + "\" }";

        try {
            String rawResponse = getPostResponse(ImportTask.JWT_URL, postRequest);
            JsonReader jsonReader = Json.createReader(new StringReader(rawResponse.toString()));
            token = "Bearer " + System.getenv("PROM_AUTH_TOKEN");
        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve token: {}", e.getMessage(), e);
        }

        return token;
    }

    @SuppressWarnings("checkstyle:ExecutableStatementCount")
    private void getUpcomingAppointments(String authToken)
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
        for (int i = 0; i < 3; i++)
        {
            String postRequest = String.format(postRequestTemplate, formatter.format(dateToQuery.getTime()));
            // Query the torch server
            try {
                String rawResponse = getPostResponse(ImportTask.URL, postRequest, authToken);

                // Read the response into a JsonObject
                LOGGER.warn(rawResponse);

                JsonReader jsonReader = Json.createReader(new StringReader(rawResponse));
                JsonObject response = jsonReader.readObject();

                JsonArray data = response.getJsonObject("data").getJsonArray("patientsByDateAndClinic");
                for (int j = 0; j < data.size(); j++) {
                    storePatient(data.getJsonObject(j));
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to query server: {}", e.getMessage(), e);
            }
            dateToQuery.add(Calendar.DATE, 1);
        }
    }


    /***
     * Get the response from a URL after submitting a POST request.
     * @param url The URL to send a POST request to
     * @param data The payload to POST
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
    void storePatient(JsonObject patientDetails) throws LoginException, IOException, RepositoryException
    {
        ResourceResolver resolver = this.resolverFactory.getResourceResolver(null);
        String mrn = patientDetails.getString("mrn");

        // Determine if this patient exists already
        Resource patient = getOrCreatePatientSubject(mrn, resolver);
        Resource patientInfo = getOrCreatePatientInformationForm(patientDetails, patient, resolver);
        updatePatientInformationForm(patientDetails, patientInfo, resolver);
        JsonObject appointmentDetails = patientDetails.getJsonObject("nextAppointment");
        Resource visit = getOrCreateVisit(appointmentDetails, patient, resolver);
        Resource visitInfo = getOrCreateVisitInformationForm(visit, resolver);
        updateVisitInformationForm(appointmentDetails, visitInfo, resolver);
        final Session session = resolver.adaptTo(Session.class);
        session.save();
    }

    /**
     * Grab a patient Subject node, or create it if it doesn't exist.
     * @param mrn MRN of the subject
     * @param resolver ResourceResolver to use when searching for/creating the node
     */
    Resource getOrCreatePatientSubject(String mrn, ResourceResolver resolver) throws RepositoryException,
        PersistenceException
    {
        Iterator<Resource> patientResource = resolver.findResources(String.format(
            "SELECT * FROM [cards:Subject] WHERE identifier = \"%s\"", mrn), ImportTask.JCR_SQL);
        if (patientResource.hasNext()) {
            return patientResource.next();
        } else {
            // Create a new patient
            Resource parentResource = resolver.getResource("/Subjects/");
            Resource patientType = resolver.getResource("/SubjectTypes/Patient");
            return resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                ImportTask.PRIMARY_TYPE, "cards:Subject",
                "identifier", mrn,
                "fullidentifier", mrn,
                "type", patientType.adaptTo(Node.class)
            ));
        }
    }

    Resource getOrCreatePatientInformationForm(JsonObject info, Resource patient, ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        Iterator<Resource> form = resolver.findResources(String.format(
            "SELECT * FROM [cards:Form] WHERE subject = \"%s\"",
            patient.adaptTo(Node.class).getProperty("jcr:uuid").getString()), ImportTask.JCR_SQL);
        if (form.hasNext()) {
            return form.next();
        } else {
            Resource parentResource = resolver.getResource("/Forms/");
            Resource patientFormType = resolver.getResource("/Questionnaires/Patient information/");
            // Create a new patient information form
            return resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                ImportTask.PRIMARY_TYPE, "cards:Form",
                "questionnaire", patientFormType.adaptTo(Node.class),
                "relatedSubjects", patient.adaptTo(Node.class),
                "subject", patient.adaptTo(Node.class)
            ));
        }
    }

    interface JsonValueGetter
    {
        String get(JsonObject in);
    }

    String safelyGetValue(JsonValueGetter valueGetter, JsonObject info)
    {
        try {
            return valueGetter.get(info);
        } catch (ClassCastException e) {
            // Class cast exceptions represent a value of "null", which could not be obtained
            return "";
        }
    }

    void updatePatientInformationForm(JsonObject info, Resource form, ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        // TODO: Clean up

        // Map of Patient information question node name => Function to get JSON value
        Map<String, JsonValueGetter> formMapping = Map.of(
            "mrn", obj -> obj.getString("mrn"),
            "health_card", obj -> obj.getString("ohip"),
            "sex", obj -> obj.getString("sex"),
            "first_name", obj -> obj.getJsonObject("name").getJsonArray("given").getString(0),
            "last_name", obj -> obj.getJsonObject("name").getString("family"),
            "email", obj -> obj.getJsonObject("com").getString("email")
        );

        // Run through the children of the node, seeing what exists
        Set<String> seenNodes = new HashSet<String>();
        Node dateNode = null;
        for (Resource existingAnswer : form.getChildren()) {
            Node answerNode = existingAnswer.adaptTo(Node.class);
            Node questionNode = answerNode.getProperty(ImportTask.QUESTION_FIELD).getNode();

            // Make note of the date node, since we handle it differently
            if ("date_of_birth".equals(questionNode.getName())) {
                dateNode = answerNode;
            }

            // Do nothing if we don't know how to update this question
            if (!formMapping.containsKey(questionNode.getName())) {
                continue;
            }

            seenNodes.add(questionNode.getName());
            answerNode.setProperty(ImportTask.VALUE_FIELD,
                safelyGetValue(formMapping.get(questionNode.getName()), info));
        }

        // Determine which of these already exists, and update them
        for (Map.Entry<String, JsonValueGetter> entry : formMapping.entrySet()) {
            // Do not create duplicates
            if (seenNodes.contains(entry.getKey())) {
                continue;
            }

            resolver.create(form, UUID.randomUUID().toString(), Map.of(
                ImportTask.QUESTION_FIELD, resolver.getResource("/Questionnaires/Patient information/"
                    + entry.getKey()).adaptTo(Node.class),
                ImportTask.VALUE_FIELD, safelyGetValue(entry.getValue(), info),
                ImportTask.PRIMARY_TYPE, "cards:TextAnswer"
                ));
        }

        // Finally, do the same with the date of birth, which has special parameters
        try {
            Calendar birthDate = Calendar.getInstance();
            birthDate.setTime(new SimpleDateFormat("yyyy-MM-dd").parse(info.getString("dob")));
            if (dateNode == null) {
                resolver.create(form, UUID.randomUUID().toString(), Map.of(
                    ImportTask.QUESTION_FIELD,
                    resolver.getResource("/Questionnaires/Patient information/date_of_birth").adaptTo(Node.class),
                    ImportTask.VALUE_FIELD, birthDate,
                    ImportTask.PRIMARY_TYPE, "cards:DateAnswer"
                    ));
            } else {
                dateNode.setProperty(ImportTask.VALUE_FIELD, birthDate);
            }
        } catch (ParseException e) {
            //TODO: handle exception
            LOGGER.error("Error occurred while parsing Date of Birth for " + info.toString());
        }
    }

    void updateVisitInformationForm(JsonObject info, Resource form, ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        Map<String, JsonValueGetter> formMapping = Map.of(
            "fhir_id", obj -> obj.getString("fhirID"),
            "status", obj -> obj.getString("status"),
            "provider", obj -> obj.getJsonObject("attending").getJsonObject("name").getString("family")
        );

        Set<String> seenNodes = new HashSet<String>();
        Node dateNode = null;
        for (Resource existingAnswer : form.getChildren()) {
            Node answerNode = existingAnswer.adaptTo(Node.class);
            Node questionNode = answerNode.getProperty(ImportTask.QUESTION_FIELD).getNode();

            // Make note of the date node, since we handle it differently
            if ("time".equals(questionNode.getName())) {
                dateNode = answerNode;
            }

            // Do nothing if we don't know how to update this question
            if (!formMapping.containsKey(questionNode.getName())) {
                continue;
            }

            seenNodes.add(questionNode.getName());
            answerNode.setProperty(ImportTask.VALUE_FIELD,
                safelyGetValue(formMapping.get(questionNode.getName()), info));
        }

        // Determine which of these already exists, and update them
        for (Map.Entry<String, JsonValueGetter> entry : formMapping.entrySet()) {
            // Do not create duplicates
            if (seenNodes.contains(entry.getKey())) {
                continue;
            }

            try {
                String value = safelyGetValue(entry.getValue(), info);
                resolver.create(form, UUID.randomUUID().toString(), Map.of(
                    ImportTask.QUESTION_FIELD, resolver.getResource("/Questionnaires/Visit information/"
                        + entry.getKey()).adaptTo(Node.class),
                    ImportTask.VALUE_FIELD, entry.getValue().get(info),
                    ImportTask.PRIMARY_TYPE, "cards:TextAnswer"
                    ));
            } catch (NullPointerException _) {
                // Skip this field, as it is not filled out
            }
        }

        // Finally, do the same with the date of birth, which has special parameters
        try {
            Calendar visitTime = Calendar.getInstance();
            visitTime.setTime(new SimpleDateFormat("yyyy-MM-dd").parse(info.getString("time")));
            if (dateNode == null) {
                resolver.create(form, UUID.randomUUID().toString(), Map.of(
                    ImportTask.QUESTION_FIELD, resolver.getResource("/Questionnaires/Visit information/time")
                        .adaptTo(Node.class),
                    ImportTask.VALUE_FIELD, visitTime,
                    ImportTask.PRIMARY_TYPE, "cards:DateAnswer"
                    ));
            } else {
                dateNode.setProperty(ImportTask.VALUE_FIELD, visitTime);
            }
        } catch (ParseException e) {
            //TODO: handle exception
            LOGGER.error("Error occurred while parsing Date of Birth for " + info.toString());
        }
    }

    Resource getOrCreateVisit(JsonObject visit, Resource patient, ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        String id = visit.getString("fhirID");
        Iterator<Resource> answer = resolver.findResources(String.format(
            "SELECT * FROM [cards:Subject] WHERE identifier = \"%s\"", id), ImportTask.JCR_SQL);
        if (answer.hasNext()) {
            return answer.next();
        } else {
            // Create a new Visit
            Resource visitType = resolver.getResource("/SubjectTypes/Patient/Visit/");
            // Create a new patient information form
            return resolver.create(patient, UUID.randomUUID().toString(), Map.of(
                ImportTask.PRIMARY_TYPE, "cards:Subject",
                "identifier", id,
                "fullidentifier", id,
                "type", visitType.adaptTo(Node.class)
            ));
        }
    }

    Resource getOrCreateVisitInformationForm(Resource visit, ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        Iterator<Resource> form = resolver.findResources(String.format(
            "SELECT * FROM [cards:Form] WHERE subject = \"%s\"",
            visit.adaptTo(Node.class).getProperty("jcr:uuid").getString()), ImportTask.JCR_SQL);
        if (form.hasNext()) {
            return form.next();
        } else {
            Resource parentResource = resolver.getResource("/Forms/");
            Resource formType = resolver.getResource("/Questionnaires/Visit information/");
            // Create a new visit information form
            return resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                ImportTask.PRIMARY_TYPE, "cards:Form",
                "questionnaire", formType.adaptTo(Node.class),
                "relatedSubjects", visit.adaptTo(Node.class),
                "subject", visit.adaptTo(Node.class)
            ));
        }
    }

    /**
     * Store information about an appointment from a JsonObject.
     * @param appointment The appointment object to add
     */
    void storeAppointment(JsonObject appointment, ResourceResolver resolver, Resource patient)
        throws RepositoryException, PersistenceException
    {
        String id = appointment.getString("fhirID");
        Iterator<Resource> answer = resolver.findResources(String.format(
            "SELECT * FROM [cards:Subject] WHERE identifier = \"%s\"", id), ImportTask.JCR_SQL);
        if (answer.hasNext()) {
            // Make sure our information is up to date
            Resource form = answer.next().getParent();
            // We need to check: time and status
        } else {
            Resource parentResource = resolver.getResource("/Forms/");
            Resource visitFormType = resolver.getResource("/Questionnaires/Visit information/");
            // Create a new patient information form
            resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                "questionnaire", visitFormType.adaptTo(Node.class),
                "questionnaire@TypeHint", "Reference",
                "relatedSubjects", patient.adaptTo(Node.class),
                "subject", patient.adaptTo(Node.class)
            ));
        }
    }
}
