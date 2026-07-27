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
package io.uhndata.cards.scripting;

import javax.script.Bindings;

import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContentTypeSetter}.
 *
 * @version $Id$
 */
public class ContentTypeSetterTest
{
    private final ContentTypeSetter contentTypeSetter = new ContentTypeSetter();

    private final Bindings bindings = mock(Bindings.class);

    private final SlingJakartaHttpServletResponse response = mock(SlingJakartaHttpServletResponse.class);

    @Before
    public void setUp()
    {
        when(this.bindings.get("jakartaResponse")).thenReturn(this.response);
        this.contentTypeSetter.init(this.bindings);
    }

    @Test
    public void initGetsJakartaResponseFromBindings()
    {
        verify(this.bindings).get("jakartaResponse");
    }

    @Test
    public void htmlSetsHtmlContentType()
    {
        this.contentTypeSetter.html();
        verify(this.response).setContentType("text/html;charset=UTF-8");
    }

    @Test
    public void javascriptSetsJavascriptContentType()
    {
        this.contentTypeSetter.javascript();
        verify(this.response).setContentType("application/javascript;charset=UTF-8");
    }

    @Test
    public void jsonSetsJsonContentType()
    {
        this.contentTypeSetter.json();
        verify(this.response).setContentType("application/json;charset=UTF-8");
    }

    @Test
    public void csvSetsCsvContentType()
    {
        this.contentTypeSetter.csv();
        verify(this.response).setContentType("text/csv;charset=UTF-8");
    }

    @Test
    public void textSetsPlainContentType()
    {
        this.contentTypeSetter.text();
        verify(this.response).setContentType("text/plain;charset=UTF-8");
    }

    @Test
    public void markdownSetsMarkdownContentType()
    {
        this.contentTypeSetter.markdown();
        verify(this.response).setContentType("text/markdown;charset=UTF-8");
    }
}
