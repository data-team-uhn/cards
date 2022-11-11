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
import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.utils.ThreadResourceResolverProvider;

abstract class AbstractAnswersChangedListener
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAnswersChangedListener.class);

    protected abstract void checkAndUpdateAnswersValues(NodeIterator nodeIterator, ResourceResolver serviceResolver,
        Session session) throws RepositoryException;

    /**
     * For every Form change detected by the listener, this handler goes through all Answers composing the changed
     * Form and updates the values of all the referenced Answers according to changes in the source Answers.
     *
     * @param event a change that happened in the repository
     */
    protected void handleEvent(final ResourceChange event, final ResourceResolverFactory resolverFactory,
        final ThreadResourceResolverProvider rrp, final FormUtils formUtils, final String serviceUserName)
    {
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, serviceUserName);

        try (ResourceResolver localResolver = resolverFactory.getServiceResourceResolver(parameters)) {
            // Get the information needed from the triggering form
            final Session session = localResolver.adaptTo(Session.class);
            if (!session.nodeExists(event.getPath())) {
                return;
            }
            final String path = event.getPath();
            final Node form = session.getNode(path);
            if (!formUtils.isForm(form)) {
                return;
            }
            try {
                rrp.push(localResolver);
                NodeIterator children = form.getNodes();
                checkAndUpdateAnswersValues(children, localResolver, session);
            } catch (RepositoryException e) {
                LOGGER.error(e.getMessage(), e);
            } finally {
                rrp.pop();
            }

        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Gets the path of the parent Form for a given descendant node.
     *
     * @param child node for which the parent form is sought
     * @return string of path the parent form
     */
    protected String getParentFormPath(Resource child)
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

    /**
     * Gets the Question node for given Answer node.
     *
     * @param answer answer for which the question is sought
     * @param session a service session providing access to the repository
     * @param serviceResolver a ResourceResolver that can be used for querying the JCR
     * @return node of question to reference answer
     * @throws RepositoryException if the form data could not be accessed
     */
    protected Node getQuestionNode(final Resource answer, final Session session, final ResourceResolver serviceResolver)
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
}
