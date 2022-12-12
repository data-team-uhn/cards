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
import java.util.Arrays;
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

    private static final String QUESTIONNAIRE_PROP = "questionnaire";

    private static final String DATA_TYPE_PROP = "dataType";

    private static final String PRIMARY_TYPE_PROP = "jcr:primaryType";

    private static final String VALUE_PROP = "value";

    private final List<String> columns;

    private final Map<String, List<QuestionInformation>> questionnaireToQuestions;

    private final Map<String, String> questionnaireToSubjectType;

    private final Map<String, String> questionnaireToSubjectColumnHeader;

    private ThreadLocal<List<String>> nodesToCheckin = ThreadLocal.withInitial(LinkedList::new);

    private ThreadLocal<VersionManager> versionManager = new ThreadLocal<>();

    private ThreadLocal<String> previousPatientId = new ThreadLocal<>();

    private ThreadLocal<Resource> previousPatientResource = new ThreadLocal<>();

    private ThreadLocal<Boolean> createdPatientInformation = new ThreadLocal<>();

    private enum QuestionType
    {
        DATE,
        STRING,
        BOOLEAN,
        CLINIC
    }

    /** Storage class for information about a question. */
    private class QuestionInformation
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

    ClarityImportTask(final ResourceResolverFactory resolverFactory)
    {
        this.resolverFactory = resolverFactory;

        this.columns = new LinkedList<>();
        this.questionnaireToQuestions = new HashMap<>();
        this.questionnaireToSubjectType = new HashMap<>();
        this.questionnaireToSubjectColumnHeader = new HashMap<>();

        // Convert our input mapping node to a mapping from column->question
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            processClarityToCardsMapping(resolver, resolver.resolve(MAPPING_CONFIG));
        } catch (RepositoryException e) {
            LOGGER.error("Error reading mapping: {}", e.getMessage(), e);
        } catch (LoginException e) {
            LOGGER.error("Could not find service user while reading mapping: {}", e.getMessage(), e);
        }
    }

    private String getSubjectTypePath(final ResourceResolver resolver, final String subjectTypeUuid)
    {
        final Iterator<Resource> iter = resolver.findResources(
            "SELECT * FROM [cards:SubjectType] as st WHERE st.'jcr:uuid'='" + subjectTypeUuid + "'", "JCR-SQL2");
        if (iter.hasNext()) {
            return iter.next().getPath();
        }
        return "";
    }

    private String getQuestionnairePathFromQuestionPath(String questionPath)
    {
        String[] questionPathArray = questionPath.split("/");
        return String.join("/", Arrays.copyOfRange(questionPathArray, 0, 3));
    }

    @SuppressWarnings("checkstyle:MultipleStringLiterals")
    private void processClarityToCardsMapping(ResourceResolver resolver, Resource mapping) throws RepositoryException
    {
        /*
         * This method needs to populate:
         *   - this.columns
         *   - this.questionnaireToQuestions
         *   - this.questionnaireToSubjectType
         *   - this.questionnaireToSubjectColumnHeader
         */

        String subjectIDColumn = resolver.resolve("/apps/cards/clarityImport").getValueMap().get("subjectIDColumn", "");

        // Populate this.columns
        for (Resource child : mapping.getChildren()) {
            this.columns.add(child.getValueMap().get("sqlColumn", ""));
        }

        // Populate this.questionnaireToSubjectType
        Resource questionnaires = resolver.resolve("/Questionnaires");
        for (Resource questionnaire : questionnaires.getChildren()) {
            String questionnairePath = questionnaire.getPath();
            String[] subjectTypesRefs = questionnaire.getValueMap().get("requiredSubjectTypes", String[].class);
            for (int i = 0; i < subjectTypesRefs.length; i++) {
                this.questionnaireToSubjectType.put(questionnairePath,
                    getSubjectTypePath(resolver, subjectTypesRefs[i]));
            }
        }

        /*
         * Populate this.questionnaireToSubjectColumnHeader, starting with a blank for all questionnaires,
         * this will be filled in later
         */
        Iterator<Map.Entry<String, String>> questionnairesIterator =
            this.questionnaireToSubjectType.entrySet().iterator();

        while (questionnairesIterator.hasNext()) {
            Map.Entry<String, String> questionnaireToSubject = questionnairesIterator.next();
            String questionnairePath = questionnaireToSubject.getKey();
            this.questionnaireToSubjectColumnHeader.put(questionnairePath, "");
        }

        // Populate this.questionnaireToQuestions
        for (Resource child : mapping.getChildren()) {
            String questionPath = child.getValueMap().get("question", "");
            String questionnairePath = this.getQuestionnairePathFromQuestionPath(questionPath);
            String sqlColumn = child.getValueMap().get("sqlColumn", "");
            QuestionInformation thisQuestion = new QuestionInformation(
                questionPath,
                this.getQuestionType(resolver.resolve(questionPath)),
                questionnairePath,
                sqlColumn
            );

            if (!this.questionnaireToQuestions.containsKey(questionnairePath)) {
                this.questionnaireToQuestions.put(questionnairePath, new LinkedList<QuestionInformation>());
            }

            this.questionnaireToQuestions.get(questionnairePath).add(thisQuestion);

            // Populate this.questionnaireToSubjectColumnHeader, actually filling in the values this time
            if (subjectIDColumn.equals(sqlColumn)) {
                this.questionnaireToSubjectColumnHeader.put(questionnairePath, subjectIDColumn);
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

    private String generateClarityQuery() throws LoginException
    {
        Map<String, String> sqlColumns = new HashMap<>();
        String queryString = "";
        String subjectIDColumn = "";
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {

            // Get the /apps/cards/clarityImport JCR node
            Resource clarityImportConfigNode = resolver.getResource("/apps/cards/clarityImport");

            // Read the "subjectIDColumn value of this node
            subjectIDColumn = clarityImportConfigNode.getValueMap().get("subjectIDColumn", "");

            // ... save it to the sqlColums hashmap
            sqlColumns.put(subjectIDColumn, "text");

            // Iterate through all the children of clarityImportConfigNode
            for (Resource child : clarityImportConfigNode.getChildren()) {
                String sqlColumn = child.getValueMap().get("sqlColumn", "");
                String question = child.getValueMap().get("question", "");
                String dataType = resolver.getResource(question).getValueMap().get("dataType", "");
                sqlColumns.put(sqlColumn, dataType);
            }
        } catch (LoginException e) {
            throw e;
        }

        queryString = "SELECT ";
        Iterator<Map.Entry<String, String>> columnsIterator = sqlColumns.entrySet().iterator();
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
        queryString += " WHERE CAST(LoadTime AS DATE) = CAST(GETDATE() AS DATE)";
        queryString += " ORDER BY " + subjectIDColumn + " ASC;";

        return queryString;
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
                LOGGER.info("Entry found: " + results.getString("PAT_MRN"));
                Resource subjectParent = resolver.resolve("/Subjects");
                for (Map.Entry<String, List<QuestionInformation>> entry
                    : this.questionnaireToQuestions.entrySet()) {
                    String questionnaire = entry.getKey();
                    try {
                        subjectParent = createNodeFromEntry(resolver, results, entry.getValue(),
                            questionnaire, subjectParent, formsHomepage);
                    } catch (ParseException e) {
                        LOGGER.warn("Failed to process a Clarity SQL row.");
                    }
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
        } finally {
            // Cleanup all ThreadLocals
            this.nodesToCheckin.remove();
            this.versionManager.remove();
            this.previousPatientId.remove();
            this.previousPatientResource.remove();
            this.createdPatientInformation.remove();
        }
    }

    private void replaceFormAnswer(final ResourceResolver resolver, final Resource form,
        final Map<String, Object> props) throws RepositoryException
    {
        final String questionUUID = ((Node) props.get("question")).getIdentifier();
        for (Resource answer : form.getChildren()) {
            String thisAnswersQuestionUUID = answer.getValueMap().get("question", "");
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

    private Map<String, Object> generateAnswerNodeProperties(final ResourceResolver resolver,
        final QuestionInformation entry, final ResultSet result) throws ParseException, SQLException
    {
        QuestionType qType = entry.type;
        Map<String, Object> props = new HashMap<>();
        props.put("question", resolver.resolve(entry.getQuestion()).adaptTo(Node.class));

        if (qType == QuestionType.STRING) {
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:TextAnswer");
            String thisEntry = result.getString(entry.getColName());
            props.put(ClarityImportTask.VALUE_PROP, thisEntry == null ? "" : thisEntry);
        } else if (qType == QuestionType.DATE) {
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:DateAnswer");
            SimpleDateFormat clarityDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
            Date date = clarityDateFormat.parse(result.getString(entry.getColName()));
            if (date != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                props.put(ClarityImportTask.VALUE_PROP, calendar);
            } else {
                LOGGER.warn(entry.getColName() + " is null");
            }
        } else if (qType == QuestionType.BOOLEAN) {
            // Note that the MS-SQL database doesn't save booleans as true/false
            // So instead we have to check if it is Yes or No
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:BooleanAnswer");
            props.put(ClarityImportTask.VALUE_PROP, "Yes".equals(result.getString(entry.getColName())) ? 1 : 0);
        } else if (qType == QuestionType.CLINIC) {
            // This is similar to a string, except we transform the output to look at the ClinicMapping node
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:TextAnswer");
            String thisEntry = result.getString(entry.getColName());
            props.put(ClarityImportTask.VALUE_PROP,
                thisEntry == null ? "" : "/Survey/ClinicMapping/" + String.valueOf(thisEntry.hashCode()));
        } else {
            LOGGER.warn("Unsupported question type: " + qType);
        }

        return props;
    }

    private Resource getUpdatableForm(final ResourceResolver resolver, final Resource questionnaire,
        final Resource subject) throws RepositoryException
    {
        final String questionnairePath = questionnaire.getPath();
        Resource newForm = null;
        if ("/Questionnaires/Patient information".equals(questionnairePath)) {
            // Let's see if there is a Patient information Form already in existance for this Patient subject
            String questionnaireUUID = questionnaire.getValueMap().get("jcr:uuid", "");
            String newSubjectUUID = subject.getValueMap().get("jcr:uuid", "");

            final Iterator<Resource> matchingFormResourceIter = resolver.findResources(
                "SELECT * FROM [cards:Form] as form WHERE form.'questionnaire'='"
                + questionnaireUUID + "' AND form.'subject'='" + newSubjectUUID + "'",
                "JCR-SQL2");

            if (matchingFormResourceIter.hasNext()) {
                Resource matchingPatientInformation = matchingFormResourceIter.next();

                // Use matchingPatientInformation as newForm instead of creating a new one
                matchingPatientInformation.adaptTo(Node.class).getSession().getWorkspace().getVersionManager().checkout(
                    matchingPatientInformation.getPath());

                newForm = matchingPatientInformation;
            }
        }
        return newForm;
    }

    private boolean formAlreadyCreated(final Resource subject, final String questionnairePath)
    {
        if ("/Questionnaires/Patient information".equals(questionnairePath) && this.createdPatientInformation.get()) {
            return true;
        }
        return false;
    }

    private Resource getSubjectForResult(final ResourceResolver resolver, final ResultSet result,
        final String questionnairePath, final Resource subjectParent) throws PersistenceException, RepositoryException,
        SQLException
    {
        /*
         * If an identifier for this created subject can be derived from this SQL row (ResultSet result),
         * use it, otherwise, generate a random UUID and use that for the identifier of the subject.
         */
        String subjectColumnHeader = this.questionnaireToSubjectColumnHeader.get(questionnairePath);
        String subjectID = "".equals(subjectColumnHeader)
            ? UUID.randomUUID().toString() : result.getString(subjectColumnHeader);

        Resource newSubject = null;
        String newSubjectType = this.questionnaireToSubjectType.get(questionnairePath);
        if ("/SubjectTypes/Patient".equals(newSubjectType) && subjectID.equals(this.previousPatientId.get())) {
            newSubject = this.previousPatientResource.get();
        } else {
            newSubject = getOrCreateSubject(subjectID, this.questionnaireToSubjectType.get(questionnairePath),
                resolver, subjectParent);

            if ("/SubjectTypes/Patient".equals(newSubjectType)) {
                this.previousPatientId.set(subjectID);
                this.previousPatientResource.set(newSubject);
                this.createdPatientInformation.set(false);
            }
        }
        return newSubject;
    }

    private void markFormAsCreated(String questionnairePath)
    {
        if ("/Questionnaires/Patient information".equals(questionnairePath)) {
            this.createdPatientInformation.set(true);
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
    private Resource createNodeFromEntry(final ResourceResolver resolver, final ResultSet result,
        final List<QuestionInformation> questionnaireQuestions, final String questionnairePath,
        final Resource subjectParent, final Resource formsHomepage)
        throws ParseException, PersistenceException, RepositoryException, SQLException
    {

        // Get a Subject Resource for this result - retrieving it if it exists, creating it if it doesn't
        Resource subject = getSubjectForResult(resolver, result, questionnairePath, subjectParent);

        /*
         * If a Form has already been created for this Subject (eg. this is another row for the same patient and we do
         * not (and should not!) create a new Patient information Form.
         */
        if (formAlreadyCreated(subject, questionnairePath)) {
            return subject;
        }

        // Create a Node corresponding to the Form
        Resource questionnaire = resolver.resolve(questionnairePath);
        Resource form = getUpdatableForm(resolver, questionnaire, subject);

        boolean updatingOldForm = false;
        if (form == null) {
            form = resolver.create(formsHomepage, UUID.randomUUID().toString(), Map.of(
                ClarityImportTask.PRIMARY_TYPE_PROP, "cards:Form",
                "questionnaire", questionnaire.adaptTo(Node.class),
                "subject", subject.adaptTo(Node.class)));
        } else {
            updatingOldForm = true;
        }

        this.nodesToCheckin.get().add(form.getPath());

        // Create an Answer for each QuestionInformation in our result set
        for (QuestionInformation entry : questionnaireQuestions) {
            Map<String, Object> props = generateAnswerNodeProperties(resolver, entry, result);

            // If a Form already existed, update its child answer nodes instead of creating new ones
            if (updatingOldForm) {
                replaceFormAnswer(resolver, form, props);
            } else {
                resolver.create(form, UUID.randomUUID().toString(), props);
            }
        }

        markFormAsCreated(questionnairePath);

        return subject;
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
            "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='%s'", identifier);
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
            final Resource newSubject = resolver.create(parentResource, UUID.randomUUID().toString(), Map.of(
                ClarityImportTask.PRIMARY_TYPE_PROP, "cards:Subject",
                "identifier", identifier,
                "type", patientType.adaptTo(Node.class)));
            this.nodesToCheckin.get().add(newSubject.getPath());
            return newSubject;
        }
    }
}
