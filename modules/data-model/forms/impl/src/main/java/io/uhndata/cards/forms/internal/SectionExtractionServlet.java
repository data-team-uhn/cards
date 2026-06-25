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
package io.uhndata.cards.forms.internal;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService;
import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService.FieldResult;
import io.uhndata.cards.forms.internal.extraction.ProposalExtractionService.FieldSpec;
import io.uhndata.cards.forms.internal.parse.ParsedMarkdownStore;

/**
 * Servlet that fills in the answers of an extraction section by running the parsed proposal document through
 * the active LLM. It is invoked by the frontend when a section flagged with {@code extractFromProposal} opens.
 * <p>
 * The flagged section names the proposal question through its {@code proposalQuestion} reference; the proposal
 * answer's parse subfolder supplies the document chunks. Each child question of the section becomes one field
 * to extract, using its {@code prompt} property as the field rules and its {@code promptKey} property (falling
 * back to the node name) as the JSON key. The extracted value is stored on the matching
 * {@code cards:ExtractedTextAnswer}, and the raw JSON the model returned for that field is stored in the
 * answer's {@code note}. Extraction is skipped when every answer already has a value and the aggregated
 * markdown has not been re-parsed since the previous run.
 * </p>
 * <p>
 * Endpoint: {@code POST /Forms/<id>.extract}. Response: {@code {"status": "...", "extracted": N, "chunks": M}}.
 * </p>
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Form" },
    methods = { "POST" },
    selectors = { "extract" })
public class SectionExtractionServlet extends SlingJakartaAllMethodsServlet
{
    private static final long serialVersionUID = -7745626264367310608L;

    private static final Logger LOGGER = LoggerFactory.getLogger(SectionExtractionServlet.class);

    private static final String EXTRACTED_TEXT_ANSWER_NODETYPE = "cards:ExtractedTextAnswer";

    private static final String PROPOSAL_ANSWER_NODETYPE = "cards:ProposalAnswer";

    private static final String ANSWER_SECTION_NODETYPE = "cards:AnswerSection";

    private static final String EXTRACTION_TIMESTAMP_PROPERTY = "extractionSourceTimestamp";

    @Reference
    private transient FormUtils formUtils;

    @Reference
    private transient QuestionnaireUtils questionnaireUtils;

    @Reference
    private transient ProposalExtractionService extractionService;

    @Override
    public void doPost(final SlingJakartaHttpServletRequest request,
        final SlingJakartaHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json;charset=UTF-8");
        JsonObject result;
        int status = SlingJakartaHttpServletResponse.SC_OK;
        try {
            result = handle(request);
        } catch (RepositoryException e) {
            LOGGER.warn("Section extraction failed", e);
            result = errorJson("Extraction failed");
            status = SlingJakartaHttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        } catch (IOException e) {
            LOGGER.warn("LLM extraction request failed: {}", e.getMessage());
            result = errorJson("LLM request failed: " + e.getMessage());
            status = SlingJakartaHttpServletResponse.SC_BAD_GATEWAY;
        }
        response.setStatus(status);
        try (Writer out = response.getWriter()) {
            out.write(result.toString());
        }
    }

    private JsonObject handle(final SlingJakartaHttpServletRequest request)
        throws RepositoryException, IOException
    {
        final Node form = request.getResource().adaptTo(Node.class);
        if (form == null || !this.formUtils.isForm(form)) {
            return statusJson("not_a_form", 0, 0);
        }
        final Node questionnaire = this.formUtils.getQuestionnaire(form);
        final Node section = findFlaggedSection(questionnaire, request.getParameter("section"));
        if (section == null) {
            return statusJson("no_section", 0, 0);
        }
        final String folder = resolveProposalFolder(form);
        final List<String> chunks = folder == null ? List.of() : ParsedMarkdownStore.readChunks(folder);
        if (chunks.isEmpty()) {
            return statusJson("no_document", 0, 0);
        }
        final boolean force = Boolean.parseBoolean(request.getParameter("force"));
        return runExtraction(form, section, folder, chunks, force);
    }

    private JsonObject runExtraction(final Node form, final Node section, final String folder,
        final List<String> chunks, final boolean force) throws RepositoryException, IOException
    {
        final List<Node> questions = listQuestions(section);
        final long sourceTimestamp = ParsedMarkdownStore.aggregateLastModified(folder);
        if (!force && !shouldExtract(form, questions, findAnswerSection(form, section), sourceTimestamp)) {
            return statusJson("skipped", 0, chunks.size());
        }
        final List<FieldSpec> fields = buildFieldSpecs(questions);
        final String categories = stringProperty(section, "categoriesDocument");
        final Map<String, FieldResult> results = this.extractionService.extract(fields, categories, chunks);
        final int written = persistAnswers(form, section, questions, results, sourceTimestamp);
        return statusJson("extracted", written, chunks.size());
    }

    private int persistAnswers(final Node form, final Node section, final List<Node> questions,
        final Map<String, FieldResult> results, final long sourceTimestamp) throws RepositoryException
    {
        final Session session = form.getSession();
        final VersionManager versionManager = session.getWorkspace().getVersionManager();
        final String formPath = form.getPath();
        // Only check the form in if it was checked in to begin with; if the user is actively editing it (checked
        // out), leave it checked out so their edit session and version state are not disturbed.
        final boolean wasCheckedIn = !versionManager.isCheckedOut(formPath);
        if (wasCheckedIn) {
            versionManager.checkout(formPath);
        }
        final Node answerSection = findOrCreateAnswerSection(form, section);
        final int written = writeAnswers(form, answerSection, questions, results);
        answerSection.setProperty(EXTRACTION_TIMESTAMP_PROPERTY, sourceTimestamp);
        session.save();
        if (wasCheckedIn) {
            versionManager.checkin(formPath);
        }
        return written;
    }

    private Node findFlaggedSection(final Node questionnaire, final String sectionName)
        throws RepositoryException
    {
        if (questionnaire == null) {
            return null;
        }
        final NodeIterator children = questionnaire.getNodes();
        while (children.hasNext()) {
            final Node child = children.nextNode();
            if (this.questionnaireUtils.isSection(child) && boolProperty(child, "extractFromProposal")
                && (StringUtils.isBlank(sectionName) || sectionName.equals(child.getName()))) {
                return child;
            }
        }
        return null;
    }

    private List<Node> listQuestions(final Node section)
        throws RepositoryException
    {
        final List<Node> questions = new ArrayList<>();
        final NodeIterator children = section.getNodes();
        while (children.hasNext()) {
            final Node child = children.nextNode();
            if (this.questionnaireUtils.isQuestion(child)) {
                questions.add(child);
            }
        }
        return questions;
    }

    private List<FieldSpec> buildFieldSpecs(final List<Node> questions)
        throws RepositoryException
    {
        final List<FieldSpec> fields = new ArrayList<>(questions.size());
        for (final Node question : questions) {
            final String taskLabel = StringUtils.defaultIfBlank(stringProperty(question, "taskLabel"),
                "Determine the " + this.questionnaireUtils.getQuestionText(question));
            fields.add(new FieldSpec(promptKey(question), taskLabel, stringProperty(question, "prompt")));
        }
        return fields;
    }

    private int writeAnswers(final Node form, final Node answerSection, final List<Node> questions,
        final Map<String, FieldResult> results) throws RepositoryException
    {
        int written = 0;
        for (final Node question : questions) {
            final FieldResult result = results.get(promptKey(question));
            if (result == null) {
                continue;
            }
            final Node answer = findOrCreateAnswer(form, answerSection, question);
            if (result.value() != null) {
                answer.setProperty(FormUtils.VALUE_PROPERTY, result.value());
            }
            answer.setProperty("note", result.rawJson());
            writeExtractionMetadata(answer, result);
            written++;
        }
        return written;
    }

    /**
     * Record the AI-extraction metadata on the answer: {@code extracted} reflects whether the model found a
     * value; {@code reasoning} is always stored when present; {@code evidence} and {@code confidence} are stored
     * only for found values and otherwise removed, so a re-extraction that no longer finds the value does not
     * leave stale supporting data behind.
     *
     * @param answer the answer node to update
     * @param result the field result from the extraction service
     * @throws RepositoryException if the repository cannot be accessed
     */
    private static void writeExtractionMetadata(final Node answer, final FieldResult result)
        throws RepositoryException
    {
        answer.setProperty("extracted", result.found());
        if (result.reasoning() != null) {
            answer.setProperty("reasoning", result.reasoning());
        } else {
            removeProperty(answer, "reasoning");
        }
        if (result.found()) {
            answer.setProperty("confidence", result.confidence());
            if (result.evidence() != null) {
                answer.setProperty("evidence", result.evidence());
            } else {
                removeProperty(answer, "evidence");
            }
        } else {
            removeProperty(answer, "evidence");
            removeProperty(answer, "confidence");
        }
    }

    private static void removeProperty(final Node node, final String name)
        throws RepositoryException
    {
        if (node.hasProperty(name)) {
            node.getProperty(name).remove();
        }
    }

    private Node findOrCreateAnswer(final Node form, final Node answerSection, final Node question)
        throws RepositoryException
    {
        final Node existing = this.formUtils.getAnswer(form, question);
        if (existing != null) {
            return existing;
        }
        final Node answer = answerSection.addNode(UUID.randomUUID().toString(), EXTRACTED_TEXT_ANSWER_NODETYPE);
        answer.setProperty(FormUtils.QUESTION_PROPERTY, question);
        return answer;
    }

    private Node findOrCreateAnswerSection(final Node form, final Node section)
        throws RepositoryException
    {
        final Node existing = findAnswerSection(form, section);
        if (existing != null) {
            return existing;
        }
        final Node answerSection = form.addNode(UUID.randomUUID().toString(), ANSWER_SECTION_NODETYPE);
        answerSection.setProperty(FormUtils.SECTION_PROPERTY, section);
        return answerSection;
    }

    private Node findAnswerSection(final Node form, final Node section)
        throws RepositoryException
    {
        final String sectionId = section.getIdentifier();
        final NodeIterator children = form.getNodes();
        while (children.hasNext()) {
            final Node child = children.nextNode();
            if (this.formUtils.isAnswerSection(child)
                && sectionId.equals(this.formUtils.getSectionIdentifier(child))) {
                return child;
            }
        }
        return null;
    }

    private boolean shouldExtract(final Node form, final List<Node> questions, final Node answerSection,
        final long sourceTimestamp) throws RepositoryException
    {
        final long lastExtracted =
            answerSection != null && answerSection.hasProperty(EXTRACTION_TIMESTAMP_PROPERTY)
                ? answerSection.getProperty(EXTRACTION_TIMESTAMP_PROPERTY).getLong() : 0L;
        if (sourceTimestamp > lastExtracted) {
            return true;
        }
        return !allAnswered(form, questions);
    }

    private boolean allAnswered(final Node form, final List<Node> questions)
    {
        for (final Node question : questions) {
            final Node answer = this.formUtils.getAnswer(form, question);
            final Object value = answer == null ? null : this.formUtils.getValue(answer);
            if (value == null || value instanceof String && ((String) value).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Locate the parse output folder of the form's single proposal answer. The whole form is backed by one
     * uploaded document (one {@code cards:ProposalAnswer}), whose aggregated markdown and chunks feed every
     * extraction section, so the proposal answer is discovered automatically rather than referenced.
     *
     * @param form the form node
     * @return the proposal answer's parse subfolder, or {@code null} if the form has no proposal answer
     * @throws RepositoryException if the repository cannot be accessed
     */
    private String resolveProposalFolder(final Node form)
        throws RepositoryException
    {
        final Node proposalAnswer = findProposalAnswer(form);
        return proposalAnswer == null ? null : answerFolder(proposalAnswer);
    }

    private static Node findProposalAnswer(final Node node)
        throws RepositoryException
    {
        final NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            final Node child = children.nextNode();
            if (child.isNodeType(PROPOSAL_ANSWER_NODETYPE)) {
                return child;
            }
            final Node found = findProposalAnswer(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String promptKey(final Node question)
        throws RepositoryException
    {
        final String key = stringProperty(question, "promptKey");
        return StringUtils.isNotBlank(key) ? key : question.getName();
    }

    private static String answerFolder(final Node answer)
        throws RepositoryException
    {
        final String uuid = stringProperty(answer, "jcr:uuid");
        return StringUtils.isNotBlank(uuid) ? uuid : answer.getName();
    }

    private static String stringProperty(final Node node, final String name)
        throws RepositoryException
    {
        return node.hasProperty(name) ? node.getProperty(name).getString() : null;
    }

    private static boolean boolProperty(final Node node, final String name)
        throws RepositoryException
    {
        return node.hasProperty(name) && node.getProperty(name).getBoolean();
    }

    private static JsonObject statusJson(final String status, final int extracted, final int chunks)
    {
        return Json.createObjectBuilder()
            .add("status", status)
            .add("extracted", extracted)
            .add("chunks", chunks)
            .build();
    }

    private static JsonObject errorJson(final String message)
    {
        return Json.createObjectBuilder().add("error", message).build();
    }
}
