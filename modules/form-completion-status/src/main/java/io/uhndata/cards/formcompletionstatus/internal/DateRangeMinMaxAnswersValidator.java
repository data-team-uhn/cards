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

import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.formcompletionstatus.spi.AnswerValidator;

/**
 * An {@link DateRangeMinMaxAnswersValidator} reports if a given number of date answers is invalid for a given question.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DateRangeMinMaxAnswersValidator implements AnswerValidator
{
    @Override
    public int getPriority()
    {
        return 15;
    }

    @Override
    @SuppressWarnings({ "checkstyle:CyclomaticComplexity", "checkstyle:NPathComplexity" })
    public void validate(NodeBuilder answer, Node question, boolean initialAnswer, Map<String, Boolean> flags)
    {
        try {
            final String dataType = question.getProperty("dataType").getString();
            final boolean isDate = "date".equals(dataType);
            final String type = question.getProperty("type").getString();
            final boolean isInterval = "interval".equals(type);

            if (!isDate || !isInterval) {
                return;
            }

            final long minAnswers =
                question.hasProperty("minAnswers") ? question.getProperty("minAnswers").getLong() : 0;
            final long maxAnswers =
                question.hasProperty("maxAnswers") ? question.getProperty("maxAnswers").getLong() : 0;
            if (minAnswers == 0 && maxAnswers == 0) {
                return;
            }

            final PropertyState answerProp = answer.getProperty(PROP_VALUE);
            if (answerProp == null) {
                return;
            }
            final Iterable<String> nodeAnswers = answerProp.getValue(Type.STRINGS);
            final int numAnswers = iterableLength(nodeAnswers) / 2;
            // Checks if the number of values divided by two is within the specified minAnswers ... maxAnswers range,
            // then set the required flags to true, else remove them
            if ((numAnswers < minAnswers && minAnswers != 0) || (numAnswers > maxAnswers && maxAnswers != 0)) {
                flags.put(FLAG_INCOMPLETE, true);
                flags.put(FLAG_INVALID, true);
            } else {
                flags.remove(FLAG_INCOMPLETE);
                flags.remove(FLAG_INVALID);
            }
        } catch (final RepositoryException ex) {
            // If something goes wrong then we definitely cannot have a valid answer
        }
    }

    /**
     * Counts the number of items in an Iterable.
     *
     * @param iterable the Iterable object to be counted
     * @return the number of objects in the Iterable
     */
    private int iterableLength(final Iterable<?> iterable)
    {
        int len = 0;
        final Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            len++;
        }
        return len;
    }
}
