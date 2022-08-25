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

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.formcompletionstatus.spi.AnswerValidator;

/**
 * An {@link MinMaxValueValidator} checks for each value if it is in the minValue ... maxValue range.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class MinMaxValueValidator implements AnswerValidator
{
    @Override
    public int getPriority()
    {
        return 50;
    }

    @Override
    @SuppressWarnings({ "checkstyle:CyclomaticComplexity", "checkstyle:NPathComplexity" })
    public void validate(NodeBuilder answer, Node question, boolean initialAnswer, Map<String, Boolean> flags)
    {
        try {
            final double minAnswers =
                question.hasProperty("minValue") ? question.getProperty("minValue").getDouble() : Double.NaN;
            final double maxAnswers =
                question.hasProperty("maxValue") ? question.getProperty("maxValue").getDouble() : Double.NaN;
            if (minAnswers == Double.NaN && maxAnswers == Double.NaN) {
                return;
            }

            final PropertyState answerProp = answer.getProperty(PROP_VALUE);
            if (answerProp == null) {
                return;
            }
            // if any value is out of range, set FLAG_INVALID to true
            if (answerProp.isArray()) {
                for (int i = 0; i < answerProp.count(); i++) {
                    Double value = answerProp.getValue(Type.DOUBLE, i);
                    if (minAnswers != Double.NaN && value < minAnswers
                        || maxAnswers != Double.NaN && value > maxAnswers) {
                        flags.put(FLAG_INVALID, true);
                        break;
                    }
                }
            } else {
                Double value = answerProp.getValue(Type.DOUBLE);
                if (minAnswers != Double.NaN && value < minAnswers || maxAnswers != Double.NaN && value > maxAnswers) {
                    flags.put(FLAG_INVALID, true);
                }
            }
            // if the current entry in the map is still false, remove it
            if (!flags.get(FLAG_INVALID)) {
                flags.remove(FLAG_INVALID);
            }
            if (!flags.get(FLAG_INCOMPLETE)) {
                flags.remove(FLAG_INCOMPLETE);
            }
        } catch (final RepositoryException ex) {
            // If something goes wrong do nothing
        }
    }
}
