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

package io.uhndata.cards.patients.emailnotifications;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumSet;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;

@SuppressWarnings("MultipleStringLiterals")
public final class AppointmentUtils
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentUtils.class);

    /** The primary node type for a Subject, an entity about which data is collected. */
    private static final String TEXT_ANSWER = "cards:TextAnswer";

    private static final String EMPTY = "";

    private static final String TOKEN_LIFETIME = "tokenLifetime";

    // Hide the utility class constructor
    private AppointmentUtils()
    {
    }

    /**
     * Returns the first answer found for a given query of a CARDS Subject and a JCR Path to a cards:Question node.
     * Returns a default value if no such match is found.
     *
     * @param <T> the Java type of the value to be returned (eg. String, long, etc...)
     * @param formUtils form utilities service
     * @param subject the CARDS Subject JCR Resource to search for an answer for
     * @param questionPath the question for which an answer is sought for
     * @param cardsDataType the CARDS data type of the expected answer (eg. cards:TextAnswer)
     * @param defaultValue the default value to return if the query can't find a match
     * @return the value of a Subject's response to a question or a default value
     */
    public static <T> T getQuestionAnswerForSubject(
        FormUtils formUtils, Node subject, String questionPath, String cardsDataType, T defaultValue)
    {
        try {
            final Session session = subject.getSession();
            final Node question = session.getNode(questionPath);
            Collection<Node> answers =
                formUtils.findAllSubjectRelatedAnswers(subject, question, EnumSet.allOf(FormUtils.SearchType.class));
            if (!answers.isEmpty()) {
                final Node answer = answers.iterator().next();
                @SuppressWarnings("unchecked")
                T answerVal = (T) formUtils.getValue(answer);
                if (Number.class.isAssignableFrom(defaultValue.getClass()) && answerVal == null) {
                    return defaultValue;
                }
                return answerVal;
            }
        } catch (RepositoryException e) {
            // TODO a
        }
        return defaultValue;
    }

    /**
     * Returns the email address for the CARDS Patient Subject provided that a valid email address has been provided and
     * the patient has consented to email communication. Otherwise, null is returned.
     *
     * @param formUtils form utilities service
     * @param patientSubject the JCR Resource for the patient whose email we wish to obtain
     * @param visitSubject the visit that we're sending emails for
     * @return the patient's email address or null
     */
    public static String getPatientConsentedEmail(FormUtils formUtils, Node patientSubject, Node visitSubject)
    {
        try {
            final Session session = patientSubject.getSession();

            long patientEmailUnsubscribed = getQuestionAnswerForSubject(
                formUtils,
                patientSubject,
                "/Questionnaires/Patient information/email_unsubscribed",
                "cards:BooleanAnswer",
                0L);
            Node visitClinic = session.getNode(getQuestionAnswerForSubject(formUtils, visitSubject,
                "/Questionnaires/Visit information/clinic", TEXT_ANSWER, EMPTY));

            if (visitClinic == null) {
                return null;
            }

            boolean ignoreEmailConsent = false;
            if (visitClinic.hasProperty("ignoreEmailConsent")) {
                ignoreEmailConsent = visitClinic.getProperty("ignoreEmailConsent").getBoolean();
            }

            long patientEmailOk = getQuestionAnswerForSubject(
                formUtils,
                patientSubject,
                "/Questionnaires/Patient information/email_ok",
                "cards:BooleanAnswer",
                0L);
            String patientEmailAddress = getQuestionAnswerForSubject(
                formUtils,
                patientSubject,
                "/Questionnaires/Patient information/email",
                TEXT_ANSWER,
                EMPTY);
            if (patientEmailUnsubscribed != 1 && (ignoreEmailConsent || patientEmailOk == 1)) {
                if (EmailValidator.getInstance().isValid(patientEmailAddress)) {
                    return patientEmailAddress;
                }
            }
        } catch (RepositoryException e) {
            // TODO
        }
        return null;
    }

    /**
     * Returns a patient subject's full name (first + last).
     *
     * @param formUtils form utilities service
     * @param patientSubject the JCR Resource for the patient whose full name we wish to obtain
     * @return the patient's first name + last name
     */
    public static String getPatientFullName(FormUtils formUtils, Node patientSubject)
    {
        String firstName = getQuestionAnswerForSubject(
            formUtils,
            patientSubject,
            "/Questionnaires/Patient information/first_name",
            TEXT_ANSWER,
            EMPTY);
        String lastName = getQuestionAnswerForSubject(
            formUtils,
            patientSubject,
            "/Questionnaires/Patient information/last_name",
            TEXT_ANSWER,
            EMPTY);
        return firstName + " " + lastName;
    }

    /**
     * Returns the cards:QuestionnaireSet JCR Resource associated with the formRelatedSubject or null if no such
     * associated Resource can be found.
     *
     * @param formUtils form utilities service
     * @param formRelatedSubject the JCR Subject Resource for which the Clinic is associated with
     * @param clinicIdLink the question linking the Subject to a clinic (eg. /Questionnaires/Visit information/surveys)
     * @return the associated cards:QuestionnaireSet JCR Resource or null
     */
    public static Node getValidClinicNode(FormUtils formUtils, Node formRelatedSubject,
        String clinicIdLink)
    {
        String clinicNodePath = getQuestionAnswerForSubject(
            formUtils,
            formRelatedSubject,
            clinicIdLink,
            TEXT_ANSWER,
            EMPTY);

        if (EMPTY.equals(clinicNodePath)) {
            return null;
        }

        try {
            return formRelatedSubject.getSession().getNode(clinicNodePath);
        } catch (RepositoryException e) {
            // TODO Auto-generated catch block
        }
        return null;
    }

    /**
     * Returns the email address associated (through the clinicEmailProperty String) with the clinic linked to the
     * formRelatedSubject Resource or null if it cannot be found.
     *
     * @param formUtils form utilities service
     * @param formRelatedSubject the JCR Subject Resource for which the Clinic is associated with
     * @param clinicIdLink the question linking the Subject to a clinic (eg. /Questionnaires/Visit information/clinic)
     * @param clinicEmailProperty the JCR node property holding the email address (eg. "emergencyContact")
     * @return the contact email address associated with a subject
     */
    public static String getValidClinicEmail(FormUtils formUtils, Node formRelatedSubject,
        String clinicIdLink, String clinicEmailProperty)
    {
        Node clinicNode = getValidClinicNode(formUtils, formRelatedSubject, clinicIdLink);
        if (clinicNode == null) {
            return null;
        }
        try {
            String clinicEmail = clinicNode.getProperty(clinicEmailProperty).getString();
            if (!EMPTY.equals(clinicEmail)) {
                return clinicEmail;
            }
        } catch (RepositoryException e) {
            // TODO Auto-generated catch block
        }
        return null;
    }

    /**
     * Returns the token lifetime associated (through the clinicEmailProperty String) with the clinic linked to the
     * formRelatedSubject Resource or default if it cannot be found.
     *
     * @param formUtils form utilities service
     * @param formRelatedSubject the JCR Subject Resource for which the Clinic is associated with
     * @param clinicIdLink the question linking the Subject to a clinic (eg. /Questionnaires/Visit information/clinic)
     * @param defaultLifetime the default to return
     * @return the token lifetime in days
     */
    public static int getTokenLifetime(FormUtils formUtils, Node formRelatedSubject,
        String clinicIdLink, int defaultLifetime)
    {
        Node clinicNode = getValidClinicNode(formUtils, formRelatedSubject, clinicIdLink);
        if (clinicNode == null) {
            return defaultLifetime;
        }
        try {
            if (clinicNode.hasProperty(TOKEN_LIFETIME)) {
                return (int) clinicNode.getProperty(TOKEN_LIFETIME).getLong();
            }
        } catch (RepositoryException e) {
            // TODO Auto-generated catch block
        }
        return defaultLifetime;
    }

    /**
     * Returns the completion status of surveys for a Visit.
     *
     * @param formUtils form utilities service
     * @param visitSubject the JCR Resource for the visit whose survey completion status we wish to obtain
     * @return the boolean survey completion status for the visit
     */
    public static boolean getVisitSurveysComplete(FormUtils formUtils, Node visitSubject)
    {
        long isComplete = getQuestionAnswerForSubject(
            formUtils,
            visitSubject,
            "/Questionnaires/Visit information/surveys_complete",
            "cards:BooleanAnswer",
            0L);
        if (isComplete == 1) {
            return true;
        }
        return false;
    }

    /**
     * Finds all appointments scheduled for a given day for all clinics.
     *
     * @param session a valid JCR session
     * @param dateToQuery the Java Calendar object for the day to query for appointments
     * @return an Iterator of cards:DateAnswer Resources representing the scheduled visits
     */
    public static NodeIterator getAppointmentsForDay(Session session, Calendar dateToQuery)
    {
        return getAppointmentsForDay(session, dateToQuery, null);
    }

    /**
     * Finds all appointments scheduled for a given day for a given clinic.
     *
     * @param session a valid JCR session
     * @param dateToQuery the Java Calendar object for the day to query for appointments
     * @param clinicId the clinic indentifier recorded in the Visit information form - ensure that it matches.
     * @return an Iterator of cards:DateAnswer Resources representing the scheduled visits
     */
    public static NodeIterator getAppointmentsForDay(Session session, Calendar dateToQuery, String clinicId)
    {
        try {
            final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            final Node visitTimeResult = session.getNode("/Questionnaires/Visit information/time");
            final String visitTimeUUID = visitTimeResult.getIdentifier();
            final String statusUUID =
                session.getNode("/Questionnaires/Visit information/status").getIdentifier();
            final String hasSurveysUUID =
                session.getNode("/Questionnaires/Visit information/has_surveys").getIdentifier();
            final String clinicUUID =
                session.getNode("/Questionnaires/Visit information/clinic").getIdentifier();
            final Calendar lowerBoundDate = (Calendar) dateToQuery.clone();
            lowerBoundDate.set(Calendar.HOUR_OF_DAY, 0);
            lowerBoundDate.set(Calendar.MINUTE, 0);
            lowerBoundDate.set(Calendar.SECOND, 0);
            lowerBoundDate.set(Calendar.MILLISECOND, 0);
            final Calendar upperBoundDate = (Calendar) lowerBoundDate.clone();
            upperBoundDate.add(Calendar.HOUR, 24);
            LOGGER.info("Querying for appointments for clinic {} between {} and {}.",
                clinicId,
                formatter.format(lowerBoundDate.getTime()),
                formatter.format(upperBoundDate.getTime()));

            final String query = "SELECT vdate.* FROM [cards:DateAnswer] AS vdate "
                + "  INNER JOIN [cards:TextAnswer] AS vstatus ON vstatus.form = vdate.form "
                + "  INNER JOIN [cards:BooleanAnswer] AS has_surveys ON has_surveys.form = vdate.form "
                + ((clinicId != null)
                    ? "  INNER JOIN [cards:ResourceAnswer] AS clinic ON clinic.form = vdate.form " : "")
                + "WHERE vdate.'question'='" + visitTimeUUID + "' "
                + "  AND vdate.'value' >= cast('" + formatter.format(lowerBoundDate.getTime()) + "' AS date)"
                + "  AND vdate.'value' < cast('" + formatter.format(upperBoundDate.getTime()) + "' AS date)"
                + "  AND vstatus.'question' = '" + statusUUID + "' "
                + "  AND vstatus.'value' <> 'cancelled'"
                + "  AND vstatus.'value' <> 'entered-in-error'"
                + "  AND has_surveys.'question' = '" + hasSurveysUUID + "' "
                + "  AND has_surveys.'value' = 1 "
                + ((clinicId != null)
                    ? ("  AND clinic.'question' = '" + clinicUUID + "' AND clinic.'value' = '" + clinicId + "'") : "")
                + " OPTION (INDEX TAG cards)";

            return session.getWorkspace().getQueryManager().createQuery(query, "JCR-SQL2").execute().getNodes();
        } catch (RepositoryException e) {
            return EmptyNodeIterator.INSTANCE;
        }
    }

    /**
     * A node iterator that is always empty.
     */
    private static final class EmptyNodeIterator implements NodeIterator
    {
        private static final NodeIterator INSTANCE = new EmptyNodeIterator();

        @Override
        public Object next()
        {
            return null;
        }

        @Override
        public boolean hasNext()
        {
            return false;
        }

        @Override
        public void skip(long skipNum)
        {
        }

        @Override
        public long getSize()
        {
            return 0;
        }

        @Override
        public long getPosition()
        {
            return 0;
        }

        @Override
        public Node nextNode()
        {
            return null;
        }
    }
}
