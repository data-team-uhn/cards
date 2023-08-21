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
package io.uhndata.cards;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.Property;
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
 * Change listener that watches for new form references and creates the link back to the originating form if that is
 * required.
 *
 * @version $Id$
 */
@Component(immediate = true, property = {
    ResourceChangeListener.PATHS + "=/Forms",
    ResourceChangeListener.CHANGES + "=ADDED"
})
public class FormReferenceListener implements ResourceChangeListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FormReferenceListener.class);

    private static final String[] LINKBACK_PROPERTIES = {"label", "recursiveDeleteParent"};
    private static final String FORM_REFERENCES_NAME = "formReferences";
    private static final String FORM_REFERENCES_CARDS_TYPE = "cards:FormReferences";
    private static final String REFERENCE_PROPERTIES_NAME = "referenceProperties";

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
        // Acquire a service session with the right privileges for accessing visits and their forms
        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "formReferenceEditor"))) {
            this.rrp.push(localResolver);
            mustPopResolver = true;
            // Get the information needed from the triggering form
            final Session session = localResolver.adaptTo(Session.class);
            if (!session.nodeExists(path)) {
                return;
            }
            final Node formReference = session.getNode(path);
            if (!"cards:FormReference".equals(formReference.getPrimaryNodeType().getName())) {
                return;
            }
            if (formReference.getNode(REFERENCE_PROPERTIES_NAME).getProperty("linkback").getBoolean()) {
                addLinkback(formReference);
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

    /**
     * Create the reference back to originating form.
     * @param existingFormReference The FormReference node that caused this event
     * @throws RepositoryException If the form reference could not be created
     */
    private void addLinkback(final Node existingFormReference) throws RepositoryException
    {
        Node formToModify = existingFormReference.getProperty("reference").getNode();
        if (formToModify == null) {
            // Could not access the form that needs to be modified
            return;
        }

        Node formToReference = this.formUtils.getForm(existingFormReference);

        boolean checkinNeeded = checkoutIfNeeded(formToModify);

        try {
            Node formReferences = getOrCreateFormReferencesNode(formToModify);
            Node newFormReference = formReferences.addNode(UUID.randomUUID().toString());
            newFormReference.setProperty("reference", formToReference);

            Node existingProperties = existingFormReference.getNode(REFERENCE_PROPERTIES_NAME);
            if (existingProperties.hasNode("linkbackProperties")) {
                Node newProperties = newFormReference.getNode(REFERENCE_PROPERTIES_NAME);
                copyNodeLinkbackProperties(newProperties, existingProperties.getNode("linkbackProperties"));
                newProperties.setPrimaryType("cards:ReferenceProperties");
            }

            newFormReference.setPrimaryType("cards:FormReference");
        } catch (RepositoryException e) {
            LOGGER.error("Failed to create form reference to {}", formToReference.getPath(), e);
        }

        try {
            formToModify.getSession().save();
        } catch (VersionException e) {
            // Node was checked in in the background, try to checkout and save again
            formToModify.getSession().refresh(true);
            checkinNeeded = checkoutIfNeeded(formToModify);
            formToModify.getSession().save();
        }
        if (checkinNeeded) {
            checkinIfNeeded(formToModify);
        }
    }

    private Node getOrCreateFormReferencesNode(Node node) throws RepositoryException
    {
        Node result;
        if (node.hasNode(FORM_REFERENCES_NAME)) {
            result = node.getNode(FORM_REFERENCES_NAME);
        } else {
            result = node.addNode(FORM_REFERENCES_NAME);
            result.setPrimaryType(FORM_REFERENCES_CARDS_TYPE);
        }
        return result;
    }

    private void copyNodeLinkbackProperties(Node newProperties, Node existingProperties)
    {
        try {
            for (final String propName : LINKBACK_PROPERTIES) {
                if (existingProperties.hasProperty(propName)) {
                    Property prop = existingProperties.getProperty(propName);
                    if (prop.isMultiple()) {
                        newProperties.setProperty(prop.getName(), prop.getValues());
                    } else {
                        newProperties.setProperty(prop.getName(), prop.getValue());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Unable to copy properties to new form reference", e);
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

    private void checkinIfNeeded(final Node form) throws RepositoryException
    {
        if (form.isCheckedOut()) {
            form.getSession().getWorkspace().getVersionManager().checkin(form.getPath());
        }
    }
}
