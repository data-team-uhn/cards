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
package io.uhndata.cards.proms.internal;

import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;

/**
 * Change listener looking for new or modified Visit Information forms.
 * Creates any forms in the specified survey set that needs to be created,
 * based on the seurvey set's specified frequency.
 *
 * @version $Id$
 */
@Component(
    service = EventHandler.class,
    property = {
        EventConstants.EVENT_TOPIC + "=org/apache/sling/api/resource/Resource/ADDED",
        EventConstants.EVENT_TOPIC + "=org/apache/sling/api/resource/Resource/CHANGED",
        EventConstants.EVENT_FILTER + "=(resourceType=cards/Form)"
    }
)
public class VisitChangeListener implements EventHandler
{
    private static final int FREQUENCY_MARGIN = 2;
    private static final Logger LOGGER = LoggerFactory.getLogger(VisitChangeListener.class);

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    @Override
    public void handleEvent(Event event)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Session session = resolver.adaptTo(Session.class);
            Node eventFormNode = session.getNode(event.getProperty("path").toString());
            Node visitNode = eventFormNode.getProperty("subject").getNode();
            VisitInformation visitInformation = null;

            // Get the information needed from the triggering form
            Node questionnaireNode = this.formUtils.getQuestionnaire(eventFormNode);
            if (VisitInformation.isVisitInformationForm(questionnaireNode)) {
                visitInformation = new VisitInformation(questionnaireNode, eventFormNode);
            } else {
                // Not a visit information form, skip
                return;
            }
            if (visitInformation.missingQuestionSetInfo()) {
                return;
            }

            Map<String, QuestionnaireFrequency> questionnaireSetInfo
                = getQuestionnaireSetInformation(session, visitInformation);
            if (questionnaireSetInfo.size() < 1) {
                // No questionnaires that need to be created
                return;
            }

            pruneQuestionnaireSet(visitNode, visitInformation, questionnaireSetInfo);

