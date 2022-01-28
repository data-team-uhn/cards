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
package io.uhndata.cards.proms;

import java.io.IOException;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/PromsHomepage" }, extensions = {
    "validateCredentials" }, methods = { "POST" })
public class ValidateCredentialsServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = 1223548547434563162L;

    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateCredentialsServlet.class);

    private static final String DOB = "date_of_birth";

    private static final String MRN = "mrn";

    private static final String HC = "health_card";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private FormUtils formUtils;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Override
    public void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        // This only works for a token-authenticated session; refuse requests if this is not the case
        final String sessionSubjectIdentifier =
            (String) this.resolverFactory.getThreadResourceResolver().getAttribute("cards:sessionSubject");
        if (sessionSubjectIdentifier == null) {
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, "Not a valid patient session");
        }

        try (ResourceResolver rr = this.resolverFactory.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "validateCredentials"))) {
            final Session session = rr.adaptTo(Session.class);
            final Node visitSubject = session.getNodeByIdentifier(sessionSubjectIdentifier);

            // Gather all the credentials
            final List<Credential> credentials = gatherData(request, session, visitSubject);

            // Validate the credentials
            if (!validateCredentials(credentials, response)) {
                return;
            }

            writeSuccess(response, sessionSubjectIdentifier, visitSubject, session);
        } catch (final LoginException e) {
            LOGGER.error("Service authorization not granted: {}", e.getMessage());
        } catch (final RepositoryException e) {
            LOGGER.warn("Exception validating patient authentication: {}", e.getMessage(), e);
        }
    }

    private List<Credential> gatherData(final SlingHttpServletRequest request, final Session session,
        final Node visitSubject)
        throws RepositoryException
    {
        final List<Credential> credentials =
            List.of(new Credential(DOB, true), new Credential(MRN), new Credential(HC));
        final Node questionnaire = getPatientInformationQuestionnaire(session);
        for (final Credential c : credentials) {
            c.presentedValue = request.getParameter(c.name);
        }

        // Look for the patient's information in the repository
        final Node patientInformationForm = getPatientInformationForm(visitSubject, questionnaire, session);
        if (patientInformationForm == null) {
            LOGGER.warn("Patient Information not found for visit {}", visitSubject.getPath());
        } else {
            // Extract the data from the patient information form
            for (final Credential c : credentials) {
                c.storedValue = toString(this.formUtils.getValue(
                    this.formUtils.getAnswer(patientInformationForm,
                        this.questionnaireUtils.getQuestion(questionnaire, c.name))));
            }
        }

        return credentials;
    }

    private boolean validateCredentials(final List<Credential> credentials, final SlingHttpServletResponse response)
        throws IOException
    {
        // Check that the client sent all the needed information
        if (credentials.stream().filter(c -> c.presentedValue != null).count() < 2
            || credentials.stream().anyMatch(c -> c.mandatory && StringUtils.isEmpty(c.presentedValue))) {
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, "Missing authentication data");
            return false;
        }

        // If the patient information is missing, abort
        if (credentials.stream().filter(c -> c.storedValue != null).count() < 2
            || credentials.stream().anyMatch(c -> c.mandatory && StringUtils.isEmpty(c.storedValue))) {
            writeError(response, SlingHttpServletResponse.SC_UNAUTHORIZED, "Missing user data");
            return false;
        }

        // Check that the request data matches what's stored
        if (!authenticate(credentials)) {
            writeError(response, SlingHttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
            return false;
        }

        // All good!
        return true;
    }

    private boolean authenticate(final List<Credential> credentials)
    {
        return credentials.stream()
            .filter(c -> c.presentedValue != null && StringUtils.equals(c.presentedValue, c.storedValue)).count() >= 2;
    }

    private Node getPatientInformationQuestionnaire(final Session session) throws RepositoryException
    {
        return session.getNode("/Questionnaires/Patient information");
    }

    private Node getPatientInformationForm(final Node visitSubject, final Node patientInformationQuestionnaire,
        final Session session) throws RepositoryException
    {
        // Look for the patient's information in the repository
        final Node patientSubject = visitSubject.getParent();
        final PropertyIterator properties = patientSubject.getReferences("subject");
        while (properties.hasNext()) {
            final Node form = properties.nextProperty().getParent();
            if (patientInformationQuestionnaire.getIdentifier()
                .equals(this.formUtils.getQuestionnaireIdentifier(form))) {
                return form;
            }
        }
        return null;
    }

    private void writeSuccess(final SlingHttpServletResponse response, final String sessionSubjectIdentifier,
        final Node visitSubject, final Session session)
        throws IOException, RepositoryException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(SlingHttpServletResponse.SC_OK);
        final Node patientInformationQuestionnaire = getPatientInformationQuestionnaire(session);

        final Node form = getPatientInformationForm(visitSubject, patientInformationQuestionnaire, session);
        try (Writer out = response.getWriter()) {
            final JsonObjectBuilder result = Json.createObjectBuilder();
            result.add("status", "success");
            result.add("sessionSubject", sessionSubjectIdentifier);
            final JsonObjectBuilder patientInformation = Json.createObjectBuilder();
            for (final var name : List.of("first_name", "last_name")) {
                final Object value = this.formUtils.getValue(this.formUtils.getAnswer(form,
                    this.questionnaireUtils.getQuestion(patientInformationQuestionnaire, name)));
                patientInformation.add(name,
                    value == null ? JsonValue.NULL : Json.createValue(String.valueOf(value)));
            }
            result.add("patientInformation", patientInformation.build());
            out.append(result.build().toString());
        }
    }

    private void writeError(final SlingHttpServletResponse response, final int statusCode, final String errorMessage)
        throws IOException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(statusCode);
        try (Writer out = response.getWriter()) {
            out.append("{\"status\":\"error\",\"error\": \"" + errorMessage + "\"}");
        }
    }

    private String toString(final Object value)
    {
        if (value == null) {
            return null;
        } else if (value instanceof Calendar) {
            return formatDate((Calendar) value);
        } else {
            return String.valueOf(value);
        }
    }

    private String formatDate(final Calendar date)
    {
        return DateTimeFormatter.ISO_LOCAL_DATE
            .format(OffsetDateTime.ofInstant(date.toInstant(), date.getTimeZone().toZoneId()));
    }

    private static final class Credential
    {
        private final String name;

        private final boolean mandatory;

        private String presentedValue;

        private String storedValue;

        Credential(final String name)
        {
            this(name, false);
        }

        Credential(final String name, final boolean mandatory)
        {
            this.name = name;
            this.mandatory = mandatory;
        }
    }
}
