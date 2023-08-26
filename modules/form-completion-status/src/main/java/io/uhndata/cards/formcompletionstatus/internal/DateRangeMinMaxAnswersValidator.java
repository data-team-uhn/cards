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
package io.uhndata.cards.formcompletionstatus.internal;

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.formcompletionstatus.spi.AnswerValidator;

/**
 * An {@link DateRangeMinMaxAnswersValidator} reports if a given number of date answers is invalid for a given question.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DateRangeMinMaxAnswersValidator extends MinMaxAnswersValidator implements AnswerValidator
{
    @Override
    public int getPriority()
    {
        return 15;
    }

    @Override
    public void validate(final NodeBuilder answer, final Node question, final boolean initialAnswer,
        final Map<String, Boolean> flags)
    {
        try {
            // This only checks the number of values for date range answers
            final String dataType = question.getProperty("dataType").getString();
            final boolean isDate = "date".equals(dataType);
            final String type = question.hasProperty("type") ? question.getProperty("type").getString() : "";
            final boolean isInterval = "interval".equals(type);

            if (!isDate || !isInterval) {
                return;
            }

            // Ranges are stored as pairs of values, so the true number of values is half of the number of actual values
            final long valuesCount = getNumberOfValues(answer) / 2;
            checkNumberOfValues(valuesCount, question, flags);
        } catch (final RepositoryException ex) {
            // If something goes wrong then we cannot verify the answer, leave it as it was before
        }
    }
}
