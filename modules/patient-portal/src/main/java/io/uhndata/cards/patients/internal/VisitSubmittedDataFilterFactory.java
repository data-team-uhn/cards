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

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.tuple.Pair;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.serialize.spi.BaseFilterFactory;
import io.uhndata.cards.serialize.spi.DataFilter;
import io.uhndata.cards.serialize.spi.DataFilterFactory;

/**
 * Filter forms based on the submitted status of the visit they belong to. Use {@code visitSubmitted=true} to filter
 * only forms belonging to a visit that has been submitted, and {@code visitSubmitted=false} to filter only forms
 * belonging to a visit that isn't submitted.
 *
 * @version $Id$
 * @since 0.9.24
 */
@Component
public class VisitSubmittedDataFilterFactory extends BaseFilterFactory implements DataFilterFactory
{
    @Reference
    private ThreadResourceResolverProvider resolverProvider;

    @Override
    public List<DataFilter> parseFilters(List<Pair<String, String>> filters, List<String> allSelectors)
    {
        return parseSingletonFilter(filters, allSelectors, "visitSubmitted", VisitSubmittedFilter::new);
    }

    class VisitSubmittedFilter implements DataFilter
    {
        private static final String VISIT_FORM_SELECTOR = "clinic_visitFormSubmitted";

        private final boolean positiveCheck;

        VisitSubmittedFilter(final String submitted)
        {
            this.positiveCheck = !"false".equals(submitted);
        }

        @Override
        public String getName()
        {
            return "visitSubmitted";
        }

        @Override
        public String getExtraQuerySelectors(String defaultSelectorName)
        {
            return " inner join [cards:Form] as " + VISIT_FORM_SELECTOR + " on " + defaultSelectorName + ".subject = "
                + VISIT_FORM_SELECTOR + ".subject";
        }

        @Override
        public String getExtraQueryConditions(final String defaultSelectorName)
        {
            try {
                return " and " + VISIT_FORM_SELECTOR + ".questionnaire = '" + getVisitQuestionnaireId() + "' and "
                    + (this.positiveCheck ? "" : "not ") + VISIT_FORM_SELECTOR
                    + ".statusFlags = 'SUBMITTED'";
            } catch (RepositoryException e) {
                // Not expected
            }
            return "";
        }

        @Override
        public String toString()
        {
            return this.getName() + "=" + this.positiveCheck;
        }

        private String getVisitQuestionnaireId() throws RepositoryException
        {
            return VisitSubmittedDataFilterFactory.this.resolverProvider.getThreadResourceResolver()
                .getResource("/Questionnaires/Visit information").adaptTo(Node.class).getIdentifier();
        }
    }
}
