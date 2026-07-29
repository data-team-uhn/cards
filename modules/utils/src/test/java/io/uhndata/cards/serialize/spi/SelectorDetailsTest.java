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
package io.uhndata.cards.serialize.spi;

import org.junit.Test;

import io.uhndata.cards.serialize.spi.SelectorDetails.SelectorOption;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link SelectorDetails}.
 *
 * @version $Id$
 */
public class SelectorDetailsTest
{
    private static final String NAME = "bare";

    private static final String DESCRIPTION = "A selector";

    @Test
    public void nameAndDescriptionConstructorDisablesByDefault()
    {
        SelectorDetails details = new SelectorDetails(NAME, DESCRIPTION);
        assertEquals(NAME, details.getName());
        assertEquals(DESCRIPTION, details.getDescription());
        assertFalse(details.isEnabledByDefault());
        assertEquals(0, details.getOptions().length);
    }

    @Test
    public void enabledByDefaultConstructorStoresFlag()
    {
        SelectorDetails details = new SelectorDetails(NAME, DESCRIPTION, true);
        assertTrue(details.isEnabledByDefault());
        assertEquals(0, details.getOptions().length);
    }

    @Test
    public void optionsArrayConstructorStoresOptions()
    {
        SelectorDetails source = new SelectorDetails(NAME, DESCRIPTION, "exclude", "What to exclude");
        SelectorOption[] options = source.getOptions();
        SelectorDetails details = new SelectorDetails(NAME, DESCRIPTION, options);
        assertFalse(details.isEnabledByDefault());
        assertEquals(1, details.getOptions().length);
        assertEquals("exclude", details.getOptions()[0].getName());
        assertEquals("What to exclude", details.getOptions()[0].getDescription());
    }

    @Test
    public void enabledByDefaultAndOptionsArrayConstructorStoresBoth()
    {
        SelectorOption[] options = new SelectorDetails(NAME, DESCRIPTION, "a", "b").getOptions();
        SelectorDetails details = new SelectorDetails(NAME, DESCRIPTION, true, options);
        assertTrue(details.isEnabledByDefault());
        assertEquals(1, details.getOptions().length);
    }

    @Test
    public void varargsConstructorPairsNamesAndDescriptions()
    {
        SelectorDetails details = new SelectorDetails(NAME, DESCRIPTION,
            "option1", "First option", "option2", "Second option");
        assertFalse(details.isEnabledByDefault());
        assertEquals(2, details.getOptions().length);
        assertEquals("option1", details.getOptions()[0].getName());
        assertEquals("First option", details.getOptions()[0].getDescription());
        assertEquals("option2", details.getOptions()[1].getName());
        assertEquals("Second option", details.getOptions()[1].getDescription());
    }

    @Test
    public void varargsConstructorIgnoresDanglingOption()
    {
        SelectorDetails details = new SelectorDetails(NAME, DESCRIPTION, true,
            "option1", "First option", "dangling");
        assertTrue(details.isEnabledByDefault());
        assertEquals(1, details.getOptions().length);
        assertEquals("option1", details.getOptions()[0].getName());
    }

    @Test
    public void varargsConstructorWithoutOptionsLeavesOptionsEmpty()
    {
        SelectorDetails details = new SelectorDetails(NAME, DESCRIPTION, true, new String[0]);
        assertTrue(details.isEnabledByDefault());
        assertEquals(0, details.getOptions().length);
    }

    @Test
    public void singleArgumentOptionHasEmptyDescription()
    {
        SelectorOption option = new SelectorDetails(NAME, DESCRIPTION).new SelectorOption("exclude");
        assertEquals("exclude", option.getName());
        assertEquals("", option.getDescription());
    }
}
