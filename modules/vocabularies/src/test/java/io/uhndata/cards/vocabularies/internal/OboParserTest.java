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

package io.uhndata.cards.vocabularies.internal;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.vocabularies.spi.VocabularyDescription;
import io.uhndata.cards.vocabularies.spi.VocabularyDescriptionBuilder;
import io.uhndata.cards.vocabularies.spi.VocabularyIndexException;
import io.uhndata.cards.vocabularies.spi.VocabularyTermSource;

/**
 * Unit tests for {@link OboParser}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class OboParserTest
{
    private static final List<VocabularyTermSource> PARSED_TERMS = new ArrayList<>();

    @BeforeClass
    public static void parse() throws IOException, VocabularyIndexException, URISyntaxException
    {
        OboParser parser = new OboParser();

        File f = new File(OboParserTest.class.getResource("/test.obo").toURI());
        VocabularyDescription description = new VocabularyDescriptionBuilder().build();
        parser.parse(f, description, new Consumer<VocabularyTermSource>()
        {
            @Override
            public void accept(VocabularyTermSource t)
            {
                PARSED_TERMS.add(t);
            }
        });
    }

    @Test
    public void canParseOboFiles()
    {
        Assert.assertTrue(new OboParser().canParse("OBO"));
    }

    @Test
    public void canParseOboFilesCaseInsensitive()
    {
        Assert.assertTrue(new OboParser().canParse("obo"));
        Assert.assertTrue(new OboParser().canParse("Obo"));
    }

    @Test
    public void canNotParseNull()
    {
        Assert.assertFalse(new OboParser().canParse(null));
    }

    @Test
    public void canNotParseEmpty()
    {
        Assert.assertFalse(new OboParser().canParse(""));
    }

    @Test
    public void canNotParseOwl()
    {
        Assert.assertFalse(new OboParser().canParse("OWL"));
    }

    @Test
    public void gracefullyIgnoresMissingFiles() throws IOException, VocabularyIndexException
    {
        new OboParser().parse(new File("/" + RandomStringUtils.random(40)), new VocabularyDescriptionBuilder().build(),
            new Consumer<VocabularyTermSource>()
            {
                @Override
                public void accept(VocabularyTermSource t)
                {
                    Assert.fail("Nothing should have been generated");
                }
            });
    }

    @Test
    public void lastFrameIsNotStoredIfItIsNotATerm() throws IOException, VocabularyIndexException, URISyntaxException
    {
        OboParser parser = new OboParser();
        final List<VocabularyTermSource> parsed = new ArrayList<>();

        File f = new File(OboParserTest.class.getResource("/eoftest.obo").toURI());
        VocabularyDescription description = new VocabularyDescriptionBuilder().build();
        parser.parse(f, description, new Consumer<VocabularyTermSource>()
        {
            @Override
            public void accept(VocabularyTermSource t)
            {
                parsed.add(t);
            }
        });

        Assert.assertEquals(1, parsed.size());
        Assert.assertNull(parsed.stream().filter(term -> "ignored".equals(term.getId())).findFirst().orElse(null));
        Assert.assertNotNull(parsed.stream().filter(term -> "TEST:ROOT".equals(term.getId())).findFirst().orElse(null));
    }

    @Test
    public void parsedWithoutErrors()
    {
        Assert.assertEquals(19, PARSED_TERMS.size());
    }

    @Test
    public void rootParsed()
    {
        VocabularyTermSource term = PARSED_TERMS.get(0);
        Assert.assertEquals("TEST:ROOT", term.getId());
        Assert.assertEquals("Root term", term.getLabel());
        Assert.assertEquals(0, term.getParents().length);
        Assert.assertEquals(0, term.getAncestors().length);
        Assert.assertEquals(2, term.getAllProperties().size());
    }

    @Test
    public void simpleTermParsed()
    {
        VocabularyTermSource term = PARSED_TERMS.get(1);
        Assert.assertEquals("TEST:SIMPLE", term.getId());
        Assert.assertEquals("Simple term", term.getLabel());
        Assert.assertEquals("A simple term.", term.getDescription());
        Assert.assertEquals(1, term.getParents().length);
        Assert.assertEquals("TEST:ROOT", term.getParents()[0]);
        Assert.assertEquals(1, term.getAncestors().length);
        Assert.assertEquals("TEST:ROOT", term.getAncestors()[0]);
        // 4 defined properties + ancestors
        Assert.assertEquals(5, term.getAllProperties().size());
    }

    @Test
    public void typedefFramesAreIgnored()
    {
        Assert.assertNull(getTerm("ignored"));
        Assert.assertNull(getTerm("ignored2"));
    }

    @Test
    public void multipleValuesAreSupported()
    {
        VocabularyTermSource term = getTerm("TEST:MULTI");
        Assert.assertNotNull(term);

        Collection<String> synonyms = term.getAllProperties().get("synonym");
        Assert.assertEquals(3, synonyms.size());
        Assert.assertTrue(synonyms.contains("Multi-valued term"));
        Assert.assertTrue(synonyms.contains("Multivalued"));
        Assert.assertTrue(synonyms.contains("A term that may have multiple values"));
    }

    @Test
    public void multipleEqualValuesAreIgnored()
    {
        VocabularyTermSource term = getTerm("TEST:MULTI");
        Assert.assertNotNull(term);

        Collection<String> altIds = term.getAllProperties().get("alt_id");
        // TEST:MV is specified twice, but it should only appear once
        Assert.assertEquals(2, altIds.size());
        Assert.assertTrue(altIds.contains("TEST:MV"));
        Assert.assertTrue(altIds.contains("TEST:MANY"));
    }

    @Test
    public void emptyTermsAreSupported()
    {
        VocabularyTermSource term = getTerm("TEST:EMPTY");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:EMPTY", term.getId());
        // getLabel falls back to the identifier if no explicit label is provided.
        Assert.assertEquals("TEST:EMPTY", term.getLabel());
        Assert.assertNull(term.getDescription());
        Assert.assertEquals(0, term.getParents().length);
        Assert.assertEquals(0, term.getAncestors().length);
        Assert.assertEquals(1, term.getAllProperties().size());
    }

    @Test
    public void combinedTermsAreSupported()
    {
        VocabularyTermSource term = getTerm("TEST:COMBINED");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:COMBINED", term.getId());
        Assert.assertEquals("Combined term", term.getLabel());
        Assert.assertEquals("A term may be combined from different frames with the same identifier,"
            + " so only one TEST:COMBINED term should end up in the data,"
            + " having both a name and a def, and two synonyms",
            term.getDescription());
        Assert.assertEquals(2, term.getAllProperties().get("synonym").size());
        Assert.assertTrue(term.getAllProperties().get("synonym").contains("Split"));
        Assert.assertTrue(term.getAllProperties().get("synonym").contains("Recomposed"));
        Assert.assertEquals("TEST:ROOT", term.getParents()[0]);
    }

    @Test
    public void commentsAreIgnored()
    {
        VocabularyTermSource term = getTerm("TEST:COMMENTS");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:COMMENTS", term.getId());
        Assert.assertEquals("Test support for comments", term.getLabel());
        Assert.assertEquals("Comments inside quotes are not! allowed", term.getDescription());
        Assert.assertFalse(term.getAllProperties().containsKey("!comment"));
        Assert.assertFalse(term.getAllProperties().containsKey("comment!"));
        Assert.assertEquals("This must be a valid tag", getFirst("after", term));
        Assert.assertEquals("Escape ! is ! possible!", getFirst("escape1", term));
        Assert.assertEquals("Double escape \\", getFirst("escape2", term));
        Assert.assertEquals("Tripe escape \\! is still an escape", getFirst("escape3", term));
        Assert.assertEquals("While quad escape \\\\", getFirst("escape4", term));
        Assert.assertEquals("Still \\\\! valid", getFirst("escape5", term));
        Assert.assertEquals("Tag name can al!so be escaped", getFirst("esc!ape", term));
    }

    @Test
    public void trailingModifiersAreIgnored()
    {
        VocabularyTermSource term = getTerm("TEST:TRAILINGMODS");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:TRAILINGMODS", term.getId());
        Assert.assertEquals("Trailing modifiers are ignored", term.getLabel());
        Assert.assertEquals("Escape {is} {possible}", getFirst("escape1", term));
        Assert.assertEquals("Double escape \\", getFirst("escape2", term));
        Assert.assertEquals("Tripe escape \\{is still an escape\\}", getFirst("escape3", term));
        Assert.assertEquals("While quad escape \\\\", getFirst("escape4", term));
        Assert.assertEquals("Still \\\\{valid}", getFirst("escape5", term));
        Assert.assertEquals("Tag name can al{so b}e escaped", getFirst("esc{ape}", term));
    }

    @Test
    public void xreflistsAreIgnoredAfterQuotedStrings()
    {
        VocabularyTermSource term = getTerm("TEST:XREFLISTS");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:XREFLISTS", term.getId());
        Assert.assertEquals("XredLists must be [ignored outside quotes, but not in the absence of quotes]",
            term.getLabel());
        Assert.assertEquals("They can only appear outside quotes", term.getDescription());
    }

    @Test
    public void quotedStringsAreSupported()
    {
        VocabularyTermSource term = getTerm("TEST:QUOTES");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:QUOTES", term.getId());
        Assert.assertEquals("All fields can be quoted", term.getLabel());
        Assert.assertEquals("\"But escaped quotes are not\" really quotes", term.getDescription());
        Assert.assertEquals("Tag names can also be quoted", getFirst("tag", term));
        Assert.assertEquals("If the \"quotes\" are not from the start, then they are simple inner quotes",
            getFirst("inner", term));
        Assert.assertEquals("Inner \"quotes\" must be escaped as well", getFirst("escape1", term));
        Assert.assertEquals("Double escaped \\", getFirst("escape2", term));
        Assert.assertEquals("Tripe escaped \\\"quotes\\\" are escaped", getFirst("escape3", term));
        Assert.assertEquals("Quad escaped \\\\", getFirst("escape4", term));
        Assert.assertEquals("Still \\\\\"valid\\\\\" escapes", getFirst("escape5", term));
    }

    @Test
    public void continuationsAreSupported()
    {
        VocabularyTermSource term = getTerm("TEST:CONTINUATION");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:CONTINUATION", term.getId());
        Assert.assertEquals("Values can be continued on the \\next line if needed \\", term.getLabel());
        Assert.assertEquals("tag names can also be continued", term.getDescription());
    }

    @Test
    public void escapeSequencesAreSupported()
    {
        VocabularyTermSource term = getTerm("TEST:ESCAPES");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:ESCAPES", term.getId());
        Assert.assertEquals("pass:ed", getFirst("te:st1", term));
        Assert.assertEquals(" spaces in the value ", getFirst("test2", term));
        Assert.assertEquals("allow\ted", getFirst("tab\ts", term));
        Assert.assertEquals("\nallowed\n", getFirst("\nnew\nlines\n", term));
        Assert.assertEquals("allow\\ed", getFirst("b\\s", term));
        Assert.assertEquals("all\\\\ow\\\\\\ed\\", getFirst("b\\\\\\s", term));
        Assert.assertEquals("!:{}\\[]!", getFirst("others", term));
    }

    @Test
    public void ancestorsAreRecursivelyGathered()
    {
        VocabularyTermSource term = getTerm("TEST:ANCESTORS");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:ANCESTORS", term.getId());
        Assert.assertEquals(6, term.getAncestors().length);
        Assert.assertEquals(3, term.getParents().length);
    }

    @Test
    public void unknownParentsAreKeptAsIs()
    {
        VocabularyTermSource term = getTerm("TEST:BROKEN_PARENTS");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:BROKEN_PARENTS", term.getId());
        Assert.assertEquals(4, term.getAncestors().length);
        Assert.assertEquals(3, term.getParents().length);
    }

    @Test
    public void termWithoutIdentifierIsIgnored()
    {
        Assert.assertNull(
            PARSED_TERMS.stream()
                .filter(term -> "TEST:MISSING_ID".equals(getFirst("alt_id", term)))
                .findFirst().orElse(null));
    }

    @Test
    public void trailingBackslashIsIgnored()
    {
        VocabularyTermSource term = getTerm("TEST:ESCAPED_EOF");
        Assert.assertNotNull(term);
        Assert.assertEquals("TEST:ESCAPED_EOF", term.getId());
        Assert.assertEquals("If the file ends with a backslash, it will be trimmed away", term.getLabel());
    }

    private VocabularyTermSource getTerm(final String id)
    {
        return PARSED_TERMS.stream().filter(term -> id.equals(term.getId())).findFirst().orElse(null);
    }

    private String getFirst(final String property, final VocabularyTermSource fromTerm)
    {
        final Collection<String> values = fromTerm.getAllProperties().get(property);
        if (values != null && !values.isEmpty()) {
            return values.iterator().next();
        }
        return null;
    }
}
