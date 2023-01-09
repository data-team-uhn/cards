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

    private final Map<String, String> sqlColumnToDataType;

    private ThreadLocal<List<String>> nodesToCheckin = ThreadLocal.withInitial(LinkedList::new);

    private ThreadLocal<VersionManager> versionManager = new ThreadLocal<>();

    private enum QuestionType
    {
        DATE,
        STRING,
        BOOLEAN,
        CLINIC
    }

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    ClarityImportTask(final ResourceResolverFactory resolverFactory)
    {
        this.resolverFactory = resolverFactory;

        this.sqlColumnToDataType = new HashMap<>();

        // Convert our input mapping node to a mapping from column->question
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            processClarityToCardsMapping(resolver, resolver.resolve(MAPPING_CONFIG));
        } catch (RepositoryException e) {
            LOGGER.error("Error reading mapping: {}", e.getMessage(), e);
        } catch (LoginException e) {
            LOGGER.error("Could not find service user while reading mapping: {}", e.getMessage(), e);
        }
    }

    private void processClarityToCardsMapping(ResourceResolver resolver, Resource mapping) throws RepositoryException
    {
        for (Resource child : mapping.getChildren()) {
            if (!"cards:claritySubjectMapping".equals(child.getValueMap().get(PRIMARY_TYPE_PROP, ""))) {
                continue;
            }
            String subjectType = child.getValueMap().get(SUBJECT_TYPE_PROP, "");
            String subjectIDColumn = child.getValueMap().get("subjectIDColumn", "");

            // Handle the questions associated with this Subject
            Resource subjectQuestionnaires = child.getChild("questionnaires");
            if (subjectQuestionnaires != null) {
                for (Resource questionnaireMapping : subjectQuestionnaires.getChildren()) {
                    for (Resource questionMapping : questionnaireMapping.getChildren()) {
                        String questionPath = questionMapping.getValueMap().get(QUESTION_PROP, "");
                        String sqlColumn = questionMapping.getValueMap().get("sqlColumn", "");

                        // Populate this.sqlColumnToDataType
                        this.sqlColumnToDataType.put(sqlColumn,
                            resolver.getResource(questionPath).getValueMap().get(DATA_TYPE_PROP, ""));
                    }
                }
            }

            // Do this same process for all nodes under childSubjects
            Resource childSubjects = child.getChild("childSubjects");
            if (childSubjects != null) {
                processClarityToCardsMapping(resolver, childSubjects);
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
        Iterator<Map.Entry<String, String>> columnsIterator = this.sqlColumnToDataType.entrySet().iterator();
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

    @SuppressWarnings({
        "checkstyle:CyclomaticComplexity",
        "checkstyle:ExecutableStatementCount"
    })
    private void walkThroughClarityImport(ResourceResolver resolver, Resource node, ResultSet sqlRow,
        String subjectPath, Resource subjectParent) throws ParseException, PersistenceException,
            RepositoryException, SQLException
    {
        for (Resource child : node.getChildren()) {
            String childNodeType = child.getValueMap().get(PRIMARY_TYPE_PROP, "");
            if ("cards:claritySubjectMapping".equals(childNodeType)) {
                String subjectNodeType = child.getValueMap().get(SUBJECT_TYPE_PROP, "");
                String subjectIDColumnLabel = child.getValueMap().get("subjectIDColumn", "");
                String subjectIDColumnValue;
                if (!"".equals(subjectIDColumnLabel)) {
                    subjectIDColumnValue = sqlRow.getString(subjectIDColumnLabel);
                } else {
                    subjectIDColumnValue = UUID.randomUUID().toString();
                }

                Resource newSubjectParent = getOrCreateSubject(subjectIDColumnValue,
                    subjectNodeType, resolver, resolver.resolve(subjectPath));
                String newSubjectPath = newSubjectParent.getPath();
                resolver.commit();

                // Iterate through all Questionnaires that are to be created
                Resource questionnaires = child.getChild("questionnaires");
                if (questionnaires != null) {
                    for (Resource questionnaire : questionnaires.getChildren()) {
                        boolean updatesExisting = questionnaire.getValueMap().get("updatesExisting", false);
                        Resource formNode = getFormForSubject(resolver, "/Questionnaires/" + questionnaire.getName(),
                            newSubjectPath);
                        if (updatesExisting && (formNode != null)) {
                            // Update the answers to an existing Form
                            this.versionManager.get().checkout(formNode.getPath());
                            for (Resource questionMapping : questionnaire.getChildren()) {
                                String questionPath = questionMapping.getValueMap().get(QUESTION_PROP, "");
                                String sqlColumn = questionMapping.getValueMap().get("sqlColumn", "");
                                String answerValue = sqlRow.getString(sqlColumn);
                                QuestionType qType = this.getQuestionType(resolver.resolve(questionPath));
                                replaceFormAnswer(resolver, formNode,
                                    generateAnswerNodeProperties(resolver, qType, questionPath, answerValue));
                            }
                            // Perform a JCR check-in to this cards:Form node once the import is completed
                            this.nodesToCheckin.get().add(formNode.getPath());
                        } else {
                            // Create a new Form
                            formNode = createForm(resolver, "/Questionnaires/" + questionnaire.getName(),
                                newSubjectPath);

                            // Attach all the Answer nodes to it
                            for (Resource questionMapping : questionnaire.getChildren()) {
                                String questionPath = questionMapping.getValueMap().get(QUESTION_PROP, "");
                                String sqlColumn = questionMapping.getValueMap().get("sqlColumn", "");
                                String answerValue = sqlRow.getString(sqlColumn);
                                QuestionType qType = this.getQuestionType(resolver.resolve(questionPath));

                                // Create the answer node in the JCR
                                resolver.create(formNode, UUID.randomUUID().toString(), generateAnswerNodeProperties(
                                    resolver, qType, questionPath, answerValue));
                            }

                            // Commit the changes to the JCR
                            resolver.commit();

                            // Perform a JCR check-in to this cards:Form node once the import is completed
                            this.nodesToCheckin.get().add(formNode.getPath());
                        }
                    }
                }

                // Recursively go through the childSubjects
                Resource childSubjects = child.getChild("childSubjects");
                if (childSubjects != null) {
                    walkThroughClarityImport(resolver, childSubjects, sqlRow, newSubjectPath, newSubjectParent);
                }
            }
        }
    }

    private void createFormsAndSubjects(ResourceResolver resolver, ResultSet sqlRow)
        throws ParseException, PersistenceException, RepositoryException, SQLException
    {
        Resource clarityImportNode = resolver.resolve("/apps/cards/clarityImport");

        // Recursively move down clarityImportNode
        walkThroughClarityImport(resolver, clarityImportNode, sqlRow, "/Subjects", resolver.resolve("/Subjects"));
    }

    @Override
    public void run()
    {
        String connectionUrl =
            "jdbc:sqlserver://" + System.getenv("CLARITY_SQL_SERVER") + ";"
            + "user=" + System.getenv("CLARITY_SQL_USERNAME") + ";"
            + "password=" + System.getenv("CLARITY_SQL_PASSWORD") + ";"
            + "encrypt=" + System.getenv("CLARITY_SQL_ENCRYPT") + ";";

        // Connect via SQL to the server
        try (Connection connection = DriverManager.getConnection(connectionUrl);
            PreparedStatement statement = connection.prepareStatement(generateClarityQuery());
            ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            final Session session = resolver.adaptTo(Session.class);
            this.versionManager.set(session.getWorkspace().getVersionManager());

            // Perform the query
            ResultSet results = statement.executeQuery();

            Resource formsHomepage = resolver.resolve("/Forms");
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

    private Resource createForm(ResourceResolver resolver, String questionnairePath, String subjectPath)
        throws PersistenceException
    {
        Resource questionnaire = resolver.resolve(questionnairePath);
        Resource subject = resolver.resolve(subjectPath);
        return resolver.create(resolver.resolve("/Forms"), UUID.randomUUID().toString(), Map.of(
                ClarityImportTask.PRIMARY_TYPE_PROP, "cards:Form",
                QUESTIONNAIRE_PROP, questionnaire.adaptTo(Node.class),
                "subject", subject.adaptTo(Node.class)));
    }

    private Resource getFormForSubject(ResourceResolver resolver, String questionnairePath, String subjectPath)
    {
        // Get the jcr:uuid associated with questionnairePath
        String questionnaireUUID = resolver.resolve(questionnairePath).getValueMap().get("jcr:uuid", "");

        // Get the jcr:uuid associated with subjectPath
        String subjectUUID = resolver.resolve(subjectPath).getValueMap().get("jcr:uuid", "");

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
