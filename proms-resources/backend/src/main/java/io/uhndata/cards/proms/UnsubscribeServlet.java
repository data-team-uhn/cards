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
    "unsubscribe" }, methods = { "POST" })
public class UnsubscribeServlet extends SlingAllMethodsServlet
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
                getPatientInformationForm(visitSubject, patientInformationQuestionnaire, session);
            if (patientInformationForm == null) {
                writeError(response, SlingHttpServletResponse.SC_CONFLICT, "Sorry, cannot record your answer");
                return;
            }

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
            unsubscribeAnswer.setProperty(FormUtils.VALUE_PROPERTY, 1L);
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
