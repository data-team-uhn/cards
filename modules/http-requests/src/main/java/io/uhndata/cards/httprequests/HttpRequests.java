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

package io.uhndata.cards.httprequests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public final class HttpRequests
{
    /*
     * How long to wait for a service to accept a connection, and how long to wait for it to answer once it has.
     * Without these, a service that accepts a connection and then goes quiet holds up the caller forever, which for
     * a scheduled job means it never runs again.
     */
    private static final int CONNECT_TIMEOUT_MS = 10000;

    private static final int RESPONSE_TIMEOUT_MS = 30000;

    // Hide the utility class constructor
    private HttpRequests()
    {
    }

    private static String readInputStream(InputStream stream) throws IOException
    {
        // Read the payload exactly as it was sent: trimming every line and joining them without a separator
        // corrupts anything whose whitespace or line structure matters
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            final StringBuilder retVal = new StringBuilder();
            final char[] buffer = new char[4096];
            int read = br.read(buffer);
            while (read != -1) {
                retVal.append(buffer, 0, read);
                read = br.read(buffer);
            }
            return retVal.toString();
        }
    }

    public static HttpResponse doHttpPost(final String url, final String data, final String contentType,
        final String payloadEncoding)
        throws IOException
    {
        final RequestConfig timeouts = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(RESPONSE_TIMEOUT_MS)
            .build();
        final HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(timeouts);
        httpPost.setEntity(new StringEntity(data, payloadEncoding));
        httpPost.setHeader("Content-type", contentType);
        // Both the client and the response must be closed even when reading the response fails, or the connection
        // is leaked
        try (CloseableHttpClient client = HttpClients.createDefault();
            CloseableHttpResponse response = client.execute(httpPost)) {
            final HttpEntity entity = response.getEntity();
            // A response may legitimately have no body at all, e.g. a 204
            final String responseString = entity == null ? "" : readInputStream(entity.getContent());
            final StatusLine statusLine = response.getStatusLine();
            final HttpResponse httpResponse = new HttpResponse(-1, responseString);
            if (statusLine != null) {
                httpResponse.setStatusCode(statusLine.getStatusCode());
            }
            return httpResponse;
        }
    }

    public static HttpResponse doHttpPost(final String url, final String data, final String contentType)
        throws IOException
    {
        return doHttpPost(url, data, contentType, "UTF-8");
    }

    public static String getPostResponse(final String url, final String data, final String contentType)
        throws IOException
    {
        HttpResponse httpResponse = doHttpPost(url, data, contentType);
        return httpResponse.getResponsePayload();
    }

    public static String getPostResponse(final String url, final String data, final String contentType,
        final String payloadEncoding) throws IOException
    {
        HttpResponse httpResponse = doHttpPost(url, data, contentType, payloadEncoding);
        return httpResponse.getResponsePayload();
    }
}
