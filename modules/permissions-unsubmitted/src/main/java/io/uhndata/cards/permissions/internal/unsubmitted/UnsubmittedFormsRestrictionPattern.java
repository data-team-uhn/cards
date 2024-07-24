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
package io.uhndata.cards.permissions.internal.unsubmitted;

import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.QuestionnaireUtils;

/**
 * A restriction that makes a permissions entry only be valid on a form if the form belongs to a Visit subject that has
 * not been submitted yet.
 *
 * @version $Id$
 */
public class UnsubmittedFormsRestrictionPattern implements RestrictionPattern
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsubmittedFormsRestrictionPattern.class);

    private static final List<String> IGNORED_QUESTIONNAIRES = List.of(
        "/Questionnaires/Visit information",
        "/Questionnaires/Patient information",
        "/Questionnaires/Survey events");

    private final QuestionnaireUtils questionnaireUtils;

    /**
     * Constructor passing all the needed information.
     *
     * @param questionnaireUtils for working with questionnaires
     */
    public UnsubmittedFormsRestrictionPattern(final QuestionnaireUtils questionnaireUtils)
    {
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
        if (property != null) {
            // This only applies to the form node itself
            return false;
        }

        if (!isForm(tree)) {
            // Not a form
            return false;
        }

        // This restriction only applies to data forms, not metadata forms
        try {
            if (IGNORED_QUESTIONNAIRES.contains(this.questionnaireUtils
                .getQuestionnaire(tree.getProperty("questionnaire").getValue(Type.STRING)).getPath())) {
                return false;
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to check form status: {}", e.getMessage());
        }

        return isUnsubmitted(tree);
    }

    private boolean isUnsubmitted(final Tree form)
    {
        boolean isSubmitted = false;
        boolean isPatientForm = false;
        PropertyState flags = form.getProperty("statusFlags");
        for (int i = 0; i < flags.count(); ++i) {
            isSubmitted |= "SUBMITTED".equals(flags.getValue(Type.STRING, i));
            isPatientForm |= "PATIENT SURVEY".equals(flags.getValue(Type.STRING, i));
        }
        return isPatientForm && !isSubmitted;
    }

    private boolean isForm(final Tree node)
    {
        return node.getProperty("jcr:primaryType") != null
            && StringUtils.equals(node.getProperty("jcr:primaryType").getValue(Type.STRING), "cards:Form");
    }
}
