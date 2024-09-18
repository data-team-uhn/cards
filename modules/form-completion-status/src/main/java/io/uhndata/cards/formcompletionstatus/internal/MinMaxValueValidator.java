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
import java.util.Set;

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
    private static final String DATA_TYPE_PROP = "dataType";

    private static final String MAX_VALUE_PROP = "maxValue";

    private static final String MIN_VALUE_PROP = "minValue";

    private static final String DISABLE_MIN_MAX_ENFORCEMENT_PROP = "disableMinMaxValueEnforcement";

    private static final Set<String> SUPPORTED_TYPES = Set.of("long", "double", "decimal");

    @Override
    public int getPriority()
    {
        return 50;
    }

    @Override
    public void validate(final NodeBuilder answer, final Node question, final boolean initialAnswer,
        final Map<String, Boolean> flags)
    {
        try {
            if (!isMinMaxValidationApplicable(question)) {
                // If this isn't a numeric value with required limits, don't validate
                return;
            }
            if (answer.hasProperty(PROP_VALUE)) {
                final double minValue = question.hasProperty(MIN_VALUE_PROP)
                    ? question.getProperty(MIN_VALUE_PROP).getDouble() : Double.NaN;
                final double maxValue = question.hasProperty(MAX_VALUE_PROP)
                    ? question.getProperty(MAX_VALUE_PROP).getDouble() : Double.NaN;

                final PropertyState answerProp = answer.getProperty(PROP_VALUE);
                // if any value is out of range, set FLAG_INVALID to true
                for (int i = 0; i < answerProp.count(); i++) {
                    final Double value = answerProp.getValue(Type.DOUBLE, i);
                    if (value < minValue || value > maxValue) {
                        flags.put(FLAG_INVALID, true);
                        break;
                    }
                }
            }
            // If the INVALID flag has not been explicitly set so far, remove it
            removeIfNotExplicitlySet(FLAG_INVALID, flags);
        } catch (final RepositoryException ex) {
            // If something goes wrong do nothing
        }
    }

    private boolean isMinMaxValidationApplicable(final Node question)
    {
        try {
            final String type = question.getProperty(DATA_TYPE_PROP).getString();
            if (!SUPPORTED_TYPES.contains(type)) {
                // This only works on numerical types, nothing to do if this is not one of them
                return false;
            }
            final Boolean limitsNotEnforced = question.hasProperty(DISABLE_MIN_MAX_ENFORCEMENT_PROP)
                ? question.getProperty(DISABLE_MIN_MAX_ENFORCEMENT_PROP).getBoolean() : Boolean.FALSE;
            if (limitsNotEnforced) {
                // Value limits are not enforced, only suggested. Do not add the INVALID flag
                return false;
            }
        } catch (final RepositoryException ex) {
            // If something goes wrong do nothing
        }
        return true;
    }
}
