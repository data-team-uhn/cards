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
package io.uhndata.cards.proms.internal.permissions;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.QueryResult;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;
import io.uhndata.cards.dataentry.api.SubjectTypeUtils;
import io.uhndata.cards.dataentry.api.SubjectUtils;
import io.uhndata.cards.permissions.spi.PermissionsManager;
import io.uhndata.cards.utils.ThreadResourceResolverProvider;

/**
 * Change listener looking for new or modified forms related to a Visit subject. Initially, when a new Visit Information
 * form is created, it also creates any forms in the specified survey set that need to be created, based on the survey
 * set's specified frequency. Then, when all the forms required for a visit are completed, it also marks in the Visit
 * Information that the patient has completed the surveys.
 *
 * @version $Id$
 */
// Temporary while testing, please flag if it's still here
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
@Component(immediate = true, property = {
    ResourceChangeListener.PATHS + "=/Forms",
    ResourceChangeListener.CHANGES + "=ADDED",
    ResourceChangeListener.CHANGES + "=CHANGED"
})
public class ClinicRestrictionListener implements ResourceChangeListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicRestrictionListener.class);

    private static final String JCR_UUID = "jcr:uuid";

    /** Provides access to resources. */
    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectUtils subjectUtils;

    @Reference
    private SubjectTypeUtils subjectTypeUtils;

    @Reference
    private PermissionsManager permissionsManager;

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        LOGGER.warn("onChange called");
        changes.forEach(this::handleEvent);
    }

    /**
     * For every form change, either creates the forms scheduled for a visit, if it's a new Visit Information, or
     * updates the flag that indicates that all the forms have been completed by the patient, if it's a regular survey
     * needed for a visit.
     *
     * @param event a change that happened in the repository
     */
    private void handleEvent(final ResourceChange event)
    {
        // Acquire a service session with the right privileges for accessing visits and their forms
        try (ResourceResolver localResolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "ClinicRestriction"))) {
            // Get the information needed from the triggering form
            final Session session = localResolver.adaptTo(Session.class);
            if (!session.nodeExists(event.getPath())) {
                return;
            }
            final String path = event.getPath();
            // Only affect forms and their children
            if (!path.startsWith("/Forms/")) {
                return;
            }

            // Grab the parent form
            LOGGER.warn("New thing: {} {}", event.getType(), path);
            final String formPath = "/Forms/" + path.split("/", 4)[2];
            final Node form = session.getNode(formPath);
            this.rrp.push(localResolver);
            final Node subject = this.formUtils.getSubject(form);
            final String subjectType = this.subjectTypeUtils.getLabel(this.subjectUtils.getType(subject));
            final Node questionnaire = this.formUtils.getQuestionnaire(form);

            if (isVisitInformation(questionnaire)) {
                handleVisitInformationForm(form, subject, questionnaire, session);
            } else if ("Visit".equals(subjectType) && event.getType() == ResourceChange.ChangeType.ADDED
                || event.getType() == ResourceChange.ChangeType.CHANGED) {
                // The form has a new thing
                // Check if there exists a Visit Information form to apply an ACL
                handleVisitDataForm(form, subject, session);
            }
            session.save();
            this.rrp.pop();
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (final RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Creates any forms that need to be completed per the visit's questionnaire set.
     *
     * @param form the Visit Information form that triggered the current event
     * @param visit the Visit that is the subject for the triggering form
     * @param questionnaire the Visit Information questionnaire
     * @param session a service session providing access to the repository
     * @throws RepositoryException if any required data could not be checked
     */
    private void handleVisitInformationForm(final Node form, final Node visit, final Node questionnaire,
        final Session session) throws RepositoryException
    {
        String clinicPath;
        try {
            final Node clinicQuestion = questionnaire.getNode("clinic");
            clinicPath = (String) this.formUtils.getValue(this.formUtils.getAnswer(form, clinicQuestion));
        } catch (PathNotFoundException e) {
            // The clinic cannot be found -- do not continue
            return;
        }

        LOGGER.warn("clinic path found: " + clinicPath);

        // Find every form who belongs to this visit
        // Add or remove their ACL
        String query = "SELECT f.* FROM [cards:Form] AS f WHERE f.'relatedSubjects'='"
            + visit.getProperty(JCR_UUID).getString() + "'";
        QueryResult results = session.getWorkspace().getQueryManager().createQuery(query, "JCR-SQL2").execute();
        NodeIterator rows = results.getNodes();
        final Principal trustedUsers = EveryonePrincipal.getInstance();
        //final Principal trustedUsers =
        //    ((JackrabbitSession) session).getPrincipalManager().getPrincipal("TrustedUsers");
        while (rows.hasNext()) {
            // Apply to the root level node, and everything should inherit it
            Node node = rows.nextNode();
            this.removeClinicFormsRestriction(node.getPath(), session);
            this.permissionsManager.addAccessControlEntry(node.getPath(), true,
                trustedUsers, new String[] { Privilege.JCR_ALL },
                Map.of("cards:clinicForms", session.getValueFactory().createValue(clinicPath)), session);
        }
    }

    private void recursiveAlterClinicACL(final Node node, final String clinicPath, final Session session,
        final Principal principal)
        throws RepositoryException
    {
        // Reset the cards:clinicForms ACL on the given node
        this.removeClinicFormsRestriction(node.getPath(), session);
        this.permissionsManager.addAccessControlEntry(node.getPath(), true,
            principal, new String[] { Privilege.JCR_ALL },
            Map.of("cards:clinicForms", session.getValueFactory().createValue(clinicPath)), session);

        // Recurse downward
        NodeIterator results = node.getNodes();
        while (results.hasNext()) {
            recursiveAlterClinicACL(results.nextNode(), clinicPath, session, principal);
        }
    }

    /***
     * Modified version of {@code PermissionsManager.removeAccessControlEntry()} that only looks at the type.
     */
    private void removeClinicFormsRestriction(String target, Session session) throws RepositoryException
    {
        AccessControlManager acm = session.getAccessControlManager();
        JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(acm, target);
        if (acl != null) {
            // Find the necessary AccessControlEntry to remove
            JackrabbitAccessControlEntry[] entries = (JackrabbitAccessControlEntry[]) acl.getAccessControlEntries();
            JackrabbitAccessControlEntry toRemove = null;
            for (JackrabbitAccessControlEntry entry : entries) {
                // Find the ACL entry we care about
                if (Arrays.asList(entry.getRestrictionNames()).contains("cards:clinicForms")) {
                    // We've found the correct entry, make a note of it
                    toRemove = entry;
                    break;
                }
            }

            // Remove it if it was found
            if (toRemove != null) {
                acl.removeAccessControlEntry(toRemove);
                acm.setPolicy(target, acl);
            }
        }
    }

    /**
     * Given a form related to a subject, apply the appropriate ACL entry to it.
     *
     * @param form the form that has changed
     * @param visit the visit subject which should have all forms checked for completion
     * @param session a service session providing access to the repository
     * @throws RepositoryException if any required data could not be retrieved
     */
    private void handleVisitDataForm(final Node form, final Node visit, final Session session)
        throws RepositoryException
    {
        // Grab the Visit information questionnaire for this visit
        Node visitInformationQuestionnaire = session.getNode("/Questionnaires/Visit information");
        String query = "SELECT f.* FROM [cards:Form] AS f WHERE f.'subject'='"
            + visit.getProperty(JCR_UUID).getString() + "' AND f.'questionnaire'='"
            + visitInformationQuestionnaire.getProperty(JCR_UUID).getString() + "'";
        QueryResult results = session.getWorkspace().getQueryManager().createQuery(query, "JCR-SQL2").execute();
        NodeIterator nodes = results.getNodes();
        if (nodes.hasNext()) {
            Node visitForm = nodes.nextNode();
            final Principal trustedUsers = EveryonePrincipal.getInstance();
            //final Principal trustedUsers =
            //    ((JackrabbitSession) session).getPrincipalManager().getPrincipal("TrustedUsers");
            try {
                final Node clinicQuestion = visitInformationQuestionnaire.getNode("clinic");
                final String clinicPath =
                    (String) this.formUtils.getValue(this.formUtils.getAnswer(visitForm, clinicQuestion));
                this.removeClinicFormsRestriction(form.getPath(), session);
                this.permissionsManager.addAccessControlEntry(form.getPath(), true,
                    trustedUsers, new String[] { Privilege.JCR_ALL },
                    Map.of("cards:clinicForms", session.getValueFactory().createValue(clinicPath)), session);
            } catch (PathNotFoundException e) {
                // The clinic cannot be found -- do not continue
                return;
            }
        }
    }

    /**
     * Check if a questionnaire is the {@code Visit Information} questionnaire.
     *
     * @param questionnaire the questionnaire to check
     * @return {@code true} if the questionnaire is indeed the {@code Visit Information}
     */
    private static boolean isVisitInformation(final Node questionnaire)
    {
        return isSpecificQuestionnaire(questionnaire, "/Questionnaires/Visit information");
    }

    /**
     * Check if a questionnaire is the specified questionnaire.
     *
     * @param questionnaire the questionnaire to check
     * @param questionnairePath the path to the desired questionnaire
     * @return {@code true} if the questionnaire is indeed the questionnaire specified by path
     */
    private static boolean isSpecificQuestionnaire(final Node questionnaire, final String questionnairePath)
    {
        try {
            return questionnaire != null && questionnairePath.equals(questionnaire.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if form is of questionnaire type {}: {}", questionnairePath, e.getMessage(), e);
            return false;
        }
    }
}
