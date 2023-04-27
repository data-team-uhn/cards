/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.uhndata.cards.clarity.importer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;
import io.uhndata.cards.metrics.Metrics;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Query the Clarity server every so often to obtain all of the visits and patients that have appeared throughout the
 * day. This will patch over patient and visit information forms.
 *
 * @version $Id$
 */
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class ClarityImportTask implements Runnable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClarityImportTask.class);

    private static final String MAPPING_CONFIG = "/apps/cards/clarityImport";

    private static final String SUBJECT_TYPE_PROP = "subjectType";

    private static final String QUESTION_PROP = "question";

    private static final String QUESTIONNAIRE_PROP = "questionnaire";

    private static final String DATA_TYPE_PROP = "dataType";

    private static final String PRIMARY_TYPE_PROP = "jcr:primaryType";

    private static final String VALUE_PROP = "value";

    private final int dayToQuery;

    private final ThreadLocal<Map<String, String>> sqlColumnToDataType = ThreadLocal.withInitial(HashMap::new);

    private final ThreadLocal<List<String>> nodesToCheckin = ThreadLocal.withInitial(LinkedList::new);

    private final ThreadLocal<VersionManager> versionManager = new ThreadLocal<>();

    private final ThreadLocal<ClaritySubjectMapping> clarityImportConfiguration =
        ThreadLocal.withInitial(ClaritySubjectMapping::new);

    private final ThreadLocal<Map<String, Long>> metricsAdjustments = ThreadLocal.withInitial(HashMap::new);

    private final ThreadResourceResolverProvider rrp;

    private final List<ClarityDataProcessor> processors;

    // Helper classes

    private enum QuestionType
    {
        DATE,
        STRING,
        BOOLEAN,
        CLINIC
    }

    private static final class ClaritySubjectMapping
    {
        private final String name;

        private final String path;

        private final String subjectIdColumn;

        private final String subjectType;

        private final String incrementMetricOnCreation;

        private final List<ClaritySubjectMapping> childSubjects;

        private final List<ClarityQuestionnaireMapping> questionnaires;

        /**
         * Constructor used for the root of the mapping tree, an empty mapping with the only purpose of holding child
         * subject mappings.
         */
        ClaritySubjectMapping()
        {
            this("", "", "", "", "");
        }

        ClaritySubjectMapping(String name, String subjectIdColumn, String subjectType, String path,
            String incrementMetricOnCreation)
        {
            this.name = name;
            this.path = path;
            this.subjectIdColumn = subjectIdColumn;
            this.subjectType = subjectType;
            this.incrementMetricOnCreation = incrementMetricOnCreation;
            this.childSubjects = new LinkedList<>();
            this.questionnaires = new LinkedList<>();
        }

        private void addChildSubject(ClaritySubjectMapping mapping)
        {
            this.childSubjects.add(mapping);
        }

        private void addQuestionnaire(ClarityQuestionnaireMapping mapping)
        {
            this.questionnaires.add(mapping);
        }

        @Override
        public String toString()
        {
            return String.format("Subject mapping %s: %s -> %s", this.name, this.subjectIdColumn, this.subjectType);
        }
    }

    private static final class ClarityQuestionnaireMapping
    {
        private final String name;

        private final boolean updatesExisting;

        private final List<ClarityQuestionMapping> questions;

        ClarityQuestionnaireMapping(final String name, final boolean updatesExisting)
        {
            this.name = name;
            this.updatesExisting = updatesExisting;
            this.questions = new LinkedList<>();
        }

        private void addQuestion(final ClarityQuestionMapping mapping)
        {
            this.questions.add(mapping);
        }

        private Resource getQuestionnaireResource(final ResourceResolver resolver)
        {
            return resolver.resolve("/Questionnaires/" + this.name);
        }

        @Override
        public String toString()
        {
            return String.format("Questionnaire mapping %s", this.name);
        }
    }

    private static final class ClarityQuestionMapping
    {
        private final String name;

        private final String question;

        private final String column;

        private final QuestionType questionType;

        private final boolean computed;

        ClarityQuestionMapping(final String name, final String question, final String column,
            final QuestionType questionType, final boolean computed)
        {
            this.name = name;
            this.question = question;
            this.column = column;
            this.questionType = questionType;
            this.computed = computed;
        }

        @Override
        public String toString()
        {
            return String.format("Question mapping %s: %s -> %s (%s)", this.name, this.column, this.question,
                this.questionType);
        }
    }

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    ClarityImportTask(final int dayToQuery, final ResourceResolverFactory resolverFactory,
        final ThreadResourceResolverProvider rrp, final List<ClarityDataProcessor> processors)
    {
        this.dayToQuery = dayToQuery;
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
        this.processors = processors;
    }

    // The entry point for running an import

    @Override
    public void run()
    {
        LOGGER.info("Running ClarityImportTask");

        String connectionUrl =
            String.format("jdbc:sqlserver://%s;user=%s;password=%s;encrypt=%s;", System.getenv("CLARITY_SQL_SERVER"),
                System.getenv("CLARITY_SQL_USERNAME"), System.getenv("CLARITY_SQL_PASSWORD"),
                System.getenv("CLARITY_SQL_ENCRYPT"));

        // Connect via SQL to the server
        boolean mustPopResolver = false;
        try (Connection connection = DriverManager.getConnection(connectionUrl);
            ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            this.rrp.push(resolver);
            mustPopResolver = true;

            final Session session = resolver.adaptTo(Session.class);
            this.versionManager.set(session.getWorkspace().getVersionManager());

            populateClarityImportConfiguration(resolver, resolver.resolve(MAPPING_CONFIG),
                this.clarityImportConfiguration.get());

            // Generate and perform the query
            PreparedStatement statement = connection.prepareStatement(generateClarityQuery());
            ResultSet results = statement.executeQuery();

            // Sort the data processors
            List<ClarityDataProcessor> sortedProcessors = new ArrayList<>(this.processors);
            Collections.sort(sortedProcessors);

            while (results.next()) {
                // Create the Subjects and Forms as is needed
                try {
                    createFormsAndSubjects(resolver, results, sortedProcessors);
                    session.save();
                } catch (ParseException | PersistenceException e) {
                    LOGGER.error("Exception while importing data to JCR", e);
                } catch (Exception e) {
                    LOGGER.error("Unhandled exception while importing data: {}", e.getMessage(), e);
                } finally {
                    // If everything was saved successfully, this shouldn't discard anything; but if there was an error,
                    // without discarding the bad data all the subsequent saves would fail too
                    session.refresh(false);
                }
            }

            checkinNodes();
            updatePerformanceCounters();

        } catch (SQLException e) {
            LOGGER.error("Failed to connect to SQL: {}", e.getMessage(), e);
        } catch (LoginException e) {
            LOGGER.error("Could not find service user while writing results: {}", e.getMessage(), e);
        } catch (RepositoryException e) {
            LOGGER.error("Error during Clarity import: {}", e.getMessage(), e);
        } finally {
            cleanupState();
            this.metricsAdjustments.remove();
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void checkinNodes()
    {
        this.nodesToCheckin.get().forEach(node -> {
            try {
                this.versionManager.get().checkin(node);
            } catch (final RepositoryException e) {
                LOGGER.warn("Failed to check in node {}: {}", node, e.getMessage(), e);
            }
        });
    }

    private void updatePerformanceCounters()
    {
        for (Entry<String, Long> metricAdjustment : this.metricsAdjustments.get().entrySet()) {
            Metrics.increment(this.resolverFactory, metricAdjustment.getKey(), metricAdjustment.getValue());
        }
    }

    // Methods for preparing the Clarity SQL query

    private void populateClarityImportConfiguration(ResourceResolver resolver, Resource configNode,
        ClaritySubjectMapping clarityConf)
    {
        for (Resource configChildNode : configNode.getChildren()) {
            String configChildNodeType = configChildNode.getValueMap().get(PRIMARY_TYPE_PROP, "");
            if ("cards:ClaritySubjectMapping".equals(configChildNodeType)) {
                String subjectNodeType = configChildNode.getValueMap().get(SUBJECT_TYPE_PROP, "");
                String subjectIDColumnLabel = configChildNode.getValueMap().get("subjectIDColumn", "");
                String incrementMetricOnCreation = configChildNode.getValueMap().get("incrementMetricOnCreation", "");

                // Add this cards:ClaritySubjectMapping to the local Java data structures
                ClaritySubjectMapping claritySubjectMapping = new ClaritySubjectMapping(configChildNode.getName(),
                    subjectIDColumnLabel, subjectNodeType, clarityConf.path + "/" + configChildNode.getName(),
                    incrementMetricOnCreation);

                // Iterate through all Questionnaires that are to be created
                Resource questionnaires = configChildNode.getChild("questionnaires");
                if (questionnaires != null) {
                    // Add the questionnaires associated with this subject to the local Java data structures
                    for (Resource questionnaire : questionnaires.getChildren()) {
                        boolean updatesExisting = questionnaire.getValueMap().get("updatesExisting", false);
                        ClarityQuestionnaireMapping clarityQuestionnaireMapping = new ClarityQuestionnaireMapping(
                            questionnaire.getName(), updatesExisting);

                        for (Resource questionMapping : questionnaire.getChildren()) {
                            // Add the questions associated with this questionnaire to the local Java data structures
                            String questionPath = questionMapping.getValueMap().get(QUESTION_PROP, "");
                            String column = questionMapping.getValueMap().get("column", "");
                            boolean computed = questionMapping.getValueMap().get("computed", Boolean.FALSE);
                            Resource questionResource = resolver.resolve(questionPath);
                            QuestionType qType = this.getQuestionType(questionResource);
                            ClarityQuestionMapping clarityQuestionMapping = new ClarityQuestionMapping(
                                questionMapping.getName(), questionPath, column, qType, computed);
                            clarityQuestionnaireMapping.addQuestion(clarityQuestionMapping);

                            // Populate this.sqlColumnToDataType
                            if (!clarityQuestionMapping.computed) {
                                this.sqlColumnToDataType.get().put(column,
                                    questionResource.getValueMap().get(DATA_TYPE_PROP, ""));
                            }
                        }
                        claritySubjectMapping.addQuestionnaire(clarityQuestionnaireMapping);
                    }
                }

                // Recursively go through the childSubjects
                Resource childSubjects = configChildNode.getChild("childSubjects");
                if (childSubjects != null) {
                    populateClarityImportConfiguration(resolver, childSubjects, claritySubjectMapping);
                }

                // Attach claritySubjectMapping as a child of clarityConf
                clarityConf.addChildSubject(claritySubjectMapping);
            }
        }
    }

    /***
     * Identify the question type from a question Resource.
     *
     * @param question the cards:Question node to analyze
     * @return A QuestionType corresponding to the given cards:Question node
     */
    private QuestionType getQuestionType(Resource question)
    {
        ValueMap questionProps = question.getValueMap();
        String dataType = questionProps.containsKey(DATA_TYPE_PROP) ? questionProps.get(DATA_TYPE_PROP, "") : "";
        String primaryType = questionProps.containsKey("primaryType") ? questionProps.get("primaryType", "") : "";
        if ("date".equals(dataType)) {
            return QuestionType.DATE;
        } else if ("boolean".equals(dataType)) {
            return QuestionType.BOOLEAN;
        } else if ("cards:ClinicMapping".equals(primaryType)) {
            return QuestionType.CLINIC;
        }
        return QuestionType.STRING;
    }

    private String generateClarityQuery()
    {
        String queryString = "SELECT ";
        Iterator<Map.Entry<String, String>> columnsIterator = this.sqlColumnToDataType.get().entrySet().iterator();
        while (columnsIterator.hasNext()) {
            Map.Entry<String, String> col = columnsIterator.next();
            if ("date".equals(col.getValue())) {
                queryString += "FORMAT([" + col.getKey() + "], 'yyyy-MM-dd HH:mm:ss') AS [" + col.getKey() + "]";
            } else {
                queryString += "[" + col.getKey() + "]";
            }
            if (columnsIterator.hasNext()) {
                queryString += ", ";
            }
        }
        queryString += " FROM " + System.getenv("CLARITY_SQL_SCHEMA") + "." + System.getenv("CLARITY_SQL_TABLE");
        if (this.dayToQuery != Integer.MAX_VALUE && System.getenv("CLARITY_EVENT_TIME_COLUMN") != null) {
            queryString += " WHERE CAST(" + System.getenv("CLARITY_EVENT_TIME_COLUMN") + " AS DATE) = CAST(GETDATE()"
                + (this.dayToQuery >= 0 ? "+" : "") + this.dayToQuery + " AS DATE)";
        }
        if (System.getenv("CLARITY_EVENT_TIME_COLUMN") != null) {
            queryString += " ORDER BY " + System.getenv("CLARITY_EVENT_TIME_COLUMN") + ";";
        }

        return queryString;
    }

    // Methods for handling a result row

    private void createFormsAndSubjects(ResourceResolver resolver, ResultSet sqlRow,
        List<ClarityDataProcessor> processors)
        throws ParseException, PersistenceException, RepositoryException, SQLException
    {
        Map<String, String> row = new HashMap<>();
        final int columnCount = sqlRow.getMetaData().getColumnCount();
        for (int column = 1; column <= columnCount; column++) {
            row.put(sqlRow.getMetaData().getColumnName(column), sqlRow.getString(column));
        }
        addSubjectIdentifiersToData(row, this.clarityImportConfiguration.get());

        for (ClarityDataProcessor processor : processors) {
            try {
                row = processor.processEntry(row);
                if (row == null) {
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("Unhandled exception while processing data: {}", e.getMessage(), e);
            }
        }
        // Recursively move down the local Clarity Import configuration tree
        walkThroughLocalConfig(resolver, row, this.clarityImportConfiguration.get(),
            resolver.resolve("/Subjects"));
    }

    private void addSubjectIdentifiersToData(final Map<String, String> row, final ClaritySubjectMapping subjectMapping)
    {
        row.put(subjectMapping.subjectType, row.get(subjectMapping.subjectIdColumn));
        subjectMapping.childSubjects.forEach(child -> addSubjectIdentifiersToData(row, child));
    }

    private void walkThroughLocalConfig(ResourceResolver resolver, Map<String, String> row,
        ClaritySubjectMapping subjectMapping, Resource subjectParent)
        throws ParseException, PersistenceException, RepositoryException, SQLException
    {
        for (ClaritySubjectMapping childSubjectMapping : subjectMapping.childSubjects) {
            // Get or create the subject
            Resource newSubjectParent = getOrCreateSubject(resolver, row, childSubjectMapping, subjectParent);
            if (newSubjectParent == null) {
                return;
            }

            for (ClarityQuestionnaireMapping questionnaireMapping : childSubjectMapping.questionnaires) {
                boolean updatesExisting = questionnaireMapping.updatesExisting;
                Resource formNode = getFormForSubject(resolver, questionnaireMapping.getQuestionnaireResource(resolver),
                    newSubjectParent);

                if (updatesExisting && (formNode != null)) {
                    // Update the answers to an existing Form
                    updateExistingForm(resolver, formNode, questionnaireMapping, row);
                } else {
                    // Create a new Form
                    formNode = createForm(resolver, questionnaireMapping.getQuestionnaireResource(resolver),
                        newSubjectParent);

                    // Attach all the Answer nodes to it
                    populateEmptyForm(resolver, formNode, questionnaireMapping, row);

                    // Commit the changes to the JCR
                    resolver.commit();

                    // Perform a JCR check-in to this cards:Form node once the import is completed
                    this.nodesToCheckin.get().add(formNode.getPath());
                }
            }
            walkThroughLocalConfig(resolver, row, childSubjectMapping, newSubjectParent);
        }
    }

    // Methods for storing subjects

    /**
     * Grab a subject of the specified type, or create it if it doesn't exist.
     *
     * @param resolver ResourceResolver to use for reading and writing to the JCR
     * @param row {@code Map<String, String>} object that maps column names to values for a SQL query result row
     * @param subjectMapping ClaritySubjectMapping object describing how a CARDS Subject is to be created from a SQL row
     * @param parent Resource if this is a child of that resource, or null (parent JCR node to defaults to /Subjects/)
     * @return A Subject resource
     */
    private Resource getOrCreateSubject(ResourceResolver resolver, Map<String, String> row,
        ClaritySubjectMapping subjectMapping, Resource parent) throws RepositoryException, PersistenceException
    {

        final String subjectTypePath = subjectMapping.subjectType;
        final String identifier = (!"".equals(subjectMapping.subjectIdColumn))
            ? row.get(subjectMapping.subjectIdColumn) : UUID.randomUUID().toString();
        final String incrementMetricOnCreation = subjectMapping.incrementMetricOnCreation;

        if (StringUtils.isEmpty(identifier)) {
            return null;
        }

        String subjectMatchQuery = String.format(
            "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='%s' option (index tag property)",
            identifier);
        resolver.refresh();
        final Iterator<Resource> subjectResourceIter = resolver.findResources(subjectMatchQuery, "JCR-SQL2");
        if (subjectResourceIter.hasNext()) {
            final Resource subjectResource = subjectResourceIter.next();
            this.versionManager.get().checkout(subjectResource.getPath());
            this.nodesToCheckin.get().add(subjectResource.getPath());
            return subjectResource;
        } else {
            Resource parentResource = parent;
            if (parentResource == null) {
                parentResource = resolver.getResource("/Subjects/");
            }
            final Resource patientType = resolver.getResource(subjectTypePath);
            final Resource newSubject = resolver.create(parentResource, identifier, Map.of(
                ClarityImportTask.PRIMARY_TYPE_PROP, "cards:Subject",
                "identifier", identifier,
                "type", patientType.adaptTo(Node.class)));
            resolver.commit();

            // Adjust the incrementMetricOnCreation referenced metric
            if (!"".equals(incrementMetricOnCreation)) {
                this.metricsAdjustments.get().compute(incrementMetricOnCreation, (k, v) -> v == null ? 1L : v + 1);
            }

            this.nodesToCheckin.get().add(newSubject.getPath());
            return newSubject;
        }
    }

    private Resource getFormForSubject(ResourceResolver resolver, Resource questionnaireResource,
        Resource subjectResource)
    {
        // Get the jcr:uuid associated with questionnairePath
        String questionnaireUUID = questionnaireResource.getValueMap().get("jcr:uuid", "");

        // Get the jcr:uuid associated with subjectPath
        String subjectUUID = subjectResource.getValueMap().get("jcr:uuid", "");

        // Query for a cards:Form node with the specified questionnaire and subject
        String formMatchQuery = String.format(
            "SELECT * FROM [cards:Form] as form WHERE"
                + " form.'subject'='%s'"
                + " AND form.'questionnaire'='%s'"
                + " option (index tag property)",
            subjectUUID,
            questionnaireUUID);

        resolver.refresh();
        final Iterator<Resource> formResourceIter = resolver.findResources(formMatchQuery, "JCR-SQL2");
        if (formResourceIter.hasNext()) {
            return formResourceIter.next();
        }

        return null;
    }

    // Methods for updating an existing form

    private void updateExistingForm(ResourceResolver resolver, Resource formNode,
        ClarityQuestionnaireMapping questionnaireMapping, Map<String, String> row)
        throws ParseException, RepositoryException, SQLException
    {
        this.versionManager.get().checkout(formNode.getPath());
        for (ClarityQuestionMapping questionMapping : questionnaireMapping.questions) {
            if (StringUtils.isBlank(questionMapping.question)) {
                continue;
            }
            replaceFormAnswer(resolver, formNode,
                generateAnswerNodeProperties(resolver, questionMapping, row));
        }
        // Perform a JCR check-in to this cards:Form node once the import is completed
        this.nodesToCheckin.get().add(formNode.getPath());
    }

    private void replaceFormAnswer(final ResourceResolver resolver, final Resource form,
        final Map<String, Object> props) throws RepositoryException
    {
        final String questionUUID = ((Node) props.get(QUESTION_PROP)).getIdentifier();
        for (Resource answer : form.getChildren()) {
            String thisAnswersQuestionUUID = answer.getValueMap().get(QUESTION_PROP, "");
            if (questionUUID.equals(thisAnswersQuestionUUID)) {
                // Now, copy the value from the props Map into the cards:Answer JCR node
                Object newValue = props.get(ClarityImportTask.VALUE_PROP);
                if (newValue instanceof String) {
                    answer.adaptTo(Node.class).setProperty(ClarityImportTask.VALUE_PROP, (String) newValue);
                } else if (newValue instanceof Calendar) {
                    answer.adaptTo(Node.class).setProperty(ClarityImportTask.VALUE_PROP, (Calendar) newValue);
                } else if (newValue instanceof Integer) {
                    answer.adaptTo(Node.class).setProperty(ClarityImportTask.VALUE_PROP,
                        ((Integer) newValue).longValue());
                }
            }
        }
    }

    // Methods for storing a new form

    private Resource createForm(ResourceResolver resolver, Resource questionnaire, Resource subject)
        throws PersistenceException
    {
        return resolver.create(resolver.resolve("/Forms"), UUID.randomUUID().toString(), Map.of(
            ClarityImportTask.PRIMARY_TYPE_PROP, "cards:Form",
            QUESTIONNAIRE_PROP, questionnaire.adaptTo(Node.class),
            "subject", subject.adaptTo(Node.class)));
    }

    private void populateEmptyForm(ResourceResolver resolver, Resource formNode,
        ClarityQuestionnaireMapping questionnaireMapping, Map<String, String> row)
        throws ParseException, PersistenceException, SQLException
    {
        for (ClarityQuestionMapping questionMapping : questionnaireMapping.questions) {
            if (StringUtils.isBlank(questionMapping.question)) {
                continue;
            }
            // Create the answer node in the JCR
            resolver.create(formNode, UUID.randomUUID().toString(),
                generateAnswerNodeProperties(resolver, questionMapping, row));
        }
    }

    private Map<String, Object> generateAnswerNodeProperties(final ResourceResolver resolver,
        final ClarityQuestionMapping questionMapping, final Map<String, String> row) throws ParseException, SQLException
    {
        String questionPath = questionMapping.question;
        String column = questionMapping.column;
        String answerValue = row.get(column);
        QuestionType qType = questionMapping.questionType;
        return generateAnswerNodeProperties(resolver, qType, questionPath, answerValue);
    }

    private Map<String, Object> generateAnswerNodeProperties(final ResourceResolver resolver, final QuestionType qType,
        final String questionPath, final String answerValue) throws ParseException
    {
        Map<String, Object> props = new HashMap<>();
        Resource questionResource = resolver.resolve(questionPath);

        props.put(QUESTION_PROP, questionResource.adaptTo(Node.class));

        if (qType == QuestionType.STRING) {
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:TextAnswer");
            props.put(ClarityImportTask.VALUE_PROP, answerValue == null ? "" : answerValue);
        } else if (qType == QuestionType.DATE) {
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:DateAnswer");
            SimpleDateFormat clarityDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
            Date date = clarityDateFormat.parse(answerValue);
            if (date != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                props.put(ClarityImportTask.VALUE_PROP, calendar);
            } else {
                LOGGER.warn("Could not parse date");
            }
        } else if (qType == QuestionType.BOOLEAN) {
            // Note that the MS-SQL database doesn't save booleans as true/false
            // So instead we have to check if it is Yes or No
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:BooleanAnswer");
            props.put(ClarityImportTask.VALUE_PROP, "Yes".equals(answerValue) ? 1 : 0);
        } else if (qType == QuestionType.CLINIC) {
            // This is similar to a string, except we transform the output to look at the ClinicMapping node
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:ResourceAnswer");
            props.put(ClarityImportTask.VALUE_PROP,
                answerValue == null ? "" : String.valueOf(answerValue));
        } else {
            LOGGER.warn("Unsupported question type: " + qType);
        }

        // Fix any instances where VALUE should be transformed into [VALUE]
        props = fixAnswerMultiValues(props, questionResource);

        return props;
    }

    /*
     * If the corresponding cards:Question has a maxAnswers value != 1 set the "value" property of this answer node to a
     * single-element list containing only the type-casted answer value.
     */
    private Map<String, Object> fixAnswerMultiValues(Map<String, Object> props, Resource questionResource)
    {
        int maxAnswers = questionResource.getValueMap().get("maxAnswers", 1);
        Object valuePropValue = props.get(ClarityImportTask.VALUE_PROP);
        if ((maxAnswers != 1) && (valuePropValue != null)) {
            // Make this value a single element "multi-valued" property
            Object[] multiValues = { valuePropValue };
            props.put(ClarityImportTask.VALUE_PROP, multiValues);
        }
        return props;
    }

    private void cleanupState()
    {
        // Cleanup all ThreadLocals
        this.nodesToCheckin.remove();
        this.versionManager.remove();
        this.clarityImportConfiguration.remove();
        this.sqlColumnToDataType.remove();
    }
}
