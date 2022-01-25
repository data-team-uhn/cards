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

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to store a patient JSON object as returned by our Torch server into JCR. This
 * is mostly a utility class for ImportTask, as it assumes the JsonObject has the following fields:
 * { name {given family} sex mrn ohip dob emailOk com {email} nextAppointment {fhirID time
 * status attending {name {family}}}
 *
 * @version $Id$
 */
public class PatientLocalStorage
{
    /** Default logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(PatientLocalStorage.class);

    /** The Sling property name for Node types. */
    private static final String PRIMARY_TYPE = "jcr:primaryType";

    /** JCR-SQL2 query language name for use with {@code ResourceResolver.findResources()}. */
    private static final String JCR_SQL = "JCR-SQL2";

    /** Sling property name for the question reference field on an Answer node. */
    private static final String QUESTION_FIELD = "question";

    /** Sling property name for the value field on an Answer node. */
    private static final String VALUE_FIELD = "value";

    /** Provides access to resources. */
    private final ResourceResolver resolver;

    /** Details about a patient that we will parse and store in JCR. */
    private JsonObject patientDetails;

    /** Start date of appointments to store. */
    private Calendar startDate;

    /** End date of appointments to store. */
    private Calendar endDate;

    /** Set of nodes that must be checked in at the end of this function. */
    private Set<String> nodesToCheckin;

    /**
     * @param resolver A reference to a ResourceResolver
     * @param startDate The start of the range of dates for appointments to find from within the patient
     * @param endDate The end of the range of dates for appointments to find from within the patient
     */
    PatientLocalStorage(ResourceResolver resolver, final Calendar startDate, final Calendar endDate)
    {
        this.resolver = resolver;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Store the patient details given to us.
     * @param value A JsonObject representing the patient
     */
    public void store(final JsonValue value)
    {
        this.patientDetails = value.asJsonObject();
        String mrn = this.patientDetails.getString("mrn");
        this.nodesToCheckin = new HashSet<String>();
        try {
            // Update information about the patient
            Resource patient = getOrCreateSubject(mrn, "/SubjectTypes/Patient", null);
            Resource patientInfo = getOrCreateForm(patient, "/Questionnaires/Patient information/");
            updatePatientInformationForm(patientInfo, this.patientDetails);

            // Update information about the visit ("appointment" is used interchangably here)
            JsonArray appointmentDetails = this.patientDetails.getJsonArray("appointments");
            for (int i = 0; i < appointmentDetails.size(); i++) {
                JsonObject appointment = appointmentDetails.getJsonObject(i);
                if (isAppointmentInTimeframe(appointment)) {
                    storeAppointment(appointment, patient);
                }
            }

            final Session session = this.resolver.adaptTo(Session.class);
            session.save();

            final VersionManager vm = session.getWorkspace().getVersionManager();
            this.nodesToCheckin.forEach(node -> {
                try {
                    vm.checkin(node);
                } catch (RepositoryException e) {
                    LOGGER.warn("Failed to check in node {}: {}", node, e.getMessage(), e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Could not save patient {}: {}", mrn, e.getMessage(), e);
        } catch (RepositoryException e) {
            LOGGER.error("Could not save patient {}: {}", mrn, e.getMessage(), e);
        }
    }

    /**
     * Store an appointment from the given JsonObject, if it is within the right time frame.
     * @param appointment JsonObject of an appointment to store
     * @param patient path to a patient subject for this appointment
     */
    void storeAppointment(JsonObject appointment, Resource patient)
        throws RepositoryException, PersistenceException
    {
        // In one usage of patientLocalStorage.store(), only store those within a specific timeframe
        List<String> surveyIDs = getSurveyIDs(appointment.getJsonArray("location"));
        for (String surveyID : surveyIDs)
        {
            LOGGER.warn("Prosessing: " + surveyID);
            Resource visit = getOrCreateSubject(appointment.getString("fhirID") + "-" + surveyID,
                "/SubjectTypes/Patient/Visit/", patient);
            Resource visitInfo = getOrCreateForm(visit, "/Questionnaires/Visit information/");
            updateVisitInformationForm(visitInfo, appointment, surveyID);
        }
    }

    /**
     * Returns whether or not the given appointment is between our start and end dates.
     * @param appointment JsonObject of an appointment to check
     * @return True if the appointment is between our dates, or false if either the date is outside our range
     *     or if we cannot parse it
     */
    Boolean isAppointmentInTimeframe(JsonObject appointment)
    {
        try {
            Date thisDate = new SimpleDateFormat("yyyy-MM-dd").parse(appointment.getString("time"));
            Calendar thisCalendar = Calendar.getInstance();
            thisCalendar.setTime(thisDate);
            LOGGER.warn("Checking appointment: " + appointment.getString("time"));
            return thisCalendar.after(this.startDate) && thisCalendar.before(this.endDate);
        } catch (ParseException e) {
            //TODO: handle exception
            LOGGER.error("Could not parse date for appointment {}: {}", appointment.getString("fhirID"),
                e.getMessage(), e);
        }
        return false;
    }

    /**
     * Grab a subject of the specified type, or create it if it doesn't exist.
     * @param identifier Identifier to use for the subject
     * @param subjectTypePath path to a SubjectType node for this subject
     * @param parent parent Resource if this is a child of that resource, or null
     * @return A Subject resource
     */
    Resource getOrCreateSubject(String identifier, String subjectTypePath, Resource parent)
        throws RepositoryException, PersistenceException
    {
        Iterator<Resource> subjectResourceIter = this.resolver.findResources(String.format(
            "SELECT * FROM [cards:Subject] WHERE identifier = \"%s\"", identifier), PatientLocalStorage.JCR_SQL);
        if (subjectResourceIter.hasNext()) {
            Resource subjectResource = subjectResourceIter.next();
            this.nodesToCheckin.add(subjectResource.getPath());
            return subjectResource;
        } else {
            Resource parentResource = parent;
            if (parentResource == null) {
                parentResource = this.resolver.getResource("/Subjects/");
            }
            Resource patientType = this.resolver.getResource(subjectTypePath);
            Resource newSubject = this.resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                PatientLocalStorage.PRIMARY_TYPE, "cards:Subject",
                "identifier", identifier,
                "type", patientType.adaptTo(Node.class)
            ));
            this.nodesToCheckin.add(newSubject.getPath());
            return newSubject;
        }
    }

    /**
     * Grab a form of the specified type, or create it if it doesn't exist.
     * @param subject Resource of the subject node for the form
     * @param questionnairePath Path to the questionnaire that this is a form of
     * @return A Form resource
     */
    Resource getOrCreateForm(Resource subject, String questionnairePath)
        throws RepositoryException, PersistenceException
    {
        Resource formType = this.resolver.getResource(questionnairePath);
        Node subjectNode = subject.adaptTo(Node.class);
        Iterator<Resource> formResourceIter = this.resolver.findResources(String.format(
            "SELECT * FROM [cards:Form] WHERE subject = \"%s\" AND questionnaire=\"%s\"",
            subjectNode.getIdentifier(),
            formType.adaptTo(Node.class).getIdentifier()), PatientLocalStorage.JCR_SQL);
        if (formResourceIter.hasNext()) {
            Resource formResource = formResourceIter.next();
            this.nodesToCheckin.add(formResource.getPath());
            return formResource;
        } else {
            Resource parentResource = this.resolver.getResource("/Forms/");
            Resource newForm = this.resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                PatientLocalStorage.PRIMARY_TYPE, "cards:Form",
                "questionnaire", formType.adaptTo(Node.class),
                "subject", subjectNode
            ));
            this.nodesToCheckin.add(newForm.getPath());
            return newForm;
        }
    }

    interface JsonStringGetter
    {
        String get(JsonObject in);
    }

    interface JsonDateGetter
    {
        Date get(JsonObject in) throws ParseException;
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

    /**
     * Update the answers in a form with the values that we were given.
     * @param form The form to fill out
     * @param info The JsonObject with responses to the form, to grab data from
     * @param parentQuestionnaire The questionnaire that this form applies to
     * @param mapping A map of Question node names to the method of getting their String answers from
     *                the {@code info} JsonObject.
     * @param dateFields A map of Question node names to the method of getting their Date answers from
     *                   the {@code info} JsonObject.
     * @return The string, or an empty string if it does not exist
     */
    void updateForm(Resource form, JsonObject info, String parentQuestionnaire,
        Map<String, JsonStringGetter> mapping, Map<String, JsonDateGetter> dateFields)
        throws RepositoryException, PersistenceException
    {
        // Run through the children of the node, seeing what exists
        Set<String> seenNodes = new HashSet<String>();
        Map<String, Node> dateNodes = new HashMap<String, Node>();
        for (Resource existingAnswer : form.getChildren()) {
            Node answerNode = existingAnswer.adaptTo(Node.class);
            Node questionNode = answerNode.getProperty(PatientLocalStorage.QUESTION_FIELD).getNode();

            // Make note of the date node, since we handle it differently
            if (dateFields.containsKey(questionNode.getName())) {
                dateNodes.put(questionNode.getName(), answerNode);
            }

            // Do nothing if we don't know how to update this question
            if (!mapping.containsKey(questionNode.getName())) {
                continue;
            }

            seenNodes.add(questionNode.getName());
            answerNode.setProperty(PatientLocalStorage.VALUE_FIELD,
                safelyGetValue(mapping.get(questionNode.getName()), info));
        }

        // For each node that does not exist, create a new answer
        String formPath = form.getPath();
        for (Map.Entry<String, JsonStringGetter> entry : mapping.entrySet()) {
            if (seenNodes.contains(entry.getKey())) {
                continue;
            }

            this.resolver.create(form, UUID.randomUUID().toString(), Map.of(
                PatientLocalStorage.QUESTION_FIELD, this.resolver.getResource(parentQuestionnaire + "/"
                    + entry.getKey()).adaptTo(Node.class),
                PatientLocalStorage.VALUE_FIELD, safelyGetValue(entry.getValue(), info),
                PatientLocalStorage.PRIMARY_TYPE, "cards:TextAnswer"
                ));
        }

        // Finally, do the same with date fields, which have different parameters
        for (Map.Entry<String, JsonDateGetter> entry : dateFields.entrySet()) {
            try {
                Calendar entryDate = Calendar.getInstance();
                entryDate.setTime(entry.getValue().get(info));
                if (dateNodes.containsKey(entry.getKey())) {
                    dateNodes.get(entry.getKey()).setProperty(PatientLocalStorage.VALUE_FIELD, entryDate);
                } else {
                    this.resolver.create(form, UUID.randomUUID().toString(), Map.of(
                        PatientLocalStorage.QUESTION_FIELD,
                        this.resolver.getResource(parentQuestionnaire + "/" + entry.getKey()).adaptTo(Node.class),
                        PatientLocalStorage.VALUE_FIELD, entryDate,
                        PatientLocalStorage.PRIMARY_TYPE, "cards:DateAnswer"
                        ));
                }
            } catch (ParseException e) {
                LOGGER.error("Error occurred while parsing {} for {}, {}", entry, info.toString(), e);
            }
        }
    }

    /**
     * Update the patient information form with values from the given JsonObject.
     * @param form The Patient Information form to update
     * @param info The JsonObject representing a patient returned from Torch
     */
    void updatePatientInformationForm(Resource form, JsonObject info)
        throws RepositoryException, PersistenceException
    {
        // Map of Patient information question node name => Function to get JSON value
        Map<String, JsonStringGetter> formMapping = Map.of(
            "mrn", obj -> obj.getString("mrn"),
            "health_card", obj -> obj.getString("ohip"),
            "sex", obj -> obj.getString("sex"),
            "first_name", obj -> obj.getJsonObject("name").getJsonArray("given").getString(0),
            "last_name", obj -> obj.getJsonObject("name").getString("family"),
            "email", obj -> obj.getJsonObject("com").getString("email"),
            "email_ok", obj -> obj.getString("emailOk"),
            "fhir_id", obj -> obj.getString("fhirID")
        );

        Map<String, JsonDateGetter> dateMapping = Map.of(
            "date_of_birth", obj -> new SimpleDateFormat("yyyy-MM-dd").parse(obj.getString("dob"))
        );

        updateForm(form, info, "/Questionnaires/Patient information", formMapping, dateMapping);
    }

    /**
     * Update the visit information form with values from the given JsonObject.
     * @param form The Visit Information form to update
     * @param info The JsonObject representing a visit returned from Torch
     * @param surveyID A string representing the internally mapped survey ID for the location of this visit
     */
    void updateVisitInformationForm(Resource form, JsonObject info, String surveyID)
        throws RepositoryException, PersistenceException
    {
        Map<String, JsonStringGetter> formMapping = Map.of(
            "fhir_id", obj -> obj.getString("fhirID"),
            "status", obj -> obj.getString("status"),
            "provider", obj -> obj.getJsonObject("attending").getJsonObject("name").getString("family"),
            // We need to map the display name of the clinic given to a survey ID
            // The mappings are stored at /Proms/ClinicMapping/<location hashcCode>
            "surveys", obj -> surveyID,
            "location", obj -> surveyID
        );

        Map<String, JsonDateGetter> dateMapping = Map.of(
            "time", obj -> new SimpleDateFormat("yyyy-MM-dd").parse(obj.getString("time"))
        );

        updateForm(form, info, "/Questionnaires/Visit information", formMapping, dateMapping);
    }

    /**
     * Map the given clinic display names to a survey ID.
     * @param appointments The appointments array to parse
     * @return The survey IDs as a | delimited string
     */
    List<String> getSurveyIDs(JsonArray appointments)
    {
        List<String> retVal = new ArrayList<>();
        for (int i = 0; i < appointments.size(); i++) {
            // Resolve this name from our mapping
            String clinic = appointments.getString(i);
            // TODO: What do we do in case of a colission?
            String mapPath = "/Proms/ClinicMapping/" + String.valueOf(clinic.hashCode());
            Resource mapping = this.resolver.getResource(mapPath);

            if (mapping == null) {
                LOGGER.error("Could not find mapping for location {} (checking {})", clinic, mapPath);
            } else {
                try
                {
                    retVal.add(mapping.adaptTo(Node.class).getProperty("surveyID").getString());
                } catch (RepositoryException e) {
                    LOGGER.error("Error while resolving clinic mapping: {} {} ", clinic, e.getMessage(), e);
                }
            }
        }

        return retVal;
    }
}
