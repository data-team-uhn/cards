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
package io.uhndata.cards.heracles.internal;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.uhndata.cards.forms.api.FormUtils;

/**
 * The configuration details for a form that needs automatic visit numbers.
 * Includes details about:
 * - The questions that have required information for determining the visit
 * - The lists of valid visit numbers for both streams
 * - The questions to save the visit number into depending on the stream
 * - Functions to retrieve questions and answers associated from forms for this questionnaire
 *
 * @version $Id$
 */
@Component(
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    immediate = true,
    service = VisitNumberConfiguration.class)
@Designate(
    ocd = VisitNumberConfiguration.Config.class,
    factory = true)
public class VisitNumberConfiguration
{
    private static final String STREAM_LOW = "Low Touch";
    private static final String STREAM_HIGH = "High Touch";

    // Configured parameters
    private String questionnaire;
    private long[] visitsLow;
    private long[] visitsHigh;
    private String visitPathLow;
    private String visitPathHigh;
    private String studyStreamPath;

    @ObjectClassDefinition(name = "Visit Number Configuration",
        description = "Configuration for the Visit Number Editor that auto fills the "
            + "visit number for applicable Heracles forms.")
    public @interface Config
    {
        @AttributeDefinition(name = "Questionnaire",
            description = "The path to a questionnaire that needs a visit nunmber")
        String questionnairePath();

        @AttributeDefinition(name = "Visit Numbers - Low",
            description = "The valid visit numbers for low stream visits")
        long[] visitsLow();

        @AttributeDefinition(name = "Visit Numbers - High",
            description = "The valid visit numbers for high stream visits")
        long[] visitsHigh();

        @AttributeDefinition(name = "Visit Path - Low",
            description = "The path to the visit number question that should be filled out for low stream visits")
        String visitPathLow();

        @AttributeDefinition(name = "Visit Path - High",
            description = "The path to the visit number question that should be filled out for high stream visits")
        String visitPathHigh();

        @AttributeDefinition(name = "Study Stream Path",
            description = "The path the the question that contains the study stream."
            + " This should be a reference question that references the study stream questionnaire")
        String studyStreamPath();
    }


    @Activate
    public VisitNumberConfiguration(final Config config)
    {
        this.questionnaire = config.questionnairePath();
        this.visitsLow = config.visitsLow();
        this.visitsHigh = config.visitsHigh();
        this.visitPathLow = config.visitPathLow();
        this.visitPathHigh = config.visitPathHigh();
        this.studyStreamPath = config.studyStreamPath();
    }

    /**
     * Retrieve the questionnaire path that this configuration applies to.
     * @return the questionnaire path
     */
    public String getQuestionnairePath()
    {
        return this.questionnaire;
    }

    /**
     * Get a list of the valid visit numbers for this questionnaire for the provided stream.
     * @param studyStream The stream for this subject. Expects {@code "Low Touch"} or {@code "High Touch"}.
     *                    Defaults to High Touch if an unexpected stream is provided
     * @return The valid visit numbers for this stream
     */
    public long[] getScheduledVisits(String studyStream)
    {
        return VisitNumberConfiguration.STREAM_LOW.equals(studyStream) ? this.visitsLow : this.visitsHigh;
    }

    /**
     * Get the path to the visit number question for the provided stream.
     * @param studyStream The stream for this subject. Expects {@code "Low Touch"} or {@code "High Touch"}.
     *                    Defaults to High Touch if an unexpected stream is provided
     * @return a relative path to the visit number question from the {@code Questionnaire} node
     */
    public String getVisitPath(String studyStream)
    {
        return VisitNumberConfiguration.STREAM_LOW.equals(studyStream) ? this.visitPathLow : this.visitPathHigh;
    }

    /**
     * Get a visit number question for the provided stream.
     * @param session the session to be used to retrieve the question node
     * @param studyStream The stream for this subject. Expects {@code "Low Touch"} or {@code "High Touch"}.
     *                    Defaults to High Touch if an unexpected stream is provided
     * @return the {@code Question} node for the visit number. May be {@code null}
     */
    public Node getVisitQuestion(Session session, String studyStream)
    {
        return getQuestion(session,
            VisitNumberConfiguration.STREAM_LOW.equals(studyStream) ? this.visitPathLow : this.visitPathHigh);
    }

    /**
     * Get the question which contains the study stream for the current questionnaire.
     * @param session the session to be used to retrieve the question node
     * @return the {@code Question} node for the study stream question. May be {@code null}
     */
    public Node getStudyStreamQuestion(Session session)
    {
        return getQuestion(session, this.studyStreamPath);
    }

    /**
     * Retrieve the visit number answer for a given form.
     * Will return either high or low stream visit numbers depending on which is available
     * @param session the session to be used to retrieve the visit number
     * @param formUtils An {@code FormUtils} instance to be used to help retrieve the visit number
     * @param form the form to retrieve the visit number for
     * @return A visit number included in the form's answers or {@code null} if no answer could be found
     */
    public Long getVisitNumber(Session session, FormUtils formUtils, Node form)
    {
        Long visitLow = getVisitNumber(session, formUtils, form, VisitNumberConfiguration.STREAM_LOW);

        // Only one (or neither) visit should have an answer.
        // If no low answer, return the high answer or the default of null if it isn't present either
        if (visitLow == null) {
            return getVisitNumber(session, formUtils, form, VisitNumberConfiguration.STREAM_HIGH);
        } else {
            return visitLow;
        }
    }

    /**
     * Check if this configuration is for a provided questionnaire.
     * @param questionnaire the questionnaire node to compare against
     * @return {@code true} if this configuration is for the provided questionnaire node
     */
    public boolean matches(Node questionnaire)
    {
        try {
            return this.questionnaire.equals(questionnaire.getPath());
        } catch (RepositoryException e) {
            return false;
        }
    }

    // Get the visit number for a given form and study string.
    // Returns null if no value could be found
    private Long getVisitNumber(Session session, FormUtils formUtils, Node form, String studyStream)
    {
        try {
            Property answerProperty = getAnswerProperty(formUtils, form, this.getVisitQuestion(session, studyStream));
            return answerProperty == null ? null : answerProperty.getLong();
        } catch (RepositoryException e) {
            return null;
        }
    }

    // Retrieve the question node that exists at a given subpath from the current questionnaire.
    // Returns null if it could not be found
    private Node getQuestion(Session session, String subPath)
    {
        try {
            return session.getNode(this.questionnaire + "/" + subPath);
        } catch (RepositoryException e) {
            return null;
        }
    }

    // Retrieve the property containing an answer for a given form and question.
    // Returns null if the property could not be found
    private Property getAnswerProperty(FormUtils formUtils, Node form, Node question)
        throws RepositoryException
    {
        Node answerNode = formUtils.getAnswer(form, question);
        if (answerNode != null && answerNode.hasProperty(FormUtils.VALUE_PROPERTY)) {
            return answerNode.getProperty(FormUtils.VALUE_PROPERTY);
        } else {
            return null;
        }
    }
}
