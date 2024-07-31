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
package io.uhndata.cards.qsets.api;

import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Questionnaire Set is a collection of related questionnaires. Other than the list of actual questionnaires to be
 * filled in, it also contains rules about how often the questionnaire set can/should be filled in, and may have an
 * associated date if this particular instance has one, for example if it's a survey filled in by a patient before,
 * during, or after a visit to a clinic. A questionnaire set will not be created if it "conflicts" with existing
 * questionnaire sets. An instance of this interface is usually associated with a {@code cards:QuestionnaireSet} node,
 * but it can store any list of questionnaires, not just the ones strictly listed in the definition, for example the
 * list of questionnaire for which forms already exist, or the list of questionnaires which need to be created given a
 * {@code cards:QuestionnaireSet} definition and the forms already present for the subject.
 *
 * @version $Id$
 * @since 0.9.25
 */
public interface QuestionnaireSet
{
    // Constants for the QuestionnaireSet data type

    /** The primary node type for a Questionnaire Set. */
    String NODETYPE = "cards:QuestionnaireSet";

    /** The name of the property of a QuestionnaireSet node that specifies the conflict mode. */
    String CONFLICT_MODE_PROPERTY = "conflictMode";

    /**
     * The name of the property of a QuestionnaireSet node that specifies if conflicts only apply to the same type of
     * set, or any set.
     */
    String FREQUENCY_IGNORE_SET_PROPERTY = "frequencyIgnoreClinic";

    /**
     * The conflict mode influences when an existing form causes a conflict with a new questionnaire set.
     *
     * @version $Id$
     */
    enum ConflictMode
    {
        /**
         * This ignores the listed conflicts and actual forms, a conflict simply means that another questionnaire set
         * instance within the expected timeframe exists.
         */
        CONFLICT_ANY("any"),
        /**
         * Look at the listed questionnaire conflicts when considering if an existing questionnaire set instance causes
         * a conflict.
         */
        CONFLICT_ANY_LISTED("anyListed");

        private static final Logger LOGGER = LoggerFactory.getLogger(ConflictMode.class);

        private final String label;

        ConflictMode(final String label)
        {
            this.label = label;
        }

        /**
         * Returns the conflict mode for the given label.
         *
         * @param label a string, one of the labels of the supported conflict modes
         * @return a conflict mode, {@code CONFLICT_ANY_LISTED} by default
         */
        public static ConflictMode parse(String label)
        {
            return Stream.of(values()).filter(mode -> mode.label.equals(label)).findFirst().orElse(CONFLICT_ANY_LISTED);
        }

        /**
         * Extract the conflict mode specified in the given node.
         *
         * @param definition a JCR node of type {@code cards:QuestionnaireSet}
         * @return a conflict mode, {@code CONFLICT_ANY_LISTED} if not specified in the node
         */
        public static ConflictMode valueOf(Node definition)
        {
            try {
                if (definition != null && definition.hasProperty(CONFLICT_MODE_PROPERTY)) {
                    return parse(definition.getProperty(CONFLICT_MODE_PROPERTY).getString());
                }
            } catch (RepositoryException e) {
                LOGGER.warn("Invalid conflict mode for {}", definition);
            }
            return CONFLICT_ANY_LISTED;
        }
    }

    /**
     * The JCR node defining this questionnaire set.
     *
     * @return a JCR node of type {@code cards:QuestionnaireSet}
     */
    Node getDefinition();

    /**
     * Retrieve all conflict definitions for this questionnaire set.
     *
     * @return a set of conflict definitions, may be empty
     */
    Set<QuestionnaireConflict> getConflicts();

    /**
     * Add a new conflict to this questionnaire set.
     *
     * @param conflict the conflict definition
     */
    void addConflict(QuestionnaireConflict conflict);

    /**
     * Retrieve the conflict mode for this questionnaire set.
     *
     * @return a conflict mode
     */
    ConflictMode getConflictMode();

    /**
     * Whether forms can constitute a conflict only if they belong to an instance of the same questionnaire set, or any
     * form more recent than the specified frequency is a conflict regardless of its questionnaire set.
     *
     * @return {@code false} if a conflict can only arise from a form for the same questionnaire set, {@code false}
     *         otherwise
     */
    boolean conflictIgnoresSet();

    /**
     * Retrieve all members of this questionnaire set.
     *
     * @return an ordered list of questionnaire references, may be empty
     */
    List<QuestionnaireRef> getQuestionnaires();

    /**
     * Retrieve the count of members in this questionnaire set.
     *
     * @return a positive number, may be 0
     */
    default int questionnairesCount()
    {
        return getQuestionnaires().size();
    }

    /**
     * Check if a questionnaire is part of this questionnaire set.
     *
     * @param questionnairePath a path to a JCR questionnaire node
     * @return {@code true} if the questionnaire belongs to this set, {@code false} otherwise
     */
    boolean containsQuestionnaire(String questionnairePath);

    /**
     * Retrieve the questionnaire reference definition for the given questionnaire.
     *
     * @param questionnairePath a path to a JCR questionnaire node, should be part of the questionnaire set
     * @return the definition for this questionnaire reference, {@code null} if the specified questionnaire isn't part
     *         of this set
     */
    QuestionnaireRef getQuestionnaire(String questionnairePath);

    /**
     * Add a new questionnaire to this questionnaire set.
     *
     * @param reference the definition for the new questionnaire reference
     */
    void addQuestionnaire(QuestionnaireRef reference);

    /**
     * Remove a questionnaire from this questionnaire set.
     *
     * @param questionnairePath a path to a JCR questionnaire node
     */
    void removeQuestionnaire(String questionnairePath);

    /**
     * Retrieve the date associated with this set, if any.
     *
     * @return a date, or {@code null}
     */
    Calendar getAssociatedDate();

    /**
     * Remove any questionnaires from the questionnaire set which do not need to be created per their frequency
     * requirements and another existing questionnaire set. This changes the questionnaire set internal data.
     *
     * @param other another questionnaire set already existing in the system, with its own associated date
     */
    void pruneConflicts(QuestionnaireSet other);
}
