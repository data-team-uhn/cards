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

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.QueryResult;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;
import io.uhndata.cards.permissions.spi.PermissionsManager;

/**
 * An {@link Editor} that sets an ACL for all forms belonging to a visit, depending
 * on the answer to a the VisitInformationForm.
 *
 * @version $Id$
 */
public class ClinicRestrictionEditor extends DefaultEditor
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicRestrictionEditor.class);

    private static final String VISIT_INFORMATION_PATH = "/Questionnaires/Visit information";

    private FormUtils formUtils;

    private NodeBuilder currentNodeBuilder;

    private QuestionnaireUtils questionnaireUtils;

    private ResourceResolverFactory rrf;

    private PermissionsManager permissionsManager;

    private boolean isVisitInformationForm;

    private boolean isClinicQuestion;

    private boolean isFormChild;

    private String visitID;

    private String primaryType;

    public ClinicRestrictionEditor(NodeBuilder builder, ResourceResolverFactory rrf,
        QuestionnaireUtils questionnaireUtils, FormUtils formUtils, PermissionsManager permissionsManager,
        boolean isFormChild)
    {
        this.currentNodeBuilder = builder;
        this.rrf = rrf;
        this.questionnaireUtils = questionnaireUtils;
        this.formUtils = formUtils;
        this.permissionsManager = permissionsManager;
        this.isFormChild = isFormChild;
        this.isClinicQuestion = isClinicQuestion(builder);
        LOGGER.warn("Restriction editor created "
            + (builder.hasProperty("@path") ? builder.getProperty("@path").getValue(Type.REFERENCE) : ""));
        // We care about this change iff:
        // We are the root node (always recurse downwards from root)
        // We are the FormsHomepage node, and a child corresponding to a visit information form is deleted
        // We are the FormsHomepage node, and a child whose relatedSubject is a Visit is added or changed
        // We are a Forms node corresponding to a Visit information form, and the clinic node is deleted
        // We are a Forms node corresponding to a form whose relatedSubject is a Visit, and a child is created
        // We are a Question node corresponding to a clinic question in a Visit information form, and we are changed,
        // deleted, or added
        this.primaryType = builder.getString("jcr:primaryType");
        if (this.isClinicQuestion) {
            // TODO: Fill out visitID
            this.visitID = getVisitID(builder);
            LOGGER.warn("Clinic res edit created");
        }
    }

    @Override
    public void propertyChanged(PropertyState before, PropertyState after) throws CommitFailedException
    {
        // relatedSubject changing on a cards:Form would cause an ACL change
        // Also value changing on a cards:Question corresponding to the /Questionnaires/Visit information/clinic
        // would cause an ACL change
        //if ()
        propertyAdded(after);
    }

    @Override
    public void propertyAdded(final PropertyState after)
        throws CommitFailedException
    {
        // We only care about properties added if we're the answer to the clinic question
        // If so, we need to update everything
        if (this.isClinicQuestion) {
            LOGGER.warn("Test added value");
            //adjustACLs(after.getValue(Type.REFERENCE));
        }
    }

    /**
     * Given that our node corresponds to an answer for a Form of a visit, adjust the ACL for all forms with the same
     * clinic.
     */
    private void adjustACLs(final String clinicPath)
    {
        // Grab the uuid for the visit information questionnaire
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, "ClinicRestriction");
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(params)) {
            final JackrabbitSession session = (JackrabbitSession) resolver.adaptTo(Session.class);
            final Node visitInformationQuestionnaire = session.getNode(ClinicRestrictionEditor.VISIT_INFORMATION_PATH);
            final Node clinicQuestion = this.questionnaireUtils.getQuestion(visitInformationQuestionnaire, "clinic");

            // Perform a query for any Visit Information form for this subject
            String query = "SELECT f.* FROM [cards:Form] AS f"
                + " WHERE f.'relatedSubjects'='" + this.visitID + "'";

            QueryResult results = session.getWorkspace().getQueryManager().createQuery(query, "JCR-SQL2").execute();
            NodeIterator rows = results.getNodes();

            // Adjust the ACL for this form, and its descendants
            Map<String, Value> permissions = new HashMap<>();
            permissions.put("cards:clinicForms", session.getValueFactory().createValue(clinicPath));
            while (rows.hasNext()) {
                this.permissionsManager.addAccessControlEntry(rows.nextNode().getPath(), false,
                    EveryonePrincipal.getInstance(), new String[] { Privilege.JCR_ALL }, permissions, session);
                // TODO: Recurse to descendants
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to set ACLs: {}", e.getMessage(), e);
        }
    }

    private void removeACLs(final String oldClinicPath)
    {

    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        // We care about this change iff:
        // We are the FormsHomepage node, and a child whose relatedSubject is a Visit is added or changed
        // We are a Forms node corresponding to a form whose relatedSubject is a Visit, and a child is created
        // The above, but all of our descendants must have our ACL applied to them
        if ("cards:FormHomepage".equals(this.primaryType) && isVisitInformationNode(after.getChildNode(name))) {
            //this.removeACLs(before.getChildNode().getChildNode())
        } else if ("cards:Form".equals(this.primaryType) && isVisitInformationNode(after)) {
            NodeState deleted = after.getChildNode(name);
            // Check that deleted corresponds to a clinic question
            //this.removeACLs(before.getChildNode(name))
        }
        return null;
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        if (this.shouldRecurse(this.currentNodeBuilder.getChildNode(name).getString("jcr:primaryType"))
            || this.isFormChild) {
            LOGGER.warn("childNodeChanged:" + name);
            return new ClinicRestrictionEditor(this.currentNodeBuilder.getChildNode(name),
                this.rrf,
                this.questionnaireUtils,
                this.formUtils,
                this.permissionsManager,
                this.isFormChild || "cards:Form".equals(this.primaryType));
        }
        return null;
    }

    @Override
    public Editor childNodeDeleted(java.lang.String name, NodeState before) throws CommitFailedException
    {
        // In the case where a child node is deleted, we care about this change iff:
        // We are the FormsHomepage node, and a child corresponding to a visit information form is deleted
        // We are a Forms node corresponding to a Visit information form, and the clinic node is deleted
        if ("cards:FormHomepage".equals(this.primaryType) && isVisitInformationNode(before.getChildNode(name))) {
            //this.removeACLs(before.getChildNode().getChildNode())
        } else if ("cards:Form".equals(this.primaryType) && isVisitInformationNode(before)) {
            NodeState deleted = before.getChildNode(name);
            // Check that deleted corresponds to a clinic question
            //this.removeACLs(before.getChildNode(name))
        }
        return null;
    }

    /***
     * Determine if the given node is a visit information node.
     */
    private boolean isVisitInformationNode(NodeState node)
    {
        String questionnaireID = node.getString("questionnaire");
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, "ClinicRestriction");
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(params)) {
            final JackrabbitSession session = (JackrabbitSession) resolver.adaptTo(Session.class);
            final Node questionnaireNode = session.getNodeByIdentifier(questionnaireID);
            return questionnaireNode != null && this.VISIT_INFORMATION_PATH.equals(questionnaireNode.getPath());
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (RepositoryException e) {
            // Will happen a lot during startup
            LOGGER.warn("Failed to get clinic question: {}", e.getMessage(), e);
        }
        return false;
    }

    private boolean isClinicQuestion(NodeBuilder node)
    {
        Map<String, Object> params = new HashMap<>();
        params.put(ResourceResolverFactory.SUBSERVICE, "ClinicRestriction");
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(params)) {
            if (node.hasProperty("question")) {
                LOGGER.warn("isClinicQuestion check: "
                    + resolver.getResource(this.VISIT_INFORMATION_PATH + "/clinic").getName()
                    + " " + node.getProperty("question").getValue(Type.REFERENCE));
            }
            return resolver != null
                && node.hasProperty("question")
                && resolver.getResource(this.VISIT_INFORMATION_PATH + "/clinic").getName()
                    .equals(node.getProperty("question").getValue(Type.REFERENCE));
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (NullPointerException e) {
            // Will happen a lot during startup
            LOGGER.warn("Failed to get clinic question: {}", e.getMessage(), e);
        }

        return false;
    }

    // Determine whether this node is potentially a parent of a node type we care about
    // That is, rep:root, cards:FormsHomepage, or cards:Form
    private boolean shouldRecurse(String primaryType)
    {
        return primaryType != null
            && ("rep:root".equals(primaryType)
            || "cards:FormsHomepage".equals(primaryType)
            || "cards:Form".equals(primaryType));
    }

    private String getVisitID(NodeBuilder node)
    {
        // To convert node
        return "";
    }
}
