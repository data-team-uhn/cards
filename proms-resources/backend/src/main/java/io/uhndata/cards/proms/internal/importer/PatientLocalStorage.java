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
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;
import javax.json.JsonObject;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(NightlyImport.class);

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

    PatientLocalStorage(JsonObject patientDetails, ResourceResolver resolver)
    {
        this.resolver = resolver;
        this.patientDetails = patientDetails;
    }

    /**
     * Store the patient details given to us.
     */
    public void store()
    {
        String mrn = this.patientDetails.getString("mrn");
        Set<String> nodesToCheckin = new HashSet<String>();
        try {
            // Update information about the patient
            Resource patient = getOrCreateSubject(mrn, "/SubjectTypes/Patient", null, this.resolver, nodesToCheckin);
            Resource patientInfo = getOrCreateForm(patient, "/Questionnaires/Patient information/", this.resolver,
                nodesToCheckin);
            updatePatientInformationForm(patientInfo, this.patientDetails, this.resolver);

            // Update information about the visit ("appointment" is used interchangably here)
            JsonObject appointmentDetails = this.patientDetails.getJsonObject("nextAppointment");
            Resource visit = getOrCreateSubject(appointmentDetails.getString("fhirID"),
                "/SubjectTypes/Patient/Visit/", patient, this.resolver, nodesToCheckin);
            Resource visitInfo = getOrCreateForm(patient, "/Questionnaires/Visit information/", this.resolver,
                nodesToCheckin);
            updateVisitInformationForm(visitInfo, appointmentDetails, this.resolver);

            final Session session = this.resolver.adaptTo(Session.class);
            session.save();

            final VersionManager vm = session.getWorkspace().getVersionManager();
            nodesToCheckin.forEach(node -> {
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
     * Grab a subject of the specified type, or create it if it doesn't exist.
     * @param identifier Identifier to use for the subject
     * @param parentTypePath path to a SubjectType node for this subject
     * @param parent parent Resource if this is a child of that resource, or null
     * @param resolver ResourceResolver to use when searching for/creating the node
     * @param nodesToCheckin Set of nodes that should be checked in after all edits are complete
     * @return A Subject resource
     */
    Resource getOrCreateSubject(String identifier, String parentTypePath, Resource parent, ResourceResolver resolver,
        Set<String> nodesToCheckin)
        throws RepositoryException, PersistenceException
    {
        Iterator<Resource> patientResource = resolver.findResources(String.format(
            "SELECT * FROM [cards:Subject] WHERE identifier = \"%s\"", identifier), PatientLocalStorage.JCR_SQL);
        if (patientResource.hasNext()) {
            return patientResource.next();
        } else {
            Resource parentResource = parent;
            if (parentResource == null) {
                parentResource = resolver.getResource("/Subjects/");
            }
            Resource patientType = resolver.getResource(parentTypePath);
            Resource newSubject = resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                PatientLocalStorage.PRIMARY_TYPE, "cards:Subject",
                "identifier", identifier,
                "fullidentifier", identifier,
                "type", patientType.adaptTo(Node.class)
            ));
            nodesToCheckin.add(newSubject.getPath());
            return newSubject;
        }
    }

    /**
     * Grab a form of the specified type, or create it if it doesn't exist.
     * @param subject Resource of the subject node for the form
     * @param questionnairePath Path to the questionnaire that this is a form of
     * @param resolver ResourceResolver to use when searching for/creating the node
     * @param nodesToCheckin Set of nodes that should be checked in after all edits are complete
     * @return A Form resource
     */
    Resource getOrCreateForm(Resource subject, String questionnairePath, ResourceResolver resolver,
        Set<String> nodesToCheckin)
        throws RepositoryException, PersistenceException
    {
        Resource formType = resolver.getResource(questionnairePath);
        Node subjectNode = subject.adaptTo(Node.class);
        Iterator<Resource> formResource = resolver.findResources(String.format(
            "SELECT * FROM [cards:Form] WHERE subject = \"%s\" AND questionnaire=\"%s\"",
            subjectNode.getProperty("jcr:uuid").getString(),
            formType.adaptTo(Node.class).getProperty("jcr:uuid").getString()), PatientLocalStorage.JCR_SQL);
        if (formResource.hasNext()) {
            return formResource.next();
        } else {
            Resource parentResource = resolver.getResource("/Forms/");
            Resource newForm = resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                PatientLocalStorage.PRIMARY_TYPE, "cards:Form",
                "questionnaire", formType.adaptTo(Node.class),
                "relatedSubjects", subjectNode,
                "subject", subjectNode
            ));
            nodesToCheckin.add(newForm.getPath());
            return newForm;
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

    /**
     * Update the answers in a form with the values that we were given.
     * @param form The form to fill out
     * @param info The JsonObject with responses to the form, to grab data from
     * @param parentQuestionnaire The questionnaire that this form applies to
     * @param mapping A map of Question node names to the method of getting their String answers from
     *                the {@code info} JsonObject.
     * @param dateFields A map of Question node names to the method of getting their Date answers from
     *                   the {@code info} JsonObject.
     * @param resolver a reference to a ResourceResolver to resolve queries
     * @return The string, or an empty string if it does not exist
     */
    void updateForm(Resource form, JsonObject info, String parentQuestionnaire,
        Map<String, JsonStringGetter> mapping, Map<String, String> dateFields, ResourceResolver resolver)
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

            resolver.create(form, UUID.randomUUID().toString(), Map.of(
                PatientLocalStorage.QUESTION_FIELD, resolver.getResource(parentQuestionnaire + "/"
                    + entry.getKey()).adaptTo(Node.class),
                PatientLocalStorage.VALUE_FIELD, safelyGetValue(entry.getValue(), info),
                PatientLocalStorage.PRIMARY_TYPE, "cards:TextAnswer"
                ));
        }

        // Finally, do the same with date fields, which have different parameters
        for (Map.Entry<String, String> entry : dateFields.entrySet()) {
            try {
                Calendar entryDate = Calendar.getInstance();
                entryDate.setTime(new SimpleDateFormat("yyyy-MM-dd").parse(info.getString(entry.getValue())));
                if (dateNodes.containsKey(entry.getKey())) {
                    dateNodes.get(entry.getKey()).setProperty(PatientLocalStorage.VALUE_FIELD, entryDate);
                } else {
                    resolver.create(form, UUID.randomUUID().toString(), Map.of(
                        PatientLocalStorage.QUESTION_FIELD,
                        resolver.getResource(parentQuestionnaire + "/" + entry.getKey()).adaptTo(Node.class),
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
     * @param form The Visit Information form to update
     * @param info The JsonObject representing a visit returned from Torch
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
