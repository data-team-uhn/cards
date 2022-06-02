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

import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;

/**
 * A restriction that makes a permissions entry only be valid on a specific Clinic node.
 *
 * @version $Id$
 */
public class ClinicRestrictionPattern implements RestrictionPattern
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicRestrictionPattern.class);

    private static final String VISIT_INFORMATION_PATH = "/Questionnaires/Visit information";

    /** The current session of the user activating this restriction. **/
    private JackrabbitSession session;

    /** The current session of the user activating this restriction. **/
    private ResourceResolver resolver;

    /** For form management. **/
    private final FormUtils formUtils;

    /** For questionnaire management. **/
    private final QuestionnaireUtils questionnaireUtils;

    /**
     * Constructor which receives the configured restriction.
     *
     * @param formUtils a reference to a FormUtils object
     * @param questionnaireUtils a reference to a QuestionnaireUtils object
     * @param resolver a reference to a session for use in resolving nodes
     */
    public ClinicRestrictionPattern(final FormUtils formUtils, final QuestionnaireUtils questionnaireUtils,
        final ResourceResolver resolver)
    {
        this.formUtils = formUtils;
        this.questionnaireUtils = questionnaireUtils;
        this.resolver = resolver;
        this.session = (JackrabbitSession) resolver.adaptTo(Session.class);
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
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
        try {
            final Node formQuestionnaire = this.session.getNodeByIdentifier(
                tree.getProperty("questionnaire").getValue(Type.REFERENCE));
            if (VISIT_INFORMATION_PATH.equals(formQuestionnaire.getPath())) {
                // Grab the clinic mapping specified within this form
                // If any of the following do not exist, we will return null in the NullReferenceException catch
                final Node form = this.session.getNodeByIdentifier(tree.getPath());
                final Node clinicQuestion = this.questionnaireUtils.getQuestion(formQuestionnaire, "clinic");
                final String clinicAnswer = (String) this.formUtils.getValue(
                    this.formUtils.getAnswer(form, clinicQuestion));
                final Node clinicMapping = this.session.getNodeByIdentifier(clinicAnswer);

                // Check if the current session user belongs to the group specified
                final String groupName = clinicMapping.getProperty("clinicName").getString();
                final UserManager userManager = this.session.getUserManager();
                Group group = (Group) userManager.getAuthorizable(groupName);
                User user = (User) userManager.getAuthorizable(this.session.getUserID());
                return group.isMember(user);
            } else {
                // If this is not a visit information form, instead we go looking for the Visit Information form
                // for this subject (implied to be a visit).
                // If the Visit information form for this subject is visible: we can see it and thus by extension
                // this restriction pattern is authorized to apply to us
                return this.getVisitInformationForSubject(tree.getProperty("subject").getValue(Type.STRING)) != null;
            }
        } catch (RepositoryException e) {
            LOGGER.error("Error calculating ACL for clinic restriction.\n" + e.getMessage());
            return false;
        } catch (NullPointerException e) {
            // There are a lot of nodes that are observed by this Restriction Pattern
            // In each case where the node does not exist, we do not want to apply to them
            LOGGER.error("Null reference exception while calculating ClinicRestrictionPattern ACL: " + e.getMessage());
            return false;
        }
    }

    /**
     * Return the visit information form for a given subject, or null if none exists.
     *
     * @param subjectID The ID of the subject
     * @return A Visit Information form for the given subject, or null if none exists. If multiple exist, returns
     *     the first one found.
     */
    public Resource getVisitInformationForSubject(String subjectID) throws RepositoryException
    {
        // Grab the uuid for the visit information questionnaire
        Node visitInformationNode = this.session.getNodeByIdentifier(ClinicRestrictionPattern.VISIT_INFORMATION_PATH);

        // Perform a query for any Visit Information form for this subject
        String query = "SELECT * FROM [cards:Form] AS f WHERE f.'subject'='" + subjectID + "' AND f.'questionnaire'='"
            + visitInformationNode.getProperty("jcr:uuid").getString() + "'";
        Iterator<Resource> nodeIter = this.resolver.findResources(query, "JCR-SQL2");

        if (nodeIter.hasNext()) {
            return nodeIter.next();
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
