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
package io.uhndata.cards.clarity.importer.internal;

import java.util.Collections;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.uhndata.cards.clarity.importer.TestUtils;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Unit tests for {@link UnsubscribedFilter}.
 *
 * @version $Id$
 */
public class UnsubscribedFilterTest
{
    private static final String PATIENT = "/SubjectTypes/Patient";

    private ThreadResourceResolverProvider rrp;

    private ResourceResolver resolver;

    private QuestionnaireUtils questionnaireUtils;

    private FormUtils formUtils;

    private Resource subjectResource;

    private Node subjectNode;

    private Node formNode;

    private Node questionnaireNode;

    private Node unsubscribedQuestion;

    private Node answerNode;

    private UnsubscribedFilter filter;

    @Before
    public void setUp()
    {
        this.rrp = Mockito.mock(ThreadResourceResolverProvider.class);
        this.resolver = Mockito.mock(ResourceResolver.class);
        this.questionnaireUtils = Mockito.mock(QuestionnaireUtils.class);
        this.formUtils = Mockito.mock(FormUtils.class);
        this.subjectResource = Mockito.mock(Resource.class);
        this.subjectNode = Mockito.mock(Node.class);
        this.formNode = Mockito.mock(Node.class);
        this.questionnaireNode = Mockito.mock(Node.class);
        this.unsubscribedQuestion = Mockito.mock(Node.class);
        this.answerNode = Mockito.mock(Node.class);
        final UnsubscribedFilter.Config config = Mockito.mock(UnsubscribedFilter.Config.class);
        Mockito.when(config.enable()).thenReturn(true);
        Mockito.when(config.supportedTypes()).thenReturn(new String[0]);
        this.filter = new UnsubscribedFilter(config);
        TestUtils.setField(this.filter, "rrp", this.rrp);
        TestUtils.setField(this.filter, "questionnaireUtils", this.questionnaireUtils);
        TestUtils.setField(this.filter, "formUtils", this.formUtils);
        Mockito.when(this.rrp.getThreadResourceResolver()).thenReturn(this.resolver);
    }

