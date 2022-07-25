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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.Privilege;
import javax.jcr.version.VersionManager;

import org.apache.jackrabbit.api.JackrabbitSession;
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
import io.uhndata.cards.permissions.spi.PermissionsManager;
import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;
import io.uhndata.cards.utils.ThreadResourceResolverProvider;

/**
 * Change listener that applies the cards:clinicForms to all forms related to a visit, as well as the visit and its
 * parent. This restriction will selectively allow users to access those forms based on whether or not they
 * belong to the given clinic.
 *
 * @version $Id$
 */
@Component(immediate = true, property = {
    ResourceChangeListener.PATHS + "=/Forms"
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

    /** List of nodes to checkin after finishing commits. */
    private final ThreadLocal<Set<String>> nodesToCheckin = ThreadLocal.withInitial(HashSet::new);

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
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
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "ClinicFormsRestriction"))) {
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
            final String formPath = "/Forms/" + path.split("/", 4)[2];
            final Node form = session.getNode(formPath);
            this.rrp.push(localResolver);
            final Node subject = this.formUtils.getSubject(form);
            final String subjectType = this.subjectTypeUtils.getLabel(this.subjectUtils.getType(subject));
            final Node questionnaire = this.formUtils.getQuestionnaire(form);
            VersionManager versionManager = session.getWorkspace().getVersionManager();

            if (isVisitInformation(questionnaire)) {
                handleVisitInformationForm(form, subject, questionnaire, session, versionManager);
            } else if ("Visit".equals(subjectType) && event.getType() == ResourceChange.ChangeType.ADDED
                || event.getType() == ResourceChange.ChangeType.CHANGED) {
                // Check if there exists a Visit Information form to apply an ACL
                handleVisitDataForm(form, subject, session, versionManager);
            }

            // Finish all commits
            session.save();
            this.nodesToCheckin.get().forEach(node -> {
                try {
                    versionManager.checkin(node);
                } catch (final RepositoryException e) {
                    LOGGER.warn("Failed to check in node {}: {}", node, e.getMessage(), e);
                }
            });
            this.rrp.pop();
            this.nodesToCheckin.remove();
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
     * @param versionManager a version manager for checking out nodes
     * @throws RepositoryException if any required data could not be checked
     */
    private void handleVisitInformationForm(final Node form, final Node visit, final Node questionnaire,
        final Session session, final VersionManager versionManager) throws RepositoryException
    {
        String clinicPath;
        try {
            final Node clinicQuestion = questionnaire.getNode("clinic");
            clinicPath = (String) this.formUtils.getValue(this.formUtils.getAnswer(form, clinicQuestion));
        } catch (PathNotFoundException e) {
            // The clinic cannot be found -- do not continue
            return;
        }

        // Find every form who belongs to this visit
        String query = "SELECT f.* FROM [cards:Form] AS f WHERE f.'relatedSubjects'='"
            + visit.getProperty(JCR_UUID).getString() + "'";
        NodeIterator results = session.getWorkspace().getQueryManager().createQuery(query, "JCR-SQL2").execute()
            .getNodes();
        final Principal trustedUsers =
            ((JackrabbitSession) session).getPrincipalManager().getPrincipal("TrustedUsers");
        final String clinicGroupName = session.getNode(clinicPath).getProperty("clinicName").getString();
        while (results.hasNext()) {
            // Apply ACL to the root level node, and everything should inherit it
            Node node = results.nextNode();
            this.resetClinicFormsRestriction(node.getPath(), clinicGroupName, session, trustedUsers, versionManager,
                false);
        }
        // Also apply to the visit and its parent subject
        this.resetClinicFormsRestriction(visit.getPath(), clinicGroupName, session, trustedUsers, versionManager,
            true);
        this.resetClinicFormsRestriction(visit.getParent().getPath(), clinicGroupName, session, trustedUsers,
            versionManager, true);
    }

    /**
     * Given a form related to a subject, apply the appropriate ACL entry to it.
     *
     * @param form the form that has changed
     * @param visit the visit subject which should have all forms checked for completion
     * @param session a service session providing access to the repository
     * @param versionManager a version manager for checking out nodes
     * @throws RepositoryException if any required data could not be retrieved
     */
    private void handleVisitDataForm(final Node form, final Node visit, final Session session,
        final VersionManager versionManager)
        throws RepositoryException
    {
        // Grab the Visit information questionnaire for this visit
        Node visitInformationQuestionnaire = session.getNode("/Questionnaires/Visit information");
        String query = "SELECT f.* FROM [cards:Form] AS f WHERE f.'subject'='"
            + visit.getProperty(JCR_UUID).getString() + "' AND f.'questionnaire'='"
            + visitInformationQuestionnaire.getProperty(JCR_UUID).getString() + "'";
        NodeIterator results = session.getWorkspace().getQueryManager().createQuery(query, "JCR-SQL2").execute()
            .getNodes();
        if (results.hasNext()) {
            Node visitForm = results.nextNode();
            final Principal trustedUsers =
                ((JackrabbitSession) session).getPrincipalManager().getPrincipal("TrustedUsers");
            try {
                final Node clinicQuestion = visitInformationQuestionnaire.getNode("clinic");
                final String clinicPath =
                    (String) this.formUtils.getValue(this.formUtils.getAnswer(visitForm, clinicQuestion));
                final String clinicGroupName = session.getNode(clinicPath).getProperty("clinicName").getString();
                this.resetClinicFormsRestriction(form.getPath(), clinicGroupName, session, trustedUsers,
                    versionManager, false);
            } catch (PathNotFoundException e) {
                // The clinic cannot be found -- do not continue
                return;
            }
        }
    }

    /**
     * Remove any {@code cards:clinicForms} restriction currently on the given path and replace it with a new one.
     *
     * @param path path to apply the {@code cards:clinicForms} restriction to
     * @param clinicGroupName the clinic group name
     * @param session session to use
     * @param principal principal to apply to
     * @param checkout whether or not to checkout the node beforehand. Be careful not to apply this to anything under
     *     /Forms, as that will form an infinite loop
     */
    private void resetClinicFormsRestriction(final String path, final String clinicGroupName, final Session session,
        final Principal principal, final VersionManager versionManager, final boolean checkout)
        throws RepositoryException
    {
        if (checkout) {
            versionManager.checkout(path);
            this.nodesToCheckin.get().add(path);
        }
        Set<String> restrictionSet = new HashSet<String>();
        restrictionSet.add("cards:clinicForms");

        try {
            this.permissionsManager.removeAccessControlEntry(path, true, principal, restrictionSet, session);
        } catch (RepositoryException e) {
            // Restriction does not exist, continue
        }

        this.permissionsManager.addAccessControlEntry(path, true,
            principal, new String[] { Privilege.JCR_ALL },
            Map.of("cards:clinicForms", session.getValueFactory().createValue(clinicGroupName)), session);
    }

    /**
     * Check if a questionnaire is the {@code Visit Information} questionnaire.
     *
     * @param questionnaire the questionnaire to check
     * @return {@code true} if the questionnaire is indeed the {@code Visit Information}
     */
    private static boolean isVisitInformation(final Node questionnaire)
    {
        try {
            return questionnaire != null && "/Questionnaires/Visit information".equals(questionnaire.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if questionnaire is Visit information: {}", e.getMessage(), e);
            return false;
        }
    }
}
