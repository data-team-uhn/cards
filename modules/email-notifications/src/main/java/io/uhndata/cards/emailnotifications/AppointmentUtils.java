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

package io.uhndata.cards.emailnotifications;

import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Iterator;

import org.apache.commons.validator.routines.EmailValidator;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppointmentUtils
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentUtils.class);

    // Hide the utility class constructor
    private AppointmentUtils()
    {
    }

    public static Resource getSubjectNode(ResourceResolver resolver, String jcrUuid)
    {
        Iterator<Resource> results;
        results = resolver.findResources(
            "SELECT * FROM [cards:Subject] as s WHERE s.'jcr:uuid'='" + jcrUuid + "'", "JCR-SQL2");
        if (results.hasNext()) {
            return results.next();
        } else {
            return null;
        }
    }

    public static Resource getRelatedSubjectOfType(ResourceResolver resolver, Resource formResource,
        String subjectTypePath)
    {
        final String patientTypeUuid = resolver.getResource(subjectTypePath).getValueMap().get("jcr:uuid", "");
        final String[] relatedSubjects = formResource.getValueMap().get("relatedSubjects", String[].class);
        for (int i = 0; i < relatedSubjects.length; i++) {
            Resource tmpSubject = getSubjectNode(resolver, relatedSubjects[i]);
            if (tmpSubject == null) {
                continue;
            }
            if (patientTypeUuid.equals(tmpSubject.getValueMap().get("type", ""))) {
                return tmpSubject;
            }
        }
        return null;
    }

    public static <T> T getQuestionAnswerForSubject(
        ResourceResolver resolver, Resource subject, String questionPath, String cardsDataType, T defaultValue)
    {
        final String subjectUUID = subject.getValueMap().get("jcr:uuid", "");
        final String questionUUID = resolver.getResource(questionPath).getValueMap().get("jcr:uuid", "");
        if ("".equals(questionUUID)) {
            return defaultValue;
        }
        Iterator<Resource> results;
        results = resolver.findResources(
            "SELECT t.* FROM [" + cardsDataType + "] as t INNER JOIN [cards:Form] AS f ON isdescendantnode(t, f)"
            + " WHERE t.'question'='" + questionUUID + "'"
            + " AND f.'subject'='" + subjectUUID + "'",
            "JCR-SQL2");
        if (results.hasNext()) {
            T answerVal = results.next().getValueMap().get("value", defaultValue);
            return answerVal;
        }
        return defaultValue;
    }

    public static String getPatientConsentedEmail(ResourceResolver resolver, Resource patientSubject)
    {
        long patientEmailOk = getQuestionAnswerForSubject(
            resolver,
            patientSubject,
            "/Questionnaires/Patient information/email_ok",
            "cards:BooleanAnswer",
            0
        );
        String patientEmailAddress = getQuestionAnswerForSubject(
            resolver,
            patientSubject,
            "/Questionnaires/Patient information/email",
            "cards:TextAnswer",
            ""
        );
        if (patientEmailOk == 1) {
            if (EmailValidator.getInstance().isValid(patientEmailAddress)) {
                return patientEmailAddress;
            }
        }
        return null;
    }
    public static String getPatientFullName(ResourceResolver resolver, Resource patientSubject)
    {
        String firstName = getQuestionAnswerForSubject(
            resolver,
            patientSubject,
            "/Questionnaires/Patient information/first_name",
            "cards:TextAnswer",
            ""
        );
        String lastName = getQuestionAnswerForSubject(
            resolver,
            patientSubject,
            "/Questionnaires/Patient information/last_name",
            "cards:TextAnswer",
            ""
        );
        return firstName + " " + lastName;
    }

    public static Resource getFormForAnswer(ResourceResolver resolver, Resource answerResource)
    {
        String answerPath = answerResource.getPath();
        String[] answerPathArray = answerPath.split("/");
        if (answerPathArray.length < 3) {
            return null;
        }
        if (!"".equals(answerPathArray[0])) {
            return null;
        }
        if (!"Forms".equals(answerPathArray[1])) {
            return null;
        }
        return resolver.getResource("/Forms/" + answerPathArray[2]);
    }

    public static Calendar parseDate(final String str)
    {
        try {
            final ZonedDateTime zdt = ZonedDateTime.parse(str);
            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(zdt.toInstant().toEpochMilli());
            return calendar;
        } catch (final DateTimeParseException e) {
            LOGGER.warn("PARSING DATE FAILED: Invalid date {}", str);
            return null;
        }
    }

    public static Iterator<Resource> getAppointmentsForDay(ResourceResolver resolver, Calendar dateToQuery)
    {
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        final Resource visitTimeResult = resolver.getResource("/Questionnaires/Visit information/time");
        final String visitTimeUUID = visitTimeResult.getValueMap().get("jcr:uuid", "");
        final Calendar lowerBoundDate = (Calendar) dateToQuery.clone();
        lowerBoundDate.set(Calendar.HOUR_OF_DAY, 0);
        lowerBoundDate.set(Calendar.MINUTE, 0);
        lowerBoundDate.set(Calendar.SECOND, 0);
        lowerBoundDate.set(Calendar.MILLISECOND, 0);
        final Calendar upperBoundDate = (Calendar) lowerBoundDate.clone();
        upperBoundDate.add(Calendar.HOUR, 24);
        LOGGER.warn("Querying for {}", formatter.format(dateToQuery.getTime()));
        LOGGER.warn("Lower bound: {}", formatter.format(lowerBoundDate.getTime()));
        LOGGER.warn("Upper bound: {}", formatter.format(upperBoundDate.getTime()));
        final Iterator<Resource> results = resolver.findResources(
            "SELECT d.* FROM [cards:DateAnswer] AS d INNER JOIN [cards:Form] AS f ON"
            + " isdescendantnode(d, f) WHERE d.'question'='" + visitTimeUUID + "'"
            + " AND d.'value' >= cast('" + formatter.format(lowerBoundDate.getTime()) + "' AS date)"
            + " AND d.'value' < cast('" + formatter.format(upperBoundDate.getTime()) + "' AS date)",
            "JCR-SQL2"
        );
        return results;
    }
}
