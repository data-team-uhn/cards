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
package io.uhndata.cards.heracles.internal;

import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionException;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Change listener that adds a reference from pause forms to resume forms when their paired resume form is created.
 *
 * @version $Id$
 */
@Component(immediate = true, property = {
    ResourceChangeListener.PATHS + "=/Forms",
    ResourceChangeListener.CHANGES + "=ADDED"
})
public class ResumeFormReferenceListener implements ResourceChangeListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ResumeFormReferenceListener.class);

    /** Provides access to resources. */
    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private FormUtils formUtils;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        changes.forEach(this::handleEvent);
    }

    private void handleEvent(final ResourceChange event)
    {
        final String path = event.getPath();
        if (!path.endsWith("formReference")) {
            return;
        }
        // Acquire a service session with the right privileges for accessing visits and their forms
        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "PauseResumeEditor"))) {
            this.rrp.push(localResolver);
            mustPopResolver = true;
            // Get the information needed from the triggering form
            final Session session = localResolver.adaptTo(Session.class);
            if (!session.nodeExists(path)) {
                return;
            }
            final Node node = session.getNode(path);
            if (isForResumeForm(node)) {
                addPauseFormReference(node);
            }
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private boolean isForResumeForm(final Node formReference)
    {
        try {
            final Node form = formReference.getParent();
            final Node questionnaire = this.formUtils.getQuestionnaire(form);
            if (questionnaire == null) {
                return false;
            }
            try {
                if (!"/Questionnaires/Pause-Resume Status".equals(questionnaire.getPath())) {
                    return false;
                }
                final Node statusQuestion = this.questionnaireUtils.getQuestion(questionnaire, "enrollment_status");
                String status = String.valueOf(this.formUtils.getValue(
                    this.formUtils.getAnswer(form, statusQuestion)));
                return "resumed".equals(status);
            } catch (RepositoryException e) {
                return false;
            }
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if answer is for question surveys_submitted: {}", e.getMessage(), e);
            return false;
        }
    }

    private void addPauseFormReference(final Node resumeFormReference) throws RepositoryException
    {
        Node pauseForm = resumeFormReference.getProperty("reference").getNode();
        Node resumeForm = resumeFormReference.getParent();

        boolean checkinNeeded = checkoutIfNeeded(pauseForm);
        try {
            Node latestReference = pauseForm.addNode("formReference");
            latestReference.setProperty("reference", resumeForm);
            latestReference.setProperty("label", "Resume Form");
            // Do not delete a pause form when the linked resume form is deleted
            latestReference.setProperty("deleteWithReference", false);
            latestReference.setPrimaryType("cards:FormReference");
        } catch (RepositoryException e) {
            LOGGER.error("Failed to create form reference to {}: {}", resumeForm.getPath(), e.getMessage());
        }

        try {
            pauseForm.getSession().save();
        } catch (VersionException e) {
            // Node was checked in in the background, try to checkout and save again
            pauseForm.getSession().refresh(true);
            checkinNeeded = checkoutIfNeeded(pauseForm);
            pauseForm.getSession().save();
        }
        if (checkinNeeded) {
            checkin(pauseForm);
        }
    }

    private boolean checkoutIfNeeded(final Node form) throws RepositoryException
    {
        if (!form.isCheckedOut()) {
            form.getSession().getWorkspace().getVersionManager().checkout(form.getPath());
            return true;
        }
        return false;
    }

    private void checkin(final Node form) throws RepositoryException
    {
        if (form.isCheckedOut()) {
            form.getSession().getWorkspace().getVersionManager().checkin(form.getPath());
        }
    }
}
