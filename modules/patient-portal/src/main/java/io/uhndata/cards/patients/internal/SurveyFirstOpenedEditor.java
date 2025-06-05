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
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeBuilder;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.links.api.Link;
import io.uhndata.cards.links.api.LinkUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * An {@link Editor} records when the patient proceeds to open the survey first time.
 *
 * @version $Id$
 */
public class SurveyFirstOpenedEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SurveyFirstOpenedEditor.class);

    private static final String OPENED_PROP = "survey_opened";

    private final NodeBuilder currentNodeBuilder;

    private final ResourceResolverFactory rrf;

    private final ThreadResourceResolverProvider rrp;

    private final QuestionnaireUtils questionnaireUtils;

    private final FormUtils formUtils;

    private final LinkUtils linkUtils;

    /**
     * Simple constructor.
     *
     * @param nodeBuilder the builder for the current node
     * @param rrf the resource resolver factory which can provide access to JCR sessions
     * @param rrp the thread resource resolver provider to store resource resolvers to
     * @param questionnaireUtils for working with questionnaire data
     * @param formUtils for working with form data
     * @param linkUtils for working with links
     */
    public SurveyFirstOpenedEditor(NodeBuilder nodeBuilder, ThreadResourceResolverProvider rrp,
        ResourceResolverFactory rrf, QuestionnaireUtils questionnaireUtils, FormUtils formUtils,
        LinkUtils linkUtils)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.rrp = rrp;
        this.rrf = rrf;
        this.formUtils = formUtils;
        this.questionnaireUtils = questionnaireUtils;
        this.linkUtils = linkUtils;
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        return new SurveyFirstOpenedEditor(this.currentNodeBuilder.getChildNode(name), this.rrp, this.rrf,
            this.questionnaireUtils, this.formUtils, this.linkUtils);
    }

    // Called when the value of an existing property gets changed
    @Override
    public void propertyChanged(PropertyState before, PropertyState after) throws CommitFailedException
    {
        // Update survey opened date only when the form is checked out
        if (!("jcr:isCheckedOut".equals(after.getName()) && after.getValue(Type.BOOLEAN))) {
            return;
        }
        // Check that if the property was changed by user
        final Session thisSession = this.rrp.getThreadResourceResolver().adaptTo(Session.class);
        final String userID = thisSession.getUserID();
        final Boolean isUser = "patient".equals(userID) || "guest-patient".equals(userID);
        if (!isUser) {
            return;
        }

        Node node = getCurrentNode(thisSession);
        if (!this.formUtils.isForm(node)) {
            return;
        }

        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.rrf
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "SurveyFirstOpenedEditor"))) {
            this.rrp.push(localResolver);
            mustPopResolver = true;

            final Session session = localResolver.adaptTo(Session.class);

            final Link link = this.linkUtils.getLinksOfType(node, "belongsToSurvey").stream().findFirst().orElse(null);
            if (link == null) {
                return;
            }
            final Node surveyForm = link.getLinkedResource();
            final Node questionnaire = this.formUtils.getQuestionnaire(surveyForm);
            updateSurveyOpenedDate(surveyForm, session, questionnaire);
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

    private void updateSurveyOpenedDate(final Node surveyForm, final Session session, final Node questionnaire)
        throws RepositoryException
    {
        if (surveyForm != null && "/Questionnaires/Survey events".equals(questionnaire.getPath())) {
            // If the form is for the survey events questionnaire, update the survey opened date
            final Node question = this.questionnaireUtils.getQuestion(questionnaire, OPENED_PROP);
            final Node answer = this.formUtils.getAnswer(surveyForm, question);
            if (this.formUtils.getValue(answer) != null) {
                return;
            }

            checkoutIfNeeded(surveyForm, session);
            answer.setProperty("value", Calendar.getInstance());
            session.save();
        }
    }

    private boolean checkoutIfNeeded(final Node form, final Session session) throws RepositoryException
    {
        session.refresh(true);
        if (!form.isCheckedOut()) {
            session.getWorkspace().getVersionManager().checkout(form.getPath());
            return true;
        }
        return false;
    }

    private Node getCurrentNode(final Session session)
    {
        try {
            if (this.currentNodeBuilder instanceof MemoryNodeBuilder) {
                final String nodePath = ((MemoryNodeBuilder) this.currentNodeBuilder).getPath();
                if (session.nodeExists(nodePath)) {
                    return session.getNode(nodePath);
                }
            }
        } catch (RepositoryException e) {
            // just return null
        }
        return null;
    }
}
