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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query the Clarity server every so often to obtain all of the visits & patients that have appeared
 * throughout the day. This will patch over patient & visit information forms.
 *
 * @version $Id$
 */
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

    private ThreadLocal<Map<String, String>> sqlColumnToDataType = new ThreadLocal<>();

    private ThreadLocal<List<String>> nodesToCheckin = ThreadLocal.withInitial(LinkedList::new);

    private ThreadLocal<VersionManager> versionManager = new ThreadLocal<>();

    private ThreadLocal<ClaritySubjectMapping> clarityImportConfiguration = new ThreadLocal<>();

    private enum QuestionType
    {
        DATE,
        STRING,
        BOOLEAN,
        CLINIC
    }

    private class ClaritySubjectMapping
    {
        private String name;
        private String path;
        private String subjectIdColumn;
        private String subjectType;
        private List<ClaritySubjectMapping> childSubjects;
        private List<ClarityQuestionnaireMapping> questionnaires;

        ClaritySubjectMapping(String name, String subjectIdColumn, String subjectType)
        {
            this.name = name;
            this.path = "";
            this.subjectIdColumn = subjectIdColumn;
            this.subjectType = subjectType;
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
    }

    private class ClarityQuestionnaireMapping
    {
        private String name;
        private boolean updatesExisting;
        private List<ClarityQuestionMapping> questions;

        ClarityQuestionnaireMapping(String name, boolean updatesExisting)
        {
            this.name = name;
            this.updatesExisting = updatesExisting;
            this.questions = new LinkedList<>();
        }

        private void addQuestion(ClarityQuestionMapping mapping)
        {
            this.questions.add(mapping);
        }

        private Resource getQuestionnaireResource(ResourceResolver resolver)
        {
            return resolver.resolve("/Questionnaires/" + this.name);
        }
    }

    private class ClarityQuestionMapping
    {
        private String name;
        private String question;
        private String sqlColumn;
        private QuestionType questionType;

        ClarityQuestionMapping(String name, String question, String sqlColumn, QuestionType questionType)
        {
            this.name = name;
            this.question = question;
            this.sqlColumn = sqlColumn;
            this.questionType = questionType;
        }
    }

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    ClarityImportTask(final ResourceResolverFactory resolverFactory)
    {
        this.resolverFactory = resolverFactory;
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
                queryString += "FORMAT(" + col.getKey() + ", 'yyyy-MM-dd HH:mm:ss') AS " + col.getKey();
            } else {
                queryString += col.getKey();
            }
            if (columnsIterator.hasNext()) {
                queryString += ", ";
            }
        }
        queryString += " FROM path.CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS";
        queryString += " WHERE CAST(LoadTime AS DATE) = CAST(GETDATE() AS DATE);";

        return queryString;
    }

    private void updateExistingForm(ResourceResolver resolver, Resource formNode,
        ClarityQuestionnaireMapping questionnaireMapping, ResultSet sqlRow)
        throws ParseException, RepositoryException, SQLException
    {
        this.versionManager.get().checkout(formNode.getPath());
        for (ClarityQuestionMapping questionMapping : questionnaireMapping.questions) {
            replaceFormAnswer(resolver, formNode,
                generateAnswerNodeProperties(resolver, questionMapping, sqlRow));
        }
        // Perform a JCR check-in to this cards:Form node once the import is completed
        this.nodesToCheckin.get().add(formNode.getPath());
    }

    private void populateEmptyForm(ResourceResolver resolver, Resource formNode,
        ClarityQuestionnaireMapping questionnaireMapping, ResultSet sqlRow)
        throws ParseException, PersistenceException, SQLException
    {
        for (ClarityQuestionMapping questionMapping : questionnaireMapping.questions) {
            // Create the answer node in the JCR
            resolver.create(formNode, UUID.randomUUID().toString(),
                generateAnswerNodeProperties(resolver, questionMapping, sqlRow));
        }
    }

    private void populateClarityImportConfiguration(ResourceResolver resolver, Resource configNode,
        ClaritySubjectMapping clarityConf)
    {
        for (Resource configChildNode : configNode.getChildren()) {
            String configChildNodeType = configChildNode.getValueMap().get(PRIMARY_TYPE_PROP, "");
            if ("cards:claritySubjectMapping".equals(configChildNodeType)) {
                String subjectNodeType = configChildNode.getValueMap().get(SUBJECT_TYPE_PROP, "");
                String subjectIDColumnLabel = configChildNode.getValueMap().get("subjectIDColumn", "");

                // Add this cards:claritySubjectMapping to the local Java data structures
                ClaritySubjectMapping claritySubjectMapping = new ClaritySubjectMapping(configChildNode.getName(),
                    subjectIDColumnLabel, subjectNodeType);
                claritySubjectMapping.path = clarityConf.path + "/" + claritySubjectMapping.name;

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
                            String sqlColumn = questionMapping.getValueMap().get("sqlColumn", "");
                            Resource questionResource = resolver.resolve(questionPath);
                            QuestionType qType = this.getQuestionType(questionResource);
                            ClarityQuestionMapping clarityQuestionMapping = new ClarityQuestionMapping(
                                questionMapping.getName(), questionPath, sqlColumn, qType);
                            clarityQuestionnaireMapping.addQuestion(clarityQuestionMapping);

                            // Populate this.sqlColumnToDataType
                            this.sqlColumnToDataType.get().put(sqlColumn,
                                questionResource.getValueMap().get(DATA_TYPE_PROP, ""));
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

    private void walkThroughLocalConfig(ResourceResolver resolver, ResultSet sqlRow,
        ClaritySubjectMapping subjectMapping, Resource subjectParent)
        throws ParseException, PersistenceException, RepositoryException, SQLException
    {
        for (ClaritySubjectMapping childSubjectMapping : subjectMapping.childSubjects) {
            // Get or create the subject
            String subjectNodeType = childSubjectMapping.subjectType;
            String subjectIDColumnLabel = childSubjectMapping.subjectIdColumn;
            String subjectIDColumnValue = (!"".equals(subjectIDColumnLabel))
                ? sqlRow.getString(subjectIDColumnLabel) : UUID.randomUUID().toString();

            Resource newSubjectParent = getOrCreateSubject(subjectIDColumnValue,
                subjectNodeType, resolver, subjectParent);
            resolver.commit();

            for (ClarityQuestionnaireMapping questionnaireMapping : childSubjectMapping.questionnaires) {
                boolean updatesExisting = questionnaireMapping.updatesExisting;
                Resource formNode = getFormForSubject(resolver, questionnaireMapping.getQuestionnaireResource(resolver),
                    newSubjectParent);

                if (updatesExisting && (formNode != null)) {
                    // Update the answers to an existing Form
                    updateExistingForm(resolver, formNode, questionnaireMapping, sqlRow);
                } else {
                    // Create a new Form
                    formNode = createForm(resolver, questionnaireMapping.getQuestionnaireResource(resolver),
                        newSubjectParent);

                    // Attach all the Answer nodes to it
                    populateEmptyForm(resolver, formNode, questionnaireMapping, sqlRow);

                    // Commit the changes to the JCR
                    resolver.commit();

                    // Perform a JCR check-in to this cards:Form node once the import is completed
                    this.nodesToCheckin.get().add(formNode.getPath());
                }
            }
            walkThroughLocalConfig(resolver, sqlRow, childSubjectMapping, newSubjectParent);
        }
    }

    private void createFormsAndSubjects(ResourceResolver resolver, ResultSet sqlRow)
        throws ParseException, PersistenceException, RepositoryException, SQLException
    {
        // Recursively move down the local Clarity Import configuration tree
        walkThroughLocalConfig(resolver, sqlRow, this.clarityImportConfiguration.get(),
            resolver.resolve("/Subjects"));
    }

    @Override
    public void run()
    {
        LOGGER.info("Running ClarityImportTask");

        String connectionUrl =
            "jdbc:sqlserver://" + System.getenv("CLARITY_SQL_SERVER") + ";"
            + "user=" + System.getenv("CLARITY_SQL_USERNAME") + ";"
            + "password=" + System.getenv("CLARITY_SQL_PASSWORD") + ";"
            + "encrypt=" + System.getenv("CLARITY_SQL_ENCRYPT") + ";";

        // Connect via SQL to the server
        try (Connection connection = DriverManager.getConnection(connectionUrl);
            ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {

            final Session session = resolver.adaptTo(Session.class);
            this.versionManager.set(session.getWorkspace().getVersionManager());

            this.clarityImportConfiguration.set(new ClaritySubjectMapping("", "", ""));

            this.sqlColumnToDataType.set(new HashMap<>());

            populateClarityImportConfiguration(resolver, resolver.resolve(MAPPING_CONFIG),
                this.clarityImportConfiguration.get());

            // Generate and perform the query
            PreparedStatement statement = connection.prepareStatement(generateClarityQuery());
            ResultSet results = statement.executeQuery();

            while (results.next()) {
                // Create the Subjects and Forms as is needed
                createFormsAndSubjects(resolver, results);
            }

            session.save();
            this.nodesToCheckin.get().forEach(node -> {
                try {
                    this.versionManager.get().checkin(node);
                } catch (final RepositoryException e) {
                    LOGGER.warn("Failed to check in node {}: {}", node, e.getMessage(), e);
                }
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to connect to SQL: {}", e.getMessage(), e);
        } catch (LoginException e) {
            LOGGER.error("Could not find service user while writing results: {}", e.getMessage(), e);
        } catch (RepositoryException e) {
            LOGGER.error("Error during Clarity import: {}", e.getMessage(), e);
        } catch (PersistenceException e) {
            LOGGER.error("PersistenceException while importing data to JCR");
        } catch (ParseException e) {
            LOGGER.error("ParseException while importing data to JCR");
        } finally {
            // Cleanup all ThreadLocals
            this.nodesToCheckin.remove();
            this.versionManager.remove();
            this.clarityImportConfiguration.remove();
            this.sqlColumnToDataType.remove();
        }
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

    private Map<String, Object> generateAnswerNodeProperties(final ResourceResolver resolver, final QuestionType qType,
        final String questionPath, final String answerValue) throws ParseException
    {
        Map<String, Object> props = new HashMap<>();
        props.put(QUESTION_PROP, resolver.resolve(questionPath).adaptTo(Node.class));

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
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:TextAnswer");
            props.put(ClarityImportTask.VALUE_PROP,
                answerValue == null ? "" : "/Survey/ClinicMapping/" + String.valueOf(answerValue));
        } else {
            LOGGER.warn("Unsupported question type: " + qType);
        }

        return props;
    }

    private Map<String, Object> generateAnswerNodeProperties(final ResourceResolver resolver,
        final ClarityQuestionMapping questionMapping, final ResultSet sqlRow) throws ParseException, SQLException
    {
        String questionPath = questionMapping.question;
        String sqlColumn = questionMapping.sqlColumn;
        String answerValue = sqlRow.getString(sqlColumn);
        QuestionType qType = questionMapping.questionType;
        return generateAnswerNodeProperties(resolver, qType, questionPath, answerValue);
    }

    /**
     * Grab a subject of the specified type, or create it if it doesn't exist.
     *
     * @param identifier Identifier to use for the subject
     * @param subjectTypePath path to a SubjectType node for this subject
     * @param resolver resource resolver to use
     * @param parent parent Resource if this is a child of that resource, or null
     * @return A Subject resource
     */
    private Resource getOrCreateSubject(final String identifier, final String subjectTypePath,
        final ResourceResolver resolver, final Resource parent) throws RepositoryException, PersistenceException
    {
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
            this.nodesToCheckin.get().add(newSubject.getPath());
            return newSubject;
        }
    }

    private Resource createForm(ResourceResolver resolver, Resource questionnaire, Resource subject)
        throws PersistenceException
    {
        return resolver.create(resolver.resolve("/Forms"), UUID.randomUUID().toString(), Map.of(
                ClarityImportTask.PRIMARY_TYPE_PROP, "cards:Form",
                QUESTIONNAIRE_PROP, questionnaire.adaptTo(Node.class),
                "subject", subject.adaptTo(Node.class)));
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
}
