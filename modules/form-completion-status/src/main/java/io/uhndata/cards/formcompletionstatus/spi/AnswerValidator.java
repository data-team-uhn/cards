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

/**
 * An {@link AnswerValidator} interface for validation service providers.
 *
 * @version $Id$
 */
public interface AnswerValidator
{
    /**
     * Field name for value.
     */
    String PROP_VALUE = "value";

    /**
     * Flag for invalid form.
     */
    String FLAG_INVALID = "INVALID";

    /**
     * Flag for incomplete form.
     */
    String FLAG_INCOMPLETE = "INCOMPLETE";

    /**
     * The priority of this processor. Processors with higher numbers are invoked after those with lower numbers,
     * receiving their output as an input. Priority {@code 0} is considered the base priority, where the properties and
     * children are serialized using a default method. Use higher numbers if you want to post-process the default
     * serialization, or negative numbers if you want to provide a different base serialization.
     *
     * @return the priority of this processor, can be any number
     */
    int getPriority();

    /**
     * Validator that can can add or remove flags.
     *
     * @param answer is a cards:Answer node that was added/modified
     * @param question is the cards:Question node referenced by the answer
     * @param initialAnswer specifies if this is the first time a value is set for the answer, to mark whether a form
     *     is just still incomplete or was complete and also invalid
     * @param flags maps flag names to whether they were explicitly set or just copied from the previous form state,
     *     and is initially populated with the previously set flags mapped to false
     */
    void validate(NodeBuilder answer, Node question, boolean initialAnswer, Map<String, Boolean> flags);
}
