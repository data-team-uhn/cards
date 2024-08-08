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
package io.uhndata.cards.qsets.internal;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.qsets.api.QuestionnaireConflict;
import io.uhndata.cards.qsets.api.QuestionnaireRef;
import io.uhndata.cards.qsets.api.QuestionnaireSet;
import io.uhndata.cards.qsets.api.QuestionnaireSetUtils;

/**
 * Change listener looking for new or modified forms related to a Visit subject. Initially, when a new Visit Information
 * form is created, it also creates any forms in the specified questionnaire set that need to be created, based on the
 * questionnaire set's specified frequency. When all the forms required for a visit are completed marks in the Visit
 * Information form that the patient has completed the required forms.
 *
 * @version $Id$
 */
@Component
public class QuestionnaireSetUtilsImpl implements QuestionnaireSetUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionnaireSetUtilsImpl.class);

    /**
     * If a questionnaire needs to be completed every X weeks, start creating that survey a few days earlier. This
     * defines how many days that is. This is to allow some margin in case visits are a few days off of their ideal
     * schedule.
     */
    private static final int FREQUENCY_MARGIN_DAYS = 2;

    private static final class QuestionnaireSetImpl implements QuestionnaireSet
    {
        private final Node definition;

        private final Map<String, QuestionnaireConflict> conflicts;

        private final Map<String, QuestionnaireRef> questionnaires;

        private final boolean conflictsIgnoreSet;

        private final ConflictMode conflictMode;

        private final Calendar associatedDate;

        QuestionnaireSetImpl(final Calendar date) throws RepositoryException
        {
            this(null, date);
        }

        QuestionnaireSetImpl(final QuestionnaireSet other)
        {
            this.definition = other.getDefinition();
            this.associatedDate = other.getAssociatedDate();
            this.conflicts = new HashMap<>();
            other.getConflicts().stream().forEach(this::addConflict);
            this.questionnaires = new LinkedHashMap<>();
            other.getQuestionnaires().stream().forEach(this::addQuestionnaire);
            this.conflictsIgnoreSet = other.conflictIgnoresSet();
            this.conflictMode = other.getConflictMode();
        }

        QuestionnaireSetImpl(final Node definition, final Calendar associatedDate)
            throws RepositoryException
        {
            this.definition = definition;
            this.associatedDate = associatedDate;
            this.conflicts = new HashMap<>();
            this.questionnaires = new LinkedHashMap<>();
            this.conflictMode = ConflictMode.valueOf(definition);
            this.conflictsIgnoreSet =
                (definition == null || !definition.hasProperty(FREQUENCY_IGNORE_SET_PROPERTY)) ? false
                    : definition.getProperty(FREQUENCY_IGNORE_SET_PROPERTY).getBoolean();
            if (definition == null) {
                return;
            }

            // Retrieve child node data from the questionnaire set, namely conflicts and member questionnaires
            for (final NodeIterator childNodes = definition.getNodes(); childNodes.hasNext();) {
                final Node node = childNodes.nextNode();
                if (node.isNodeType(QuestionnaireRef.NODETYPE)
                    && node.hasProperty(QuestionnaireRef.QUESTIONNAIRE_PROPERTY)) {
                    final Node questionnaire = node.getProperty(QuestionnaireRef.QUESTIONNAIRE_PROPERTY).getNode();
                    long frequency = 0;
                    if (node.hasProperty(QuestionnaireRef.FREQUENCY_PROPERTY)) {
                        frequency = node.getProperty(QuestionnaireRef.FREQUENCY_PROPERTY).getLong();
                    }
                    addQuestionnaire(new QuestionnaireRefImpl(questionnaire, frequency));
                } else if (node.isNodeType(QuestionnaireConflict.NODETYPE)
                    && node.hasProperty(QuestionnaireConflict.QUESTIONNAIRE_PROPERTY)) {
                    addConflict(new QuestionnaireConflictImpl(node));
                }
            }
        }

        @Override
        public void addConflict(final QuestionnaireConflict conflict)
        {
            this.conflicts.put(conflict.getQuestionnairePath(), conflict);
        }

        @Override
        public List<QuestionnaireRef> getQuestionnaires()
        {
            return Collections.unmodifiableList(new ArrayList<>(this.questionnaires.values()));
        }

        @Override
        public QuestionnaireRef getQuestionnaire(final String questionnairePath)
        {
            return this.questionnaires.get(questionnairePath);
        }

        @Override
        public void addQuestionnaire(final QuestionnaireRef questionnaire)
        {
            this.questionnaires.put(questionnaire.getQuestionnairePath(), questionnaire);
        }

        @Override
        public boolean removeQuestionnaire(final String questionnairePath)
        {
            return this.questionnaires.remove(questionnairePath) != null;
        }

        @Override
        public boolean containsQuestionnaire(String questionnairePath)
        {
            return this.questionnaires.containsKey(questionnairePath);
        }

        @Override
        public boolean conflictIgnoresSet()
        {
            return this.conflictsIgnoreSet;
        }

        private boolean containsConflict(final QuestionnaireSet other)
        {
            // Treat default as any listed case
            for (final QuestionnaireRef questionnaire : other.getQuestionnaires()) {
                long frequencyPeriod = 0;
                if (ConflictMode.CONFLICT_ANY.equals(this.conflictMode)) {
                    frequencyPeriod = this.questionnaires.values().stream()
                        .map(QuestionnaireRef::getFrequency)
                        .max(Long::compare).get();
                } else if (this.conflicts.containsKey(questionnaire.getQuestionnairePath())) {
                    frequencyPeriod = this.conflicts.get(questionnaire.getQuestionnairePath()).getFrequency();
                } else {
                    continue;
                }

                frequencyPeriod = frequencyPeriod * 7 - FREQUENCY_MARGIN_DAYS;

                if (isWithinDateRange(this.getAssociatedDate(), other.getAssociatedDate(), frequencyPeriod)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public ConflictMode getConflictMode()
        {
            return this.conflictMode;
        }

        @Override
        public Set<QuestionnaireConflict> getConflicts()
        {
            return new HashSet<>(this.conflicts.values());
        }

        @Override
        public void pruneConflicts(QuestionnaireSet other)
        {
            // First check if the other set matters when looking for a conflict
            // This means that conflicts care about the set, and the same set is used for both qsets
            try {
                if (!conflictIgnoresSet()
                    &&
                    (other.getDefinition() == null || this.getDefinition() == null
                        || !this.getDefinition().isSame(other.getDefinition()))) {
                    return;
                }
            } catch (RepositoryException e) {
                // Shouldn't happen
            }

            // Check if any of the provided questionnaires create a conflict with this qset
            if (containsConflict(other)) {
                // If there is a conflict, do not create anything for this questionnaire set
                this.questionnaires.clear();
            } else {
                // Otherwise, check if any of this visit's forms meet a frequency requirement for the questionnaire set.
                // If they do, remove those forms' questionnaires from the set.
                for (final QuestionnaireRef questionnaire : other.getQuestionnaires()) {
                    if (containsQuestionnaire(questionnaire.getQuestionnairePath())) {
                        long frequencyPeriod =
                            getQuestionnaire(questionnaire.getQuestionnairePath()).getFrequency();
                        frequencyPeriod = frequencyPeriod * 7 - FREQUENCY_MARGIN_DAYS;

                        if (isWithinDateRange(this.getAssociatedDate(), other.getAssociatedDate(), frequencyPeriod)) {
                            removeQuestionnaire(questionnaire.getQuestionnairePath());
                        }
                    }
                }
            }
        }

        @Override
        public Node getDefinition()
        {
            return this.definition;
        }

        @Override
        public Calendar getAssociatedDate()
        {
            return this.associatedDate;
        }

        /**
         * Check if two dates are within a given range of each other.
         *
         * @param testedDate the date to check
         * @param baseDate the base date to compare against
         * @param dateRange the maximum number of days before or after the base date to consider as within range
         * @return {@code true} if the tested date is in range, {@code false} otherwise
         */
        private boolean isWithinDateRange(final Calendar testedDate, final Calendar baseDate, final long dateRange)
        {
            if (testedDate == null || baseDate == null) {
                return false;
            }
            final Calendar start = addDays(baseDate, -Math.abs(dateRange));
            final Calendar end = addDays(baseDate, Math.abs(dateRange));
            return testedDate.after(start) && testedDate.before(end);
        }

        /**
         * Get the result of adding or removing a number of days to a date. Does not mutate the provided date.
         *
         * @param date the date that is being added to
         * @param days number of days being added, may be negative
         * @return the resulting date
         */
        private Calendar addDays(final Calendar date, final long days)
        {
            final Calendar result = (Calendar) date.clone();
            result.add(Calendar.DATE, (int) days);
            return result;
        }
    }

    private static final class QuestionnaireRefImpl implements QuestionnaireRef
    {
        private final Node questionnaire;

        private final long frequency;

        QuestionnaireRefImpl(final Node questionnaire, final long frequency)
        {
            this.questionnaire = questionnaire;
            this.frequency = frequency;
        }

        QuestionnaireRefImpl(final Node definition)
            throws ItemNotFoundException, ValueFormatException, PathNotFoundException, RepositoryException
        {
            this(definition.getProperty(QUESTIONNAIRE_PROPERTY).getNode(),
                definition.hasProperty(FREQUENCY_PROPERTY) ? definition.getProperty(FREQUENCY_PROPERTY).getLong() : 0);
        }

        @Override
        public Node getQuestionnaire()
        {
            return this.questionnaire;
        }

        @Override
        public long getFrequency()
        {
            return this.frequency;
        }

        @Override
        public String getQuestionnairePath()
        {
            try {
                return this.questionnaire.getPath();
            } catch (RepositoryException e) {
                return "";
            }
        }
    }

    private static final class QuestionnaireConflictImpl implements QuestionnaireConflict
    {
        private final Node questionnaire;

        private final long frequency;

        QuestionnaireConflictImpl(final Node definition) throws RepositoryException
        {
            this.questionnaire = definition.getProperty(QUESTIONNAIRE_PROPERTY).getNode();
            long tempFrequency = 0;
            try {
                tempFrequency =
                    definition.hasProperty(FREQUENCY_PROPERTY) ? definition.getProperty(FREQUENCY_PROPERTY).getLong()
                        : 0;
            } catch (RepositoryException e) {
                // Ignore for now
            }
            this.frequency = tempFrequency;
        }

        @Override
        public Node getQuestionnaire()
        {
            return this.questionnaire;
        }

        @Override
        public long getFrequency()
        {
            return this.frequency;
        }

        @Override
        public String getQuestionnairePath()
        {
            try {
                return this.questionnaire.getPath();
            } catch (RepositoryException e) {
                return "";
            }
        }
    }

    @Override
    public QuestionnaireConflict toQuestionnaireConflict(Node definition)
    {
        try {
            return new QuestionnaireConflictImpl(definition);
        } catch (RepositoryException e) {
            LOGGER.error("Failed to adapt QuestionnaireConflict for {}: {}", definition, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public QuestionnaireRef toQuestionnaireRef(Node questionnaire, long frequency)
    {
        return new QuestionnaireRefImpl(questionnaire, frequency);
    }

    @Override
    public QuestionnaireRef toQuestionnaireRef(Node definition)
    {
        try {
            return new QuestionnaireRefImpl(definition);
        } catch (RepositoryException e) {
            LOGGER.error("Failed to adapt QuestionnaireRef for {}: {}", definition, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public QuestionnaireSet toQuestionnaireSet(Node definition, Calendar associatedDate)
    {
        try {
            return new QuestionnaireSetImpl(definition, associatedDate);
        } catch (RepositoryException e) {
            LOGGER.error("Failed to adapt QuestionnaireSet for {}: {}", definition, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public QuestionnaireSet toQuestionnaireSet(Calendar associatedDate)
    {
        try {
            return new QuestionnaireSetImpl(associatedDate);
        } catch (RepositoryException e) {
            LOGGER.error("Failed to adapt QuestionnaireSet: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public QuestionnaireSet copy(QuestionnaireSet toCopy)
    {
        return new QuestionnaireSetImpl(toCopy);
    }
}
