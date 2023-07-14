/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.uhndata.cards.patients.internal;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.uhndata.cards.patients.api.DataRetentionConfiguration;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.spi.AbstractNodeUtils;

/**
 * Implementation for the {@link DataRetentionConfiguration} service storing the configuration in a JCR node,
 * {@code /DataRetention/DataRetention}.
 *
 * @version $Id$
 */
@Component
public class DataRetentionConfigurationImpl extends AbstractNodeUtils implements DataRetentionConfiguration
{
    /** The location of the configuration node for patient auth. */
    private static final String CONFIG_NODE = "/DataRetention/DataRetention";

    /** Property on config node for whether or not unneeded PII should be deleted. */
    private static final String DELETE_PII_PROP = "deleteUnneededPatientDetails";

    /** Property on config node for whether or not draft answers should be deleted. */
    private static final String DELETE_DRAFT_ANSWERS_PROP = "deleteDraftAnswers";

    /** Property on config node for the number of days draft responses from patients are kept. */
    private static final String DRAFT_LIFETIME_PROP = "draftLifetime";

    /** Whether or not PII should be deleted by default. */
    private static final Boolean DELETE_PII_DEFAULT = false;

    /** Whether or not draft answers should be deleted by default. */
    private static final Boolean DELETE_DRAFT_ANSWERS_DEFAULT = false;

    /** The number of days a patient's draft response is kept for by default (used in case of errors). */
    private static final int DRAFT_LIFETIME_DEFAULT = -1;

    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ThreadResourceResolverProvider rrp;

    /**
     * Returns the property specified in the configuration node. The resource resolver must have {@code jcr:read} access
     * to the configuration node. Returns null if the configuration cannot be found.
     *
     * @param prop Name of the property to use.
     * @return The property specified in the configuration. If the configuration cannot be found, returns null.
     */
    private Property getConfig(final String prop) throws RepositoryException
    {
        ResourceResolver rr = this.rrp.getThreadResourceResolver();
        if (rr == null) {
            return null;
        }
        Session session = rr.adaptTo(Session.class);
        if (!session.nodeExists(CONFIG_NODE)) {
            return null;
        }

        Node config = session.getNode(CONFIG_NODE);
        return config.getProperty(prop);
    }

    @Override
    public boolean deleteUnneededPatientDetails()
    {
        try {
            Property delete = getConfig(DELETE_PII_PROP);
            return delete == null ? DELETE_PII_DEFAULT : delete.getBoolean();
        } catch (RepositoryException e) {
            return DELETE_PII_DEFAULT;
        }
    }

    @Override
    public boolean deleteDraftAnswers()
    {
        try {
            Property delete = getConfig(DELETE_DRAFT_ANSWERS_PROP);
            return delete == null ? DELETE_DRAFT_ANSWERS_DEFAULT : delete.getBoolean();
        } catch (RepositoryException e) {
            return DELETE_DRAFT_ANSWERS_DEFAULT;
        }
    }

    @Override
    public int getDraftLifetime()
    {
        try {
            Property lifetime = getConfig(DRAFT_LIFETIME_PROP);
            int value = lifetime == null ? DRAFT_LIFETIME_DEFAULT : (int) lifetime.getLong();
            return value < -1 ? DRAFT_LIFETIME_DEFAULT : value;
        } catch (RepositoryException e) {
            return DRAFT_LIFETIME_DEFAULT;
        }
    }

}
