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
package io.uhndata.cards.heracles.internal;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Visit Editor looking for new Heracles forms that should have a visit number auto-created.
 * When a new form matching one of the configured questionnaires is detected, it will check for
 * a study stream answer using the study stream reference question's path to determine which
 * visit numbers are valid. It will then check for other instances of the same questionnaire on
 * the subject and set the visit number to the lowest unused valid number for the study stream.
 *
 * @version $Id$
 */
public class VisitNumberEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(VisitNumberEditor.class);

    private final ResourceResolverFactory rrf;

    private final ThreadResourceResolverProvider rrp;

    private final FormUtils formUtils;

    private final QuestionnaireUtils questionnaireUtils;

    private boolean isForm;

    private boolean isNew;

    private List<VisitNumberConfiguration> questionnaireDetails;

    private NodeBuilder currentNodeBuilder;

    public VisitNumberEditor(final NodeBuilder nodeBuilder, final ResourceResolverFactory rrf,
        final ThreadResourceResolverProvider rrp, final FormUtils formUtils,
        final QuestionnaireUtils questionnaireUtils, final boolean isNew,
        final List<VisitNumberConfiguration> questionnaireDetails)
    {
        this.currentNodeBuilder = nodeBuilder;
        this.rrf = rrf;
        this.rrp = rrp;
        this.formUtils = formUtils;
        this.questionnaireUtils = questionnaireUtils;
        this.isNew = isNew;
        this.questionnaireDetails = questionnaireDetails;

        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.rrf
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "VisitNumberEditor")))
        {
            this.rrp.push(resolver);
            mustPopResolver = true;

            this.isForm = formUtils.isForm(nodeBuilder);
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session to check form state {}", e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    @Override
    public Editor childNodeAdded(final String name, final NodeState after)
    {
        return processChildNode(name, null, after);
    }

    @Override
    public Editor childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        return processChildNode(name, before, after);
    }

    private Editor processChildNode(final String name, final NodeState before, final NodeState after)
    {
        if (this.isForm) {
            // Found a form: no need to traverse further
            return null;
        } else {
            return new VisitNumberEditor(this.currentNodeBuilder.getChildNode(name), this.rrf, this.rrp,
                this.formUtils, this.questionnaireUtils, before == null, this.questionnaireDetails);
        }
    }

    @Override
    public void leave(final NodeState before, final NodeState after)
    {
        // When the form node is found, save its NodeBuilder
        if (this.isForm && this.isNew) {
            handleNewForm(after);
        }
    }

    // Check if the new form needs to have a visit number assigned and do so if required
    private void handleNewForm(NodeState after)
    {
        boolean mustPopResolver = false;
        try (ResourceResolver resolver = this.rrf
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "VisitNumberEditor"))) {
            this.rrp.push(resolver);
            mustPopResolver = true;

            Node questionnaireNode = this.formUtils.getQuestionnaire(after);

            // Get the configuration details for the current questionnaire.
            // If no configuration details are available, this form does not need to be modified
            VisitNumberConfiguration details = getQuestionnaireDetails(questionnaireNode);
            if (details != null) {
                String subjectUUID = this.formUtils.getSubjectIdentifier(after);
                String studyStream = getStudyStream(resolver, details, subjectUUID);
                // Retrieve the set of visit numbers that already exist for the current subject and questionnaire
                Set<Long> existingVisits = findExistingVisits(resolver, details, subjectUUID, questionnaireNode);

                // If there is an unused valid visit number for the current questionnaire and stream, save that
                for (Long visit : details.getScheduledVisits(studyStream)) {
                    if (!existingVisits.contains(visit)) {
                        setVisitNumber(visit, details, studyStream, questionnaireNode);
                        return;
                    }
                }
            }
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session to update form {}", e.getMessage(), e);
        } catch (ItemNotFoundException e) {
            LOGGER.info("Unable to locate required information for setting visit number");
        } catch (RepositoryException e) {
            LOGGER.warn("Enexpected error processing visit number {}", e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    // Retrieve the configured details for the specified questionnaire.
    // Returns null if nothing found
    private VisitNumberConfiguration getQuestionnaireDetails(Node questionnaireNode)
    {
        // Get the information needed from the triggering form
        return this.questionnaireDetails.stream()
            .filter(details -> details.matches(questionnaireNode)).findAny().orElse(null);
    }

    // Retrieve the study stream for the specified subject.
    // Cannot retrieve this data directly from the reference question on the current form as Editors cannot
    // see the modifications made by other Editors during the current commit (namely the ReferenceAnswerEditor)
    // so instead pull the study stream from the Study Stream form using the study stream reference question's path.
    // Throws itemNotFoundException if a study stream could not be found.
    private String getStudyStream(final ResourceResolver resolver, final VisitNumberConfiguration details,
        final String subjectUUID)
        throws RepositoryException
    {
        // Study stream question from the current form's questionnaire
        Node studyStreamReferenceQuestion = details.getStudyStreamQuestion(resolver.adaptTo(Session.class));
        // Study stream question from the study stream questionnaire
        Node studyStreamSourceQuestion = studyStreamReferenceQuestion.getProperty("question").getNode();
        // UUID of the study stream questionnaire's study stream question
        String sourceQuestionnaireUUID = this.questionnaireUtils.getOwnerQuestionnaire(studyStreamSourceQuestion)
            .getIdentifier();

        // Get the study stream form for the current subject, if present
        Iterator<Resource> sourceForms = this.getSubjectForms(resolver, sourceQuestionnaireUUID, subjectUUID);
        if (sourceForms.hasNext()) {
            Node foundForm = sourceForms.next().adaptTo(Node.class);
            Node studyStreamAnswer = this.formUtils.getAnswer(foundForm, studyStreamSourceQuestion);
            Property answerProperty = studyStreamAnswer.getProperty("value");

            // Throw an exception if a study stream could not be found to halt processing early
            if (answerProperty == null) {
                throw new ItemNotFoundException();
            }
            return answerProperty.getString();
        } else {
            // Throw an exception if a study stream could not be found to halt processing early
            throw new ItemNotFoundException();
        }
    }

    // Get the set of visit numbers that exist for the current subject and questionnaire.
    // This is study stream agnostic as we don't want to duplicate visits even if the subject has changed streams
    private Set<Long> findExistingVisits(final ResourceResolver resolver, final VisitNumberConfiguration details,
        final String subjectUUID, final Node questionnaireNode)
        throws RepositoryException
    {
        Iterator<Resource> foundForms = getSubjectForms(resolver, questionnaireNode.getIdentifier(), subjectUUID);

        Set<Long> recordedVisits = new HashSet<>();
        while (foundForms.hasNext()) {
            Node foundForm = foundForms.next().adaptTo(Node.class);
            recordedVisits.add(details.getVisitNumber(resolver.adaptTo(Session.class), this.formUtils, foundForm));
        }

        return recordedVisits;
    }

    // Get all the forms for a given subject and questionnaire
    private Iterator<Resource> getSubjectForms(final ResourceResolver resolver, final String questionnaireUUID,
        final String subjectUUID)
    {
        String sqlQuery = "SELECT * FROM [cards:Form] as f WHERE f.'relatedSubjects'='" + subjectUUID + "'"
            + " AND f.'questionnaire'='" + questionnaireUUID + "'"
            + " OPTION (index tag cards)";

        return resolver.findResources(sqlQuery, "JCR-SQL2");
    }

    // Save the specified visit number into the current form based on the current study stream.
    // Will create any required sections.
    private void setVisitNumber(final Long visitNumber, final VisitNumberConfiguration details,
        final String studyStream, final Node questionnaireNode)
    {
        String[] visitPath = details.getVisitPath(studyStream).split("/");

        Node node = questionnaireNode;
        NodeBuilder nodeBuilder = this.currentNodeBuilder;

        try {
            // Traverse through the questionnaire to the visit question,
            // generating any missing answer sections or answers as required
            for (String nodeName : visitPath) {
                node = node.getNode(nodeName);
                nodeBuilder = getChildNodeBuilder(nodeBuilder, node);
            }

            if (nodeBuilder != null) {
                // Found the required answer, save the value
                nodeBuilder.setProperty("value", visitNumber);
            } else {
                LOGGER.warn("Unable to traverse form to locate visit answer");
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Unable save the calculated visit number {}", e.getMessage(), e);
        }
    }

    // Retrieve the answer or answer section child for a given question or section
    NodeBuilder getChildNodeBuilder(final NodeBuilder parent, Node questionnaireNode)
        throws RepositoryException
    {
        // Missing required node: exit early
        if (parent == null || questionnaireNode == null) {
            return null;
        }

        boolean isQuestion = this.questionnaireUtils.isQuestion(questionnaireNode);
        // Easier to traverse children of NodeStates than NodeBuilders
        NodeState parentState = parent.getNodeState();
        // Get the ID of the desired question or section
        String questionnaireNodeUUID = questionnaireNode.getIdentifier();

        NodeBuilder result = null;
        // Traverse through children looking for an answer or answer section matching the desired questionnaire node
        for (ChildNodeEntry childEntry : parentState.getChildNodeEntries()) {
            NodeState child = childEntry.getNodeState();
            boolean isAnswer = this.formUtils.isAnswer(child);

            // Type of child does not match desired type, this cannot be the right child
            if (isAnswer != isQuestion) {
                continue;
            }

            String childQuestionnaireID = this.formUtils.isAnswer(child)
                ? this.formUtils.getQuestionIdentifier(child)
                : this.formUtils.getSectionIdentifier(child);

            if (questionnaireNodeUUID.equals(childQuestionnaireID)) {
                // Found the right child: done
                result = parent.child(childEntry.getName());
                break;
            }
        }

        if (result == null) {
            // No matching child was found: Create it
            result = isQuestion ? generateAnswer(parent, questionnaireNodeUUID)
                : generateSection(parent, questionnaireNodeUUID);
        }

        return result;
    }

    private NodeBuilder generateAnswer(NodeBuilder parent, String questionUUID)
    {
        return generateChild(parent, FormUtils.QUESTION_PROPERTY, questionUUID, "LongAnswer", "Answer");
    }

    private NodeBuilder generateSection(NodeBuilder parent, String sectionUUID)
    {
        return generateChild(parent, FormUtils.SECTION_PROPERTY, sectionUUID, "AnswerSection", "Resource");
    }

    private NodeBuilder generateChild(NodeBuilder parent, String questionnaireNodeProperty, String questionnaireNodeID,
        String type, String superType)
    {
        final String uuid = UUID.randomUUID().toString();
        NodeBuilder node = parent.setChildNode(uuid);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        node.setProperty("jcr:created", dateFormat.format(new Date()), Type.DATE);
        node.setProperty("jcr:createdBy", this.rrp.getThreadResourceResolver().getUserID(), Type.NAME);
        node.setProperty(questionnaireNodeProperty, questionnaireNodeID, Type.REFERENCE);
        node.setProperty("jcr:primaryType", "cards:" + type, Type.NAME);
        node.setProperty("sling:resourceSuperType", "cards/" + superType, Type.STRING);
        node.setProperty("sling:resourceType", "cards/" + type, Type.STRING);
        node.setProperty("statusFlags", Collections.emptyList(), Type.STRINGS);

        return node;

    }
}
