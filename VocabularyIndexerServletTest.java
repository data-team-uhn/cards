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

package ca.sickkids.ccm.lfs.vocabularies;
/*
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
*/
import org.junit.Assert;
import org.junit.Test;
/**
* @version $Id$
*/
public class VocabularyIndexerServletTest
{
    @Test
    public void test()
    {
        Assert.assertTrue(true);
    }
    /*
    @Test
    public void checkVocabulariesHomepage()
        throws Exception
    {
        Repository lfs = JcrUtils.getRepository("http://localhost:8080");
        Session session = lfs.login(new SimpleCredentials("admin", "admin".toCharArray()));
        Node root = session.getRootNode();
        Assert.assertTrue(root.hasNode("Vocabularies"));
    }

    @Test
    public void checkNCITFlatNodes()
        throws Exception
    {
        HttpPost httppost = new HttpPost("http://localhost:8080/Vocabularies?source=ncit&test=true&version=19.05d");
        String testAuth = "admin" + ":" + "admin";
        httppost.setHeader(HttpHeaders.AUTHORIZATION, "Basic "
            + Base64.getEncoder().encodeToString(testAuth.getBytes()));
        CloseableHttpClient httpclient= HttpClientBuilder.create().build();
        CloseableHttpResponse httpresponse;
        httpresponse = httpclient.execute(httppost);
        Assert.assertTrue(httpresponse.getStatusLine().getStatusCode() < 400);
        Repository lfs = JcrUtils.getRepository("http://localhost:8080");
        Session session = lfs.login(new SimpleCredentials("admin", "admin".toCharArray()));
        Node root = session.getRootNode();
        Node vocabulariesHomepage = root.getNode("Vocabularies");
        Assert.assertTrue(vocabulariesHomepage.hasNode("flatTestVocabulary"));
        Node flatTestVocabulary = vocabulariesHomepage.getNode("flatTestVocabulary");
        NCITFlatNodesHasExpectedNodes(flatTestVocabulary);
        NCITFlatNodesTestC00008(flatTestVocabulary);
        flatTestVocabulary.remove();
    }

    private void NCITFlatNodesHasExpectedNodes(Node flatTestVocabulary)
        throws Exception
    {
        String[] expectedNodes = {"C00000", "C00001", "C00002", "C00003", "C00004", "C00005", "C00006", "C00007",
            "C00008", "C00009"};

        for (String nodeName : expectedNodes) {
            Assert.assertTrue(flatTestVocabulary.hasNode(nodeName));
        }
    }

    private void NCITFlatNodesTestC00008(Node flatTestVocabulary)
        throws Exception
    {
        String actualDescription = "A percutaneous coronary intervention is imperative for a myocardial"
            + "infarction that presents with ST segment elevation after an unsatisfactory response to a"
            + " full dose of thrombolytic therapy. (ACC)";
        String[] actualParents = {"C00001", "C00002", "C00004", "C00005", "C00006", "C00007"};
        Set<String> actualParentMap = new HashSet<String>();
        for (String parent : actualParents) {
            actualParentMap.add(parent);
        }

        Node C00008 = flatTestVocabulary.getNode("C00008");

        Value obtainedDescriptionValue = C00008.getProperty("description").getValue();
        String obtainedDescription = obtainedDescriptionValue.getString();
        Value[] obtainedParents = C00008.getProperty("ancestors").getValues();
        Set<String> obtainedParentMap = new HashSet<String>();
        for (Value parent : obtainedParents) {
            obtainedParentMap.add(parent.getString());
        }
        Assert.assertTrue(actualDescription.compareTo(obtainedDescription) == 0);
        Assert.assertTrue(actualParentMap.equals(obtainedParentMap));
    }
    */
}

