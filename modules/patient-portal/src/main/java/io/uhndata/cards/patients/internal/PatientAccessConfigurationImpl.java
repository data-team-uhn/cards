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

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.patients.api.PatientAccessConfiguration;
import io.uhndata.cards.patients.emailnotifications.AppointmentUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.spi.AbstractNodeUtils;

/**
 * Basic utilities for grabbing patient authentication config details.
 *
 * @version $Id$
 */
@Component
public class PatientAccessConfigurationImpl extends AbstractNodeUtils implements PatientAccessConfiguration
{
    /** The location of the configuration node for patient auth. */
    private static final String CONFIG_NODE = "/Survey/PatientAccess";

    /** Property on config node for whether or not tokenless auth is enabled. */
    private static final String TOKENLESS_AUTH_ENABLED_PROP = "tokenlessAuthEnabled";

    /** Property on config node for whether or not patient identification is required. */
    private static final String PATIENT_IDENTIFICATION_REQUIRED_PROP = "PIIAuthRequired";

    /** Default property on config node for the number of days a token is valid for. */
    private static final String DEFAULT_TOKEN_LIFETIME_PROP = "daysRelativeToEventWhileSurveyIsValid";

    /** Clinic property for the number of days a token is valid for. */
    private static final String TOKEN_LIFETIME_PROP = "daysRelativeToEventWhileSurveyIsValid";

    /** Property on config node for the number of days draft responses from patients are kept. */
    private static final String DRAFT_LIFETIME_PROP = "draftLifetime";

    /** Whether or not tokenless auth is enabled by default (used in case of errors). */
    private static final Boolean TOKENLESS_AUTH_ENABLED_DEFAULT = false;

    /** Whether or not patient identification is required by default (used in case of errors). */
    private static final Boolean PATIENT_IDENTIFICATION_REQUIRED_DEFAULT = true;

    /** The number of days a token is valid for by default (used in case of errors). */
    private static final int TOKEN_LIFETIME_DEFAULT = 0;

    /** The number of days a patient's draft response is kept for by default (used in case of errors). */
    private static final int DRAFT_LIFETIME_DEFAULT = -1;

    @Reference
    private FormUtils formUtils;

    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ThreadResourceResolverProvider rrp;

    /**
     * Returns the property specified in the configuration node. The resource resolver must have {@code jcr:read}
     * access to the configuration node. Returns null if the configuration cannot be found.
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
    public boolean isTokenlessAuthEnabled()
    {
        try
        {
            Property enabled = getConfig(TOKENLESS_AUTH_ENABLED_PROP);
            return enabled == null ? TOKENLESS_AUTH_ENABLED_DEFAULT : enabled.getBoolean();
        } catch (RepositoryException e) {
            return TOKENLESS_AUTH_ENABLED_DEFAULT;
        }
    }

    @Override
    public boolean isPatientIdentificationRequired()
    {
        try
        {
            Property required = getConfig(PATIENT_IDENTIFICATION_REQUIRED_PROP);
            return isTokenlessAuthEnabled()
                || (required == null ? PATIENT_IDENTIFICATION_REQUIRED_DEFAULT : required.getBoolean());
        } catch (RepositoryException e) {
            return PATIENT_IDENTIFICATION_REQUIRED_DEFAULT;
        }
    }

    @Override
    public int getDaysRelativeToEventWhileSurveyIsValid()
    {
        try
        {
            Property lifetime = getConfig(DEFAULT_TOKEN_LIFETIME_PROP);
            return lifetime == null ? TOKEN_LIFETIME_DEFAULT : (int) lifetime.getLong();
        } catch (RepositoryException e) {
            return TOKEN_LIFETIME_DEFAULT;
        }
    }

    @Override
    public int getDaysRelativeToEventWhileSurveyIsValid(Node visitInformationNode)
    {
        final int defaultTokenLifetime = getDaysRelativeToEventWhileSurveyIsValid();
        try
        {
            Node visitSubject = this.formUtils.getSubject(visitInformationNode, "/SubjectTypes/Patient/Visit");
            Node clinicNode = AppointmentUtils.getValidClinicNode(this.formUtils, visitSubject);

            if (clinicNode != null && clinicNode.hasProperty(TOKEN_LIFETIME_PROP)) {
                return (int) clinicNode.getProperty(TOKEN_LIFETIME_PROP).getLong();
            }
        } catch (RepositoryException e) {
            // TODO Auto-generated catch block
        }
        return defaultTokenLifetime;
    }

    @Override
    public int getDraftLifetime()
    {
        try
        {
            Property lifetime = getConfig(DRAFT_LIFETIME_PROP);
            int value = lifetime == null ? DRAFT_LIFETIME_DEFAULT : (int) lifetime.getLong();
            return value < -1 ? DRAFT_LIFETIME_DEFAULT : value;
        } catch (RepositoryException e) {
            return DRAFT_LIFETIME_DEFAULT;
        }
    }
}
