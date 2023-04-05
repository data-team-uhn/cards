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

import org.apache.commons.collections4.IterableUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.formcompletionstatus.spi.AnswerValidator;

/**
 * Checks if the number of values entered for an answer match the required minimum/maximum required by the question.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class MinMaxAnswersValidator implements AnswerValidator
{
    @Override
    public int getPriority()
    {
        return 10;
    }

    @Override
    public void validate(final NodeBuilder answer, final Node question, final boolean initialAnswer,
        final Map<String, Boolean> flags)
    {
        try {
            final long valuesCount = getNumberOfValues(answer);
            checkNumberOfValues(valuesCount, question, flags);
        } catch (final RepositoryException ex) {
            // If something goes wrong then we cannot verify the answer, leave it as it was before
        }
    }

    protected void checkNumberOfValues(final long valuesCount, final Node question, final Map<String, Boolean> flags)
            throws RepositoryException
    {
        final long minAnswers =
            question.hasProperty("minAnswers") ? question.getProperty("minAnswers").getLong() : 0;
        final long maxAnswers =
            question.hasProperty("maxAnswers") ? question.getProperty("maxAnswers").getLong() : 0;

        // Checks if the number of values is within the specified minAnswers ... maxAnswers range,
        if (valuesCount < minAnswers && minAnswers != 0) {
            flags.put(FLAG_INCOMPLETE, true);
        } else {
            flags.remove(FLAG_INCOMPLETE);
        }
        if (valuesCount > maxAnswers && maxAnswers != 0) {
            flags.put(FLAG_INVALID, true);
        } else {
            flags.remove(FLAG_INVALID);
        }
    }

    protected long getNumberOfValues(final NodeBuilder answer)
    {
        if (answer.hasProperty(PROP_VALUE)) {
            final PropertyState answerProp = answer.getProperty(PROP_VALUE);
            final Iterable<String> nodeAnswers = answerProp.getValue(Type.STRINGS);
            return IterableUtils.size(nodeAnswers);
        }
        return 0;
    }
}
