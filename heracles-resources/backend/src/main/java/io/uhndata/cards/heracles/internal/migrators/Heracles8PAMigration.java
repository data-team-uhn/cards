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
package io.uhndata.cards.heracles.internal.migrators;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.version.VersionManager;

import org.osgi.framework.Version;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.migrators.spi.DataMigrator;

@Component(immediate = true)
public class Heracles8PAMigration implements DataMigrator
{
    private static final String PA_REASON_QUESTION_PATH = "/Questionnaires/Physical Assessments/pa_reason";
    private static final String PA_QUESTIONNAIRE_PATH = "/Questionnaires/Physical Assessments";
    private static final String VISIT_NUMBER_SECTION_PATH = "/Questionnaires/Physical Assessments/section_visit_number";
    private static final String OLD_VISIT_NUMBER_QUESTION_PATH = "/Questionnaires/Physical Assessments/visit_number";
    private static final String NEW_VISIT_NUMBER_QUESTION_PATH
        = "/Questionnaires/Physical Assessments/section_visit_number/visit_number";

    private static final Logger LOGGER = LoggerFactory.getLogger(Heracles8PAMigration.class);

    @Reference
    private FormUtils formUtils;

    @Override
    public String getName()
    {
        return "Heracles-8: Remove Physical Assessment Form's old visit_number and add pa_reason question";
    }

    @Override
    public boolean shouldRun(Version previousVersion, Version currentVersion, Session session)
    {
        return previousVersion != null && previousVersion.compareTo(Version.valueOf("0.9.24")) < 0;
    }

    @Override
    public void run(Version previousVersion, Version currentVersion, Session session)
    {
        try {
            VersionManager versionManager = session.getWorkspace().getVersionManager();
            final List<String> nodesToCheckin = new ArrayList<>();

            if (!session.nodeExists(PA_QUESTIONNAIRE_PATH)) {
                return;
            }

            // Create the pa_reason question
            final Node questionnaire = session.getNode(PA_QUESTIONNAIRE_PATH);
            if (!session.nodeExists(PA_REASON_QUESTION_PATH)) {
                createPaReasonQuestion(session, versionManager, questionnaire, nodesToCheckin);
            }

            // Move visit_number to conditional section
            if (!session.nodeExists(NEW_VISIT_NUMBER_QUESTION_PATH)) {
                migrateVisitNumberQuestion(session, versionManager, questionnaire, nodesToCheckin);
            }

            // Modify Physical Assessment forms
            migratePaForms(session, versionManager, questionnaire, nodesToCheckin);

            // Save and checkin any changes
            session.save();
            nodesToCheckin.forEach(f -> {
                try {
                    versionManager.checkin(f);
                } catch (RepositoryException e) {
                    LOGGER.warn("Failed to checkin {}: {}", f, e);
                }
            });
        } catch (RepositoryException e) {
            LOGGER.error("Failed to run migrator {}: {}", getName(), e.getMessage(), e);
        }
    }

    private void createPaReasonQuestion(Session session, VersionManager versionManager, Node questionnaire,
        List<String> nodesToCheckin)
        throws RepositoryException
    {
        checkoutIfNeeded(versionManager, questionnaire.getPath(), nodesToCheckin);

        Node question = questionnaire.addNode("pa_reason", "cards:Question");
        question.setProperty("maxAnswers", 1L);
        question.setProperty("dataType", "text");
        question.setProperty("text", "Reason for Physical Assessments");
        question.setProperty("displayMode", "list");
        question.setProperty("minAnswers", 1L);
        addAnswerOption(question, "Study", "Study", 1L);
        addAnswerOption(question, "Triggered Assessment", "Triggered Assessment", 2L);
    }

