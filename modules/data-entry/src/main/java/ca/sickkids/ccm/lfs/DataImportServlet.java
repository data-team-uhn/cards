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
package ca.sickkids.ccm.lfs;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

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

/**
 * A servlet for importing LFS data from CSV files.
 *
 * @version $Id$
 */
@Component(service = Servlet.class,

    property = {
    "service.description=Data Import Servlet",
    "service.vendor=CCM@SickKids",
    }

)
@SlingServletResourceTypes(resourceTypes = { "lfs/FormsHomepage" }, methods = { "POST" })
@SlingServletName(servletName = "Data Import Servlet")
public class DataImportServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -5821127949309764050L;

    private static final String VALUE_PROPERTY = "value";

    private static final String LABEL_PROPERTY = "label";

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

    private static final List<String> SUBJECT_COLUMN_LABELS = Arrays.asList(
        "Patient ID",
        "Subject ID",
        "Patient",
        "Subject");

    /** Cached Question nodes. */
    private final ThreadLocal<Map<String, Node>> questionCache = new ThreadLocal<Map<String, Node>>()
    {
        @Override
        protected Map<String, Node> initialValue()
        {
            return new HashMap<>();
        }
    };

    /** Cached Question nodes. */
    private final ThreadLocal<Set<String>> warnedCache = new ThreadLocal<Set<String>>()
    {
        @Override
        protected Set<String> initialValue()
        {
            return new HashSet<>();
        }
    };

    /** The Resource Resolver for the current request. */
    private final ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    /** The questionnaire to use for the uploaded CSV. */
    private final ThreadLocal<Node> questionnaire = new ThreadLocal<>();

    /** The {@code /Subjects} resource. */
    private final ThreadLocal<Resource> subjectsHomepage = new ThreadLocal<>();

    /** The {@code /Forms} resource. */
    private final ThreadLocal<Resource> formsHomepage = new ThreadLocal<>();

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws ServletException, IOException
    {
        try {
            final ResourceResolver resourceResolver = request.getResourceResolver();
            this.resolver.set(resourceResolver);
            this.formsHomepage.set(resourceResolver.getResource("/Forms"));
            this.subjectsHomepage.set(resourceResolver.getResource("/Subjects"));
            parseData(request, StringUtils.equals("true", request.getParameter(":patch")));
        } catch (RepositoryException e) {
            LOGGER.error("Failed to import data: {}", e.getMessage(), e);
        } finally {
            this.warnedCache.remove();
            this.questionCache.remove();
            this.formsHomepage.remove();
            this.subjectsHomepage.remove();
            this.resolver.remove();
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
        this.questionnaire.set(this.resolver.get().getResource(questionnaireName).adaptTo(Node.class));

        CSVFormat format = CSVFormat.TDF.withFirstRecordAsHeader();
        try (CSVParser data = CSVParser.parse(dataFile.getInputStream(), StandardCharsets.UTF_8, format)) {
            data.forEach(row -> {
                try {
                    this.parseRow(row, patch);
                } catch (PersistenceException e) {
                    LOGGER.warn("Failed to import row: {}", e.getMessage());
                }
            });
        }
        request.getResourceResolver().adaptTo(Session.class).save();
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
        final Resource form = getOrCreateForm(row, patch);
        row.toMap().forEach((fieldName, fieldValue) -> {
            try {
                parseAnswer(fieldName, fieldValue, form);
            } catch (PersistenceException | RepositoryException e) {
                LOGGER.warn("Failed to parse row [{}]: {}", row.getRecordNumber(), e.getMessage());
            }
        });
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
        if (StringUtils.isBlank(fieldValue)) {
            return;
        }

        Node question = getQuestion(fieldName);
        if (question == null) {
            if (this.warnedCache.get().add(fieldName)) {
                LOGGER.info("Unknown field: {}", fieldName);
            }
            return;
        }

        Resource answer = getOrCreateAnswer(form, question);

        if (question.getProperty("maxAnswers").getLong() == 0) {
            String[] rawValues = fieldValue.split("\n|,");
            Value[] values = new Value[rawValues.length];
            for (int i = 0; i < rawValues.length; ++i) {
                values[i] = parseAnswerValue(rawValues[i], question);
            }
            answer.adaptTo(Node.class).setProperty(VALUE_PROPERTY, values);
        } else {
            answer.adaptTo(Node.class).setProperty(VALUE_PROPERTY, parseAnswerValue(fieldValue, question));
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
                    String.format("select n from [lfs:Question] as n where n.text = '%s' and isdescendantnode(n,'%s')",
                        columnName.replace("'", "''"), this.questionnaire.get().getPath().replaceAll("'", "''"));
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
        return cache.get(columnName);
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
        final String query =
            String.format("select n from [lfs:Answer] as n where n.question = '%s' and isdescendantnode(n,'%s')",
                question.getIdentifier(), form.getPath());
        Iterator<Resource> results = this.resolver.get().findResources(query, "JCR-SQL2");
        if (results.hasNext()) {
            return results.next();
        }

        Map<String, Object> answerProperties = new LinkedHashMap<>();
        answerProperties.put("jcr:primaryType", getAnswerNodeType(question));
        answerProperties.put("question", question);
        Resource answerParent = findOrCreateParent(form, question);
        return this.resolver.get().create(answerParent, UUID.randomUUID().toString(), answerProperties);
    }

    private Resource findOrCreateParent(final Resource form, final Node question)
        throws PersistenceException, RepositoryException
    {
        // Find all the intermediate sections between the question and the questionnaire, bottom-to-top
        Node questionParent = question.getParent();
        final Stack<Node> sections = new Stack<>();
        while (!"lfs:Questionnaire".equals(questionParent.getPrimaryNodeType().getName())) {
            sections.push(questionParent);
            questionParent = questionParent.getParent();
        }
        // Create all the needed intermediate answer sections between the form and the answer, top-to-bottom
        Resource answerParent = form;
        while (!sections.isEmpty()) {
            Node section = sections.pop();
            String sectionRef = section.getProperty("jcr:uuid").getString();
            Resource answerSection = null;
            Iterator<Resource> children = answerParent.getChildren().iterator();
            while (children.hasNext()) {
                Resource child = children.next();
                if (sectionRef.equals(child.getValueMap().get("section", ""))) {
                    answerSection = child;
                }
            }
            if (answerSection != null) {
                answerParent = answerSection;
            } else {
                Map<String, Object> answerSectionProperties = new LinkedHashMap<>();
                answerSectionProperties.put("jcr:primaryType", "lfs:AnswerSection");
                answerSectionProperties.put("section", section);
                answerParent =
                    this.resolver.get().create(answerParent, UUID.randomUUID().toString(), answerSectionProperties);
            }
        }
        return answerParent;
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
                result = "lfs:LongAnswer";
                break;
            case "double":
                result = "lfs:DoubleAnswer";
                break;
            case "decimal":
                result = "lfs:DecimalAnswer";
                break;
            case "boolean":
                result = "lfs:BooleanAnswer";
                break;
            case "date":
                result = "lfs:DateAnswer";
                break;
            case "text":
            default:
                result = "lfs:TextAnswer";
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
                    result = valueFactory.createValue(BooleanUtils.toBoolean(rawValue));
                    break;
                case "date":
                    result = valueFactory.createValue(parseDate(rawValue));
                    break;
                case "text":
                default:
                    result = valueFactory.createValue(standardizeValue(rawValue, question));
            }
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
                    if (!"lfs:AnswerOption".equals(childNode.getPrimaryNodeType().getName())
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
        final Node subject = getOrCreateSubject(row).adaptTo(Node.class);
        Resource result = null;
        if (patch && subject != null) {
            result = findForm(subject);
        }
        if (result == null) {
            final Map<String, Object> formProperties = new LinkedHashMap<>();
            formProperties.put("jcr:primaryType", "lfs:Form");
            formProperties.put("questionnaire", this.questionnaire.get());
            formProperties.put("subject", subject);
            result = this.resolver.get().create(this.formsHomepage.get(), UUID.randomUUID().toString(), formProperties);
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
                String.format("select n from [lfs:Form] as n where n.subject = '%s' and n.questionnaire = '%s'",
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
     * <p>
     * At the moment, this assumes that either a column labeled {@code Patient ID} is present in the CSV, or the subject
     * identifier is the first column in the CSV.
     * </p>
     *
     * @param row the input CSV row to process, where the affected Subject identifier is to be found
     * @return the Resource where the Subject is stored; may be an existing or a newly created resource; may be
     *         {@code null} if a Subject identifier is not present in the row
     */
    private Resource getOrCreateSubject(final CSVRecord row)
    {
        final String subjectId = findSubjectId(row);
        if (StringUtils.isBlank(subjectId)) {
            return null;
        }
        final String query =
            String.format("select n from [lfs:Subject] as n where n.identifier = '%s'", subjectId.replace("'", "''"));
        final Iterator<Resource> results = this.resolver.get().findResources(query, "JCR-SQL2");
        if (results.hasNext()) {
            return results.next();
        }
        final Map<String, Object> subjectProperties = new LinkedHashMap<>();
        subjectProperties.put("jcr:primaryType", "lfs:Subject");
        subjectProperties.put("identifier", subjectId);
        try {
            return this.resolver.get()
                .create(this.subjectsHomepage.get(), UUID.randomUUID().toString(), subjectProperties);
        } catch (PersistenceException e) {
            LOGGER.warn("Unexpected exception while creating a new node for Subject [{}]: {}", subjectId,
                e.getMessage());
            return null;
        }
    }

    /**
     * Looks for a Subject Identifier in the given data row.
     *
     * @param row the input CSV row to process, where the affected Subject identifier is to be found
     * @return a subject identifier, or {@code null} if one cannot be found
     */
    private String findSubjectId(final CSVRecord row)
    {
        final String result = SUBJECT_COLUMN_LABELS.stream().map(label -> {
            try {
                return row.get(label);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }).filter(Objects::nonNull).findFirst().orElse(row.get(0));
        return result;
    }
}
