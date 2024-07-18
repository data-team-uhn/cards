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
    /** Property on an answer node that stores the a reference to the question. */
    public static final String QUESTION = "question";

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
                            + escape(node.getProperty(QUESTION).getNode().getPath()) + "'"
                            // The answer belongs to the same subject or one of its descendants
                            + "    AND f.relatedSubjects = '" + subject + "'"
                            // Use the fast index for the query
                            + " OPTION (index tag cards)",
                        "JCR-SQL2")
                    .execute().getNodes();
                final Property sourceAnswerValue =
                    !node.hasProperty(FormUtils.VALUE_PROPERTY) ? null : node.getProperty(FormUtils.VALUE_PROPERTY);
                while (resourceIteratorReferencingAnswers.hasNext()) {
                    final Node referenceAnswer = resourceIteratorReferencingAnswers.nextNode();
                    final Node referenceQuestion = referenceAnswer.getProperty(QUESTION).getNode();
                    if (ReferenceConditionUtils.referenceHasCondition(referenceQuestion)
                        && !ReferenceConditionUtils.isReferenceConditionSatisfied(
                            this.formUtils, referenceQuestion, node)) {
                        updateValueWithFallback(versionManager, session, checkoutPaths, referenceAnswer, node);
                    } else {
                        updateValueFromSource(versionManager, checkoutPaths, sourceAnswerValue, referenceAnswer, node);
                    }
                }
            }
        }
        session.save();
        for (String path : checkoutPaths) {
            versionManager.checkin(path);
        }
    }

    /**
     * Fill out a refernce answer with a value copied from the referenced question.
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
        if (isNotSame(sourceAnswerValue, referenceAnswer)) {
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
    }


    /**
     * Fill out a refernce answer with a value copied from the referenced question.
     * @param versionManager A version manager to be used to checkout forms if needed
     * @param checkoutPaths The list of forms that have been checkout out and need to be checked back in
     * @param sourceAnswerValue The source answer value to copy the answer from
     * @param referenceAnswer The reference answer to copy the value into
     * @param sourceNode The source answer node that the value is being copied from
     * @throws RepositoryException if an unexpected error occurs
     */

    /**
     * Fill out a refernce answer with the value specified as the fallback value.
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
            referenceAnswer.getProperty(QUESTION).getNode());
        Property referenceAnswerProperty = referenceAnswer.hasProperty(FormUtils.VALUE_PROPERTY)
            ? referenceAnswer.getProperty(FormUtils.VALUE_PROPERTY)
            : null;

        if (values instanceof Value[]) {
            if (isNotSame(referenceAnswerProperty, (Value[]) values)) {
                checkoutFormIfNeeded(versionManager, referenceAnswer, checkoutPaths);
                referenceAnswer.setProperty(FormUtils.VALUE_PROPERTY, (Value[]) values);
                referenceAnswer.setProperty("copiedFrom", sourceNode.getPath());
            }
        } else {
            if (isNotSame(referenceAnswerProperty, (Value) values)) {
                checkoutFormIfNeeded(versionManager, referenceAnswer, checkoutPaths);
                referenceAnswer.setProperty(FormUtils.VALUE_PROPERTY, (Value) values);
                referenceAnswer.setProperty("copiedFrom", sourceNode.getPath());
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
     * Check if a property and an answer node have different values.
     * Two null values count as the same, a null and a non-null are different
     * @param property A property to compare
     * @param answerNode An answer node to compare
     * @return True if the values are different
     * @throws RepositoryException if an unexpected error occurs
     */
    private boolean isNotSame(final Property property, final Node answerNode) throws RepositoryException
    {
        final Property nodeValue =
            !answerNode.hasProperty(FormUtils.VALUE_PROPERTY) ? null : answerNode.getProperty(FormUtils.VALUE_PROPERTY);

        if (!isNullStatusSame(property, nodeValue)) {
            return true;
        }
        Set<String> propertyValues = propertyToStrings(property);
        Set<String> nodeValues = propertyToStrings(nodeValue);
        return isNotSame(propertyValues, nodeValues);
    }

    /**
     * Check if a property and a value have different values.
     * Two null values count as the same, a null and a non-null are different
     * @param property A property to compare
     * @param value A value to compare
     * @return True if the values are different
     * @throws RepositoryException if an unexpected error occurs
     */
    private boolean isNotSame(final Property property, final Value value)
        throws RepositoryException
    {
        if (!isNullStatusSame(property, value)) {
            return true;
        }

        Set<String> propertyStrings = propertyToStrings(property);
        Set<String> valueStrings = new HashSet<>();
        valueStrings.add(value.getString());
        return isNotSame(propertyStrings, valueStrings);
    }

    /**
     * Check if a property and a value array have different values.
     * Two null values count as the same, a null and a non-null are different
     * @param property A property to compare
     * @param value A value array to compare
     * @return True if the values are different
     * @throws RepositoryException if an unexpected error occurs
     */
    private boolean isNotSame(final Property property, final Value[] values)
        throws RepositoryException
    {
        if (!isNullStatusSame(property, values)) {
            return true;
        }

        Set<String> propertyStrings = propertyToStrings(property);
        Set<String> valueStrings = new HashSet<>();
        for (Value v : values) {
            valueStrings.add(v.getString());
        }
        return isNotSame(propertyStrings, valueStrings);
    }

    /**
     * Check if two sets of strings contain diffferent values.
     * @param left A set of strings to compare
     * @param right A set of strings to compare
     * @return True if the sets contain different values
     */
    private boolean isNotSame(Set<String> left, Set<String> right)
    {
        return !left.equals(right);
    }

    /**
     * Check if two objects are either both null or both not null.
     * @param left An object to check
     * @param right An object to check
     * @return True if both object are null or both are not null
     */
    private boolean isNullStatusSame(Object left, Object right)
    {
        return (left == null && right == null) || (left != null && right != null);
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
