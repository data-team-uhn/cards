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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
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
                checkAndUpdateAnswersValues(children, localResolver, session);
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
     * This method reads through a NodeIterator of changed Nodes. If a given changed Node is a cards/Answer node
     * all other cards/Answer nodes that make reference to it are updated so that the value property of the
     * referenced Node matches the value property of the changed node.
     *
     * @param nodeIterator  an iterator of nodes of which have changed due to an update made to a Form
     * @param serviceResolver a ResourceResolver that can be used for querying the JCR
     * @param session a service session providing access to the repository
     */
    private void checkAndUpdateAnswersValues(final NodeIterator nodeIterator, final ResourceResolver serviceResolver,
                                             final Session session) throws RepositoryException
    {
        final VersionManager versionManager = session.getWorkspace().getVersionManager();
        Set<String> checkoutPaths = new HashSet<>();
        while (nodeIterator.hasNext()) {
            final Node node = nodeIterator.nextNode();
            if (node.isNodeType("cards:AnswerSection")) {
                checkAndUpdateAnswersValues(node.getNodes(), serviceResolver, session);
            } else if (node.hasProperty("sling:resourceSuperType")
                        && "cards/Answer".equals(node.getProperty("sling:resourceSuperType").getString())) {
                final Iterator<Resource> resourceIteratorReferencingAnswers = serviceResolver.findResources(
                        "SELECT a.* FROM [cards:Answer] AS a WHERE a.copiedFrom = '" + node.getPath() + "'",
                        "JCR-SQL2");
                while (resourceIteratorReferencingAnswers.hasNext()) {
                    final Resource referenceAnswer = resourceIteratorReferencingAnswers.next();

                    if (!referenceAnswer.getValueMap().containsKey(VALUE)) {
                        continue;
                    }
                    if (!node.hasProperty(VALUE)) {
                        continue;
                    }
                    final Property sourceAnswerValue = node.getProperty(VALUE);
                    final Object referenceAnswerValue = referenceAnswer.getValueMap().get(VALUE);
                    if (!referenceAnswerValue.toString().equals(sourceAnswerValue.getString())) {
                        final String referenceFormPath = getParentFormPath(referenceAnswer);
                        versionManager.checkout(referenceFormPath);
                        checkoutPaths.add(referenceFormPath);
                        final Node formNode = session.getNode(referenceFormPath);
                        Node changingAnswer = this.formUtils.getAnswer(formNode,
                                getQuestionNode(referenceAnswer, session, serviceResolver));
                        changingAnswer.setProperty(VALUE, sourceAnswerValue.getValue());
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
     * Gets the Question node for given Answer node.
     *
     * @param answer answer for which the question is sought
     * @param session a service session providing access to the repository
     * @param serviceResolver a ResourceResolver that can be used for querying the JCR
     * @return node of question to reference answer
     * @throws RepositoryException if the form data could not be accessed
     */
    private Node getQuestionNode(final Resource answer, final Session session, final ResourceResolver serviceResolver)
            throws RepositoryException
    {
        String questionUUID = answer.getValueMap().get("question").toString();
        final Iterator<Resource> resourceIteratorQuestion = serviceResolver.findResources(
                "SELECT q.* FROM [cards:Question] AS q WHERE q.'jcr:uuid' = '" + questionUUID + "'",
                "JCR-SQL2");
        if (!resourceIteratorQuestion.hasNext()) {
            return null;
        }
        return session.getNode(resourceIteratorQuestion.next().getPath());
    }

    /**
     * Gets the path of the parent Form for a given descendant node.
     *
     * @param child node for which the parent form is sought
     * @return string of path the parent form
     */
    private String getParentFormPath(Resource child)
    {
        Resource parent = child.getParent();

        if (parent == null) {
            return null;
        }
        if (!parent.isResourceType("cards/Form")) {
            return getParentFormPath(parent);
        }
        return parent.getPath();
    }
}
