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
package io.uhndata.cards;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.version.VersionManager;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletName;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.spi.SearchUtils;

/**
 * A servlet for importing CARDS data from CSV files.
 *
 * @version $Id$
 */
@Component(service = Servlet.class,
    property = {
        "service.description=Data Import Servlet",
        "service.vendor=DATA@UHN",
    })
@SlingServletResourceTypes(resourceTypes = { "cards/FormsHomepage" }, methods = { "POST" })
@SlingServletName(servletName = "Data Import Servlet")
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class DataImportServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -5821127949309764050L;

    private static final String VALUE_PROPERTY = "value";

    private static final String LABEL_PROPERTY = "label";

    private static final String NOTE_PROPERTY = "note";

    private static final String NOTE_SUFFIX = "_notes";

    private static final Logger LOGGER = LoggerFactory.getLogger(DataImportServlet.class);

    /** Supported date formats. */
    private static final List<SimpleDateFormat> DATE_FORMATS = Arrays.asList(
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz"),
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSz"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz"),
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ssz"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"),
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm"),
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm"),
        new SimpleDateFormat("yyyy-MM-dd"),
        new SimpleDateFormat("M/d/y"));

    /** Cached Question nodes. */
    private final ThreadLocal<Map<String, Node>> questionCache = ThreadLocal.withInitial(HashMap::new);

    /** Cached Subject nodes (for multiple forms for the same subject, for instance). */
    private final ThreadLocal<Map<String, Node>> subjectCache = ThreadLocal.withInitial(HashMap::new);

    /** Cached Question nodes. */
    private final ThreadLocal<Set<String>> warnedCache = ThreadLocal.withInitial(HashSet::new);

    private final ThreadLocal<Set<String>> nodesToCheckin = ThreadLocal.withInitial(HashSet::new);

    /** The Resource Resolver for the current request. */
    private final ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    /** The questionnaire to use for the uploaded CSV. */
    private final ThreadLocal<Node> questionnaire = new ThreadLocal<>();

    /** The {@code /Subjects} resource. */
    private final ThreadLocal<Resource> subjectsHomepage = new ThreadLocal<>();

    /** The {@code /Forms} resource. */
    private final ThreadLocal<Resource> formsHomepage = new ThreadLocal<>();

    /** The list of subjectTypes. */
    private final ThreadLocal<String[]> subjectTypes = new ThreadLocal<>();

    /** A query manager to handle queries. */
    private final ThreadLocal<QueryManager> queryManager = new ThreadLocal<>();

    /** A local mapping for question node identifiers to answer nodes. */
    private final ThreadLocal<Map<String, Resource>> cachedAnswers = new ThreadLocal<>();

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws IOException
    {
        try {
            final ResourceResolver resourceResolver = request.getResourceResolver();
            this.resolver.set(resourceResolver);
            this.formsHomepage.set(resourceResolver.getResource("/Forms"));
            this.subjectsHomepage.set(resourceResolver.getResource("/Subjects"));
            this.queryManager.set(resourceResolver.adaptTo(Session.class).getWorkspace().getQueryManager());

            String[] subjectTypesParam = request.getParameterValues(":subjectType");
            // If :subjectType isn't set, then /SubjectTypes/Patient should be assumed to be the default value.
            if (subjectTypesParam == null || subjectTypesParam.length == 0) {
                subjectTypesParam = new String[] { "/SubjectTypes/Patient" };
            }
            this.subjectTypes.set(subjectTypesParam);

            parseData(request, StringUtils.equals("true", request.getParameter(":patch")));
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (RepositoryException e) {
            LOGGER.error("Failed to import data: {}", e.getMessage(), e);
        } finally {
            this.subjectsHomepage.remove();
            this.subjectTypes.remove();
            this.subjectCache.remove();
            this.questionnaire.remove();
            this.questionCache.remove();
            this.warnedCache.remove();
            this.formsHomepage.remove();
            this.nodesToCheckin.remove();
            this.cachedAnswers.remove();
            this.resolver.remove();
            this.queryManager.remove();
        }
    }

    /**
     * Parses the uploaded data file, creating or updating nodes of type {@code Form} referencing a specific
     * questionnaire.
     *
     * @param request the request to process, holding the needed request data
     * @param patch if {@code true}, try to update existing nodes when possible; if {@code false}, new forms are always
     *            created
     * @throws IOException if getting the data from the request fails
     * @throws RepositoryException if saving the processed data fails due to repository errors or incorrect data
     */
    private void parseData(final SlingHttpServletRequest request, boolean patch) throws IOException, RepositoryException
    {
        final RequestParameter dataFile = request.getRequestParameter(":data");
        if (dataFile == null) {
            throw new IllegalArgumentException("Required parameter \":data\" missing");
        }

        final String questionnaireName = request.getParameter(":questionnaire");
        if (StringUtils.isBlank(questionnaireName)) {
            throw new IllegalArgumentException("Required parameter \":questionnaire\" missing");
        }
        try {
            this.questionnaire.set(this.resolver.get().getResource(questionnaireName).adaptTo(Node.class));
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Invalid questionnaire name " + questionnaireName);
        }

        CSVFormat format = CSVFormat.TDF.builder().setHeader().setSkipHeaderRecord(true).build();
        try (CSVParser data = CSVParser.parse(dataFile.getInputStream(), StandardCharsets.UTF_8, format)) {
            data.forEach(row -> {
                try {
                    this.parseRow(row, patch);
                } catch (PersistenceException e) {
                    LOGGER.warn("Failed to import row: {}", e.getMessage());
                }
            });
        }
        final Session session = request.getResourceResolver().adaptTo(Session.class);
        session.save();
        final VersionManager vm = session.getWorkspace().getVersionManager();
        this.nodesToCheckin.get().forEach(node -> {
            try {
                vm.checkin(node);
            } catch (RepositoryException e) {
                LOGGER.warn("Failed to check in node {}: {}", node, e.getMessage(), e);
            }
        });
    }

    /**
     * Parses and stores one row of data into a {@code Form} node.
     *
     * @param row the row to parse
     * @param patch if {@code true}, try to find and update an existing form; if {@code false}, a new form is created
     * @throws PersistenceException if saving the processed data fails due to repository errors or incorrect data
     */
    private void parseRow(CSVRecord row, boolean patch) throws PersistenceException
    {
        this.cachedAnswers.set(new HashMap<>());
        final Resource form = getOrCreateForm(row, patch);
        if (form == null) {
            return;
        }
        row.toMap().forEach((fieldName, fieldValue) -> {
            try {
                if (StringUtils.isBlank(fieldValue)) {
                    return;
                }

                if (fieldName.endsWith(NOTE_SUFFIX)) {
                    parseNote(fieldName.trim(), fieldValue, form);
                } else {
                    parseAnswer(fieldName.trim(), fieldValue, form);
                }
            } catch (PersistenceException | RepositoryException e) {
                LOGGER.warn("Failed to parse row [{}]: {}", row.getRecordNumber(), e.getMessage());
            }
        });
        this.nodesToCheckin.get().add(form.getPath());
    }

    /**
     * Parse and store a note to an Answer. This will reuse the answer if it already exists.
     *
     * @param fieldName the column name for this field
     * @param fieldValue the raw value for this field
     * @param form the Form that this Answer belongs to
     * @throws PersistenceException if saving the processed data fails due to repository errors or incorrect data
     * @throws RepositoryException if saving the processed data fails due to repository errors or incorrect data
     */
    private void parseNote(String fieldName, String fieldValue, Resource form)
        throws PersistenceException, RepositoryException
    {
        // Truncate the suffix from the fieldName before finding the related question
        String fieldNamePrefix = fieldName.substring(0, fieldName.length() - NOTE_SUFFIX.length());
        Node question = getQuestion(fieldNamePrefix);
        if (question == null) {
            return;
        }

        Resource answer = getOrCreateAnswer(form, question);
        answer.adaptTo(Node.class).setProperty(NOTE_PROPERTY, fieldValue);
    }

    /**
     * Parses and stores one Answer.
     *
     * @param fieldName the column name for this field
     * @param fieldValue the raw value for this field
     * @param form the Form that this Answer belongs to
     * @throws PersistenceException if saving the processed data fails due to repository errors or incorrect data
     * @throws RepositoryException if saving the processed data fails due to repository errors or incorrect data
     */
    private void parseAnswer(String fieldName, String fieldValue, Resource form)
        throws PersistenceException, RepositoryException
    {
        Node question = getQuestion(fieldName);
        if (question == null) {
            return;
        }

        Resource answer = getOrCreateAnswer(form, question);

        if (question.getProperty("maxAnswers").getLong() == 0) {
            String[] rawValues = fieldValue.split("\n");
            Value[] values = new Value[rawValues.length];
            for (int i = 0; i < rawValues.length; ++i) {
                values[i] = parseAnswerValue(rawValues[i].trim(), question);
            }
            answer.adaptTo(Node.class).setProperty(VALUE_PROPERTY, values);
        } else {
            answer.adaptTo(Node.class).setProperty(VALUE_PROPERTY, parseAnswerValue(fieldValue.trim(), question));
        }
    }

    /**
     * Finds the question corresponding to a field in the current row.
     *
     * @param columnName the name of the column in the CSV
     * @return the corresponding question node, or {@code null} if no question can be automatically identified from the
     *         given column name
     */
    private Node getQuestion(String columnName)
    {
        Map<String, Node> cache = this.questionCache.get();
        try {
            if (!cache.containsKey(columnName)) {
                String query =
                    String.format("select n from [cards:Question] as n where isdescendantnode(n,'%s') and ",
                        SearchUtils.escapeQueryArgument(this.questionnaire.get().getPath()));

                // checking if NAME(n) = <an invalid identifier> will cause the entire query to fail
                // instead, we'll form the query differently depending on whether or not it is a valid JCR name
                if (SearchUtils.isValidNodeName(columnName)) {
                    query += String.format("(n.text = '%s' or NAME(n) = '%s')",
                        SearchUtils.escapeQueryArgument(columnName),
                        SearchUtils.escapeQueryArgument(columnName));
                } else {
                    query += String.format("n.text = '%s'",
                        SearchUtils.escapeQueryArgument(columnName));
                }

                Iterator<Resource> results = this.resolver.get().findResources(query, "JCR-SQL2");
                if (!results.hasNext()) {
                    cache.put(columnName, null);
                } else {
                    cache.put(columnName, results.next().adaptTo(Node.class));
                }
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Unexpected exception while searching for the question [{}]: {}", columnName, e.getMessage());
        }

        Node question = cache.get(columnName);
        if (question == null) {
            if (this.warnedCache.get().add(columnName)) {
                LOGGER.info("Unknown field: {}", columnName);
            }
        }
        return question;
    }

    /**
     * Returns a Resource for storing an Answer corresponding to the given question. This may be an existing node, if
     * one already exists in this form, or a newly created one otherwise.
     * <p>
     * FIXME This needs to be revisited to add support for repeated sections.
     * </p>
     *
     * @param form the form being processed
     * @param question the question being answered
     * @throws RepositoryException if accessing the resource fails due to repository errors
     * @throws PersistenceException if creating a new resource fails due to repository errors
     */
    private Resource getOrCreateAnswer(final Resource form, final Node question)
        throws RepositoryException, PersistenceException
    {
        if (this.cachedAnswers.get().containsKey(question.getIdentifier())) {
            return this.cachedAnswers.get().get(question.getIdentifier());
        }

        final String query =
            String.format("select n from [cards:Answer] as n where n.question = '%s' and isdescendantnode(n,'%s')",
                question.getIdentifier(), form.getPath());
        Iterator<Resource> results = this.resolver.get().findResources(query, "JCR-SQL2");
        if (results.hasNext()) {
            return results.next();
        }

        Map<String, Object> answerProperties = new HashMap<>();
        answerProperties.put("jcr:primaryType", getAnswerNodeType(question));
        answerProperties.put("question", question);
        Resource answerParent = findOrCreateParent(form, question);
        Resource newNode = this.resolver.get().create(answerParent, UUID.randomUUID().toString(), answerProperties);
        this.cachedAnswers.get().put(question.getIdentifier(), newNode);
        return newNode;
    }

    /**
     * Gets the parent node under which an answer must be stored. This can be either the form directly, or a (possibly
     * nested) {@code AnswerSection}.
     *
     * @param form the form being processed
     * @param question the question being answered
     * @return the resource node under which the answer must be stored
     * @throws PersistenceException if a new resource must be created, but doing so fails
     * @throws RepositoryException if accessing the repository fails
     */
    private Resource findOrCreateParent(final Resource form, final Node question)
        throws PersistenceException, RepositoryException
    {
        // Find all the intermediate sections between the question and the questionnaire, bottom-to-top
        final Iterator<Node> sections = getAncestorSections(question);
        // Create all the needed intermediate answer sections between the form and the answer, top-to-bottom
        Resource answerParent = form;
        while (sections.hasNext()) {
            answerParent = getAnswerSection(sections.next(), answerParent);
        }
        return answerParent;
    }

    /**
     * Returns an iterator over the ancestor sections, in descending order from the questionnaire down to the question
     * itself. The iterator may be empty, if the question is a direct child of the questionnaire.
     *
     * @param question the question whose ancestors are to be retrieved
     * @return an iterator over {@code Section} nodes, may be empty
     * @throws RepositoryException if accessing the repository fails
     */
    private Iterator<Node> getAncestorSections(final Node question) throws RepositoryException
    {
        Node questionParent = question.getParent();
        final Deque<Node> sections = new LinkedList<>();
        while (!"cards:Questionnaire".equals(questionParent.getPrimaryNodeType().getName())) {
            sections.push(questionParent);
            questionParent = questionParent.getParent();
        }
        return sections.iterator();
    }

    /**
     * Finds or creates an {@code AnswerSection} node under {@code parent} corresponding to the given {@code section}.
     *
     * @param section the questionnaire section to be answered
     * @param parent the parent node in which to look for the answer section, either a {@code Form} or another
     *            {@code AnswerSection}
     * @return a resource of type {@code cards:AnswerSection} referencing the given questionnaire section, either one
     *         that already existed, or a newly created one
     * @throws PersistenceException if a new resource must be created, but doing so fails
     * @throws RepositoryException if accessing the repository fails
     */
    private Resource getAnswerSection(final Node section, final Resource parent)
        throws PersistenceException, RepositoryException
    {
        String sectionRef = section.getProperty("jcr:uuid").getString();
        Resource answerSection = null;
        Resource result = null;
        Iterator<Resource> children = parent.listChildren();
        while (children.hasNext()) {
            Resource child = children.next();
            if (sectionRef.equals(child.getValueMap().get("section", ""))) {
                answerSection = child;
                break;
            }
        }
        if (answerSection != null) {
            result = answerSection;
        } else {
            Map<String, Object> answerSectionProperties = new HashMap<>();
            answerSectionProperties.put("jcr:primaryType", "cards:AnswerSection");
            answerSectionProperties.put("section", section);
            result = this.resolver.get().create(parent, UUID.randomUUID().toString(), answerSectionProperties);
        }
        return result;
    }

    /**
     * Computes the right node type for storing an Answer, based on the configuration of its Question.
     *
     * @param question the question that is being answered, where the Answer configuration is defined
     * @return a value to use for the {@code jcr:primaryType} of the Answer node to be created
     * @throws RepositoryException if accessing the resource fails due to repository errors
     */
    private String getAnswerNodeType(final Node question) throws RepositoryException
    {
        final String dataType = question.getProperty("dataType").getString();
        String result;
        switch (dataType) {
            case "long":
                result = "cards:LongAnswer";
                break;
            case "double":
                result = "cards:DoubleAnswer";
                break;
            case "decimal":
                result = "cards:DecimalAnswer";
                break;
            case "boolean":
                result = "cards:BooleanAnswer";
                break;
            case "date":
                result = "cards:DateAnswer";
                break;
            case "time":
                result = "cards:TimeAnswer";
                break;
            case "vocabulary":
                result = "cards:VocabularyAnswer";
                break;
            case "text":
            default:
                result = "cards:TextAnswer";
        }
        return result;
    }

    /**
     * Converts a text read from the input CSV into a properly typed value to store in the repository.
     *
     * @param rawValue the serialized value to parse, may be {@code null}
     * @param question the question that is being answered, where the Answer configuration is defined
     * @return a typed Value to store in the repository
     * @throws RepositoryException if accessing the resource fails due to repository errors
     */
    private Value parseAnswerValue(String rawValue, Node question) throws RepositoryException
    {
        final String dataType = question.getProperty("dataType").getString();
        Value result = null;
        try {
            ValueFactory valueFactory = this.resolver.get().adaptTo(Session.class).getValueFactory();

            switch (dataType) {
                case "long":
                    result = valueFactory.createValue(Long.valueOf(rawValue));
                    break;
                case "double":
                    result = valueFactory.createValue(Double.valueOf(rawValue));
                    break;
                case "decimal":
                    result = valueFactory.createValue(new BigDecimal(rawValue));
                    break;
                case "boolean":
                    result = valueFactory.createValue(
                        BooleanUtils.toInteger(BooleanUtils.toBooleanObject(rawValue), 1, 0, -1));
                    break;
                case "date":
                    result = valueFactory.createValue(parseDate(rawValue));
                    break;
                case "text":
                default:
                    result = valueFactory.createValue(standardizeValue(rawValue, question));
            }
        } catch (NumberFormatException | NullPointerException e) {
            LOGGER.warn("Invalid value of type {}: {}", dataType, rawValue);
        } catch (RepositoryException e) {
            LOGGER.warn("Value factory is unexpectedly unavailable: {}", e.getMessage());
            return null;
        }
        return result;
    }

    /**
     * Parses a date from the given input string.
     *
     * @param str the serialized date to parse
     * @return the parsed date, or {@code null} if the date cannot be parsed
     */
    private Calendar parseDate(final String str)
    {
        final Date date = DATE_FORMATS.stream().map(format -> {
            try {
                return format.parse(str);
            } catch (Exception ex) {
                return null;
            }
        }).filter(Objects::nonNull).findFirst().orElse(null);
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }

    /**
     * Converts user-facing labels to the stored value, if the question being answered has a list of default options,
     * and one of the options has a label or value matching (case insensitive) the parsed value. To allow for different
     * options that differ only in their case, priority is given, in order, to:
     * <ol>
     * <li>Exact match of a value, which means that the input file already stored the correct value</li>
     * <li>Case-insensitive match of a value</li>
     * <li>Case-sensitive match of a label</li>
     * <li>Case-insensitive match of a label</li>
     * </ol>
     *
     * @param value the value as read from the input file
     * @param question the question that is being answered, where the Answer configuration is defined
     * @return an equivalent standard value to be stored, may be the same as the input value
     */
    private String standardizeValue(final String value, Node question)
    {
        String result = null;
        try {
            for (String prop : new String[] { VALUE_PROPERTY, LABEL_PROPERTY }) {
                NodeIterator childNodes = question.getNodes();
                while (childNodes.hasNext()) {
                    Node childNode = childNodes.nextNode();
                    if (!"cards:AnswerOption".equals(childNode.getPrimaryNodeType().getName())
                        || !childNode.hasProperty(prop)) {
                        continue;
                    }
                    if (StringUtils.equals(value, childNode.getProperty(prop).getString())) {
                        // We found an exact match for a known option, no need to do any further processing
                        return childNode.getProperty(VALUE_PROPERTY).getString();
                    } else if (StringUtils.equalsIgnoreCase(value, childNode.getProperty(prop).getString())) {
                        result = childNode.getProperty(VALUE_PROPERTY).getString();
                    }
                }
                if (result != null) {
                    // We found a case-insensitive value match, return it
                    return result;
                }
            }
            return value;
        } catch (RepositoryException ex) {
            LOGGER.warn("Unexpected error while standardizing value [{}] for question [{}]: {}", value, question,
                ex.getMessage());
        }
        return result;
    }

    /**
     * Returns a Resource for storing a form corresponding to the given data row. This may be an existing node, if
     * {@code patch == true} and one already exists for the targeted questionnaire and subject, or a newly created one
     * otherwise.
     *
     * @param row the input CSV row to process, where the affected Subject identifier is to be found
     * @param patch if {@code true}, try to find an existing form; otherwise, force the creation of a new node in the
     *            repository
     * @return the Resource to use for storing the row
     * @throws PersistenceException if creating a new Resource fails
     */
    private Resource getOrCreateForm(final CSVRecord row, boolean patch) throws PersistenceException
    {
        final Node subject = getOrCreateSubject(row);
        if (subject == null) {
            LOGGER.warn("Cannot determine subject for row #{}", row.getRecordNumber());
            return null;
        }
        Resource result = null;
        if (patch) {
            result = findForm(subject);
        }
        if (result == null) {
            final Map<String, Object> formProperties = new HashMap<>();
            formProperties.put("jcr:primaryType", "cards:Form");
            formProperties.put("questionnaire", this.questionnaire.get());
            formProperties.put("subject", subject);
            result = this.resolver.get().create(this.formsHomepage.get(), UUID.randomUUID().toString(), formProperties);
        } else {
            try {
                result.adaptTo(Node.class).getSession().getWorkspace().getVersionManager().checkout(result.getPath());
            } catch (RepositoryException e) {
                LOGGER.warn("Failed to checkout form {}: {}", result.getPath(), e.getMessage(), e);
            }
        }
        return result;
    }

    /**
     * Finds an existing form for the given questionnaire and subject.
     *
     * @param subject the subject for which the Form is completed
     * @return an existing resource matching the targeted questionnaire and subject, or {@code null} if such a resource
     *         doesn't exist yet
     */
    private Resource findForm(final Node subject)
    {
        try {
            final String query =
                String.format("select n from [cards:Form] as n where n.subject = '%s' and n.questionnaire = '%s'",
                    subject.getIdentifier(), this.questionnaire.get().getIdentifier());
            final Iterator<Resource> results = this.resolver.get().findResources(query, "JCR-SQL2");
            if (results.hasNext()) {
                return results.next();
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Unexpected exception while searching for a form: {}", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns the Node where a specific Subject is stored. If the Subject wasn't already stored in the repository, a
     * new node is created for it and returned.
     *
     * @param row the input CSV row to process, where the affected Subject identifier is to be found
     * @return the Resource where the Subject is stored; may be an existing or a newly created resource; may be
     *         {@code null} if a Subject identifier is not present in the row
     */
    private Node getOrCreateSubject(final CSVRecord row)
    // For each subject type, identify the target subject
    // Given a parent subject (initially null) & a subject type path:
    // 1. get the Node for the subject type (cached for future rows)
    // 2. read label, look for a column with that label or label + “ ID” in the row, get value
    // 3. search for a Subject with the right type, parent, and identifier
    // If found, use it as the current subject. If not, create it,
    // specifying the type, parent, and identifier, and use it as the current subject.
    // Update the parent variable to be the current subject.
    // If there are more entries in the subject types list, recurse with the new parent and new subject type.
    // When the whole list of subject types is processed, return current subject as the subject to use for the row.
    {
        Node previous = null;
        Node current = null;
        for (String type : this.subjectTypes.get()) {
            current = getOrCreateSubject(row, type, current);
            // If this subject identifier is empty, then the last used subject type
            // e.g. If a patient and tumor is specified but no tumor region, then we instead want to create/use
            // the tumor ID
            if (current == null) {
                return previous;
            }
            previous = current;
        }
        return current;
    }

    private Node getOrCreateSubject(CSVRecord row, String type, Node parent)
    {
        // Find the subject corresponding to this
        Node typeNode = this.resolver.get().getResource(type).adaptTo(Node.class);
        String subjectId = findSubjectId(row, typeNode);
        if (StringUtils.isBlank(subjectId)) {
            return null;
        }
        String subjectTypeString = type;
        String subjectKey = subjectId.concat(subjectTypeString);
        if (parent != null) {
            try {
                subjectKey = parent.getProperty("identifier").getString().concat(subjectKey);
            } catch (RepositoryException ex) {
                // No change
            }
        }

        Node subject = findSubject(subjectKey, subjectId, typeNode, parent);
        if (subject != null) {
            return subject;
        }

        // Create a new subject
        return createSubject(subjectKey, subjectId, typeNode, parent);
    }

    /***
     * Find a subject with the given parameters.
     *
     * @param subjectKey A key for this subject to search the cache for
     * @param subjectId The identifier of the subject
     * @param typeNode The Node of the cards:SubjectType for the subject
     * @param parent The parent cards:Subject for this subject
     * @return A subject Node if it exists, or null.
     */
    private Node findSubject(String subjectKey, String subjectId, Node typeNode, Node parent)
    {
        // Load a cached version if we already have one
        Map<String, Node> cache = this.subjectCache.get();
        if (cache.containsKey(subjectKey)) {
            return cache.get(subjectKey);
        }

        String query = String.format("select n from [cards:Subject] as n where n.identifier = '%s'",
            SearchUtils.escapeQueryArgument(subjectId));
        try {
            if (typeNode != null) {
                query += " and n.type = '" + typeNode.getProperty("jcr:uuid").getValue() + "'";
            }
            if (parent != null) {
                query += " and ischildnode(n, '" + parent.getPath() + "')";
            }
        } catch (RepositoryException ex) {
            // No change to query
        }

        try {
            Query queryObj = this.queryManager.get().createQuery(query, "JCR-SQL2");
            queryObj.setLimit(1);
            NodeIterator nodeResult = queryObj.execute().getNodes();

            // If a result was found, cache it and return
            if (nodeResult.hasNext()) {
                Node subject = nodeResult.nextNode();
                cache.put(subjectKey, subject);
                return subject;
            }
        } catch (RepositoryException ex) {
            // Could not find subject, return null
        }
        return null;
    }

    /***
     * Create a new subject.
     *
     * @param subjectId The identifier for the subject
     * @param typeNode The node of the cards:SubjectType for this subject
     * @param parent The parent of this subject
     * @param subjectKey A string to identify this subject by in the cache
     * @return A new subject Node if one could be made, or null.
     */
    private Node createSubject(String subjectKey, String subjectId, Node typeNode, Node parent)
    {
        final Map<String, Object> subjectProperties = new HashMap<>();
        subjectProperties.put("jcr:primaryType", "cards:Subject");
        subjectProperties.put("identifier", subjectId);
        subjectProperties.put("type", typeNode);
        if (parent != null) {
            subjectProperties.put("parents", parent);
        }
        try {
            Resource parentResource = parent != null
                ? this.resolver.get().getResource(parent.getPath())
                : this.subjectsHomepage.get();
            Node subject = this.resolver.get().create(parentResource, UUID.randomUUID().toString(), subjectProperties)
                .adaptTo(Node.class);
            this.subjectCache.get().put(subjectKey, subject);
            this.nodesToCheckin.get().add(subject.getPath());
            return subject;
        } catch (PersistenceException e) {
            LOGGER.warn("Failed to create new subject {}: {}", subjectKey, e.getMessage(), e);
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to check in new subject {}: {}", subjectKey, e.getMessage(), e);
        }
        return null;
    }

    /**
     * Looks for a Subject Identifier in the given data row.
     *
     * @param row the input CSV row to process, where the affected Subject identifier is to be found
     * @param typeNode Subject type node
     * @return a subject identifier, or {@code null} if one cannot be found
     */
    private String findSubjectId(CSVRecord row, Node typeNode)
    {
        String label;
        try {
            label = typeNode.getProperty("label").getString();
        } catch (RepositoryException ex) {
            return null;
        }

        String result = null;
        String[] suffixes = { "", " ID" };
        for (String suffix : suffixes) {
            try {
                result = row.get(label + suffix);
                if (StringUtils.isNotBlank(result)) {
                    break;
                }
            } catch (IllegalArgumentException ex) {
                // Column is not mapped, continue;
            }
        }
        return result;
    }
}
