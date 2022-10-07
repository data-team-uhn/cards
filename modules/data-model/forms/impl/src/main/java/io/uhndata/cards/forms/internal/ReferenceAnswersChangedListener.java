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
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Change listener looking for modified forms whose answers have references in others. Initially, when the form is
 * changed, it goes through all the answers which belong to it and checks whether the answer has other references. If
 * so - check if these values match. In such a case value of the property "value" is changed to the one as a source
 * answer.
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
     * For every form change it goes through all answers composing the changed form and update values of all the
     * reference answers according to changes in source answer.
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
     * For every form change it goes through all answers composing the changed form and update values of all the
     * reference answers according to changes in source answer.
     *
     * @param nodeIterator  an iterator of nodes of which consist the changed form of
     * @param serviceResolver a ResourceResolver that can be used for querying the JC
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
                final Iterator<Resource> resourceIteratorAnswer = serviceResolver.findResources(
                        "SELECT a.* FROM [cards:Answer] AS a WHERE a.copiedFrom = '" + node.getPath() + "'",
                        "JCR-SQL2");
                while (resourceIteratorAnswer.hasNext()) {
                    final Resource referenceAnswer = resourceIteratorAnswer.next();

                    if (referenceAnswer.getValueMap().containsKey(VALUE) && node.hasProperty(VALUE)) {
                        final Property sourceAnswerValue = node.getProperty(VALUE);
                        final Object referenceAnswerValue = referenceAnswer.getValueMap().get(VALUE);
                        if (!referenceAnswerValue.toString().equals(sourceAnswerValue.getString())) {
                            final String referenceFormPath = getParentFormPath(referenceAnswer);
                            versionManager.checkout(referenceFormPath);
                            checkoutPaths.add(referenceFormPath);
                            final Node formNode = session.getNode(referenceFormPath);
                            Node changingAnswer = this.formUtils.getAnswer(formNode,
                                    getQuestionNode(formNode, referenceAnswer, session, serviceResolver));
                            changingAnswer.setProperty(VALUE, sourceAnswerValue.getValue());
                        }
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
     * Get the node of question for given reference answer and the form which it belongs to.
     *
     * @param formNode form which contains reference answer
     * @param answer answer for which the question is sought
     * @param session a service session providing access to the repository
     * @param serviceResolver a ResourceResolver that can be used for querying the JCR
     * @return node of question to reference answer
     * @throws RepositoryException if the form data could not be accessed
     */
    private Node getQuestionNode(final Node formNode, final Resource answer, final Session session,
                                 final ResourceResolver serviceResolver) throws RepositoryException
    {
        String questionnaireUUID = formNode.getProperty("questionnaire").getString();
        final Iterator<Resource> resourceIteratorQuestionnaire = serviceResolver.findResources(
                "SELECT q.* FROM [cards:Questionnaire] AS q WHERE q.'jcr:uuid' = '" + questionnaireUUID + "'",
                "JCR-SQL2");

        String questionUUID = answer.getValueMap().get("question").toString();
        final Iterator<Resource> resourceIteratorQuestion = serviceResolver.findResources(
                "SELECT q.* FROM [cards:Question] AS q WHERE q.'jcr:uuid' = '" + questionUUID + "'",
                "JCR-SQL2");
        if (!resourceIteratorQuestionnaire.hasNext() || !resourceIteratorQuestion.hasNext()) {
            return null;
        }
        String questionnairePath = resourceIteratorQuestionnaire.next().getPath();
        Node questionnaire = session.getNode(questionnairePath);
        String questionName = resourceIteratorQuestion.next().getPath().substring(questionnairePath.length() + 1);
        return this.questionnaireUtils.getQuestion(questionnaire, questionName);
    }

    /**
     * Get path of parent form which consist of the given node.
     *
     * @param child node for which the parent form is sought
     * @return string of path the parent form
     */
    private String getParentFormPath(Resource child)
    {
        Resource parent = child.getParent();
        if (!parent.isResourceType("cards/Form")) {
            return getParentFormPath(parent);
        }
        return parent.getPath();
    }
}
