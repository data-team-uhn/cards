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
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
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
import io.uhndata.cards.patients.emailnotifications.AppointmentUtils;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/PatientHomepage" }, extensions = {
    "unsubscribe" }, methods = { "GET", "POST" })
public class UnsubscribeServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = 552901093350213103L;

    private static final Logger LOGGER = LoggerFactory.getLogger(UnsubscribeServlet.class);

    private static final String UNSUBSCRIBE = "email_unsubscribed";

    private static final String UNSUBSCRIBED_LIST = "unsubscribed_list";

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
            Map.of(ResourceResolverFactory.SUBSERVICE, "unsubscribe"))) {
            final Session session = rr.adaptTo(Session.class);
            final Node visitSubject = session.getNodeByIdentifier(sessionSubjectIdentifier);

            final String clinicPath = AppointmentUtils.getQuestionAnswerForSubject(
                this.formUtils,
                visitSubject,
                AppointmentUtils.CLINIC_PATH,
                "cards:TextAnswer",
                "");

            final Node patientInformationQuestionnaire = getPatientInformationQuestionnaire(session);
            final Node patientInformationForm =
                getPatientInformationForm(visitSubject, patientInformationQuestionnaire);
            if (patientInformationForm == null) {
                writeError(response, SlingHttpServletResponse.SC_NOT_FOUND, "Sorry, cannot find your profile");
                return;
            }

            final Property unsubscribedProp =
                getProperty(patientInformationForm, patientInformationQuestionnaire, UNSUBSCRIBE);
            final Property unsusbcribedListProp =
                    getProperty(patientInformationForm, patientInformationQuestionnaire, UNSUBSCRIBED_LIST);

            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(SlingHttpServletResponse.SC_OK);
            try (Writer out = response.getWriter()) {
                final JsonObjectBuilder result = Json.createObjectBuilder();
                result.add("status", "success");
                result.add("currentClinic", clinicPath);
                result.add(UNSUBSCRIBE, this.formUtils.serializeProperty(unsubscribedProp));
                result.add(UNSUBSCRIBED_LIST, this.formUtils.serializeProperty(unsusbcribedListProp));

                out.append(result.build().toString());
            }
        } catch (final LoginException e) {
            LOGGER.error("Service authorization not granted: {}", e.getMessage());
        } catch (final RepositoryException e) {
            LOGGER.warn("Exception validating patient authentication: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings({"checkstyle:ExecutableStatementCount"})
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

        try (ResourceResolver rr = this.resolverFactory.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "unsubscribe"))) {
            final Session session = rr.adaptTo(Session.class);
            final Node visitSubject = session.getNodeByIdentifier(sessionSubjectIdentifier);
            final Node patientInformationQuestionnaire = getPatientInformationQuestionnaire(session);
            final Node patientInformationForm =
                getPatientInformationForm(visitSubject, patientInformationQuestionnaire);
            if (patientInformationForm == null) {
                writeError(response, SlingHttpServletResponse.SC_CONFLICT, "Sorry, cannot record your answer");
                return;
            }

            final VersionManager versionManager = session.getWorkspace().getVersionManager();
            final boolean checkin = !versionManager.isCheckedOut(patientInformationForm.getPath());
            versionManager.checkout(patientInformationForm.getPath());

            Node unsubscribedListAnswer =
                    getAnswer(patientInformationForm, patientInformationQuestionnaire, UNSUBSCRIBED_LIST,
                        "cards:ClinicMapping");
            Node unsubscribeAnswer =
                getAnswer(patientInformationForm, patientInformationQuestionnaire, UNSUBSCRIBE, "cards:BooleanAnswer");
            final String unsubscribedAll = request.getParameter(UNSUBSCRIBE);
            if (unsubscribedAll != null) {
                final long value = Long.valueOf(StringUtils.defaultString(unsubscribedAll, "1"));
                unsubscribeAnswer.setProperty(FormUtils.VALUE_PROPERTY, value);
                unsubscribedListAnswer.setProperty(FormUtils.VALUE_PROPERTY, (Value) null);
            } else {
                final String[] list = request.getParameterValues(UNSUBSCRIBED_LIST);
                unsubscribedListAnswer.setProperty(FormUtils.VALUE_PROPERTY, list);
                unsubscribeAnswer.setProperty(FormUtils.VALUE_PROPERTY, (Value) null);
            }

            session.save();
            if (checkin) {
                versionManager.checkin(patientInformationForm.getPath());
            }

            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(SlingHttpServletResponse.SC_OK);
            try (Writer out = response.getWriter()) {
                final JsonObjectBuilder result = Json.createObjectBuilder();
                result.add("status", "success");
                out.append(result.build().toString());
            }
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

    private Node getPatientInformationForm(final Node visitSubject, final Node patientInformationQuestionnaire)
        throws RepositoryException
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

    private void writeError(final SlingHttpServletResponse response, final int statusCode, final String errorMessage)
        throws IOException
    {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(statusCode);
        try (Writer out = response.getWriter()) {
            out.append("{\"status\":\"error\",\"error\": \"" + errorMessage + "\"}");
        }
    }

    private Property getProperty(final Node form, final Node questionnaire, final String name)
        throws RepositoryException
    {
        final Node question = this.questionnaireUtils.getQuestion(questionnaire, name);
        final Node answer = this.formUtils.getAnswer(form, question);
        return answer != null && answer.hasProperty(FormUtils.VALUE_PROPERTY)
            ? answer.getProperty(FormUtils.VALUE_PROPERTY) : null;
    }

    private Node getAnswer(final Node form, final Node questionnaire, final String name, final String type)
        throws PathNotFoundException, RepositoryException
    {
        final Node question = this.questionnaireUtils.getQuestion(questionnaire, name);
        Node answer = this.formUtils.getAnswer(form, question);
        if (answer == null) {
            answer = form.addNode(UUID.randomUUID().toString(), type);
            answer.setProperty("question", question);
        }
        return answer;
    }
}
