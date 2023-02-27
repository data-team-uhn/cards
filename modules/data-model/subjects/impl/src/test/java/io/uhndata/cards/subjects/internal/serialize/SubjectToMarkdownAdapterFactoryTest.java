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
package io.uhndata.cards.subjects.internal.serialize;

import javax.jcr.Node;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link SubjectToMarkdownAdapterFactory}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class SubjectToMarkdownAdapterFactoryTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String TYPE_PROPERTY = "type";
    private static final String IDENTIFIER_PROPERTY = "identifier";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private SubjectToTextAdapterFactory subjectToTextAdapterFactory;


    @Test
    public void canProcessForSubjectReturnsTrue()
    {
        Resource subject = this.context.resourceResolver().getResource("/Subjects/r1");
        assertTrue(this.subjectToTextAdapterFactory.canProcess(subject));
    }

    @Test
    public void canProcessForSubjectTypeReturnsFalse()
    {
        Resource subjectType = this.context.resourceResolver().getResource("/SubjectTypes/Root");
        assertFalse(this.subjectToTextAdapterFactory.canProcess(subjectType));
    }

    @Before
    public void setupRepo()
    {
        this.context.build()
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .commit();
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");

        this.context.build()
                .resource("/Subjects/r1",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root")
                .resource("/Subjects/r1/b1",
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root/Branch").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Branch")
                .commit();
    }
}
