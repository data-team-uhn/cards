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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.formcompletionstatus.spi.AnswerValidator;

/**
 * An {@link MinMaxValueValidator} checks for each value if it is in the minValue ... maxValue range. Values matching a
 * predefined answer option are accepted even if they fall outside that range.
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

    private static final String ANSWER_OPTION_NODETYPE = "cards:AnswerOption";

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

                final Set<Double> optionValues = getAnswerOptionValues(question);

                final PropertyState answerProp = answer.getProperty(PROP_VALUE);
                // if any value is out of range, set FLAG_INVALID to true
                for (int i = 0; i < answerProp.count(); i++) {
                    final Double value = answerProp.getValue(Type.DOUBLE, i);
                    // A predefined answer option (e.g. a "prefer not to answer" sentinel) is always accepted,
                    // even if its value falls outside the configured range
                    if ((value < minValue || value > maxValue) && !optionValues.contains(value)) {
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

    /**
     * Collect the numeric values of all predefined answer options defined for the question. These are accepted even
     * when they fall outside the configured range, since they represent deliberate choices offered to the user rather
     * than free-form input.
     *
     * @param question the cards:Question node
     * @return the set of answer option values, as doubles; may be empty, never {@code null}
     */
    private Set<Double> getAnswerOptionValues(final Node question)
    {
        final Set<Double> optionValues = new HashSet<>();
        try {
            final NodeIterator children = question.getNodes();
            while (children.hasNext()) {
                final Node child = children.nextNode();
                if (child.isNodeType(ANSWER_OPTION_NODETYPE) && child.hasProperty(PROP_VALUE)) {
                    try {
                        optionValues.add(child.getProperty(PROP_VALUE).getDouble());
                    } catch (final RepositoryException ex) {
                        // A non-numeric option value cannot match a numeric answer, so it can be safely ignored
                    }
                }
            }
        } catch (final RepositoryException ex) {
            // If something goes wrong, treat it as if there were no options
        }
        return optionValues;
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
