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
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
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

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/PromsHomepage" }, extensions = {
    "termsOfUse" }, methods = { "GET", "POST" })
public class TermsOfUseServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -5555906093850253193L;

    private static final Logger LOGGER = LoggerFactory.getLogger(TermsOfUseServlet.class);

    private static final String TOU = "tou_accepted";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private FormUtils formUtils;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        // This only works for a token-authenticated session; refuse requests if this is not the case
        final String sessionSubjectIdentifier =
            (String) this.resolverFactory.getThreadResourceResolver().getAttribute("cards:sessionSubject");
        if (sessionSubjectIdentifier == null) {
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, "Not a valid patient session");
            return;
        }

        try (ResourceResolver rr = this.resolverFactory.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "tou"))) {
            final Session session = rr.adaptTo(Session.class);
            final Node visitSubject = session.getNodeByIdentifier(sessionSubjectIdentifier);
            final Node patientInformationQuestionnaire = getPatientInformationQuestionnaire(session);
            final Node patientInformationForm =
                getPatientInformationForm(visitSubject, patientInformationQuestionnaire, session);
            final Node touQuestion = this.questionnaireUtils.getQuestion(patientInformationQuestionnaire, TOU);
            final Node touAnswer = this.formUtils.getAnswer(patientInformationForm, touQuestion);
            final String value = (String) this.formUtils.getValue(touAnswer);
            writeCurrentTouVersion(response, value);
        } catch (final LoginException e) {
            LOGGER.error("Service authorization not granted: {}", e.getMessage());
        } catch (final RepositoryException e) {
            LOGGER.warn("Exception validating patient authentication: {}", e.getMessage(), e);
        }
    }

    @Override
    public void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        // This only works for a token-authenticated session; refuse requests if this is not the case
        final String sessionSubjectIdentifier =
            (String) this.resolverFactory.getThreadResourceResolver().getAttribute("cards:sessionSubject");
        if (sessionSubjectIdentifier == null) {
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST, "Not a valid patient session");
            return;
        }

        final String touVersionAccepted = request.getParameter(TOU);
        if (StringUtils.isBlank(touVersionAccepted)) {
            writeError(response, SlingHttpServletResponse.SC_BAD_REQUEST,
                "Must specify the version of Terms of Use accepted");
            return;
        }

        try (ResourceResolver rr = this.resolverFactory.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "tou"))) {
            final Session session = rr.adaptTo(Session.class);
            final Node subject = session.getNodeByIdentifier(sessionSubjectIdentifier);
            final Node patientInformationQuestionnaire = getPatientInformationQuestionnaire(session);
            final Node patientInformationForm =
                getPatientInformationForm(subject, patientInformationQuestionnaire, session);
            if (patientInformationForm == null) {
                writeError(response, SlingHttpServletResponse.SC_CONFLICT, "Sorry, cannot record your answer");
                return;
            }

            final VersionManager versionManager = session.getWorkspace().getVersionManager();
            final boolean checkin = !versionManager.isCheckedOut(patientInformationForm.getPath());
            versionManager.checkout(patientInformationForm.getPath());

            final Node touQuestion = this.questionnaireUtils.getQuestion(patientInformationQuestionnaire, TOU);
            Node touAnswer = this.formUtils.getAnswer(patientInformationForm, touQuestion);
            if (touAnswer == null && patientInformationForm != null) {
                touAnswer = patientInformationForm.addNode(UUID.randomUUID().toString(), "cards:TextAnswer");
                touAnswer.setProperty("question", touQuestion);
            }
            touAnswer.setProperty(FormUtils.VALUE_PROPERTY, touVersionAccepted);
            session.save();
            if (checkin) {
                versionManager.checkin(patientInformationForm.getPath());
            }
            writeSuccess(response);
        } catch (final LoginException e) {
            LOGGER.error("Service authorization not granted: {}", e.getMessage());
        } catch (final RepositoryException e) {
            LOGGER.warn("Exception validating patient authentication: {}", e.getMessage(), e);
        }
    }

    private Node getPatientInformationQuestionnaire(final Session session) throws RepositoryException
    {
        return session.getNode("/Questionnaires/Patient information");
    }

    private Node getPatientInformationForm(final Node subject, final Node patientInformationQuestionnaire,
        final Session session) throws RepositoryException
    {
        // Look for the patient's information in the repository
        final Node patientSubject;

        // Make sure we have the root patient subject, not a visit.
        if (StringUtils.countMatches(subject.getPath(), "/") > 2) {
            // Path is in the form /Subjects/patientId/VisitId
            patientSubject = subject.getParent();
        } else {
            // Path is in the form /Subjects/patientId
            patientSubject = subject;
        }

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

    private void writeSuccess(final SlingHttpServletResponse response)
        throws IOException, RepositoryException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(SlingHttpServletResponse.SC_OK);
        try (Writer out = response.getWriter()) {
            final JsonObjectBuilder result = Json.createObjectBuilder();
            result.add("status", "success");
            out.append(result.build().toString());
        }
    }

    private void writeCurrentTouVersion(final SlingHttpServletResponse response, final String value)
        throws IOException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(SlingHttpServletResponse.SC_OK);
        try (Writer out = response.getWriter()) {
            final JsonObjectBuilder result = Json.createObjectBuilder();
            result.add("status", "success");
            result.add(TOU, value == null ? "none" : String.valueOf(value));
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
}
