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
package io.uhndata.cards.forms.internal;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.version.VersionManager;

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
 * Change listener looking for modified Forms whose Answers are referenced in other Forms. Initially, when the Form is
 * changed, this handler goes through all the Answers which belong to the Form and checks whether a given Answer is
 * referenced elsewhere. If so, the source and referenced Answer values are compared and if they do not match the
 * referenced value is updated to match the source value.
 *
 * @version $Id$
 */
@Component(immediate = true, property = {
    ResourceChangeListener.PATHS + "=/Forms",
    ResourceChangeListener.CHANGES + "=CHANGED"
})
public class ReferenceAnswersChangedListener implements ResourceChangeListener
{
    /** Answer's property name. **/
    public static final String VALUE = "value";

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceAnswersChangedListener.class);

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
    public void onChange(List<ResourceChange> changes)
    {
        changes.forEach(this::handleEvent);
    }

    /**
     * For every Form change detected by the listener, this handler goes through all Answers composing the changed
     * Form and updates the values of all the referenced Answers according to changes in the source Answers.
     *
     * @param event a change that happened in the repository
     */
    private void handleEvent(final ResourceChange event)
    {
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "referenceAnswersChangedListener");

        try (ResourceResolver localResolver = this.resolverFactory.getServiceResourceResolver(parameters)) {
            // Get the information needed from the triggering form
            final Session session = localResolver.adaptTo(Session.class);
            if (!session.nodeExists(event.getPath())) {
                return;
            }
            final String path = event.getPath();
            final Node form = session.getNode(path);
            if (!this.formUtils.isForm(form)) {
                return;
            }
            try {
                this.rrp.push(localResolver);
                NodeIterator children = form.getNodes();
                checkAndUpdateAnswersValues(children, session);
            } catch (RepositoryException e) {
                LOGGER.error(e.getMessage(), e);
            } finally {
                this.rrp.pop();
            }

        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * This method reads through a NodeIterator of changed Nodes. If a given changed Node is a cards/Answer node all
     * other cards/Answer nodes that make reference to it are updated so that the value property of the referenced Node
     * matches the value property of the changed node.
     *
     * @param nodeIterator an iterator of nodes of which have changed due to an update made to a Form
     * @param serviceResolver a ResourceResolver that can be used for querying the JCR
     * @param session a service session providing access to the repository
     */
    private void checkAndUpdateAnswersValues(final NodeIterator nodeIterator, final Session session)
        throws RepositoryException
    {
        final VersionManager versionManager = session.getWorkspace().getVersionManager();
        Set<String> checkoutPaths = new HashSet<>();
        while (nodeIterator.hasNext()) {
            final Node node = nodeIterator.nextNode();
            if (node.isNodeType("cards:AnswerSection")) {
                checkAndUpdateAnswersValues(node.getNodes(), session);
            } else if (node.isNodeType("cards:Answer")) {
                final String answerNodeType = node.getPrimaryNodeType().getName();
                final String subject = this.formUtils.getSubject(this.formUtils.getForm(node)).getIdentifier();
                // TODO: is this query needed with the refactor in CARDS-2509/2571?
                // May be possible to replace it with a loop on node.getReferences()
                final NodeIterator resourceIteratorReferencingAnswers = session
                    .getWorkspace().getQueryManager().createQuery(
                        // Answers that were explicitly copied from this answer
                        "SELECT a.* FROM [" + answerNodeType + "] AS a WHERE a.copiedFrom = '"
                            + escape(node.getPath()) + "'"
                            + " UNION "
                            // Answers that don't have a value yet
                            + "SELECT a.* FROM [" + answerNodeType + "] AS a"
                            + "  INNER JOIN [cards:Form] AS f ON a.form = f.[jcr:uuid]"
                            + "  INNER JOIN [cards:Question] AS q ON a.question = q.[jcr:uuid]"
                            + "  WHERE"
                            // The answer doesn't have a value
                            + "    a.value is null"
                            // The answer's question references this question
                            + "    AND q.question = '"
                            + escape(node.getProperty("question").getNode().getPath()) + "'"
                            // The answer belongs to the same subject or one of its descendants
                            + "    AND f.relatedSubjects = '" + subject + "'"
                            // Use the fast index for the query
                            + " OPTION (index tag cards)",
                        "JCR-SQL2")
                    .execute().getNodes();
                final Property sourceAnswerValue =
                    !node.hasProperty(VALUE) ? null : node.getProperty(VALUE);
                while (resourceIteratorReferencingAnswers.hasNext()) {
                    final Node referenceAnswer = resourceIteratorReferencingAnswers.nextNode();
                    if (shouldUpdateValue(sourceAnswerValue, referenceAnswer)) {
                        final Node formNode = this.formUtils.getForm(referenceAnswer);
                        final String referenceFormPath = formNode.getPath();
                        versionManager.checkout(referenceFormPath);
                        checkoutPaths.add(referenceFormPath);
                        if (sourceAnswerValue == null) {
                            referenceAnswer.setProperty(VALUE, (Value) null);
                        } else if (sourceAnswerValue.isMultiple()) {
                            referenceAnswer.setProperty(VALUE, sourceAnswerValue.getValues());
                        } else {
                            referenceAnswer.setProperty(VALUE, sourceAnswerValue.getValue());
                        }
                        referenceAnswer.setProperty("copiedFrom", node.getPath());
                    }
                }
            }
        }
        session.save();
        for (String path : checkoutPaths) {
            versionManager.checkin(path);
        }
    }

    private boolean shouldUpdateValue(final Property source, final Node reference) throws RepositoryException
    {
        String updateMode = "";
        Node referenceQuestion = this.formUtils.getQuestion(reference);
        if (referenceQuestion.hasProperty("updateMode")) {
            updateMode = referenceQuestion.getProperty("updateMode").getString();
        }
        boolean updatePolicyMatches;
        switch (updateMode) {
            case "initial_only":
                // This listener only runs on answers that are referenced by an existing reference answer.
                // This means every time it runs the reference answer will already have copied the state of this answer
                // and thus should not be overwritten
                updatePolicyMatches = false;
                break;
            case "always":
            default:
                updatePolicyMatches = true;
                break;
        }
        return updatePolicyMatches && isNotSame(source, reference);
    }

    // TODO: should be renamed to avoid negative
    // Will handle while dealing with 2509/2571 merge
    // TODO: Fix Cyclomatic Complexity
    // Will handle while dealing with 2509/2571 merge
    @SuppressWarnings({"checkstyle:CyclomaticComplexity"})
    private boolean isNotSame(final Property source, final Node reference) throws RepositoryException
    {
        final Property referenceAnswerValue =
            !reference.hasProperty(VALUE) ? null : reference.getProperty(VALUE);

        if (source == null && referenceAnswerValue != null || source != null && referenceAnswerValue == null) {
            return true;
        } else if (source == null) {
            // Both must be null
            return false;
        }
        Set<String> sourceValues = new HashSet<>();
        if (source.isMultiple()) {
            for (Value v : source.getValues()) {
                sourceValues.add(v.getString());
            }
        } else {
            sourceValues.add(source.getValue().getString());
        }
        Set<String> referenceValues = new HashSet<>();
        if (referenceAnswerValue.isMultiple()) {
            for (Value v : referenceAnswerValue.getValues()) {
                referenceValues.add(v.getString());
            }
        } else {
            referenceValues.add(referenceAnswerValue.getValue().getString());
        }
        return !sourceValues.equals(referenceValues);
    }

    private String escape(final String value)
    {
        return value.replace("'", "''");
    }
}
