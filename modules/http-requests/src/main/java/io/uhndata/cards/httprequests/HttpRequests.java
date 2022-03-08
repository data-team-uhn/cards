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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public final class HttpRequests
{
    // Hide the utility class constructor
    private HttpRequests()
    {
    }

    private static String readInputStream(InputStream stream) throws IOException
    {
        final BufferedReader br = new BufferedReader(new InputStreamReader(stream, "utf-8"));
        String responseLine = null;
        final StringBuilder retVal = new StringBuilder();
        while ((responseLine = br.readLine()) != null) {
            retVal.append(responseLine.trim());
        }
        return retVal.toString();
    }

    public static String getPostResponse(final String url, final String data, final String contentType)
        throws IOException
    {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        StringEntity entity = new StringEntity(data);
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-type", contentType);
        CloseableHttpResponse response = client.execute(httpPost);
        String responseString = readInputStream(response.getEntity().getContent());
        client.close();
        return responseString;
    }
}
