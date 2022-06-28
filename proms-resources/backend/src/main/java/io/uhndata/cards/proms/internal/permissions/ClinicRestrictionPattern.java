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
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.QueryResult;

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
        // If this is being called on a property, the user already has access to the node
        // In most cases, we want to apply the same rule to it
        // However, since this restriction pattern is costly to apply, we shortcut to applying to it
        if (property != null) {
            return true;
        }

        // Determine if this is a subject
        if (tree.hasProperty("sling:resourceType")
            && tree.getProperty("sling:resourceType").getValue(Type.STRING).equals("cards/Subject")) {
            return appliesToSubject(tree.getProperty("jcr:uuid").getValue(Type.STRING));
        }

        // This Node might not be the parent tree, find it among this tree's ancestors if possible
        Tree formNode = getFormNodeParent(tree);

        if (formNode != null) {
            return appliesToForm(formNode);
        } else {
            // Not a child of a form: ignore
            return false;
        }
    }

    /**
     * Returns the form node corresponding to this tree, or null if none exists.
     *
     * @param tree Tree which might be a form node
     * @return The form node parent, or null if none exists
     */
    public Tree getFormNodeParent(final Tree tree)
    {
        if (tree.hasProperty("sling:resourceType")
            && tree.getProperty("sling:resourceType").getValue(Type.STRING).equals("cards/Form")) {
            return tree;
        } else {
            // Recurse upwards
            if (!tree.isRoot()) {
                try {
                    return getFormNodeParent(tree.getParent());
                } catch (IllegalStateException e) {
                    // Should not happen, since we check for root
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    /**
     * Returns whether or not this restriction pattern applies to the given subject.
     *
     * @param subjectId The jcr:uuid of the subject node
     * @return If true, this restriction pattern applies to the given input.
     */
    private boolean appliesToSubject(final String subjectId)
    {
        try (ResourceResolver srr = this.rrf.getServiceResourceResolver(
            Map.of(ResourceResolverFactory.SUBSERVICE, "ClinicFormsRestriction"))) {
            // The thread resolver must not be closed, so it's outside the try-with-resources block
            ResourceResolver trr = this.rrf.getThreadResourceResolver();
            final JackrabbitSession serviceSession = (JackrabbitSession) srr.adaptTo(Session.class);
            final JackrabbitSession userSession = (JackrabbitSession) trr.adaptTo(Session.class);

            final String clinic = getClinic(subjectId, serviceSession);
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
     * Returns whether or not this restriction pattern applies to the given form.
     *
     * @param tree A Jackrabbit {@code Tree} object corresponding to a {@code Form} resource.
     * @return If true, this restriction pattern applies to the given input.
     */
    private boolean appliesToForm(final Tree tree)
    {
        return appliesToSubject(tree.getProperty("subject").getValue(Type.STRING));
    }

    /**
     * Return the clinic name in the visit information form for a given subject, or null if it is inaccessible or none
     * exists.
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
        String query = "SELECT f.* FROM [cards:Form] AS f"
            + " WHERE f.'relatedSubjects'='" + subjectID
            + "' AND f.'questionnaire'='" + visitInformationQuestionnaire.getIdentifier() + "'";

        QueryResult results = session.getWorkspace().getQueryManager().createQuery(query, "JCR-SQL2").execute();
        NodeIterator rows = results.getNodes();

        // Grab the answer to the Clinic question
        if (rows.hasNext()) {
            return this.formUtils.getValue(this.formUtils.getAnswer(rows.nextNode(), clinicQuestion)).toString();
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
