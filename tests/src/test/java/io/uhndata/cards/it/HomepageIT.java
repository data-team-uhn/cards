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
package io.uhndata.cards.it;

import java.util.concurrent.TimeoutException;

import org.apache.sling.testing.clients.SlingClient;
import org.apache.sling.testing.clients.SlingHttpResponse;
import org.apache.sling.testing.clients.util.poller.Polling;
import org.apache.sling.testing.junit.rules.SlingInstanceRule;
import org.apache.sling.testing.junit.rules.SlingRule;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.apache.http.HttpStatus.SC_OK;

public class HomepageIT
{
    @ClassRule
    public static SlingInstanceRule slingInstanceRule = new SlingInstanceRule();

    private static SlingClient client;

    @Rule
    public SlingRule slingMethodRule = new SlingRule();

    @BeforeClass
    public static void waitForStartup() throws TimeoutException, InterruptedException
    {
        client = slingInstanceRule.defaultInstance.getClient(SlingClient.class, "admin", "admin");

        Polling p = new Polling()
        {
            @Override
            public Boolean call() throws Exception
            {
                return HomepageIT.client.doGet("/login").getStatusLine().getStatusCode() == SC_OK;
            }

            @Override
            protected String message()
            {
                return "Server did not correctly start after %1$d ms";
            }
        };

        // Poll every 200 milliseconds for at most 60 seconds
        p.poll(60000, 200);
    }

    @Test
    public void homepageIsDisplayedAtContextRoot() throws Exception
    {
        checkHtmlHomepage("/");
    }

    @Test
    public void homepageIsDisplayedAtSlashContent() throws Exception
    {
        checkHtmlHomepage("/content");
    }

    @Test
    public void homepageIsDisplayedAtSlashContentDotHtml() throws Exception
    {
        checkHtmlHomepage("/content.html");
    }

    private void checkHtmlHomepage(final String location) throws Exception
    {
        SlingHttpResponse response = client.doGet(location, 200);
        assertMimeType(response, "text/html");
        Assert.assertTrue(response.getContent().contains("<title>Welcome to CARDS</title>"));
    }

    private void assertMimeType(final SlingHttpResponse response, final String expectedMimeType) throws Exception
    {
        Assert.assertTrue(response.getFirstHeader("Content-Type").getValue().startsWith(expectedMimeType));
    }
}
