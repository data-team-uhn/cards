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
package io.uhndata.cards.patients.internal;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.links.api.LinkUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Change listener looking for new Forms that belong to a survey and should be linked to a survey event form.
 * Listen for new forms.
 * If the form is a survey event form, look for any existing forms in the visits clinic and link them to this form.
 * If the form is a standard form, check if it belongs to the clinic and link it the most recent survey event form.
 *
 * @version $Id$
 */
@Component(immediate = true, property = {
    ResourceChangeListener.PATHS + "=/Forms",
    ResourceChangeListener.CHANGES + "=ADDED"
})
public class SurveyEventsFormListener implements ResourceChangeListener
{
    private static final String VISIT_INFORMATION_PATH = "/Questionnaires/Visit information";
    private static final String SURVEY_EVENTS_PATH = "/Questionnaires/Survey events";
    private static final String SURVEY_EVENTS_CLINIC = "/Questionnaires/Survey events/assigned_survey";

    private static final Logger LOGGER = LoggerFactory.getLogger(SurveyEventsFormListener.class);

    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory rrf;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private FormUtils formUtils;

    @Reference
    private LinkUtils linkUtils;

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        changes.forEach(this::handleEvent);
    }

    private void handleEvent(final ResourceChange event)
    {
        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.rrf
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "SurveyEventsFormListener"))
        ) {
            Session session = localResolver.adaptTo(Session.class);
            String path = event.getPath();
            if (!session.nodeExists(path)) {
                // Should not happen but check for null path anyways
                return;
            }

            Node node = session.getNode(path);
            this.rrp.push(localResolver);
            mustPopResolver = true;
            if (!this.formUtils.isForm(node) || isVisitInformationForm(node)) {
                // Only process new forms that are not visit information forms
                return;
            }

            // Get the current questionnaire
            Node questionnaire = this.formUtils.getQuestionnaire(node);
            // An iterator of all properties that reference the current subject.
            // This should only include forms.
            Node visit = this.formUtils.getSubject(node);
            PropertyIterator visitReferences = visit.getReferences(FormUtils.SUBJECT_PROPERTY);

            // There's two cases where we may need to create a link:
            // 1. New survey events form.
            //    Check for any forms for the current clinic and link them to the current form.
            // 2. New form that belongs to the current clinic. Link it to an existing survey events form.
            if (isSurveyEventsQuestionnaire(questionnaire)) {
                handleSurveyEventsForm(session, node, visit, visitReferences);
            } else {
                handleNonSurveyEventsForm(session, node, questionnaire, visitReferences);
            }
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (final RepositoryException e) {
            // Shouldn't happen
            LOGGER.error("Unexpected error", e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void handleSurveyEventsForm(final Session session, final Node form, final Node visit,
        final PropertyIterator visitReferences)
        throws RepositoryException
    {
        // A list of all the questionnaires belonging to the current clinic
        List<String> clinicQuestionnaires = getVisitClinicQuestionnaires(session, visit);
        if (clinicQuestionnaires.size() < 1) {
            // No questionnaires that are a part of the current clinic: Nothing to do
            return;
        }
        // Found a new Survey Events form:
        // Check for existing forms that are present in the current clinic and link to them.
        // Can't use the current Survey Event form's clinic answer as that may not have been set yet.
        while (visitReferences.hasNext()) {
            // Get the node that is referencing the current subject. This should be a form.
            Node referencedNode = visitReferences.nextProperty().getParent();
            if (this.formUtils.isForm(referencedNode)
                && clinicQuestionnaires.contains(this.formUtils.getQuestionnaire(referencedNode).getIdentifier())
            ) {
                // Found a form for the current clinic:
                // - Remove any links from the form to old Survey Event forms but keep the link from the
                //   survey event form to that form, if any exist
                // - Link the form to the current Survey Event form
                this.linkUtils.removeLinks(referencedNode, null, "belongsToSurvey", null);
                // linkUtils expects the link between source and destination to have the same label as the backlink.
                // Since the link from form to survey is the important link, use the label for that direction.
                this.linkUtils.addLink(form, referencedNode, "includesSurveyForm", "Belongs to Survey");
            }
        }
    }

    private void handleNonSurveyEventsForm(final Session session, final Node form,
        final Node questionnaire, final PropertyIterator visitReferences)
        throws RepositoryException
    {
        // Found a new form that is a part of the current clinic:
        // Search for an existing Survey Events form
        Node surveyEventsForm = null;
        Node surveyClinicQuestion = session.getNode(SURVEY_EVENTS_CLINIC);

        while (visitReferences.hasNext()) {
            Node referencedForm = visitReferences.nextProperty().getParent();
            Node referencedQuestionnaire = this.formUtils.getQuestionnaire(referencedForm);
            if (isSurveyEventsQuestionnaire(referencedQuestionnaire)) {
                // Found a survey events form for the current visit: check if this form is a part of that
                // survey event form's clinic
                Node clinic = this.formUtils.getAnswer(referencedForm, surveyClinicQuestion);
                // Only keep track of the most recently created Survey Events form in case
                // there are multiple that contain this form
                if (getClinicQuestionnaires(session, clinic).contains(questionnaire.getIdentifier())
                    && (surveyEventsForm == null
                        || getCreatedDate(referencedForm).after(getCreatedDate(surveyEventsForm)))
                ) {
                    surveyEventsForm = referencedForm;
                    break;
                }
            }
        }

        if (surveyEventsForm != null) {
            // Found a survey events form: Link to it
            this.linkUtils.addLink(form, surveyEventsForm, "belongsToSurvey", "Belongs to Survey");
        }
    }

    private List<String> getVisitClinicQuestionnaires(Session session, Node subject)
        throws RepositoryException
    {
        // Get relevant nodes on the visit information form
        Node visitInformationQuestionnaire = session.getNode(VISIT_INFORMATION_PATH);
        Node visitClinicQuestion = visitInformationQuestionnaire.getNode("clinic");
        Node visitInformationForm = getVisitInformationForm(subject, visitInformationQuestionnaire);

        if (visitInformationForm != null) {
            // Get the list of questionnaires associated with the current clinic
            Node clinic = this.formUtils.getAnswer(visitInformationForm, visitClinicQuestion);
            return getClinicQuestionnaires(session, clinic);
        }
        return new ArrayList<String>();
    }

    private List<String> getClinicQuestionnaires(Session session, Node clinicAnswer)
        throws RepositoryException
    {
        List<String> surveyFormIds = new LinkedList<>();
        if (clinicAnswer != null && clinicAnswer.hasProperty(FormUtils.VALUE_PROPERTY)) {
            String clinic = clinicAnswer.getProperty(FormUtils.VALUE_PROPERTY).getString();
            Node clinicMapping = session.getNode(clinic);
            Node survey = session.getNode("/Survey/" + clinicMapping.getProperty("survey").getString());
            NodeIterator surveyForms = survey.getNodes();
            while (surveyForms.hasNext()) {
                Node surveyForm = surveyForms.nextNode();
                if ("cards:QuestionnaireRef".equals(surveyForm.getPrimaryNodeType().getName())) {
                    surveyFormIds.add(surveyForm.getProperty("questionnaire").getNode().getIdentifier());
                }
            }
        }
        return surveyFormIds;
    }

    private boolean isVisitInformationForm(final Node form)
    {
        try {
            return VISIT_INFORMATION_PATH.equals(this.formUtils.getQuestionnaire(form).getPath());
        } catch (RepositoryException e) {
            LOGGER.warn("Failed check if form is Visit information form: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isSurveyEventsQuestionnaire(final Node questionnaire)
    {
        try {
            return questionnaire != null && SURVEY_EVENTS_PATH.equals(questionnaire.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if form is Survey events form: {}", e.getMessage(), e);
            return false;
        }
    }

    private Node getVisitInformationForm(final Node subject, final Node visitInformationQuestionnaire)
        throws RepositoryException
    {
        final PropertyIterator references = subject.getReferences(FormUtils.SUBJECT_PROPERTY);
        while (references.hasNext()) {
            Property reference = references.nextProperty();
            Node form = reference.getParent();

            if (this.formUtils.isForm(form) && isVisitInformationForm(form)) {
                return form;
            }
        }

        return null;
    }

    private Calendar getCreatedDate(Node node)
        throws RepositoryException
    {
        return node.getProperty("jcr:created").getDate();
    }
}
