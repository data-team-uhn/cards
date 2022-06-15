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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.QueryResult;
import javax.jcr.query.RowIterator;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
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

    /**
     * Constructor which receives the configured restriction.
     *
     * @param rrf resource resolver factory providing access to resources
     * @param formUtils a reference to a FormUtils object
     * @param questionnaireUtils a reference to a QuestionnaireUtils object
     */
    public ClinicRestrictionPattern(final ResourceResolverFactory rrf, final FormUtils formUtils,
        final QuestionnaireUtils questionnaireUtils)
    {
        this.formUtils = formUtils;
        this.questionnaireUtils = questionnaireUtils;
        this.rrf = rrf;
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        if (property != null) {
            return true;
        }
        // Make sure we can discern what this node is, before continuing
        if (!tree.hasProperty("sling:resourceType")) {
            return false;
        }

        if (tree.getProperty("sling:resourceType").getValue(Type.STRING).equals("cards/Form")) {
            return appliesToForm(tree);
        } else {
            // Unknown node type: ignore
            return false;
        }
    }

    /**
     * Returns whether or not this restriction pattern applies to the given form.
     *
     * @param tree A Jackrabbit {@code Tree} object corresponding to a {@code Form} resource.
     * @return If true, this restriction pattern applies to the given input.
     */
    private boolean appliesToForm(final Tree tree)
    {
        try (ResourceResolver srr = this.rrf.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "ClinicFormsRestriction"))) {
            // The thread resolver must not be closed, so it's outside the try-with-resources block
            ResourceResolver trr = this.rrf.getThreadResourceResolver();
            final JackrabbitSession serviceSession = (JackrabbitSession) srr.adaptTo(Session.class);
            final JackrabbitSession userSession = (JackrabbitSession) trr.adaptTo(Session.class);

            final String clinic = getClinic(tree.getProperty("subject").getValue(Type.STRING), serviceSession);
            if (StringUtils.isBlank(clinic)) {
                return false;
            }
            final Node clinicMapping = serviceSession.getNode(clinic);

            // Check if the current session user belongs to the group specified
            final String groupName = clinicMapping.getProperty("clinicName").getString();
            final UserManager userManager = serviceSession.getUserManager();
            Group group = (Group) userManager.getAuthorizable(groupName);
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

    /**
     * Return the visit information form for a given subject, or null if it is inaccessible or none exists.
     *
     * @param subjectID The ID of the subject
     * @param session a service session with read access to the repository
     * @return A Visit Information form for the given subject, or null if none exists. If multiple exist, returns the
     *         first one found.
     */
    public String getClinic(String subjectID, Session session) throws RepositoryException
    {
        // Grab the uuid for the visit information questionnaire
        final Node visitInformationQuestionnaire = session.getNode(ClinicRestrictionPattern.VISIT_INFORMATION_PATH);
        final Node clinicQuestion = this.questionnaireUtils.getQuestion(visitInformationQuestionnaire, "clinic");

        // Perform a query for any Visit Information form for this subject
        String query = "SELECT a.value FROM [cards:Form] AS f INNER JOIN [cards:Answer] AS a ON ISDESCENDANTNODE(a, f)"
            + " WHERE f.'subject'='" + subjectID
            + "' AND f.'questionnaire'='" + visitInformationQuestionnaire.getIdentifier()
            + "' AND a.'question'='" + clinicQuestion.getIdentifier() + "'";

        QueryResult results = session.getWorkspace().getQueryManager().createQuery(query, "JCR-SQL2").execute();
        RowIterator rows = results.getRows();
        if (rows.hasNext()) {
            return rows.nextRow().getValue("a.value").getString();
        }
        return null;
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
