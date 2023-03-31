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
package io.uhndata.cards.spi;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link SearchUtils}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SearchUtilsTest
{

    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String FORM_TYPE = "cards:Form";

    // Keys in Response Json
    private static final String QUERY_MATCH_PROPERTY = "cards:queryMatch";
    private static final String QUESTION_PROPERTY = "question";
    private static final String IN_NOTES_PROPERTY = "inNotes";
    private static final String PATH_PROPERTY = "@path";
    private static final String BEFORE_PROPERTY = "before";
    private static final String AFTER_PROPERTY = "after";
    private static final String TEXT_PROPERTY = "text";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Test
    public void escapeLikeTextReplacesUnderscoreChar()
    {
        String input = "answer_value";
        String processedText = SearchUtils.escapeLikeText(input);
        assertEquals("answer\\_value", processedText);
    }

    @Test
    public void escapeLikeTextReplacesBackSlashChar()
    {
        String input = "answer\\value";
        String processedText = SearchUtils.escapeLikeText(input);
        assertEquals("answer\\\\value", processedText);
    }

    @Test
    public void escapeLikeTextReplacesPercentChar()
    {
        String input = "answer%value";
        String processedText = SearchUtils.escapeLikeText(input);
        assertEquals("answer\\%value", processedText);
    }

    @Test
    public void escapeLikeTextReplacesQuotationMarkChar()
    {
        String input = "answer'value";
        String processedText = SearchUtils.escapeLikeText(input);
        assertEquals("answer\\'value", processedText);
    }

    @Test
    public void escapeLikeTextReplacesSeveralChars()
    {
        String input = "answer'value_test";
        String processedText = SearchUtils.escapeLikeText(input);
        assertEquals("answer\\'value\\_test", processedText);
    }

    @Test
    public void escapeQueryArgumentReplacesQuotationMarkChar()
    {
        String input = "'value'";
        String processedText = SearchUtils.escapeQueryArgument(input);
        assertEquals("''value''", processedText);
    }

    @Test
    public void getMatchFromArrayReturnsFirstMatchIgnoreCase()
    {
        String searchedSubstring = "name";
        String firstMatch = SearchUtils.getMatchFromArray(new String[]{
            "email", "firstName", "age", "lastname"
        }, searchedSubstring);
        assertEquals("firstName", firstMatch);
    }

    @Test
    public void getMatchFromArrayWithNoMatchReturnsNull()
    {
        String searchedSubstring = "name";
        String firstMatch = SearchUtils.getMatchFromArray(new String[]{
            "email", "age"
        }, searchedSubstring);
        assertNull(firstMatch);
    }

    @Test
    public void getMatchFromArrayForNullArrayReturnsNull()
    {
        String searchedSubstring = "name";
        String firstMatch = SearchUtils.getMatchFromArray(null, searchedSubstring);
        assertNull(firstMatch);
    }

    @Test
    public void addMatchMetadata()
    {
        String resourceValue = "MatchFirstNameValueAnswer";
        String question = "What is your firstName?";
        String query = "name";
        String path = "/Forms/f1/a1";
        JsonObjectBuilder parent = Json.createObjectBuilder();
        parent.add(NODE_TYPE, FORM_TYPE);

        JsonObject jsonObject =
                SearchUtils.addMatchMetadata(resourceValue, query, question, parent.build(), false, path);
        assertNotNull(jsonObject);
        assertTrue(jsonObject.containsKey(NODE_TYPE));
        assertEquals(FORM_TYPE, jsonObject.getString(NODE_TYPE));
        assertTrue(jsonObject.containsKey(QUERY_MATCH_PROPERTY));
        JsonObject queryMatchObject = jsonObject.getJsonObject(QUERY_MATCH_PROPERTY);
        assertTrue(queryMatchObject.containsKey(QUESTION_PROPERTY));
        assertEquals(question, queryMatchObject.getString(QUESTION_PROPERTY));
        assertTrue(queryMatchObject.containsKey(IN_NOTES_PROPERTY));
        assertFalse(queryMatchObject.getBoolean(IN_NOTES_PROPERTY));
        assertTrue(queryMatchObject.containsKey(PATH_PROPERTY));
        assertEquals(path, queryMatchObject.getString(PATH_PROPERTY));
        assertTrue(queryMatchObject.containsKey(BEFORE_PROPERTY));
        assertEquals("...tchFirst", queryMatchObject.getString(BEFORE_PROPERTY));
        assertTrue(queryMatchObject.containsKey(AFTER_PROPERTY));
        assertEquals("ValueAns...", queryMatchObject.getString(AFTER_PROPERTY));
        assertTrue(queryMatchObject.containsKey(TEXT_PROPERTY));
        assertEquals("Name", queryMatchObject.getString(TEXT_PROPERTY));
    }
}