            List<String> createdForms = createForms(resolver, visitNode, questionnaireSetInfo);
            commitForms(session, resolver, createdForms);
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        } catch (RepositoryException | PersistenceException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static Calendar getFormDate(Node formNode, String questionIdentifier)
    {
        try {
            Property prop = getFormProperty(formNode, questionIdentifier);
            return prop == null ? null : prop.getDate();
        } catch (RepositoryException e) {
            // Do nothing
            // TODO: Can this be handled better?
        }
        return null;
    }

    private static String getFormString(Node formNode, String questionIdentifier)
    {
        try {
            Property prop = getFormProperty(formNode, questionIdentifier);
            return prop == null ? null : prop.getString();
        } catch (RepositoryException e) {
            // Do nothing
            // TODO: Can this be handled better?
        }
        return null;
    }

    private static Property getFormProperty(Node formNode, String questionIdentifier)
    {
        try {
            for (NodeIterator answers = formNode.getNodes(); answers.hasNext();) {
                Node answer = answers.nextNode();
                if (answer.hasProperty("question")
                    && questionIdentifier.equals(answer.getProperty("question").getString())) {
                    if (answer.hasProperty("value")) {
                        return answer.getProperty("value");
                    } else {
                        // Question wasn't answered
                        break;
                    }
                }
            }
        } catch (RepositoryException e) {
            // Do nothing
            // TODO: Can this be handled better?
        }
        return null;
    }

    private static Calendar getFrequencyDate(Calendar visitDate, int frequency)
    {
        Calendar result = (Calendar) visitDate.clone();
        result.add(Calendar.DATE, (-7 * frequency + VisitChangeListener.FREQUENCY_MARGIN));
        return result;
    }

    private Map<String, QuestionnaireFrequency> getQuestionnaireSetInformation(Session session,
        VisitInformation visitInformation)
    {
        Map<String, QuestionnaireFrequency> result = new HashMap<>();
        try {
            // Get frequencies
            for (NodeIterator questionnaireSet = session.getNode("/Proms/"
                + visitInformation.getQuestionnaireSet()).getNodes();
                questionnaireSet.hasNext();)
            {
                Node questionnaireRef = questionnaireSet.nextNode();
                Node questionnaire = questionnaireRef.getProperty("questionnaire").getNode();
                String uuid = questionnaire.getIdentifier();
                int frequency = 0;
                if (questionnaireRef.hasProperty("frequency")) {
                    frequency = (int) questionnaireRef.getProperty("frequency").getLong();
                }
                result.put(uuid, new QuestionnaireFrequency(frequency, questionnaire));
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to retrieve questionnaire frequency: {}", e.getMessage(), e);
        }
        return result;
    }

    private void pruneQuestionnaireSet(Node visitNode,
        VisitInformation visitInformation,
        Map<String, QuestionnaireFrequency> questionnaireSetInfo)
        throws RepositoryException
    {
        Node patientNode = visitNode.getParent();
        for (NodeIterator visits = patientNode.getNodes(); visits.hasNext();)
        {
            Node visit = visits.nextNode();

            // Create a list of all forms that exist for this visit.
            // Record the visit's date as well, if there is a Visit Information form with a date.
            List<String> questionnaires = new LinkedList<>();
            Calendar visitDate = null;
            for (PropertyIterator forms = visit.getReferences(); forms.hasNext();)
            {
                Node form = forms.nextProperty().getParent();
                if (this.formUtils.isForm(form)) {
                    String questionnaireIdentifier = this.formUtils.getQuestionnaireIdentifier(form);
                    if (questionnaireIdentifier.equals(visitInformation.getQuestionnaireIdentifier())) {
                        visitDate = getFormDate(form, visitInformation.getTimeQuestionIdentifier());
                    } else {
                        questionnaires.add(questionnaireIdentifier);
                    }
                }
            }

            // Check if any of this visit's forms meet a frequency requirement for the current questionnaire set.
            // If they do, remove those forms' questionnaires from the set.
            for (String questionnaireIdentifier: questionnaires) {
                if (questionnaireSetInfo.containsKey(questionnaireIdentifier)) {
                    Calendar compareDate = getFrequencyDate(visitInformation.getEventDate(),
                        questionnaireSetInfo.get(questionnaireIdentifier).getFrequency());
                    if (compareDate.compareTo(visitDate) <= 0) {
                        // Current visit is the same day or after the compare threshold:
                        // This form satisfies the questionnaire's frequency requirement
                        questionnaireSetInfo.remove(questionnaireIdentifier);
                    }
                }
            }
        }
    }

    private List<String> createForms(ResourceResolver resolver,
        Node visitNode, Map<String,
        QuestionnaireFrequency> questionnaireSetInfo)
        throws PersistenceException, RepositoryException
    {
        List<String> results = new LinkedList<>();

        for (String questionnaireIdentifier: questionnaireSetInfo.keySet()) {
            Map<String, Object> formProperties = new HashMap<>();
            formProperties.put("jcr:primaryType", "cards:Form");
            formProperties.put("questionnaire", questionnaireSetInfo.get(questionnaireIdentifier).getNode());
            formProperties.put("subject", visitNode);

            String uuid = UUID.randomUUID().toString();
            resolver.create(resolver.resolve("/Forms/"), uuid, formProperties);
            results.add("/Forms/" + uuid);
        }

        return results;
    }

    private void commitForms(Session session, ResourceResolver resolver, List<String> createdForms)
    {
        if (createdForms.size() > 0) {
            // Commit new forms and check them in
            try {
                resolver.commit();
            } catch (PersistenceException e) {
                LOGGER.error("Failed to commit forms: {}", e.getMessage(), e);
            }

            try {
                VersionManager versionManager = session.getWorkspace().getVersionManager();
                for (String formPath: createdForms) {
                    try {
                        versionManager.checkin(formPath);
                    } catch (RepositoryException e) {
                        LOGGER.error("Failed to checkin form: {}", e.getMessage(), e);
                    }
                }
            } catch (RepositoryException e) {
                LOGGER.error("Failed to obtain version manager: {}", e.getMessage(), e);
            }
        }
    }

    private static final class VisitInformation
    {
        private final Calendar eventDate;
        private final String questionnaireIdentifier;
        private final String timeQuestionIdentifier;
        private final String questionnaireSet;

        VisitInformation(Node questionnaireNode, Node formNode) throws RepositoryException
        {
            this.questionnaireIdentifier = questionnaireNode.getIdentifier();
            this.timeQuestionIdentifier = questionnaireNode.getNode("time").getIdentifier();
            this.eventDate = getFormDate(formNode, this.timeQuestionIdentifier);
            String questionSetIdentifier = questionnaireNode.getNode("surveys").getIdentifier();
            this.questionnaireSet = getFormString(formNode, questionSetIdentifier);
        }

        public static Boolean isVisitInformationForm(Node node)
        {
            try {
                return node != null
                    && node.hasProperty("title")
                    && "Visit information".equals(node.getProperty("title").getString());
            } catch (RepositoryException e) {
                LOGGER.warn("Failed check for visit information form: {}", e.getMessage(), e);
                return false;
            }
        }

        public Boolean missingQuestionSetInfo()
        {
            return this.eventDate == null
                || this.questionnaireSet == null
                || "".equals(this.questionnaireSet);
        }

        public Calendar getEventDate()
        {
            return this.eventDate;
        }

        public String getQuestionnaireIdentifier()
        {
            return this.questionnaireIdentifier;
        }

        public String getTimeQuestionIdentifier()
        {
            return this.timeQuestionIdentifier;
        }

        public String getQuestionnaireSet()
        {
            return this.questionnaireSet;
        }
    }

    private static final class QuestionnaireFrequency
    {
        private final Integer frequency;
        private final Node node;

        QuestionnaireFrequency(int frequency, Node node)
        {
            this.frequency = frequency;
            this.node = node;

        }

        public int getFrequency()
        {
            return this.frequency;
        }

        public Node getNode()
        {
            return this.node;
        }
    }
}
