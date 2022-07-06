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
package io.uhndata.cards.proms.internal.permissions;

import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;

/**
 * A restriction that makes a permissions entry only be valid on a form for a specific Clinic.
 *
 * @version $Id$
 */
public class ClinicRestrictionPattern implements RestrictionPattern
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicRestrictionPattern.class);

    private static final String VISIT_INFORMATION_PATH = "/Questionnaires/Visit information";

    /** The current session of the user activating this restriction. **/
    private ResourceResolverFactory rrf;

    /** For form management. **/
    private final FormUtils formUtils;

    /** For questionnaire management. **/
    private final QuestionnaireUtils questionnaireUtils;

    /** Value we were set to. **/
    private final String clinic;

    /**
     * Constructor which receives the configured restriction.
     *
     * @param rrf resource resolver factory providing access to resources
     * @param formUtils a reference to a FormUtils object
     * @param questionnaireUtils a reference to a QuestionnaireUtils object
     * @param clinic a clinic to match against
     */
    public ClinicRestrictionPattern(final ResourceResolverFactory rrf, final FormUtils formUtils,
        final QuestionnaireUtils questionnaireUtils, final String clinic)
    {
        this.formUtils = formUtils;
        this.questionnaireUtils = questionnaireUtils;
        this.rrf = rrf;
        this.clinic = clinic;
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        // If this is being called on a property, the user already has access to the node
        // In most cases, we want to apply the same rule to it
        // However, since this restriction pattern is costly to apply, we shortcut to applying to it
        if (property != null || this.rrf == null) {
            return true;
        }

        // Grab the group named after this clinic, and see if the user is part of it
        try (ResourceResolver srr = this.rrf.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "ClinicFormsRestriction"))) {
            ResourceResolver trr = this.rrf.getThreadResourceResolver();
            final JackrabbitSession serviceSession = (JackrabbitSession) srr.adaptTo(Session.class);
            final JackrabbitSession userSession = (JackrabbitSession) trr.adaptTo(Session.class);
            final UserManager userManager = serviceSession.getUserManager();
            Group group = (Group) userManager.getAuthorizable(this.clinic);
            User user = (User) userManager.getAuthorizable(userSession.getUserID());
            return group != null && group.isMember(user);
        } catch (LoginException | RepositoryException e) {
            LOGGER.error("Error calculating ACL for clinic restriction.\n" + e.getMessage());
            return false;
        } catch (NullPointerException e) {
            // There are a lot of nodes that are observed by this Restriction Pattern
            // In each case where the node does not exist, we do not want to apply to them
            LOGGER.error("Null reference exception while calculating ClinicRestrictionPattern ACL: " + e.getMessage(),
                e);
            return false;
        }
    }

    @Override
    public boolean matches(String path)
    {
        // This method doesn't seem to be called, the one above is used instead
        return false;
    }

    @Override
    public boolean matches()
    {
        // This is not a repository-wide restriction, it only applies to specific nodes
        return false;
    }
}
