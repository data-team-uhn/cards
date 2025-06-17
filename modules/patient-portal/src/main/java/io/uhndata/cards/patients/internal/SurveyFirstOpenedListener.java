/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.patients.internal;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.links.api.Link;
import io.uhndata.cards.links.api.LinkUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * An {@link ResourceChangeListener} records when the patient proceeds to open the survey first time.
 *
 * @version $Id$
 */
@Component(immediate = true, property = {
    ResourceChangeListener.PATHS + "=/Forms",
    ResourceChangeListener.CHANGES + "=CHANGED"
    })
public class SurveyFirstOpenedListener implements ResourceChangeListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SurveyFirstOpenedListener.class);

    private static final String QUESTION_NAME = "survey_opened";

    private static final String LINK_DEFINITION_NAME = "belongsToSurvey";

    private static final String SURVEY_EVENTS_PATH = "/Questionnaires/Survey events";

    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
            policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory rrf;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private FormUtils formUtils;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private LinkUtils linkUtils;

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        changes.forEach(this::handleEvent);
    }

    private void handleEvent(final ResourceChange event)
    {
        // Check that if the property was changed by user
        final String userID = event.getUserId();
        final Boolean isPatient = "patient".equals(userID) || "guest-patient".equals(userID);
        if (!isPatient) {
            return;
        }

        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.rrf
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "SurveyFirstOpenedListener"))
        ) {
            Session session = localResolver.adaptTo(Session.class);
            String path = event.getPath();
            if (!session.nodeExists(path)) {
                // Should not happen but check for null path anyways
                return;
            }

            Node node = session.getNode(path);
            this.rrp.push(localResolver);
            mustPopResolver = true;
            if (!this.formUtils.isForm(node)) {
                return;
            }

            final Link link = this.linkUtils.getLinksOfType(node, LINK_DEFINITION_NAME)
                .stream().findFirst().orElse(null);
            if (link == null) {
                return;
            }
            final Node surveyForm = link.getLinkedResource();
            updateSurveyOpenedDate(surveyForm, session);
        } catch (LoginException e) {
            LOGGER.error("Could not find service user while writing results: {}", e.getMessage(), e);
        } catch (RepositoryException e) {
            LOGGER.error("Error during updating Survey events first opened time: {}", e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void updateSurveyOpenedDate(final Node surveyForm, final Session session)
        throws RepositoryException
    {
        if (surveyForm == null) {
            return;
        }
        final Node questionnaire = this.formUtils.getQuestionnaire(surveyForm);
        if (SURVEY_EVENTS_PATH.equals(questionnaire.getPath())) {
            // If the form is for the survey events questionnaire, update the survey opened date
            final Node question = this.questionnaireUtils.getQuestion(questionnaire, QUESTION_NAME);
            final Node answer = this.formUtils.getAnswer(surveyForm, question);
            if (this.formUtils.getValue(answer) != null) {
                return;
            }

            answer.setProperty(FormUtils.VALUE_PROPERTY, Calendar.getInstance());
            session.save();
        }
    }
}
