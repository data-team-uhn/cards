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

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.version.VersionManager;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to store a patient JSON object as returned by our Torch server into JCR. This is mostly a utility class
 * for ImportTask, as it assumes the JsonObject has the following fields: { name {given family} sex mrn ohip dob emailOk
 * com {email} nextAppointment {fhirID time status attending {name {family}}}
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

    /** Sling property name for the value field on an Answer node. */
    private static final String STATUS_FIELD = "statusFlags";

    /** Empty status flags value. */
    private static final String[] STATUS_FLAGS = new String[0];

    /** Sling property name for the fhir ID field returned from a Patient or Provider. */
    private static final String FHIR_FIELD = "fhirID";

    /** Provides access to resources. */
    private final ResourceResolver resolver;

    /** Details about a patient that we will parse and store in JCR. */
    private JsonObject patientDetails;

    /** Start date of appointments to store. */
    private final Calendar startDate;

    /** End date of appointments to store. */
    private final Calendar endDate;

    /** List of providers to query. If empty, all providers' appointments are used. */
    private final List<String> providerIDs;

    /** List of allowed provider roles, such that providerIDs must be the given role. Optionally set. */
    private final List<String> providerRoles;

    /** Set of nodes that must be checked in at the end of this function. */
    private Set<String> nodesToCheckin;

    /** Version Manager for checking in or out nodes. */
    private VersionManager versionManager;

    /** Count of appointments created. */
    private long appointmentsCreated;

    /** Dates to check. */
    private final List<Calendar> datesToQuery;

    /**
     * @param resolver A reference to a ResourceResolver
     * @param startDate The start of the range of dates for appointments to find from within the patient
     * @param endDate The end of the range of dates for appointments to find from within the patient
     * @param providerIDs List of providers to query. If empty, all providers' appointments are used
     */
    PatientLocalStorage(final ResourceResolver resolver, final Calendar startDate, final Calendar endDate,
        final String[] providerIDs, final String[] providerRoles, final List<Calendar> datesToQuery)
    {
        this.resolver = resolver;
        this.startDate = startDate;
        this.endDate = endDate;
        this.providerIDs = Arrays.asList(providerIDs);
        this.providerRoles = Arrays.asList(providerRoles);
        this.appointmentsCreated = 0;
        this.datesToQuery = datesToQuery;
    }

    public long getCountAppointmentsCreated()
    {
        return this.appointmentsCreated;
    }

    /**
     * Store the patient details given to us.
     *
     * @param value A JsonObject representing the patient
     */
    public void store(final JsonValue value)
    {
        this.patientDetails = value.asJsonObject();
        final String mrn = this.patientDetails.getString("mrn");
        this.nodesToCheckin = new HashSet<>();
        try {
            final Session session = this.resolver.adaptTo(Session.class);
            this.versionManager = session.getWorkspace().getVersionManager();
            Resource patient = null;

            // Update information about the visit ("appointment" is used interchangeably here)
            final JsonArray appointmentDetails = this.patientDetails.getJsonArray("appointments");
            long appointmentsPendingCreation = 0;
            for (int i = 0; i < appointmentDetails.size(); i++) {
                final JsonObject appointment = appointmentDetails.getJsonObject(i);
                if (isAppointmentInTimeframe(appointment) && isAppointmentByAllowedProvider(appointment)) {
                    if (patient == null) {
                        // Update information about the patient, but only if we already know a visit is also valid
                        patient = getOrCreateSubject(mrn, "/SubjectTypes/Patient", null);
                        final Resource patientInfo = getOrCreateForm(patient, "/Questionnaires/Patient information");
                        updatePatientInformationForm(patientInfo, this.patientDetails);
                    }
                    storeAppointment(appointment, patient);
                    appointmentsPendingCreation += 1;
                }
            }

            session.save();
            this.appointmentsCreated += appointmentsPendingCreation;

            this.nodesToCheckin.forEach(node -> {
                try {
                    this.versionManager.checkin(node);
                } catch (final RepositoryException e) {
                    LOGGER.warn("Failed to check in node {}: {}", node, e.getMessage(), e);
                }
            });
        } catch (final IOException e) {
            LOGGER.error("Could not save patient {}: {}", mrn, e.getMessage(), e);
        } catch (final RepositoryException e) {
            LOGGER.error("Could not save patient {}: {}", mrn, e.getMessage(), e);
        }
    }

    /**
     * Store an appointment from the given JsonObject, if it is within the right time frame.
     *
     * @param appointment JsonObject of an appointment to store
     * @param patient path to a patient subject for this appointment
     */
    void storeAppointment(final JsonObject appointment, final Resource patient)
        throws RepositoryException, PersistenceException
    {
        // In one usage of patientLocalStorage.store(), only store those within a specific timeframe
        final List<ClinicInfo> clinicInfos = getClinicInfo(appointment.getJsonArray("location"));
        for (final ClinicInfo clinicInfo : clinicInfos) {
            final Resource visit = getOrCreateSubject(appointment.getString(PatientLocalStorage.FHIR_FIELD)
                + "-" + clinicInfo.getSurveyId(), "/SubjectTypes/Patient/Visit/", patient);
            final Resource visitInfo = getOrCreateForm(visit, "/Questionnaires/Visit information");
            updateVisitInformationForm(visitInfo, appointment, clinicInfo.getDisplayName(),
                clinicInfo.getClinicPath());
        }
    }

    /**
     * Returns whether or not the given appointment is between our start and end dates.
     *
     * @param appointment JsonObject of an appointment to check
     * @return True if the appointment is between our dates, or false if either the date is outside our range or if we
     *         cannot parse it
     */
    Boolean isAppointmentInTimeframe(final JsonObject appointment)
    {
        try {
            final Date thisDate = new SimpleDateFormat("yyyy-MM-dd").parse(appointment.getString("time"));
            final Calendar thisCalendar = Calendar.getInstance();
            thisCalendar.setTime(thisDate);
            if (this.datesToQuery.size() > 0 && !listContainsDate(this.datesToQuery, thisCalendar)) {
                return false;
            }

            return thisCalendar.after(this.startDate) && thisCalendar.before(this.endDate);
        } catch (final ParseException e) {
            LOGGER.error("Could not parse date for appointment {}: {}",
                appointment.getString(PatientLocalStorage.FHIR_FIELD), e.getMessage(), e);
        }
        return false;
    }

    /**
     * Returns True if the given list contains the given date.
     *
     * @param dates List of Calendar objects
     * @param toCheck Calendar object to check is contained within the above list
     * @return True if the list of Calendar objects contains an object with the same date as toCheck,
     *      or False otherwise
     */
    Boolean listContainsDate(final List<Calendar> dates, Calendar toCheck)
    {
        for (Calendar date : this.datesToQuery) {
            if (date.get(Calendar.YEAR) == toCheck.get(Calendar.YEAR)
                && date.get(Calendar.MONTH) == toCheck.get(Calendar.MONTH)
                && date.get(Calendar.DATE) == toCheck.get(Calendar.DATE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether or not the given appointment is attended by an approved provider.
     *
     * @param appointment JsonObject of an appointment to check
     * @return True if the appointment is with an approved provider or if no providers are used.
     */
    Boolean isAppointmentByAllowedProvider(final JsonObject appointment)
    {
        // Check if we even have provider IDs to use
        // If there are none, it is assumed that all providers in the clinic are allowed
        if (this.providerIDs.size() == 0) {
            return true;
        }

        // Return true if at least one provider matches
        final JsonValue rawProvider = appointment.get("participants");
        if (rawProvider == null) {
            return false;
        }
        if (rawProvider.getValueType() == ValueType.OBJECT) {
            // If we are returned a single object, it is a single provider
            final JsonObject provider = rawProvider.asJsonObject();
            if (isAllowedProvider(provider)) {
                return true;
            }
        } else if (rawProvider.getValueType() == ValueType.ARRAY) {
            final JsonArray providers = rawProvider.asJsonArray();
            for (int i = 0; i < providers.size(); i++) {
                if (isAllowedProvider(providers.getJsonObject(i))) {
                    return true;
                }
            }
        }

        return false;
    }

    /***
     * Returns whether or not the given provider is on our list of allowed providers.
     *
     * @param appointment JsonObject of a provider from the "providers" object within an appointment
     * @return True if the provider is approved and is an attendee.
     */
    private boolean isAllowedProvider(final JsonObject provider)
    {
        // Check that the provider is an allowed role
        if (this.providerRoles.size() > 0) {
            if (provider.containsKey("role")) {
                Boolean isAllowedRole = false;
                for (final String allowedRole : this.providerRoles) {
                    if (allowedRole.equals(provider.getString("role"))) {
                        isAllowedRole = true;
                        break;
                    }
                }

                if (!isAllowedRole) {
                    return false;
                }
            } else {
                // At least one provider role needed and this doesn't have any: reject
                return false;
            }
        }

        // Check that the provider is in our approved list
        for (final String providerID : this.providerIDs) {
            if (providerID.equals(provider.getJsonObject("physician").getString("eID"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Grab a subject of the specified type, or create it if it doesn't exist.
     *
     * @param identifier Identifier to use for the subject
     * @param subjectTypePath path to a SubjectType node for this subject
     * @param parent parent Resource if this is a child of that resource, or null
     * @return A Subject resource
     */
    Resource getOrCreateSubject(final String identifier, final String subjectTypePath, final Resource parent)
        throws RepositoryException, PersistenceException
    {
        final Iterator<Resource> subjectResourceIter = this.resolver.findResources(String.format(
            "SELECT * FROM [cards:Subject] WHERE identifier = \"%s\" OPTION (index tag property)", identifier),
            PatientLocalStorage.JCR_SQL);
        if (subjectResourceIter.hasNext()) {
            final Resource subjectResource = subjectResourceIter.next();
            this.versionManager.checkout(subjectResource.getPath());
            this.nodesToCheckin.add(subjectResource.getPath());
            return subjectResource;
        } else {
            Resource parentResource = parent;
            if (parentResource == null) {
                parentResource = this.resolver.getResource("/Subjects/");
            }
            final Resource patientType = this.resolver.getResource(subjectTypePath);
            final Resource newSubject = this.resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                PatientLocalStorage.PRIMARY_TYPE, "cards:Subject",
                "identifier", identifier,
                "type", patientType.adaptTo(Node.class)));
            this.nodesToCheckin.add(newSubject.getPath());
            return newSubject;
        }
    }

    /**
     * Grab a form of the specified type, or create it if it doesn't exist.
     *
     * @param subject Resource of the subject node for the form
     * @param questionnairePath Path to the questionnaire that this is a form of
     * @return A Form resource
     */
    Resource getOrCreateForm(final Resource subject, final String questionnairePath)
        throws RepositoryException, PersistenceException
    {
        final Resource formType = this.resolver.getResource(questionnairePath);
        final Node subjectNode = subject.adaptTo(Node.class);
        final Iterator<Resource> formResourceIter = this.resolver.findResources(String.format(
            "SELECT * FROM [cards:Form] WHERE subject = \"%s\" AND questionnaire=\"%s\" OPTION (index tag property)",
            subjectNode.getIdentifier(),
            formType.adaptTo(Node.class).getIdentifier()), PatientLocalStorage.JCR_SQL);
        if (formResourceIter.hasNext()) {
            final Resource formResource = formResourceIter.next();
            this.versionManager.checkout(formResource.getPath());
            this.nodesToCheckin.add(formResource.getPath());
            return formResource;
        } else {
            final Resource parentResource = this.resolver.getResource("/Forms/");
            final Resource newForm = this.resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                PatientLocalStorage.PRIMARY_TYPE, "cards:Form",
                "questionnaire", formType.adaptTo(Node.class),
                "subject", subjectNode));
            this.nodesToCheckin.add(newForm.getPath());
            return newForm;
        }
    }

    interface JsonGetter
    {
        Object get(JsonObject in);
    }

    interface JsonDateGetter
    {
        Date get(JsonObject in) throws ParseException;
    }

    /**
     * Get a string from a JSONObject using the given JsonStringGetter function, returning an empty string if the value
     * does not exist.
     *
     * @param valueGetter The JsonStringGetter to use when obtaining a value from a JsonObject
     * @param info The JsonObject to apply valueGetter to.
     * @return The string, or an empty string if it does not exist
     */
    Object safelyGetValue(final JsonGetter valueGetter, final JsonObject info)
    {
        try {
            return valueGetter.get(info);
        } catch (final NullPointerException | ClassCastException e) {
            // Class cast exceptions represent a value of "null", which could not be obtained
            return "";
        }
    }

    /**
     * Update the answers in a form with the values that we were given.
     *
     * @param form The form to fill out
     * @param info The JsonObject with responses to the form, to grab data from
     * @param parentQuestionnaire The questionnaire that this form applies to
     * @param mapping A map of Question node names to the method of getting their String answers from the {@code info}
     *            JsonObject.
     * @param dateFields A map of Question node names to the method of getting their Date answers from the {@code info}
     *            JsonObject.
     * @return The string, or an empty string if it does not exist
     */
    void updateForm(final Resource form, final JsonObject info, final String parentQuestionnaire,
        final Map<String, JsonGetter> mapping)
        throws RepositoryException, PersistenceException
    {
        // Run through the children of the node, seeing what exists
        final Set<String> seenNodes = new HashSet<>();
        final ValueFactory valueFactory = form.getResourceResolver().adaptTo(Session.class).getValueFactory();
        for (final Resource existingAnswer : form.getChildren()) {
            final Node answerNode = existingAnswer.adaptTo(Node.class);
            final Node questionNode = answerNode.getProperty(PatientLocalStorage.QUESTION_FIELD).getNode();
            final String questionName = questionNode.getName();

            // Do nothing if we don't know how to update this question
            final JsonGetter matchingGetter = mapping.entrySet().stream()
                .filter(entry -> StringUtils.equals(StringUtils.substringBefore(entry.getKey(), "@"),
                    questionName))
                .map(Entry::getValue)
                .findFirst().orElse(null);
            if (matchingGetter == null) {
                continue;
            }

            seenNodes.add(questionName);
            final Object newRawValue = safelyGetValue(matchingGetter, info);
            if (newRawValue instanceof Object[]) {
                answerNode.setProperty(PatientLocalStorage.VALUE_FIELD, toValues((Object[]) newRawValue, valueFactory));
            } else {
                answerNode.setProperty(PatientLocalStorage.VALUE_FIELD, toValue(newRawValue, valueFactory));
            }
        }

        for (final Map.Entry<String, JsonGetter> entry : mapping.entrySet()) {
            final String questionName = StringUtils.substringBefore(entry.getKey(), "@");
            if (seenNodes.contains(questionName)) {
                continue;
            }

            final String answerType =
                StringUtils.defaultIfEmpty(StringUtils.substringAfter(entry.getKey(), "@"), "cards:TextAnswer");

            this.resolver.create(form, UUID.randomUUID().toString(), Map.of(
                PatientLocalStorage.QUESTION_FIELD, this.resolver.getResource(parentQuestionnaire + "/"
                    + questionName).adaptTo(Node.class),
                PatientLocalStorage.VALUE_FIELD, safelyGetValue(entry.getValue(), info),
                PatientLocalStorage.PRIMARY_TYPE, answerType,
                PatientLocalStorage.STATUS_FIELD, STATUS_FLAGS));
        }
    }

    private Value toValue(final Object rawValue, final ValueFactory valueFactory)
    {
        Value result = null;
        if (rawValue instanceof Long) {
            result = valueFactory.createValue((Long) rawValue);
        } else if (rawValue instanceof Double) {
            result = valueFactory.createValue((Double) rawValue);
        } else if (rawValue instanceof Boolean) {
            result = valueFactory.createValue(
                BooleanUtils.toInteger((Boolean) rawValue, 1, 0, -1));
        } else if (rawValue instanceof Calendar) {
            result = valueFactory.createValue((Calendar) rawValue);
        } else {
            result = valueFactory.createValue(String.valueOf(rawValue));
        }
        return result;
    }

    private Value[] toValues(final Object[] rawValue, final ValueFactory valueFactory)
    {
        Value[] result = new Value[rawValue.length];
        for (int i = 0; i < rawValue.length; ++i) {
            result[i] = toValue(rawValue[i], valueFactory);
        }
        return result;
    }

    /**
     * Update the patient information form with values from the given JsonObject.
     *
     * @param form The Patient Information form to update
     * @param info The JsonObject representing a patient returned from Torch
     */
    void updatePatientInformationForm(final Resource form, final JsonObject info)
        throws RepositoryException, PersistenceException
    {
        // Map of Patient information question node name => Function to get JSON value
        final Map<String, JsonGetter> formMapping = Map.of(
            "mrn", obj -> obj.getString("mrn"),
            "health_card", obj -> obj.getString("ohip"),
            "sex", obj -> obj.getString("sex"),
            "first_name", obj -> obj.getJsonObject("name").getJsonArray("given").getString(0),
            "last_name", obj -> obj.getJsonObject("name").getString("family"),
            "date_of_birth@cards:DateAnswer", obj -> toCalendar(obj.getString("dob"), "yyyy-MM-dd"),
            "email", obj -> obj.getJsonObject("com").getJsonObject("email").values().stream()
                .filter(e -> e != null && e != JsonValue.NULL && (e.getValueType() == JsonValue.ValueType.STRING))
                .map(e -> ((JsonString) e).getString())
                .findFirst().orElse(""),
            "email_ok@cards:BooleanAnswer", obj -> BooleanUtils.toInteger(obj.getBoolean("emailOk", false), 1, 0, -1),
            "fhir_id", obj -> obj.getString(PatientLocalStorage.FHIR_FIELD));

        updateForm(form, info, "/Questionnaires/Patient information", formMapping);
    }

    /**
     * Update the visit information form with values from the given JsonObject.
     *
     * @param form The Visit Information form to update
     * @param info The JsonObject representing a visit returned from Torch
     * @param locationName A string representing the display name of the location for this visit
     * @param clinicPath The path to the clinicMapping node for the clinic of this visit
     */
    void updateVisitInformationForm(final Resource form, final JsonObject info, final String locationName,
        final String clinicPath)
        throws RepositoryException, PersistenceException
    {
        final Map<String, JsonGetter> formMapping = Map.of(
            "fhir_id", obj -> obj.getString(PatientLocalStorage.FHIR_FIELD),
            "time@cards:DateAnswer", obj -> toCalendar(obj.getString("time"), "yyyy-MM-dd'T'HH:mm:ss"),
            "status", obj -> obj.getString("status"),
            "provider", obj -> {
                final JsonArray participants = obj.getJsonArray("participants");
                if (participants == null || participants.isEmpty()) {
                    return "";
                }
                final List<String> providerNames = new LinkedList<>();
                for (int i = 0; i < participants.size(); ++i) {
                    JsonObject participantObj = participants.getJsonObject(i).getJsonObject("physician");
                    JsonObject nameObj = participantObj.getJsonObject("name");
                    if (nameObj == null || nameObj == JsonValue.NULL) {
                        continue;
                    }

                    final List<String> fullName = new LinkedList<>(
                        PatientLocalStorage.mapJsonString(nameObj.getJsonArray("prefix")));
                    fullName.addAll(PatientLocalStorage.mapJsonString(nameObj.getJsonArray("given")));
                    if (nameObj.containsKey("family")) {
                        fullName.add(nameObj.getString("family"));
                    }
                    fullName.addAll(PatientLocalStorage.mapJsonString(nameObj.getJsonArray("suffix")));
                    providerNames.add(String.join(" ", fullName));
                }
                return providerNames.toArray();
            },
            "location", obj -> locationName,
            "clinic", obj -> clinicPath);

        updateForm(form, info, "/Questionnaires/Visit information", formMapping);

        // Also create some questions that need to be present for the VisitChangeListener, if they don't exist
        ensureAnswerExists(form, "/Questionnaires/Visit information/surveys_complete", "cards:BooleanAnswer", 0L);
        ensureAnswerExists(form, "/Questionnaires/Visit information/surveys_submitted", "cards:BooleanAnswer", 0L);
    }

    /**
     * Parse a date/time string into a Calendar object, or {@code null} if the value is missing or not according to the
     * format.
     *
     * @param dateStr a date or datetime representation, may be {@code null} or an empty string
     * @param format the expected date or datetime format of {@code dateStr}
     * @return the parsed date as a Calendar, or {@code null}
     */
    private Calendar toCalendar(final String dateStr, final String format)
    {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }
        try {
            final Calendar result = Calendar.getInstance();
            result.setTime(new SimpleDateFormat(format).parse(dateStr));
            return result;
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Convert a {@code JsonArray} of strings to a {@code List<String>}.
     *
     * @param list A JsonArray full of JsonStrings to convert
     * @return List of Strings
     */
    static List<String> mapJsonString(final JsonArray list)
    {
        final List<String> results = new LinkedList<String>();

        if (list == null) {
            return results;
        }

        for (final JsonString str : list.getValuesAs(JsonString.class)) {
            results.add(str.getString());
        }
        return results;
    }

    /**
     * Ensure that an answer to the question exists for the given form.
     *
     * @param form Form resource to alter
     * @param questionPath Boolean question path for the question
     * @param primaryType Answer Node type (e.g. cards:TextAnswer)
     * @param defaultValue Default value to use for the node
     */
    void ensureAnswerExists(final Resource form, final String questionPath, final String primaryType,
        final Object defaultValue)
        throws RepositoryException, PersistenceException
    {
        for (final Resource existingAnswer : form.getChildren()) {
            final Node answerNode = existingAnswer.adaptTo(Node.class);
            final Node questionNode = answerNode.getProperty(PatientLocalStorage.QUESTION_FIELD).getNode();

            if (questionPath.equals(questionNode.getPath())) {
                return;
            }
        }
        this.resolver.create(form, UUID.randomUUID().toString(), Map.of(
            PatientLocalStorage.QUESTION_FIELD,
            this.resolver.getResource(questionPath).adaptTo(Node.class),
            PatientLocalStorage.VALUE_FIELD, defaultValue,
            PatientLocalStorage.PRIMARY_TYPE, primaryType,
            PatientLocalStorage.STATUS_FIELD, STATUS_FLAGS));
    }

    /**
     * Storage class for clinic information.
     */
    class ClinicInfo
    {
        private String clinicPath;

        private String displayName;

        private String surveyId;

        public void setClinicPath(final String path)
        {
            this.clinicPath = path;
        }

        public String getClinicPath()
        {
            return this.clinicPath;
        }

        public void setDisplayName(final String name)
        {
            this.displayName = name;
        }

        public String getDisplayName()
        {
            return this.displayName;
        }

        public void setSurveyId(final String id)
        {
            this.surveyId = id;
        }

        public String getSurveyId()
        {
            return this.surveyId;
        }
    }

    /**
     * Map the given Torch clinic name to a survey ID and display name.
     *
     * @param locations The appointment locations array to parse
     * @return The clinic information as a list
     */
    List<ClinicInfo> getClinicInfo(final JsonArray locations)
    {
        final List<ClinicInfo> results = new LinkedList<>();
        for (int i = 0; i < locations.size(); i++) {
            // Resolve this name from our mapping
            final String clinic = locations.getString(i);
            // TODO: What do we do in case of a collision?
            final String mapPath = "/Survey/ClinicMapping/" + String.valueOf(clinic.hashCode());
            final Resource mapping = this.resolver.getResource(mapPath);

            if (mapping == null) {
                LOGGER.warn("Could not find mapping for location {} (checking {})", clinic, mapPath);
            } else {
                try {
                    final ClinicInfo thisClinic = new ClinicInfo();
                    final Node thisNode = mapping.adaptTo(Node.class);
                    thisClinic.setSurveyId(thisNode.getProperty("survey").getString());
                    thisClinic.setDisplayName(thisNode.getProperty("displayName").getString());
                    thisClinic.setClinicPath(mapPath);
                    results.add(thisClinic);
                } catch (final RepositoryException e) {
                    LOGGER.error("Error while resolving clinic mapping: {} {} ", clinic, e.getMessage(), e);
                }
            }
        }

        return results;
    }
}
