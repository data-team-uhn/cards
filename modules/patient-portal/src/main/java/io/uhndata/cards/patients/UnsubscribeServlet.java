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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.Servlet;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/PatientHomepage" }, extensions = {
    "unsubscribe" }, methods = { "GET", "POST" })
public class UnsubscribeServlet extends SlingJakartaAllMethodsServlet
{
    private static final long serialVersionUID = 552901093350213103L;

    private static final Logger LOGGER = LoggerFactory.getLogger(UnsubscribeServlet.class);

    private static final String UNSUBSCRIBE = "email_unsubscribed";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private FormUtils formUtils;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Override
    public void doGet(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        try (ResourceResolver rr = this.resolverFactory.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "unsubscribe"))) {
            final Session session = rr.adaptTo(Session.class);

            final Node patientInformationQuestionnaire = getPatientInformationQuestionnaire(session);
            final Node patientInformationForm =
                getPatientInformationForm(request, session, patientInformationQuestionnaire);

            final Node unsubscribeQuestion =
                this.questionnaireUtils.getQuestion(patientInformationQuestionnaire, UNSUBSCRIBE);
            Node unsubscribeAnswer = this.formUtils.getAnswer(patientInformationForm, unsubscribeQuestion);
            final boolean unsubscribed =
                unsubscribeAnswer != null && unsubscribeAnswer.hasProperty(FormUtils.VALUE_PROPERTY)
                    ? unsubscribeAnswer.getProperty(FormUtils.VALUE_PROPERTY).getLong() == 1 : false;
            writeSuccess(response, unsubscribed);
        } catch (final LoginException e) {
            LOGGER.error("Service authorization not granted: {}", e.getMessage());
        } catch (final IllegalAccessException e) {
            writeError(response, SlingJakartaHttpServletResponse.SC_BAD_REQUEST, "Not a valid patient session");
        } catch (final ItemNotFoundException e) {
            writeError(response, SlingJakartaHttpServletResponse.SC_NOT_FOUND, "Sorry, cannot find your profile");
        } catch (final RepositoryException e) {
            LOGGER.warn("Exception validating patient authentication: {}", e.getMessage(), e);
        }
    }

    @Override
    public void doPost(final SlingJakartaHttpServletRequest request, final SlingJakartaHttpServletResponse response)
        throws IOException
    {
        try (ResourceResolver rr = this.resolverFactory.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "unsubscribe"))) {
            final Session session = rr.adaptTo(Session.class);

            final Node patientInformationQuestionnaire = getPatientInformationQuestionnaire(session);
            final Node patientInformationForm =
                getPatientInformationForm(request, session, patientInformationQuestionnaire);

            final VersionManager versionManager = session.getWorkspace().getVersionManager();
            final boolean checkin = !versionManager.isCheckedOut(patientInformationForm.getPath());
            versionManager.checkout(patientInformationForm.getPath());

            final Node unsubscribeQuestion =
                this.questionnaireUtils.getQuestion(patientInformationQuestionnaire, UNSUBSCRIBE);
            Node unsubscribeAnswer = this.formUtils.getAnswer(patientInformationForm, unsubscribeQuestion);
            if (unsubscribeAnswer == null && patientInformationForm != null) {
                unsubscribeAnswer = patientInformationForm.addNode(UUID.randomUUID().toString(), "cards:BooleanAnswer");
                unsubscribeAnswer.setProperty("question", unsubscribeQuestion);
            }
            final long value = Long.valueOf(Objects.toString(request.getParameter("unsubscribe"), "1"));
            unsubscribeAnswer.setProperty(FormUtils.VALUE_PROPERTY, value);
            session.save();
            if (checkin) {
                versionManager.checkin(patientInformationForm.getPath());
            }
            writeSuccess(response, value == 1);
        } catch (final LoginException e) {
            LOGGER.error("Service authorization not granted: {}", e.getMessage());
        } catch (final IllegalAccessException e) {
            writeError(response, SlingJakartaHttpServletResponse.SC_BAD_REQUEST, "Not a valid patient session");
        } catch (final ItemNotFoundException e) {
            writeError(response, SlingJakartaHttpServletResponse.SC_NOT_FOUND, "Sorry, cannot find your profile");
        } catch (final RepositoryException e) {
            LOGGER.warn("Exception validating patient authentication: {}", e.getMessage(), e);
        }
    }

    private Node getPatientInformationQuestionnaire(final Session session) throws RepositoryException
    {
        return session.getNode("/Questionnaires/Patient information");
    }

    private Node getPatientInformationForm(final SlingJakartaHttpServletRequest request, final Session session,
        final Node patientInformationQuestionnaire)
        throws IllegalAccessException, ItemNotFoundException, RepositoryException
    {
        // This only works for a uuid-authenticated session; refuse requests if this is not the case
        final String sessionPatientIdentifier = request.getParameter("patient");
        String sessionSubjectIdentifier = null;
        if (sessionPatientIdentifier == null) {
            // Fall back to the previous version of unsubscribing params
            sessionSubjectIdentifier =
                (String) this.resolverFactory.getThreadResourceResolver().getAttribute("cards:sessionSubject");
        } else {
            // Check that this is indeed a patient profile
            final Node patient = session.getNodeByIdentifier(sessionPatientIdentifier);
            if (!patient.isNodeType("cards:Form")
                || !this.formUtils.getQuestionnaire(patient).isSame(patientInformationQuestionnaire)) {
                throw new IllegalAccessException();
            }
        }

        final Node patientInformationForm;
        if (sessionPatientIdentifier != null) {
            patientInformationForm = session.getNodeByIdentifier(sessionPatientIdentifier);
        } else {
            final Node visitSubject = session.getNodeByIdentifier(sessionSubjectIdentifier);
            patientInformationForm =
                getPatientInformationFormFromVisit(visitSubject, patientInformationQuestionnaire, session);
        }
        if (patientInformationForm == null) {
            throw new ItemNotFoundException();
        }
        return patientInformationForm;
    }

    private Node getPatientInformationFormFromVisit(final Node visitSubject, final Node patientInformationQuestionnaire,
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

    private void writeSuccess(final SlingJakartaHttpServletResponse response, final Boolean value)
        throws IOException, RepositoryException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(SlingJakartaHttpServletResponse.SC_OK);
        try (Writer out = response.getWriter()) {
            final JsonObjectBuilder result = Json.createObjectBuilder();
            result.add("status", "success");
            if (value != null) {
                result.add("unsubscribed", value);
            }
            out.append(result.build().toString());
        }
    }

    private void writeError(final SlingJakartaHttpServletResponse response, final int statusCode,
        final String errorMessage) throws IOException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(statusCode);
        try (Writer out = response.getWriter()) {
            out.append("{\"status\":\"error\",\"error\": \"" + errorMessage + "\"}");
        }
    }
}
