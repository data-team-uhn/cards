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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
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
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Change listener that adds a {@code SUBMITTED} flag to all the forms in a visit when the {@code Visit Information}
 * form is submitted.
 *
 * @version $Id$
 */
@Component(immediate = true, property = {
    ResourceChangeListener.PATHS + "=/Forms",
    ResourceChangeListener.CHANGES + "=CHANGED"
})
public class SubmissionListener implements ResourceChangeListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionListener.class);

    private static final String STATUS_FLAGS = "statusFlags";

    private static final String[] STRING_ARRAY = {};

    /** Provides access to resources. */
    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private FormUtils formUtils;

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        changes.forEach(this::handleEvent);
    }

    private void handleEvent(final ResourceChange event)
    {
        // Acquire a service session with the right privileges for accessing visits and their forms
        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "VisitFormsPreparation"))) {
            this.rrp.push(localResolver);
            mustPopResolver = true;
            // Get the information needed from the triggering form
            final Session session = localResolver.adaptTo(Session.class);
            final String path = event.getPath();
            if (!session.nodeExists(path)) {
                return;
            }
            final Node node = session.getNode(path);
            if (isAnswerForSurveysSubmitted(node) && isSubmitted(node)) {
                addSubmittedFlagToVisitForms(node, session);
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

    private boolean isAnswerForSurveysSubmitted(final Node answer)
    {
        try {
            final Node question = this.formUtils.getQuestion(answer);
            return question != null
                && ("/Questionnaires/Visit information/surveys_submitted").equals(question.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if answer is for question surveys_submitted: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isSubmitted(final Node submittedAnswer) throws RepositoryException
    {
        final Long submitted = (Long) this.formUtils.getValue(submittedAnswer);
        return submitted != null && submitted == 1;
    }

    private void addSubmittedFlagToVisitForms(final Node submittedAnswer, final Session session)
    {
        final Node visitInformationForm = this.formUtils.getForm(submittedAnswer);
        final String subjectId = this.formUtils.getSubjectIdentifier(visitInformationForm);
        try {
            NodeIterator forms = session.getWorkspace().getQueryManager().createQuery(String.format(
                // select the forms
                "SELECT form.*"
                    + "  FROM [cards:Form] AS form"
                    + " WHERE"
                    // link to the correct Visit subject
                    + "  form.subject = '%1$s'"
                    // use the synchronous index to not miss recently created forms
                    + " OPTION (index tag property)",
                subjectId), "JCR-SQL2").execute().getNodes();
            while (forms.hasNext()) {
                updateFormFlags(forms.nextNode());
            }
        } catch (RepositoryException e) {
            LOGGER.error("Failed to mark forms as SUBMITTED: {}", e.getMessage(), e);
        }
    }

    private void updateFormFlags(final Node form) throws RepositoryException
    {
        boolean checkinNeeded = checkoutIfNeeded(form);
        Set<String> flags = new TreeSet<>();
        if (form.hasProperty(STATUS_FLAGS)) {
            Set.of(form.getProperty("statusFlags").getValues()).forEach(v -> {
                try {
                    flags.add(v.getString());
                } catch (RepositoryException e) {
                    LOGGER.warn("Failed to read flag: {}", e.getMessage());
                }
            });
        }
        flags.add("SUBMITTED");
        form.setProperty("statusFlags", flags.toArray(STRING_ARRAY));
        try {
            form.getSession().save();
        } catch (VersionException e) {
            // Node was checked in in the background, try to checkout and save again
            form.getSession().refresh(true);
            checkinNeeded = checkoutIfNeeded(form);
            form.getSession().save();
        }
        if (checkinNeeded) {
            checkin(form);
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
