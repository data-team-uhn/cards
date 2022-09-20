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
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
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
import io.uhndata.cards.utils.ThreadResourceResolverProvider;

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

    @Override
    public void onChange(@NotNull List<ResourceChange> changes)
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
            this.rrp.push(localResolver);
            NodeIterator children = form.getNodes();
            checkAndUpdateAnswersValues(children);

        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
    private void checkAndUpdateAnswersValues(NodeIterator nodeIterator) throws RepositoryException
    {
        while (nodeIterator.hasNext()) {
            Node node = nodeIterator.nextNode();
            if (node.isNodeType("cards:AnswerSection")) {
                checkAndUpdateAnswersValues(node.getNodes());
            } else if (node.hasProperty("sling:resourceSuperType")
                        && "cards/Answer".equals(node.getProperty("sling:resourceSuperType").getString())) {
                PropertyIterator references = node.getReferences();
                while (references.hasNext()) {
                    Node referenceAnswer = references.nextProperty().getParent();
                    if (referenceAnswer.hasProperty(VALUE) && node.hasProperty(VALUE)) {
                        Property sourceAnswerValue = node.getProperty(VALUE);
                        Property referenceAnswerValue = referenceAnswer.getProperty(VALUE);
                        if (!referenceAnswerValue.getString().equals(referenceAnswerValue.getString())) {
                            referenceAnswer.setProperty(VALUE, sourceAnswerValue.getValue());
                        }
                    }
                }
            }
        }
    }

}