    /** Point the resolver at a single patient, whose forms are the given nodes. */
    private void givenPatientWithForms(final Node... forms) throws RepositoryException
    {
        Mockito.when(this.resolver.findResources(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Collections.singletonList(this.subjectResource).iterator());
        Mockito.when(this.subjectResource.adaptTo(Node.class)).thenReturn(this.subjectNode);
        final PropertyIterator properties = Mockito.mock(PropertyIterator.class);
        final Boolean[] more = new Boolean[forms.length + 1];
        for (int i = 0; i < forms.length; i++) {
            more[i] = true;
        }
        more[forms.length] = false;
        Mockito.when(properties.hasNext()).thenReturn(more[0], java.util.Arrays.copyOfRange(more, 1, more.length));
        if (forms.length > 0) {
            final Property property = Mockito.mock(Property.class);
            Mockito.when(property.getParent()).thenReturn(forms[0]);
            Mockito.when(properties.nextProperty()).thenReturn(property);
        }
        Mockito.when(this.subjectNode.getReferences("subject")).thenReturn(properties);
    }

    @Test
    public void aRowWithoutAnMrnIsDiscarded()
    {
        Assert.assertNull(this.filter.processEntry(TestUtils.row()));
        Assert.assertNull(this.filter.processEntry(TestUtils.row(PATIENT, "")));
    }

    @Test
    public void aRowForAnUnknownPatientIsKept()
    {
        Mockito.when(this.resolver.findResources(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Collections.<Resource>emptyList().iterator());
        final Map<String, String> input = TestUtils.row(PATIENT, "MRN1");
        Assert.assertSame(input, this.filter.processEntry(input));
    }

    @Test
    public void aPatientWithNoFormsIsKept() throws RepositoryException
    {
        givenPatientWithForms();
        final Map<String, String> input = TestUtils.row(PATIENT, "MRN1");
        Assert.assertSame(input, this.filter.processEntry(input));
    }

    @Test
    public void anUnsubscribedPatientIsDiscarded() throws RepositoryException
    {
        givenPatientWithForms(this.formNode);
        Mockito.when(this.formUtils.getQuestionnaire(this.formNode)).thenReturn(this.questionnaireNode);
        Mockito.when(this.questionnaireNode.getPath()).thenReturn("/Questionnaires/Patient information");
        Mockito.when(this.questionnaireUtils.getQuestion(this.questionnaireNode, "email_unsubscribed"))
            .thenReturn(this.unsubscribedQuestion);
        Mockito.when(this.formUtils.getAnswer(this.formNode, this.unsubscribedQuestion)).thenReturn(this.answerNode);
        Mockito.when(this.formUtils.getValue(this.answerNode)).thenReturn(Long.valueOf(1));

        Assert.assertNull(this.filter.processEntry(TestUtils.row(PATIENT, "MRN1")));
    }

    @Test
    public void aStillSubscribedPatientIsKept() throws RepositoryException
    {
        givenPatientWithForms(this.formNode);
        Mockito.when(this.formUtils.getQuestionnaire(this.formNode)).thenReturn(this.questionnaireNode);
        Mockito.when(this.questionnaireNode.getPath()).thenReturn("/Questionnaires/Patient information");
        Mockito.when(this.questionnaireUtils.getQuestion(this.questionnaireNode, "email_unsubscribed"))
            .thenReturn(this.unsubscribedQuestion);
        Mockito.when(this.formUtils.getAnswer(this.formNode, this.unsubscribedQuestion)).thenReturn(this.answerNode);
        Mockito.when(this.formUtils.getValue(this.answerNode)).thenReturn(Long.valueOf(0));

        final Map<String, String> input = TestUtils.row(PATIENT, "MRN1");
        Assert.assertSame(input, this.filter.processEntry(input));
    }

    @Test
    public void anUnansweredUnsubscribeQuestionKeepsThePatient() throws RepositoryException
    {
        givenPatientWithForms(this.formNode);
        Mockito.when(this.formUtils.getQuestionnaire(this.formNode)).thenReturn(this.questionnaireNode);
        Mockito.when(this.questionnaireNode.getPath()).thenReturn("/Questionnaires/Patient information");
        Mockito.when(this.questionnaireUtils.getQuestion(this.questionnaireNode, "email_unsubscribed"))
            .thenReturn(this.unsubscribedQuestion);
        Mockito.when(this.formUtils.getAnswer(this.formNode, this.unsubscribedQuestion)).thenReturn(null);
        Mockito.when(this.formUtils.getValue((Node) null)).thenReturn(null);

        final Map<String, String> input = TestUtils.row(PATIENT, "MRN1");
        Assert.assertSame(input, this.filter.processEntry(input));
    }

    @Test
    public void formsOfOtherQuestionnairesAreIgnored() throws RepositoryException
    {
        givenPatientWithForms(this.formNode);
        Mockito.when(this.formUtils.getQuestionnaire(this.formNode)).thenReturn(this.questionnaireNode);
        Mockito.when(this.questionnaireNode.getPath()).thenReturn("/Questionnaires/Visit information");

        final Map<String, String> input = TestUtils.row(PATIENT, "MRN1");
        Assert.assertSame(input, this.filter.processEntry(input));
    }

    @Test
    public void aFormWithoutAQuestionnaireIsIgnored() throws RepositoryException
    {
        givenPatientWithForms(this.formNode);
        Mockito.when(this.formUtils.getQuestionnaire(this.formNode)).thenReturn(null);

        final Map<String, String> input = TestUtils.row(PATIENT, "MRN1");
        Assert.assertSame(input, this.filter.processEntry(input));
    }

    /** If the check itself cannot be made, the safe answer is to not email the patient. */
    @Test
    public void aRepositoryFailureDiscardsTheRow() throws RepositoryException
    {
        Mockito.when(this.resolver.findResources(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Collections.singletonList(this.subjectResource).iterator());
        Mockito.when(this.subjectResource.adaptTo(Node.class)).thenReturn(this.subjectNode);
        Mockito.when(this.subjectNode.getReferences("subject")).thenThrow(new RepositoryException("boom"));

        Assert.assertNull(this.filter.processEntry(TestUtils.row(PATIENT, "MRN1")));
    }

    @Test
    public void theFilterRunsEarly()
    {
        Assert.assertEquals(10, this.filter.getPriority());
    }
}
