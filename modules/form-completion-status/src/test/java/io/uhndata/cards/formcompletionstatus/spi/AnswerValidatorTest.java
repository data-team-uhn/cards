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

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the default methods of {@link AnswerValidator}.
 *
 * @version $Id$
 */
public class AnswerValidatorTest
{
    /** A validator relying on the default methods of the interface. */
    private final AnswerValidator lowPriorityValidator = validatorWithPriority(10);

    private final AnswerValidator highPriorityValidator = validatorWithPriority(50);

    @Test
    public void removeIfNotExplicitlySetRemovesFalseFlag()
    {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put(AnswerValidator.FLAG_INVALID, false);
        this.lowPriorityValidator.removeIfNotExplicitlySet(AnswerValidator.FLAG_INVALID, flags);
        assertFalse(flags.containsKey(AnswerValidator.FLAG_INVALID));
    }

    @Test
    public void removeIfNotExplicitlySetKeepsTrueFlag()
    {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put(AnswerValidator.FLAG_INVALID, true);
        this.lowPriorityValidator.removeIfNotExplicitlySet(AnswerValidator.FLAG_INVALID, flags);
        assertTrue(flags.get(AnswerValidator.FLAG_INVALID));
    }

    @Test
    public void removeIfNotExplicitlySetIgnoresMissingFlag()
    {
        Map<String, Boolean> flags = new HashMap<>();
        this.lowPriorityValidator.removeIfNotExplicitlySet(AnswerValidator.FLAG_INVALID, flags);
        assertFalse(flags.containsKey(AnswerValidator.FLAG_INVALID));
    }

    @Test
    public void compareToOrdersByPriority()
    {
        assertTrue(this.lowPriorityValidator.compareTo(this.highPriorityValidator) < 0);
        assertTrue(this.highPriorityValidator.compareTo(this.lowPriorityValidator) > 0);
        assertEquals(0, this.lowPriorityValidator.compareTo(this.lowPriorityValidator));
    }

    private AnswerValidator validatorWithPriority(final int priority)
    {
        return new AnswerValidator()
        {
            @Override
            public int getPriority()
            {
                return priority;
            }

            @Override
            public void validate(final NodeBuilder answer, final Node question, final Map<String, Boolean> flags)
            {
                // Nothing to do
            }
        };
    }
}
