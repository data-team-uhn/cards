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
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.formcompletionstatus.spi.AnswerValidator;

/**
 * An {@link RegExpValueValidator} checks for each value if it matches the provided regular expression.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class RegExpValueValidator implements AnswerValidator
{
    private static final Set<String> SUPPORTED_TYPES = Set.of("text");

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
            final String type = question.getProperty("dataType").getString();
            if (!SUPPORTED_TYPES.contains(type)) {
                // This only works on text, nothing to do otherwise
                return;
            }
            if (question.hasProperty("validationRegexp") && answer.hasProperty(PROP_VALUE)) {
                final String regexp = question.getProperty("validationRegexp").getString();
                Pattern pattern = Pattern.compile(regexp);

                final PropertyState answerProp = answer.getProperty(PROP_VALUE);
                // if any value does not match the pattern, set FLAG_INVALID to true
                for (int i = 0; i < answerProp.count(); i++) {
                    final String value = answerProp.getValue(Type.STRING, i);
                    if (!pattern.matcher(value).find()) {
                        flags.put(FLAG_INVALID, true);
                        return;
                    }
                }
            }
            // If the INVALID flag has not been explicitly set so far, remove it
            removeIfNotExplicitlySet(FLAG_INVALID, flags);
        } catch (final RepositoryException ex) {
            // If something goes wrong do nothing
        }
    }
}
