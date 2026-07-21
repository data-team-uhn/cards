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
package io.uhndata.cards.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * A simple "hello world" test for {@link LlmEndpoint}.
 * <p>
 * NOTE: this is an early placeholder test and is not expected to pass yet; forwarding to a real
 * OpenAI-compatible server requires either live credentials or a mocked LangChain4j chat model,
 * which will be wired up in a follow-up.
 * </p>
 */
public class LlmEndpointTest
{
    @Test
    public void postForwardsContentAndReturnsReply() throws IOException
    {
        final LlmEndpoint endpoint = new LlmEndpoint();
        endpoint.activate(config("", "", "gpt-4o-mini"));

        final SlingJakartaHttpServletRequest request = Mockito.mock(SlingJakartaHttpServletRequest.class);
        final SlingJakartaHttpServletResponse response = Mockito.mock(SlingJakartaHttpServletResponse.class);

        Mockito.when(request.getReader())
            .thenReturn(new BufferedReader(new StringReader("{\"content\": \"Hello, world\"}")));
        final StringWriter output = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(output));

        endpoint.doPost(request, response);

        Assert.assertTrue("The response should echo back a content field",
            output.toString().contains("content"));
    }

    private static LlmConfigDefinition config(final String baseUrl, final String apiKey, final String model)
    {
        return new LlmConfigDefinition()
        {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType()
            {
                return LlmConfigDefinition.class;
            }

            @Override
            public String openAiBaseUrl()
            {
                return baseUrl;
            }

            @Override
            public String openAiApiKey()
            {
                return apiKey;
            }

            @Override
            public String model()
            {
                return model;
            }
        };
    }
}
