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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
     * other cards/Answer nodes that make reference to it are updated if appropriate so that the value property of the
     * referenced Node matches the value property of the changed node.
     *
     * @param nodeIterator an iterator of nodes of which have changed due to an update made to a Form
     * @param session a service session providing access to the repository
     */
    private void checkAndUpdateAnswersValues(final NodeIterator nodeIterator, final Session session)
        throws RepositoryException
    {
        final VersionManager versionManager = session.getWorkspace().getVersionManager();
        Set<String> checkoutPaths = new HashSet<>();
        checkAndUpdateAnswersValues(nodeIterator, session, checkoutPaths, versionManager);
        if (session.hasPendingChanges()) {
            session.save();
            for (String path : checkoutPaths) {
                versionManager.checkin(path);
            }
        }
    }

    /**
     * This method reads through a NodeIterator of changed Nodes. If a given changed Node is a cards/Answer node all
     * other cards/Answer nodes that make reference to it are updated if appropriate so that the value property of the
     * referenced Node matches the value property of the changed node.
     * @param nodeIterator an iterator of nodes of which have changed due to an update made to a Form
     * @param session a service session providing access to the repository
     * @param checkoutPaths the list of forms that were checked out which will need to be checked back in
     * @param versionManager the version manager that should be used to checkout any needed forms
     * @throws RepositoryException if the node could not be processed
     */
    private void checkAndUpdateAnswersValues(final NodeIterator nodeIterator, final Session session,
        Set<String> checkoutPaths, VersionManager versionManager)
        throws RepositoryException
    {
        while (nodeIterator.hasNext()) {
            final Node node = nodeIterator.nextNode();
            if (node.isNodeType("cards:AnswerSection")) {
                checkAndUpdateAnswersValues(node.getNodes(), session, checkoutPaths, versionManager);
            } else if (node.isNodeType("cards:Answer")) {
                processAnswer(versionManager, session, checkoutPaths, node);
            }
        }
    }

    /**
     * Process a changed cards/Answer node and check if there are any other cards/Answer nodes that reference the
     * changed node or should reference the changed node. If there are any referencing cards/Answer nodes, update
     * their values to the newly changed value if appropriate.
     * @param versionManager the version manager that should be used to checkout any needed forms
     * @param session a service session providing access to the repository
     * @param checkoutPaths the list of forms that were checked out which will need to be checked back in
     * @param answerNode the cards/Answer node to check for any (potential) referencing nodes
     * @throws RepositoryException if the node could not be processed
     */
    private void processAnswer(final VersionManager versionManager, final Session session,
        final Set<String> checkoutPaths, final Node answerNode)
        throws RepositoryException
    {
        final String answerNodeType = answerNode.getPrimaryNodeType().getName();
        final String subject = this.formUtils.getSubject(this.formUtils.getForm(answerNode)).getIdentifier();
        // Query for two different types of answers:
        // 1. Reference answers that are referencing the current changed answer
        //    (Reference answers already copying from the current answer)
        // 2. Reference answers that have no value and who's question references the current answer's question
        //    (Unanswered reference answers that could potentially be copied from the current answer)
        final NodeIterator resourceIteratorReferencingAnswers = session
            .getWorkspace().getQueryManager().createQuery(
                // Answers that were explicitly copied from this answer
                "SELECT a.* FROM [" + answerNodeType + "] AS a WHERE a.copiedFrom = '"
                    + escape(answerNode.getPath()) + "'"
                    + " UNION "
                    // Answers that don't have a value yet
                    + "SELECT a.* FROM [" + answerNodeType + "] AS a"
                    + "  INNER JOIN [cards:Form] AS f ON a.form = f.[jcr:uuid]"
                    + "  INNER JOIN [cards:Question] AS q ON a.question = q.[jcr:uuid]"
                    + "  WHERE"
                    // The answer doesn't have a value
                    + "    a.copiedFrom is null"
                    // The answer's question references this question
                    + "    AND q.question = '"
                    + escape(answerNode.getProperty(FormUtils.QUESTION_PROPERTY).getNode().getPath()) + "'"
                    // The answer belongs to the same subject or one of its descendants
                    + "    AND f.relatedSubjects = '" + subject + "'"
                    // Use the fast index for the query
                    + " OPTION (index tag cards)",
                "JCR-SQL2")
            .execute().getNodes();
        final Property sourceAnswerValue =
            !answerNode.hasProperty(FormUtils.VALUE_PROPERTY) ? null : answerNode.getProperty(FormUtils.VALUE_PROPERTY);
        while (resourceIteratorReferencingAnswers.hasNext()) {
            final Node referenceAnswer = resourceIteratorReferencingAnswers.nextNode();
            final Node referenceQuestion = referenceAnswer.getProperty(FormUtils.QUESTION_PROPERTY).getNode();
            if (updatePolicyApplies(sourceAnswerValue, referenceAnswer)) {
                if (ReferenceConditionUtils.referenceHasCondition(referenceQuestion)
                    && !ReferenceConditionUtils.isReferenceConditionSatisfied(
                        this.formUtils, referenceQuestion, answerNode)) {
                    updateValueWithFallback(versionManager, session, checkoutPaths, referenceAnswer, answerNode);
                } else {
                    updateValueFromSource(versionManager, checkoutPaths, sourceAnswerValue, referenceAnswer,
                        answerNode);
                }
            }
        }
    }

    /**
     * Check if the reference question's update policy allows for updating the reference answer.
     * @param source The source answer that may be copied from
     * @param reference The reference answer that may be updated
     * @return True if the policy allows for updating, false otherwise
     * @throws RepositoryException if an unexpected error occurs determining if the policy allows for changes
     */
    private boolean updatePolicyApplies(final Property source, final Node reference) throws RepositoryException
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
        return updatePolicyMatches;
    }

    /**
     * Fill out a reference answer with a value copied from the referenced question.
     * @param versionManager A version manager to be used to checkout forms if needed
     * @param checkoutPaths The list of forms that have been checkout out and need to be checked back in
     * @param sourceAnswerValue The source answer value to copy the answer from
     * @param referenceAnswer The reference answer to copy the value into
     * @param sourceNode The source answer node that the value is being copied from
     * @throws RepositoryException if an unexpected error occurs
     */
    private void updateValueFromSource(final VersionManager versionManager, final Set<String> checkoutPaths,
        final Property sourceAnswerValue, final Node referenceAnswer, final Node sourceNode)
        throws RepositoryException
    {
        if (!isSame(sourceAnswerValue, referenceAnswer)) {
            checkoutFormIfNeeded(versionManager, referenceAnswer, checkoutPaths);
            if (sourceAnswerValue == null) {
                referenceAnswer.setProperty(FormUtils.VALUE_PROPERTY, (Value) null);
            } else if (sourceAnswerValue.isMultiple()) {
                referenceAnswer.setProperty(FormUtils.VALUE_PROPERTY, sourceAnswerValue.getValues());
            } else {
                referenceAnswer.setProperty(FormUtils.VALUE_PROPERTY, sourceAnswerValue.getValue());
            }
            referenceAnswer.setProperty("copiedFrom", sourceNode.getPath());
        }
        removeInvalidSourceStatusFlag(versionManager, referenceAnswer, checkoutPaths);
    }

    /**
     * Fill out a reference answer with the value specified as the fallback value.
     * If no fallback value is present, fill out a null value.
     * @param versionManager A version manager to be used to checkout forms if needed
     * @param session A session that can be used to retrieve the reference question
     * @param checkoutPaths The list of forms that have been checkout out and need to be checked back in
     * @param referenceAnswer The reference answer to copy the value into
     * @param sourceNode The source answer node that the value is being copied from
     * @throws RepositoryException if an unexpected error occurs
     */
    private void updateValueWithFallback(final VersionManager versionManager, final Session session,
        final Set<String> checkoutPaths, final Node referenceAnswer, final Node sourceNode)
        throws RepositoryException
    {
        Object values = ReferenceConditionUtils.getFallbackValue(session,
            referenceAnswer.getProperty(FormUtils.QUESTION_PROPERTY).getNode());
        Property referenceAnswerProperty = referenceAnswer.hasProperty(FormUtils.VALUE_PROPERTY)
            ? referenceAnswer.getProperty(FormUtils.VALUE_PROPERTY)
            : null;

        if (values instanceof Value[]) {
            if (!isSame(referenceAnswerProperty, (Value[]) values)) {
                checkoutFormIfNeeded(versionManager, referenceAnswer, checkoutPaths);
                referenceAnswer.setProperty(FormUtils.VALUE_PROPERTY, (Value[]) values);
                referenceAnswer.setProperty("copiedFrom", sourceNode.getPath());
            }
        } else {
            if (!isSame(referenceAnswerProperty, (Value) values)) {
                checkoutFormIfNeeded(versionManager, referenceAnswer, checkoutPaths);
                referenceAnswer.setProperty(FormUtils.VALUE_PROPERTY, (Value) values);
                referenceAnswer.setProperty("copiedFrom", sourceNode.getPath());
            }
        }
        addInvalidSourceStatusFlag(versionManager, referenceAnswer, checkoutPaths);
    }

    private void addInvalidSourceStatusFlag(final VersionManager versionManager, Node answer,
        final Set<String> checkoutPaths)
        throws RepositoryException
    {
        if (answer.hasProperty(FormUtils.STATUS_FLAGS_PROPERTY)) {
            List<String> statusValues = Arrays.stream(answer.getProperty(FormUtils.STATUS_FLAGS_PROPERTY).getValues())
                .map(v -> v.toString()).collect(Collectors.toList());
            if (!statusValues.contains(ReferenceConditionUtils.INVALID_SOURCE_FLAG)) {
                checkoutFormIfNeeded(versionManager, answer, checkoutPaths);
                statusValues.add(ReferenceConditionUtils.INVALID_SOURCE_FLAG);
                answer.setProperty(FormUtils.STATUS_FLAGS_PROPERTY,
                    statusValues.toArray(new String[statusValues.size()]));
            }
        }
    }

    private void removeInvalidSourceStatusFlag(final VersionManager versionManager, Node answer,
        final Set<String> checkoutPaths)
        throws RepositoryException
    {
        if (answer.hasProperty(FormUtils.STATUS_FLAGS_PROPERTY)) {
            Value[] statusValues = answer.getProperty(FormUtils.STATUS_FLAGS_PROPERTY).getValues();
            Value[] filteredValues = Arrays.stream(statusValues)
                .filter(v -> !ReferenceConditionUtils.INVALID_SOURCE_FLAG.equals(v.toString())).toArray(Value[]::new);
            if (statusValues.length != filteredValues.length) {
                checkoutFormIfNeeded(versionManager, answer, checkoutPaths);
                answer.setProperty(FormUtils.STATUS_FLAGS_PROPERTY, filteredValues);
            }
        }
    }

    /**
     * Checkout the parent form of an answer if it is checked in.
     * @param versionManager The version manager that should be used to check out the form
     * @param answerNode The answer which should have it's parent form checked out
     * @param checkoutPaths The list of forms that have been checked out and need to be checked in again
     * @throws RepositoryException If an unexpected error occurs
     */
    private void checkoutFormIfNeeded(final VersionManager versionManager, final Node answerNode,
        final Set<String> checkoutPaths)
        throws RepositoryException
    {
        final String path = this.formUtils.getForm(answerNode).getPath();
        final boolean wasCheckedOut = versionManager.isCheckedOut(path);
        if (!wasCheckedOut) {
            versionManager.checkout(path);
            checkoutPaths.add(path);
        }
    }

    /**
     * Check if a property and an answer node have the same values.
     * Two null values count as the same, a null and a non-null are different
     * @param property A property to compare
     * @param answerNode An answer node to compare
     * @return True if the values are the same
     * @throws RepositoryException if an unexpected error occurs
     */
    private boolean isSame(final Property property, final Node answerNode) throws RepositoryException
    {
        final Property nodeValue =
            !answerNode.hasProperty(FormUtils.VALUE_PROPERTY) ? null : answerNode.getProperty(FormUtils.VALUE_PROPERTY);

        if (property != null && nodeValue != null) {
            // Both values are not null: check if the values are the same
            Set<String> propertyValues = propertyToStrings(property);
            Set<String> nodeValues = propertyToStrings(nodeValue);
            return isSame(propertyValues, nodeValues);
        } else {
            // Same if both are null, otherwise not the same
            return property == null && nodeValue == null;
        }
    }

    /**
     * Check if a property and a value have the same values.
     * Two null values count as the same, a null and a non-null are different
     * @param property A property to compare
     * @param value A value to compare
     * @return True if the values are the same
     * @throws RepositoryException if an unexpected error occurs
     */
    private boolean isSame(final Property property, final Value value)
        throws RepositoryException
    {
        if (property != null && value != null) {
            Set<String> propertyStrings = propertyToStrings(property);
            Set<String> valueStrings = new HashSet<>();
            valueStrings.add(value.getString());
            return isSame(propertyStrings, valueStrings);
        } else {
            return property == null && value == null;
        }

    }

    /**
     * Check if a property and a value array have the same values.
     * Two null values count as the same, a null and a non-null are different
     * @param property A property to compare
     * @param value A value array to compare
     * @return True if the values are the same
     * @throws RepositoryException if an unexpected error occurs
     */
    private boolean isSame(final Property property, final Value[] values)
        throws RepositoryException
    {
        if (property != null && values != null) {
            Set<String> propertyStrings = propertyToStrings(property);
            Set<String> valueStrings = new HashSet<>();
            for (Value v : values) {
                valueStrings.add(v.getString());
            }
            return isSame(propertyStrings, valueStrings);
        } else {
            return property == null && values == null;
        }

    }

    /**
     * Check if two sets of strings contain the same values.
     * @param left A set of strings to compare
     * @param right A set of strings to compare
     * @return True if the sets contain the same values
     */
    private boolean isSame(Set<String> left, Set<String> right)
    {
        return left.equals(right);
    }

    /**
     * Etract the values of  property into a set of strings.
     * @param property The values to extract the values from
     * @return The set of strings representing the properties' values
     * @throws RepositoryException if an unexpected error occurs
     */
    private Set<String> propertyToStrings(final Property property)
        throws RepositoryException
    {
        Set<String> values = new HashSet<>();
        if (property.isMultiple()) {
            for (Value v : property.getValues()) {
                values.add(v.getString());
            }
        } else {
            values.add(property.getValue().getString());
        }
        return values;
    }

    private String escape(final String value)
    {
        return value.replace("'", "''");
    }
}
