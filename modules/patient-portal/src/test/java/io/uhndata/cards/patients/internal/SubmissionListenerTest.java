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
package io.uhndata.cards.patients.internal;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.version.VersionIterator;
import javax.jcr.version.VersionManager;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Unit tests for {@link SubmissionListener}, against a real repository, since what they check is how marking a form
 * submitted interacts with versioning.
 *
 * @version $Id$
 */
public class SubmissionListenerTest
{
    private static final String ANSWER = "/Forms/visitInformation/surveysSubmitted";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private SubmissionListener listener;

    private Session session;

    private Calendar weekOne;

    private Node questionnaire;

    private Node visit;

    @Before
    public void setUp() throws Exception
    {
        this.session = this.context.resourceResolver().adaptTo(Session.class);
        final Node root = this.session.getRootNode();
        root.addNode("Forms").addNode("visitInformation").addNode("surveysSubmitted");
        final Node questionnaires = root.addNode("Questionnaires");
        final Node question = questionnaires.addNode("Visit information").addNode("surveys_submitted");
        this.questionnaire = questionnaires.addNode("Survey");
        this.questionnaire.addMixin("mix:referenceable");
        this.visit = root.addNode("Subjects").addNode("visit");
        this.visit.addMixin("mix:referenceable");
        this.session.save();

        this.weekOne = Calendar.getInstance();
        this.weekOne.add(Calendar.DATE, -7);

        final FormUtils formUtils = Mockito.mock(FormUtils.class);
        Mockito.when(formUtils.getQuestion(ArgumentMatchers.any(Node.class))).thenReturn(question);
        Mockito.when(formUtils.getValue(ArgumentMatchers.any(Node.class))).thenReturn(1L);
        Mockito.when(formUtils.getSubjectIdentifier(ArgumentMatchers.<Node>any()))
            .thenReturn(this.visit.getIdentifier());

        // The listener closes the session it is given, so it gets one of its own on the same repository
        final ResourceResolverFactory realFactory = this.context.getService(ResourceResolverFactory.class);
        final ResourceResolverFactory factory = Mockito.mock(ResourceResolverFactory.class);
        Mockito.when(factory.getServiceResourceResolver(ArgumentMatchers.any()))
            .thenAnswer(invocation -> openSession(realFactory));

        this.listener = new SubmissionListener();
        setField("resolverFactory", factory);
        setField("rrp", Mockito.mock(ThreadResourceResolverProvider.class));
        setField("formUtils", formUtils);
    }

    @Test
    public void markingACheckedInSurveySubmittedCountsAsModifyingIt() throws Exception
    {
        final Node form = createForm("survey", true, "PATIENT SURVEY");
        final long versions = countVersions(form);

        submit();

        Assert.assertTrue(flagsOf(form).contains("SUBMITTED"));
        Assert.assertTrue(form.getProperty("jcr:lastModified").getDate().after(this.weekOne));
        // Left as it was found, with the submission recorded in a version of its own
        Assert.assertFalse(form.isCheckedOut());
        Assert.assertEquals(versions + 1, countVersions(form));
    }

    @Test
    public void aSurveyBeingEditedIsLeftCheckedOut() throws Exception
    {
        final Node form = createForm("survey", false, "PATIENT SURVEY");
        final long versions = countVersions(form);

        submit();

        Assert.assertTrue(flagsOf(form).contains("SUBMITTED"));
        Assert.assertTrue(form.getProperty("jcr:lastModified").getDate().after(this.weekOne));
        Assert.assertTrue(form.isCheckedOut());
        Assert.assertEquals(versions, countVersions(form));
    }

    @Test
    public void aSurveyAlreadySubmittedIsNotModifiedAgain() throws Exception
    {
        final Node form = createForm("survey", true, "PATIENT SURVEY", "SUBMITTED");
        final long versions = countVersions(form);

        submit();

        Assert.assertEquals(this.weekOne.getTimeInMillis(),
            form.getProperty("jcr:lastModified").getDate().getTimeInMillis());
        Assert.assertEquals(versions, countVersions(form));
    }

    @Test
    public void aFormThatIsNotASurveyIsLeftAlone() throws Exception
    {
        final Node form = createForm("clinical", true, "INCOMPLETE");

        submit();

        Assert.assertFalse(flagsOf(form).contains("SUBMITTED"));
        Assert.assertEquals(this.weekOne.getTimeInMillis(),
            form.getProperty("jcr:lastModified").getDate().getTimeInMillis());
    }

    private Node createForm(final String name, final boolean checkedIn, final String... flags) throws Exception
    {
        final Node form = this.session.getNode("/Forms").addNode(name, "cards:Form");
        form.setProperty("questionnaire", this.questionnaire);
        form.setProperty("subject", this.visit);
        form.setProperty("statusFlags", flags);
        form.setProperty("jcr:lastModified", this.weekOne);
        this.session.save();
        if (checkedIn) {
            versionManager().checkin(form.getPath());
        }
        return form;
    }

    private void submit() throws Exception
    {
        this.listener.onChange(List.of(new ResourceChange(ResourceChange.ChangeType.CHANGED, ANSWER, false)));
        this.session.refresh(false);
    }

    private List<String> flagsOf(final Node form) throws Exception
    {
        return Arrays.stream(form.getProperty("statusFlags").getValues()).map(SubmissionListenerTest::text).toList();
    }

    private static String text(final Value value)
    {
        try {
            return value.getString();
        } catch (final javax.jcr.RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }

    private long countVersions(final Node form) throws Exception
    {
        // The iterator does not know its own size
        final VersionIterator versions = versionManager().getVersionHistory(form.getPath()).getAllVersions();
        long count = 0;
        for (; versions.hasNext(); versions.nextVersion()) {
            ++count;
        }
        return count;
    }

    private VersionManager versionManager() throws Exception
    {
        return this.session.getWorkspace().getVersionManager();
    }

    @SuppressWarnings("deprecation")
    private static ResourceResolver openSession(final ResourceResolverFactory factory) throws Exception
    {
        return factory.getAdministrativeResourceResolver(new HashMap<>());
    }

    private void setField(final String name, final Object value) throws Exception
    {
        final Field field = SubmissionListener.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(this.listener, value);
    }
}
