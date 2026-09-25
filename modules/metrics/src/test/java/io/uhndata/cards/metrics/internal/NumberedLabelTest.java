/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.uhndata.cards.metrics.internal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link NumberedLabel}.
 *
 * @version $Id$
 * @since 0.9.42
 */
public class NumberedLabelTest
{
    @Test
    public void splitsTheNumberFromTheLabel()
    {
        final NumberedLabel label = NumberedLabel.parse("{007} UHN-IP Initial Emails Sent");
        assertEquals("UHN-IP Initial Emails Sent", label.getLabel());
        assertEquals(Long.valueOf(7), label.getOrder());
    }

    @Test
    public void doesNotNeedASpaceAfterTheNumber()
    {
        final NumberedLabel label = NumberedLabel.parse("{12}Imported");
        assertEquals("Imported", label.getLabel());
        assertEquals(Long.valueOf(12), label.getOrder());
    }

    @Test
    public void aLabelWithoutANumberIsAllLabel()
    {
        final NumberedLabel label = NumberedLabel.parse("  Plain label ");
        assertEquals("Plain label", label.getLabel());
        assertNull(label.getOrder());
    }

    @Test
    public void aNumberAloneHasNoLabel()
    {
        final NumberedLabel label = NumberedLabel.parse("{3} ");
        assertNull(label.getLabel());
        assertEquals(Long.valueOf(3), label.getOrder());
    }

    @Test
    public void aNumberTooLargeToBeAnOrderIsPartOfTheLabel()
    {
        final NumberedLabel label = NumberedLabel.parse("{1234567890123456789} Huge");
        assertEquals("{1234567890123456789} Huge", label.getLabel());
        assertNull(label.getOrder());
    }

    @Test
    public void nothingParsesAsNothing()
    {
        final NumberedLabel label = NumberedLabel.parse(null);
        assertNull(label.getLabel());
        assertNull(label.getOrder());
    }
}
