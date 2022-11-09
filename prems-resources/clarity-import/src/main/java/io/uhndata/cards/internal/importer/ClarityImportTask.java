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

package io.uhndata.cards.internal.importer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private static final String SUBJECT_TYPE_PROP = "subjectType";

    private static final String QUESTIONNAIRE_PROP = "questionnaire";

    private static final String DATA_TYPE_PROP = "dataType";

    private static final String PRIMARY_TYPE_PROP = "jcr:primaryType";

    private static final String VALUE_PROP = "value";

    /** Sling property name for the value field on an Answer node. */
    private static final String STATUS_FIELD_PROP = "statusFlags";

    /** Empty status flags value. */
    private static final String[] STATUS_FLAGS = new String[0];

    private final List<String> columns;

    private final Map<String, List<QuestionInformation>> questionnaireToQuestions;

    private final Map<String, String> questionnaireToSubjectType;

    private final Map<String, String> questionnaireToSubjectID;

    private ThreadLocal<List<String>> nodesToCheckin = ThreadLocal.withInitial(LinkedList::new);

    private ThreadLocal<VersionManager> versionManager = new ThreadLocal<>();

    enum QuestionType
    {
        DATE,
        STRING,
        BOOLEAN,
        CLINIC
    }

    /** Storage class for information about a question. */
    class QuestionInformation
    {
        private String question;
        private QuestionType type;
        private String questionnaire;
        private String colName;

        QuestionInformation(String question, QuestionType type, String questionnaire, String colName)
        {
            this.question = question;
            this.type = type;
            this.questionnaire = questionnaire;
            this.colName = colName;
        }

        public String getQuestion()
        {
            return this.question;
        }

        public QuestionType getType()
        {
            return this.type;
        }

        public String getQuestionnaire()
        {
            return this.questionnaire;
        }

        public String getColName()
        {
            return this.colName;
        }
    }

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    ClarityImportTask(final ResourceResolverFactory resolverFactory, Resource mapping)
    {
        this.resolverFactory = resolverFactory;

        this.columns = new LinkedList<>();
        this.questionnaireToQuestions = new HashMap<>();
        this.questionnaireToSubjectType = new HashMap<>();
        this.questionnaireToSubjectID = new HashMap<>();

        // Convert our input mapping node to a mapping from column->question
        try (ResourceResolver resolver = this.resolverFactory
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "ClarityImport"))) {
            getMappingRecursive(resolver, mapping);
        } catch (RepositoryException e) {
            LOGGER.error("Error reading mapping: {}", e.getMessage(), e);
        } catch (LoginException e) {
            LOGGER.error("Could not find service user while reading mapping: {}", e.getMessage(), e);
        }
    }

    private void getMappingRecursive(ResourceResolver resolver, Resource mapping) throws RepositoryException
    {
        // Check for valid node
        ValueMap properties = mapping.getValueMap();
        if (!"cards:clarityMapping".equals(properties.get(PRIMARY_TYPE_PROP, ""))) {
            return;
        }

        // Add properties to mapping
        String questionnaire = properties.get(QUESTIONNAIRE_PROP, "");
        List<QuestionInformation> questionnaireList = new LinkedList<QuestionInformation>();
        String subjectType = properties.get(SUBJECT_TYPE_PROP, "");
        String subjectID = properties.get("subjectIDColumn", "");
        for (String propertyName : properties.keySet()) {
            String questionPath = properties.get(propertyName, "");
            Resource question = resolver.resolve(questionPath);
            if (QUESTIONNAIRE_PROP.equals(propertyName)
                || SUBJECT_TYPE_PROP.equals(propertyName)
                || question.isResourceType(Resource.RESOURCE_TYPE_NON_EXISTING)) {
                continue;
            }

            QuestionType qType = this.getQuestionType(question);
            LOGGER.info(propertyName + " found with question type: " + qType.name());

            QuestionInformation questionInformation =
                new QuestionInformation(questionPath, qType, questionnaire, propertyName);
            questionnaireList.add(questionInformation);
            this.columns.add(propertyName);
        }
        this.questionnaireToQuestions.put(questionnaire, questionnaireList);
        this.questionnaireToSubjectType.put(questionnaire, subjectType);
        this.questionnaireToSubjectID.put(questionnaire, subjectID);

        // Recurse on children
        for (Resource child : mapping.getChildren()) {
            getMappingRecursive(resolver, child);
        }
    }

    /***
     * Identify the question type from a question Resource.
     *
     * @param question the cards:Question node to analyze
     * @return A QuestionType corresponding to the given cards:Question node
     */
    QuestionType getQuestionType(Resource question)
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

    @Override
    public void run()
    {
        String connectionUrl =
            "jdbc:sqlserver://" + System.getenv("CLARITY_SQL_SERVER") + ";"
            + "user=" + System.getenv("CLARITY_SQL_USERNAME") + ";"
            + "password=" + System.getenv("CLARITY_SQL_PASSWORD") + ";"
            + "encrypt=" + System.getenv("CLARITY_SQL_ENCRYPT") + ";";

        // Connect via SQL to the server
        String query = "SELECT * FROM path.CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS"
            + " WHERE CAST(LoadTime AS DATE) = CAST(GETDATE() AS DATE);";
        try (Connection connection = DriverManager.getConnection(connectionUrl);
            PreparedStatement statement = connection.prepareStatement(query);
            ResourceResolver resolver = this.resolverFactory
                .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "ClarityImport"))) {
            final Session session = resolver.adaptTo(Session.class);
            this.versionManager.set(session.getWorkspace().getVersionManager());

            // Perform the query
            ResultSet results = statement.executeQuery();

            Resource formsHomepage = resolver.resolve("/Forms");
            while (results.next()) {
                LOGGER.info("Entry found: " + results.getString("PAT_MRN"));
                Resource subjectParent = resolver.resolve("/Subjects");
                for (Map.Entry<String, List<QuestionInformation>> entry
                    : this.questionnaireToQuestions.entrySet()) {
                    String questionnaire = entry.getKey();
                    subjectParent = createNodeFromEntry(resolver, results, entry.getValue(),
                        questionnaire, subjectParent, formsHomepage);
                }
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
            LOGGER.error("Error during Clarity import: {}", e.getMessage(), e);
        }
    }

    /**
     * Create a subject/Form/answer nodes for the given ResultSet.
     *
     * @param resolver ResourceResolver to use
     * @param result ResultSet with entry details
     * @param questionnaireQuestions List of all questions for this result set
     * @param questionnairePath Path to the questionnaire to fill out
     * @param subjectParent the parent subject for the new subject to create (if needed)
     * @param formsHomepage the /Forms node
     * @return The Subject resource created. The form and answers will point to this.
     */
    public Resource createNodeFromEntry(final ResourceResolver resolver, final ResultSet result,
        final List<QuestionInformation> questionnaireQuestions, final String questionnairePath,
        final Resource subjectParent, final Resource formsHomepage)
        throws PersistenceException, RepositoryException, SQLException
    {
        // Create a new subject for the Form
        String subjectID = this.questionnaireToSubjectID.get(questionnairePath);
        subjectID = "".equals(subjectID) ? UUID.randomUUID().toString() : result.getString(subjectID);
        Resource newSubject = getOrCreateSubject(subjectID, this.questionnaireToSubjectType.get(questionnairePath),
            resolver, subjectParent);

        // Create a Node corresponding to the Form
        Resource questionnaire = resolver.resolve(questionnairePath);
        Resource newForm = resolver.create(formsHomepage, UUID.randomUUID().toString(), Map.of(
                ClarityImportTask.PRIMARY_TYPE_PROP, "cards:Form",
                "questionnaire", questionnaire.adaptTo(Node.class),
                "subject", newSubject.adaptTo(Node.class)));
        this.nodesToCheckin.get().add(newForm.getPath());

        // Create an Answer for each QuestionInformation in our result set
        for (QuestionInformation entry : questionnaireQuestions) {
            QuestionType qType = entry.type;
            Map<String, Object> props = new HashMap<>();
            props.put("question", resolver.resolve(entry.getQuestion()).adaptTo(Node.class));
            props.put(ClarityImportTask.STATUS_FIELD_PROP, ClarityImportTask.STATUS_FLAGS);
            if (qType == QuestionType.STRING) {
                props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:TextAnswer");
                String thisEntry = result.getString(entry.getColName());
                props.put(ClarityImportTask.VALUE_PROP, thisEntry == null ? "" : thisEntry);
            } else if (qType == QuestionType.DATE) {
                props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:DateAnswer");
                Date date = result.getDate(entry.getColName());
                if (date != null) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date);
                    props.put(ClarityImportTask.VALUE_PROP, calendar);
                    props.put(ClarityImportTask.VALUE_PROP, calendar);
                } else {
                    LOGGER.warn(entry.getColName() + " is null");
                }
            } else if (qType == QuestionType.BOOLEAN) {
                // Note that the MS-SQL database doesn't save booleans as true/false
                // So instead we have to check if it is Yes or No
                props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:BooleanAnswer");
                props.put(ClarityImportTask.VALUE_PROP, "Yes".equals(result.getString(entry.getColName())));
            } else if (qType == QuestionType.CLINIC) {
                // This is similar to a string, except we transform the output to look at the ClinicMapping node
                props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:TextAnswer");
                String thisEntry = result.getString(entry.getColName());
                props.put(ClarityImportTask.VALUE_PROP,
                    thisEntry == null ? "" : "/Survey/ClinicMapping/" + String.valueOf(thisEntry.hashCode()));
            } else {
                LOGGER.warn("Unsupported question type: " + qType);
            }
            resolver.create(newForm, UUID.randomUUID().toString(), props);
        }

        return newSubject;
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
    Resource getOrCreateSubject(final String identifier, final String subjectTypePath, final ResourceResolver resolver,
        final Resource parent)
        throws RepositoryException, PersistenceException
    {
        final Iterator<Resource> subjectResourceIter = resolver.findResources(String.format(
            "SELECT * FROM [cards:Subject] WHERE identifier = \"%s\"", identifier), "JCR-SQL2");
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
            final Resource newSubject = resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                ClarityImportTask.PRIMARY_TYPE_PROP, "cards:Subject",
                "identifier", identifier,
                "type", patientType.adaptTo(Node.class)));
            this.nodesToCheckin.get().add(newSubject.getPath());
            return newSubject;
        }
    }
}
