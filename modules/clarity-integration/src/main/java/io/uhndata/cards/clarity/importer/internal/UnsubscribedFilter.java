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

package io.uhndata.cards.clarity.importer.internal;

import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.AbstractClarityDataProcessor;
import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Clarity import processor that discards visits for patients who have unsubscribed from emails.
 *
 * @version $Id$
 */
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = UnsubscribedFilter.Config.class)
public class UnsubscribedFilter extends AbstractClarityDataProcessor implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsubscribedFilter.class);

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectUtils subjectUtils;

    @Reference
    private SubjectTypeUtils subjectTypeUtils;

    @ObjectClassDefinition(name = "Clarity import filter - Unsubscribed Patient Discarder",
        description = "Configuration for the Clarity importer filter that discards visits for patients who have "
            + "unsubscribed from survey emails.")
    public @interface Config
    {
        @AttributeDefinition(name = "Enabled")
        boolean enable() default false;

        @AttributeDefinition(name = "Supported import types", description = "Leave empty to support all imports")
        String[] supportedTypes();
    }

    @Activate
    public UnsubscribedFilter(Config configuration)
    {
        super(configuration.enable(), configuration.supportedTypes(), 10);
    }

    @Override
    public Map<String, String> processEntry(final Map<String, String> input)
    {
        final String mrn = input.get("/SubjectTypes/Patient");
        final String id = input.getOrDefault("/SubjectTypes/Patient/Visit", "Unknown");

        if (mrn == null || mrn.length() == 0) {
            LOGGER.warn("Discarded visit {} due to no mrn", id);
            return null;
        } else {
            // Get all patients with that MRN
            ResourceResolver resolver = this.rrp.getThreadResourceResolver();
            String subjectMatchQuery = String.format(
                "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='%s' option (index tag property)",
                mrn);
            resolver.refresh();
            final Iterator<Resource> subjectResourceIter = resolver.findResources(subjectMatchQuery, "JCR-SQL2");
            // Should only be 0 or 1 patient with that MRN. Process it if found.
            if (subjectResourceIter.hasNext() && patientHasUnsubscribed(subjectResourceIter.next(), id)) {
                LOGGER.error("discarding patient");
                return null;
            }
        }
        return input;
    }

    private boolean patientHasUnsubscribed(Resource subjectResource, String id)
    {
        final Node subject = subjectResource.adaptTo(Node.class);
        try {
            // Iterate through forms for the patient looking for the patient information form
            for (final PropertyIterator forms = subject.getReferences("subject"); forms.hasNext();) {
                final Node form = forms.nextProperty().getParent();
                if (formIsUnsubscribed(form)) {
                    // Discard the clarity row
                    LOGGER.warn("Discarded visit {} due to unsubscription", id);
                    return true;
                }
            }
        } catch (RepositoryException e) {
            // Failed to get forms for this subject: Could not check if the patient has unsubscribed.
            // Default to discarding this visit
            LOGGER.error("Discarded visit {}: Could not check for unsubscription", id);
            return true;
        }

        // No unsubscription was found
        return false;
    }

    private boolean formIsUnsubscribed(Node form)
    {
        final Node questionnaire = this.formUtils.getQuestionnaire(form);
        if (isPatientInformationForm(questionnaire)) {
            Node unsubscribedQuestion =
                this.questionnaireUtils.getQuestion(questionnaire, "email_unsubscribed");

            final Long unsubscribed =
                (Long) this.formUtils.getValue(
                    this.formUtils.getAnswer(form, unsubscribedQuestion));

            if (unsubscribed != null && unsubscribed == 1) {
                return true;
            }
        }
        return false;
    }

    private boolean isPatientInformationForm(final Node questionnaire)
    {
        try {
            return questionnaire != null && "/Questionnaires/Patient information".equals(questionnaire.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if form is Patient Information form: {}", e.getMessage(), e);
            return false;
        }
    }
}
