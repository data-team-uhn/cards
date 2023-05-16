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
package io.uhndata.cards.patients;

import java.io.IOException;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.servlet.Servlet;
import javax.servlet.http.Cookie;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.auth.token.TokenManager;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.patients.api.PatientAccessConfiguration;
import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

@Component(service = { Servlet.class }, property = { "sling.auth.requirements=-/Survey" })
@SlingServletResourceTypes(resourceTypes = { "cards/PatientHomepage" }, extensions = {
    "validateCredentials" }, methods = { "POST" })

@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class ValidateCredentialsServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = 1223548547434563162L;

    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateCredentialsServlet.class);

    private static final String DOB = "date_of_birth";

    private static final String MRN = "mrn";

    private static final String HC = "health_card";

    private static final String VISIT = "visit";

    private static final String VALUE = "value";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectTypeUtils subjectTypeUtils;

    @Reference
    private SubjectUtils subjectUtils;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private TokenManager tokenManager;

    @Reference
    private PatientAccessConfiguration patientAccessConfiguration;

    @Override
    public void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        try (ResourceResolver rr = this.resolverFactory.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "validateCredentials"))) {
            final Session session = rr.adaptTo(Session.class);

            // Check if this is a token-authenticated session
            String sessionSubjectIdentifier =
                (String) this.resolverFactory.getThreadResourceResolver().getAttribute("cards:sessionSubject");

            // Check if the current token is a temporary terms of use token.
            // If so, discard this token as it should only be used for checking the patients terms of use version
            if (sessionSubjectIdentifier != null
                && "Patient".equals(this.subjectTypeUtils.getLabel(this.subjectUtils.getType(
                    this.subjectUtils.getSubject(sessionSubjectIdentifier))))) {
                // By default, existing auth tokens are recreated by TokenAuthenticationHandler.
                // Reset the response to clear the old terms of use token
                response.reset();
                sessionSubjectIdentifier = null;
            }

            if (sessionSubjectIdentifier == null) {
                // Not a token authenticated session. Verify based on provided patient details
                handleTokenlessAuthentication(request, response, session, rr);
            } else {
                handleTokenAuthentication(request, response, session, sessionSubjectIdentifier);
            }
        } catch (final LoginException e) {
            LOGGER.error("Service authorization not granted: {}", e.getMessage());
        } catch (final RepositoryException e) {
            LOGGER.warn("Exception validating patient authentication: {}", e.getMessage(), e);
        }
    }

    private void handleTokenlessAuthentication(final SlingHttpServletRequest request,
        final SlingHttpServletResponse response, final Session session, final ResourceResolver rr)
        throws IOException, RepositoryException
    {
        Node patientInformationForm = findMatchingPatientInformation(request, session, rr);
        final Node patientSubject = this.formUtils.getSubject(patientInformationForm);

        if (patientSubject == null || !this.patientAccessConfiguration.isTokenlessAuthEnabled()) {
            writeInvalidCredentialsError(response);
            return;
        }

        final List<Node> validVisitForms = getVisitForms(request, session, rr, patientSubject, patientInformationForm);

        if (validVisitForms.size() == 1) {
            Node visitSubject = this.formUtils.getSubject(validVisitForms.get(0));
            String visitSubjectPath = visitSubject.getPath();

            generateAndApplyToken(request, response, "guest-patient", visitSubjectPath);
            writeSuccess(response, visitSubjectPath, visitSubject, session, false);
        } else {
            // Patient has zero or multiple possible visits: Send back to the client to continue
            Node visitQuestionnaire = getVisitInformationQuestionnaire(session);
            Node visitLocationQuestion = this.questionnaireUtils.getQuestion(visitQuestionnaire, "location");

            generateAndApplyToken(request, response, "tou-patient", patientSubject.getPath());
            writeVisitSelection(response, validVisitForms, visitLocationQuestion);
        }
    }

    private void generateAndApplyToken(final SlingHttpServletRequest request,
        final SlingHttpServletResponse response, String tokenUser, String subjectPath)
    {
        // Generate token
        Calendar tokenExpiryDate = Calendar.getInstance();
        tokenExpiryDate.add(Calendar.HOUR_OF_DAY, 1);
        final String token = this.tokenManager.create(
            tokenUser,
            tokenExpiryDate,
            Collections.singletonMap("cards:sessionSubject", subjectPath))
            .getToken();

        // Apply token
        // Cookie should expire in 1 hour when the token expires, so 60 * 60 = 3600 seconds
        createCookie(request, response, token, 3600);
    }

    private void createCookie(final SlingHttpServletRequest request,
        final SlingHttpServletResponse response, final String value, final int maxAgeSeconds)
    {
        final Cookie cookie = new Cookie("cards_auth_token", value);
        final String ctxPath = request.getContextPath();
        final String cookiePath = (ctxPath == null || ctxPath.length() == 0) ? "/" : ctxPath;
        cookie.setPath(cookiePath);
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAgeSeconds);
        response.addCookie(cookie);

    }

    private void handleTokenAuthentication(final SlingHttpServletRequest request,
        final SlingHttpServletResponse response, final Session session, String sessionSubjectIdentifier)
        throws IOException, RepositoryException
    {
        Node visitSubject = null;
        try {
            visitSubject = session.getNodeByIdentifier(sessionSubjectIdentifier);
        } catch (ItemNotFoundException e) {
            writeError(response, SlingHttpServletResponse.SC_UNAUTHORIZED, "Visit not found");
        }

        if (this.patientAccessConfiguration.isPatientIdentificationRequired()) {
            // Look for the patient's information in the repository
            final Node patientInformationQuestionniare = getPatientInformationQuestionnaire(session);
            final Node patientInformationForm = getPatientInformationForm(visitSubject,
                patientInformationQuestionniare, session);
            if (patientInformationForm == null) {
                LOGGER.warn("Patient Information not found for visit {}", visitSubject.getPath());
                writeError(response, SlingHttpServletResponse.SC_UNAUTHORIZED, "Missing user data");
                return;
            }

            if (!validatePatient(request, response, session,
                patientInformationForm, patientInformationQuestionniare)) {
                return;
            }
        }

        writeSuccess(response, sessionSubjectIdentifier, visitSubject, session, true);
    }

    private Node findMatchingPatientInformation(final SlingHttpServletRequest request, final Session session,
        final ResourceResolver rr)
        throws IOException, RepositoryException
    {
        String presentedMRN = request.getParameter(MRN);
        Boolean mrnProvided = presentedMRN != null && !"undefined".equals(presentedMRN) && !"".equals(presentedMRN);
        String presentedID = mrnProvided ? presentedMRN : request.getParameter(HC);

        Node patientQuestionnaire = getPatientInformationQuestionnaire(session);
        String identifierQuestion = this.questionnaireUtils.getQuestion(patientQuestionnaire,
            (mrnProvided ? MRN : HC)).getIdentifier();

        // Find patients matching the provided ID
        final Iterator<Resource> results = rr.findResources(
            "SELECT f.* FROM [cards:TextAnswer] AS t "
                + "  INNER JOIN [cards:Form] AS f ON t.form = f.[jcr:uuid] "
                + "WHERE t.'question'='" + identifierQuestion + "' "
                + "  AND t.'value'='" + presentedID.replaceAll("'", "''") + "'",
            "JCR-SQL2");
        while (results.hasNext()) {
            Node patientInformationForm = results.next().adaptTo(Node.class);

            // Check if the DOB matches
            if (validatePatient(request, null, session,
                patientInformationForm, patientQuestionnaire))
            {
                return patientInformationForm;
            }
        }

        return null;
    }

    List<Node> getVisitForms(final SlingHttpServletRequest request, final Session session,
        final ResourceResolver rr, final Node patientSubject, final Node patientInformationForm)
        throws RepositoryException
    {
        final List<Node> validVisitForms = new ArrayList<Node>();

        // Get the list of user selected visits, or upcoming visits if none selected yet
        String presentedVisit = request.getParameter(VISIT);
        if (presentedVisit != null && presentedVisit.startsWith(patientSubject.getPath())) {
            // User has selected a visit, and this visit is for the validated patient
            Node visitSubject = rr.getResource(presentedVisit).adaptTo(Node.class);
            Node visitInformationForm = getVisitInformationForm(session, visitSubject);

            if (isVisitUpcoming(session, visitInformationForm)) {
                validVisitForms.add(visitInformationForm);
            }
        } else if (patientSubject == null) {
            LOGGER.warn("Patient not found for form {}", patientInformationForm.getPath());
        } else {
            final NodeIterator visits = patientSubject.getNodes();
            while (visits.hasNext()) {
                Node visitSubject = visits.nextNode();
                Node visitInformationForm = getVisitInformationForm(session, visitSubject);
                if (isVisitUpcoming(session, visitInformationForm)) {
                    validVisitForms.add(visitInformationForm);
                }
            }
        }

        return validVisitForms;
    }

    private boolean validatePatient(final SlingHttpServletRequest request,
        final SlingHttpServletResponse response, final Session session,
        final Node patientInformationForm, Node patientInformationQuestionnaire)
        throws IOException, RepositoryException
    {
        // Gather all the credentials
        final List<Credential> credentials = gatherData(request, session,
            patientInformationForm, patientInformationQuestionnaire);

        // Validate the credentials
        return validateCredentials(credentials, response);
    }

    private List<Credential> gatherData(final SlingHttpServletRequest request, final Session session,
        final Node patientInformationForm, Node patientInformationQuestionnaire)
        throws RepositoryException
    {
        final List<Credential> credentials =
            List.of(new Credential(DOB, true), new Credential(MRN), new Credential(HC));
        for (final Credential c : credentials) {
            c.presentedValue = request.getParameter(c.name);
        }

        // Extract the data from the patient information form
        for (final Credential c : credentials) {
            c.storedValue = toString(this.formUtils.getValue(
                this.formUtils.getAnswer(patientInformationForm,
                    this.questionnaireUtils.getQuestion(patientInformationQuestionnaire, c.name))));
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
            writeInvalidCredentialsError(response);
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

    private Node getVisitInformationQuestionnaire(final Session session) throws RepositoryException
    {
        return session.getNode("/Questionnaires/Visit information");
    }

    private Node getVisitInformationForm(final Session session, Node visitSubject) throws RepositoryException
    {
        Node visitQuestionnaire = getVisitInformationQuestionnaire(session);

        final PropertyIterator references = visitSubject.getReferences();
        while (references.hasNext()) {
            Property reference = references.nextProperty();
            Node form = reference.getParent();

            if (this.formUtils.isForm(form)
                && visitQuestionnaire.getIdentifier().equals(this.formUtils.getQuestionnaireIdentifier(form))) {
                return form;
            }
        }

        return null;
    }

    @SuppressWarnings({ "checkstyle:CyclomaticComplexity", "checkstyle:NPathComplexity" })
    private boolean isVisitUpcoming(final Session session, Node visitInformationForm) throws RepositoryException
    {
        Node visitQuestionnaire = getVisitInformationQuestionnaire(session);
        Map<String, Predicate<Property>> tests = Map.of(
            // Verify clinic set
            "clinic", p -> true,

            // Verify visit is upcoming
            "time", p -> {
                try {
                    return Calendar.getInstance().before(p.getDate());
                } catch (RepositoryException e) {
                    return false;
                }
            },

            // Verify visit hasn't been cancelled or entered in error
            "status", p -> {
                try {
                    return !("cancelled".equals(p.getString())
                        || "entered-in-error".equals(p.getString()));
                } catch (RepositoryException e) {
                    return false;
                }
            },

            // Verify visit has surveys
            "has_surveys", p -> {
                try {
                    return 0 != p.getLong();
                } catch (RepositoryException e) {
                    return false;
                }
            },

            // Verify visit surveys haven't been submitted
            "!surveys_submitted", p -> {
                try {
                    return 1 != p.getLong();
                } catch (RepositoryException e) {
                    return false;
                }
            });

        return tests.entrySet().stream().allMatch(e -> {
            boolean mustBePresent = !StringUtils.startsWith(e.getKey(), "!");
            Node question =
                this.questionnaireUtils.getQuestion(visitQuestionnaire, StringUtils.removeStart(e.getKey(), "!"));
            Node answer = this.formUtils.getAnswer(visitInformationForm, question);
            try {
                if (!mustBePresent && (answer == null || !answer.hasProperty(VALUE))) {
                    return true;
                }
                return (answer != null && answer.hasProperty(VALUE) && e.getValue().test(answer.getProperty(VALUE)));
            } catch (RepositoryException e1) {
                return false;
            }
        });
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
        final Node visitSubject, final Session session, final boolean includePatientInformation)
        throws IOException, RepositoryException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(SlingHttpServletResponse.SC_OK);
        final Node patientInformationQuestionnaire = getPatientInformationQuestionnaire(session);

        try (Writer out = response.getWriter()) {
            final JsonObjectBuilder result = Json.createObjectBuilder();
            result.add("status", "success");
            result.add("sessionSubject", sessionSubjectIdentifier);
            final JsonObjectBuilder patientInformation = Json.createObjectBuilder();
            if (includePatientInformation) {
                final Node form = getPatientInformationForm(visitSubject, patientInformationQuestionnaire, session);
                for (final var name : List.of("first_name", "last_name")) {
                    final Object value = this.formUtils.getValue(this.formUtils.getAnswer(form,
                        this.questionnaireUtils.getQuestion(patientInformationQuestionnaire, name)));
                    patientInformation.add(name,
                        value == null ? JsonValue.NULL : Json.createValue(String.valueOf(value)));
                }
            }
            result.add("patientInformation", patientInformation.build());
            out.append(result.build().toString());
        }
    }

    private void writeInvalidCredentialsError(final SlingHttpServletResponse response)
        throws IOException
    {
        writeError(response, SlingHttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
    }

    private void writeError(final SlingHttpServletResponse response, final int statusCode, final String errorMessage)
        throws IOException
    {
        if (response != null) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(statusCode);
            try (Writer out = response.getWriter()) {
                out.append("{\"status\":\"error\",\"error\": \"" + errorMessage + "\"}");
            }
        }
    }

    private void writeVisitSelection(final SlingHttpServletResponse response, final List<Node> visits,
        final Node visitLocationQuestion)
        throws IOException, RepositoryException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
        try (Writer out = response.getWriter()) {
            final JsonObjectBuilder result = Json.createObjectBuilder();
            result.add("status", "needsVisit");
            final JsonArrayBuilder visitsArray = Json.createArrayBuilder();
            for (final Node visit : visits) {
                final JsonObjectBuilder visitJson = Json.createObjectBuilder();
                visitJson.add("subject", visit.getProperty("subject").getNode().getPath());
                Node visitLocationAnswer = this.formUtils.getAnswer(visit, visitLocationQuestion);
                if (visitLocationAnswer != null && visitLocationAnswer.hasProperty(VALUE)) {
                    visitJson.add("location", visitLocationAnswer.getProperty("value").getString());
                }
                visitsArray.add(visitJson);
            }
            result.add("visits", visitsArray);
            out.append(result.build().toString());
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
