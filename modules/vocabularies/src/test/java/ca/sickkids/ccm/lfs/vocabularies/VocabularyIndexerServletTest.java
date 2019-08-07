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

import java.io.FileOutputStream;
import java.io.PrintStream;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
//import javax.jcr.Repository;
import org.apache.sling.testing.mock.jcr.MockJcr;
import org.junit.Assert;
import org.junit.Test;
//import org.junit.Rule;
//import org.apache.sling.testing.mock.sling.ResourceResolverType;
//import org.apache.sling.testing.mock.sling.context.SlingContextImpl;
//import org.apache.sling.testing.mock.sling.junit.SlingContext;
public class VocabularyIndexerServletTest
{
    //@Rule
    //public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Test
    public void testAutocreateVocabulariesHomepage()
        throws Exception
    {
        Session session = MockJcr.newSession();
        //Repository repository = MockJcr.newRepository();
        Node root = session.getRootNode();
        NodeIterator it = root.getNodes();
        FileOutputStream result = new FileOutputStream("./memoized.txt");
        PrintStream ps = new PrintStream(result);

        if (it.hasNext()) {
            ps.println(it.next());
        }

        Assert.assertFalse(root.hasNode("Vocabularies"));
    }

    @Test
    public void testNodes()
    {

    }
}