    private void migrateVisitNumberQuestion(Session session, VersionManager versionManager, Node questionnaire,
        List<String> nodesToCheckin)
        throws RepositoryException
    {
        checkoutIfNeeded(versionManager, questionnaire.getPath(), nodesToCheckin);
        Node section = getOrCreateNode(questionnaire, "section_visit_number", "cards:Section");

        Node condition = getOrCreateNode(section, "condition0", "cards:Conditional");
        condition.setProperty("comparator", "=");
        Node opA = getOrCreateNode(condition, "operandA", "cards:ConditionalValue");
        opA.setProperty("value", new String[] {"pa_reason"});
        opA.setProperty("isReference", true);
        Node opB = getOrCreateNode(condition, "operandB", "cards:ConditionalValue");
        opB.setProperty("value", new String[] {"Study"});
        opB.setProperty("isReference", false);

        // TODO: if old_visit_number does not exist
        session.move(OLD_VISIT_NUMBER_QUESTION_PATH, NEW_VISIT_NUMBER_QUESTION_PATH);
    }

    private void migratePaForms(Session session, VersionManager versionManager, Node questionnaire,
        List<String> nodesToCheckin)
        throws RepositoryException
    {
        final String formId = questionnaire.getIdentifier();
        final String visitQuestionId = session.getNode(NEW_VISIT_NUMBER_QUESTION_PATH).getIdentifier();
        final Node paQuestion = session.getNode(PA_REASON_QUESTION_PATH);
        final Node visitSection = session.getNode(VISIT_NUMBER_SECTION_PATH);

        final NodeIterator forms = session.getWorkspace().getQueryManager().createQuery(
            "select form.* from [cards:Form] as form"
                + " where form.questionnaire = '" + formId + "'",
            Query.JCR_SQL2).execute().getNodes();

        while (forms.hasNext()) {
            Node form = forms.nextNode();
            final boolean wasCheckedOut = versionManager.isCheckedOut(form.getPath());
            if (!wasCheckedOut) {
                versionManager.checkout(form.getPath());
                nodesToCheckin.add(form.getPath());
            }

            // Move visit_number answer
            Node visitAnswerNode = null;

            final NodeIterator answers = form.getNodes();
            while (answers.hasNext()) {
                Node answer = answers.nextNode();
                if (answer.hasProperty(FormUtils.QUESTION_PROPERTY)
                    && visitQuestionId.equals(answer.getProperty(FormUtils.QUESTION_PROPERTY).getString())) {
                    visitAnswerNode = answer;
                    break;
                }
            }

            if (visitAnswerNode != null) {
                Node section = form.addNode(UUID.randomUUID().toString(), FormUtils.ANSWER_SECTION_NODETYPE);
                section.setProperty(FormUtils.SECTION_PROPERTY, visitSection);
                LOGGER.error("Moving from {} to {}", visitAnswerNode.getPath(),
                    section.getPath() + "/" + visitAnswerNode.getName());
                session.move(visitAnswerNode.getPath(), section.getPath() + "/" + visitAnswerNode.getName());
            }

            // Create pa_reason answer
            Node paAnswer = form.addNode(UUID.randomUUID().toString(), "cards:TextAnswer");
            paAnswer.setProperty(FormUtils.QUESTION_PROPERTY, paQuestion);
            paAnswer.setProperty("value", "Study");

        }
    }

    private void checkoutIfNeeded(VersionManager versionManager, String path, List<String> nodesToCheckin)
        throws RepositoryException
    {
        final boolean wasCheckedOut = versionManager.isCheckedOut(path);
        if (!wasCheckedOut) {
            versionManager.checkout(path);
            nodesToCheckin.add(path);
        }
    }

    private Node getOrCreateNode(Node parent, String relativePath, String nodeType)
        throws RepositoryException
    {
        return parent.hasNode(relativePath) ? parent.getNode(relativePath) : parent.addNode(relativePath, nodeType);
    }

    private void addAnswerOption(Node question, String label, String value, long defaultOrder)
        throws RepositoryException
    {
        Node option = getOrCreateNode(question, value, "cards:AnswerOption");
        option.setProperty("label", label);
        option.setProperty(FormUtils.VALUE_PROPERTY, value);
        option.setProperty("defaultOrder", defaultOrder);
    }
}
