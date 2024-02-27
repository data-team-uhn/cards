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
package io.uhndata.cards.patients.internal;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.serialize.spi.BaseFilterFactory;
import io.uhndata.cards.serialize.spi.DataFilter;
import io.uhndata.cards.serialize.spi.DataFilterFactory;

/**
 * Filter forms based on the clinic their visit belongs to. Use {@code clinic=/Path/To/Clinic} to filter only forms
 * belonging to a visit for that clinic, and {@code clinicNot=/Path/To/Clinic} to filter only forms not belonging to a
 * visit for that clinic. If the clinic name does not start with {@code /}, it will be prefixed with
 * {@code /Survey/ClinicMapping/}. Since {@code /} is not allowed in the URL, it must be escaped as {@code %252F}, so a
 * real URL would look like {@code ...dataFilter:clinic=%252F%53urvey%252FClinicMapping%252F-432465813}. Multiple
 * clinics can be allowed (either of) or excluded (any of) by using this filter multiple times.
 *
 * @version $Id$
 * @since 0.9.22
 */
@Component
public class ClinicDataFilterFactory extends BaseFilterFactory implements DataFilterFactory
{
    @Reference
    private ThreadResourceResolverProvider resolverProvider;

    @Override
    public List<DataFilter> parseFilters(List<Pair<String, String>> filters, List<String> allSelectors)
    {
        return parseDoubleSetFilters(filters, allSelectors, "clinic", "clinicNot", ClinicFilter::new);
    }

    class ClinicFilter implements DataFilter
    {
        private static final String VISIT_FORM_SELECTOR = "clinic_visitForm";

        private static final String CLINIC_ANSWER_SELECTOR = "clinic_clinicAnswer";

        private final Set<String> clinics;

        private final boolean positiveCheck;

        ClinicFilter(final Set<String> clinics, final boolean positiveCheck)
        {
            this.clinics = clinics;
            this.positiveCheck = positiveCheck;
        }

        @Override
        public String getName()
        {
            return "clinic";
        }

        @Override
        public String getExtraQuerySelectors(String defaultSelectorName)
        {
            return " inner join [cards:Form] as " + VISIT_FORM_SELECTOR + " on " + defaultSelectorName + ".subject = "
                + VISIT_FORM_SELECTOR + ".subject"
                + " inner join [cards:ResourceAnswer] as " + CLINIC_ANSWER_SELECTOR + " on " + CLINIC_ANSWER_SELECTOR
                + ".form = " + VISIT_FORM_SELECTOR + ".[jcr:uuid]";
        }

        @Override
        public String getExtraQueryConditions(final String defaultSelectorName)
        {
            try {
                return " and " + VISIT_FORM_SELECTOR + ".questionnaire = '" + getVisitQuestionnaireId() + "'"
                    + " and " + CLINIC_ANSWER_SELECTOR + ".question = '" + getClinicQuestionId() + "'"
                    + " and ("
                    + StringUtils.join(
                        this.clinics.stream()
                            // SQL escape
                            .map(clinic -> clinic.replaceAll("'", "''"))
                            // Allow specifying filters without the leading /Survey/ClinicMapping/
                            .map(clinic -> StringUtils.startsWith(clinic, "/") ? clinic
                                : "/Survey/ClinicMapping/" + clinic)
                            .map(clinic -> CLINIC_ANSWER_SELECTOR + ".value = '" + clinic + "'")
                            .map(condition -> (this.positiveCheck ? "" : "not ") + condition)
                            .collect(Collectors.toList()),
                        this.positiveCheck ? " or " : " and ")
                    + ")";
            } catch (RepositoryException e) {
                // Not expected
            }
            return "";
        }

        private String getVisitQuestionnaireId() throws RepositoryException
        {
            return ClinicDataFilterFactory.this.resolverProvider.getThreadResourceResolver()
                .getResource("/Questionnaires/Visit information").adaptTo(Node.class).getIdentifier();
        }

        private String getClinicQuestionId() throws RepositoryException
        {
            return ClinicDataFilterFactory.this.resolverProvider.getThreadResourceResolver()
                .getResource("/Questionnaires/Visit information/clinic").adaptTo(Node.class).getIdentifier();
        }

        @Override
        public String toString()
        {
            return (this.positiveCheck ? "clinic" : "clinicNot") + "=" + this.clinics;
        }
    }
}
