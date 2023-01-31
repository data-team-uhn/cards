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
package io.uhndata.cards.formcompletionstatus.spi;

import java.util.Map;

import javax.jcr.Node;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;

import io.uhndata.cards.formcompletionstatus.AnswerCompletionStatusEditor;
import io.uhndata.cards.forms.api.FormUtils;

/**
 * An interface for answer validation service providers. Implementations of this service will be invoked by
 * {@link AnswerCompletionStatusEditor} every time a form is saved, and they can add or remove flags to be set on the
 * answer node. The validators will be called one after another, in ascending order of their {@link #getPriority()
 * priority}, receiving the answer value along with the flags computed by the previous processors, starting with the
 * flags set on the answer before the form was changed.
 *
 * @version $Id$
 */
public interface AnswerValidator extends Comparable<AnswerValidator>
{
    /**
     * JCR node property name for the answer value.
     */
    String PROP_VALUE = FormUtils.VALUE_PROPERTY;

    /**
     * Flag for marking an answer as invalid.
     */
    String FLAG_INVALID = "INVALID";

    /**
     * Flag for marking an answer as incomplete.
     */
    String FLAG_INCOMPLETE = "INCOMPLETE";

    /**
     * The priority of this validator. Validators with higher numbers are invoked after those with lower numbers,
     * receiving their computed flags as an input. The expected range is 0-100. Priority {@code 0} is considered the
     * base priority. Below 50, a validator is free to add or remove flags regardless of whether they have been
     * explicitly added or just carried over from the previous state, but 50 or higher are supposed to respect the flags
     * set by lower-priority validators and only remove a flag if it was not explicitly set.
     *
     * @return the priority of this validator, can be any number
     */
    int getPriority();

    /**
     * Validate the answer and add or remove flags accordingly.
     *
     * @param answer a cards:Answer node that was added/modified
     * @param question is the cards:Question node referenced by the answer
     * @param initialAnswer specifies if this is the first time a value is set for the answer, to mark whether a form is
     *            just still incomplete or was complete and also invalid
     * @param flags maps flag names to whether they were explicitly set or just copied from the previous form state, and
     *            is initially populated with the previously set flags mapped to false
     */
    void validate(NodeBuilder answer, Node question, boolean initialAnswer, Map<String, Boolean> flags);

    /**
     * Helper method which removes a flag only if it wasn't explicitly added.
     *
     * @param flag the flag to conditionally remove
     * @param flags the map of flags being managed during the {@link #validate} process
     */
    default void removeIfNotExplicitlySet(final String flag, final Map<String, Boolean> flags)
    {
        if (flags.getOrDefault(flag, Boolean.TRUE) == Boolean.FALSE) {
            flags.remove(flag);
        }
    }

    @Override
    default int compareTo(AnswerValidator o)
    {
        return this.getPriority() - o.getPriority();
    }
}
