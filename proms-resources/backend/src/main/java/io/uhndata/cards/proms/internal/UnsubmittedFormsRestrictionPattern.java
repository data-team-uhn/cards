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
package io.uhndata.cards.proms.internal;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;

/**
 * A restriction that makes a permissions entry only be valid on a form if the form belongs to a Visit subject that has
 * not been submitted yet.
 *
 * @version $Id$
 */
public class UnsubmittedFormsRestrictionPattern implements RestrictionPattern
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsubmittedFormsRestrictionPattern.class);

    private static final String VISIT_INFORMATION_PATH = "/Questionnaires/Visit information";

    /** The current session, which may contain a subject identifier. */
    private final Session session;

    private final FormUtils formUtils;

    private final QuestionnaireUtils questionnaireUtils;

    /**
     * Constructor passing all the needed information.
     *
     * @param session the current session
     * @param formUtils for working with forms
     * @param questionnaireUtils for working with questionnaires
     */
    public UnsubmittedFormsRestrictionPattern(final Session session, final FormUtils formUtils,
        final QuestionnaireUtils questionnaireUtils)
    {
        this.session = session;
        this.formUtils = formUtils;
        this.questionnaireUtils = questionnaireUtils;
    }

    @Override
    public boolean matches()
    {
        // This is not a repository-wide restriction, it only applies to specific nodes
        return false;
    }

    @Override
    public boolean matches(final String path)
    {
        // This is called when a new node is being created
        return false;
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        try {
            if (this.session == null || property != null) {
                // This only applies to the form node itself, and only when we have a session
                return false;
            }

            if (!isForm(tree)) {
                // Not a form
                return false;
            }

            // This restriction does not apply to the Visit Information form itself
            if (VISIT_INFORMATION_PATH.equals(this.questionnaireUtils
                .getQuestionnaire(tree.getProperty("questionnaire").getValue(Type.STRING)).getPath())) {
                return false;
            }

            return isUnsubmitted(tree);
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed to determine if form belongs to an unsubmitted visit: {}", e.getMessage(), e);
        }
        return false;
    }

    private boolean isUnsubmitted(final Tree form) throws RepositoryException
    {
        final String subjectUUID = form.getProperty("subject").getValue(Type.STRING);

        final Node visitInformation = findVisitInformationForm(subjectUUID);
        final Node visitInformationQuestionnaire = this.formUtils.getQuestionnaire(visitInformation);
        final Node submittedQuestion =
            this.questionnaireUtils.getQuestion(visitInformationQuestionnaire, "surveys_submitted");
        final Object submitted = this.formUtils.getValue(this.formUtils.getAnswer(visitInformation, submittedQuestion));
        return submitted == null || !Long.valueOf(1).equals(submitted);
    }

    private boolean isForm(final Tree node)
    {
        return node.getProperty("jcr:primaryType") != null
            && StringUtils.equals(node.getProperty("jcr:primaryType").getValue(Type.STRING), "cards:Form");
    }

    private Node findVisitInformationForm(final String subjectUUID) throws RepositoryException
    {
        final String visitInformationUUID = this.session.getNode(VISIT_INFORMATION_PATH).getIdentifier();
        final NodeIterator results = this.session.getWorkspace().getQueryManager()
            .createQuery("select vi.* from [cards:Form] as vi where vi.questionnaire = '" + visitInformationUUID
                + "' and vi.subject = '" + subjectUUID + "'", Query.JCR_SQL2)
            .execute().getNodes();
        if (results.hasNext()) {
            return results.nextNode();
        }
        return null;
    }
}
