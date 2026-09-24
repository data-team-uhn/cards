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

package io.uhndata.cards.vocabularies.spi;

import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link VocabularyTermSource}.
 *
 * @version $Id$
 */
public class VocabularyTermSourceTest
{
    @Test
    public void missingParentsAndAncestorsAreEmpty()
    {
        final VocabularyTermSource term =
            new VocabularyTermSource("TEST:1", "Test", null, null, new ArrayListValuedHashMap<>());
        Assert.assertEquals(0, term.getParents().length);
        Assert.assertEquals(0, term.getAncestors().length);
    }

    @Test
    public void missingLabelFallsBackToTheIdentifier()
    {
        final VocabularyTermSource term =
            new VocabularyTermSource("TEST:1", null, new String[0], new String[0], new ArrayListValuedHashMap<>());
        Assert.assertEquals("TEST:1", term.getLabel());
    }

    @Test
    public void missingUriIsEmpty()
    {
        final VocabularyTermSource term =
            new VocabularyTermSource("TEST:1", "Test", new String[0], new String[0], new ArrayListValuedHashMap<>());
        Assert.assertEquals("", term.getURI());
    }
}
