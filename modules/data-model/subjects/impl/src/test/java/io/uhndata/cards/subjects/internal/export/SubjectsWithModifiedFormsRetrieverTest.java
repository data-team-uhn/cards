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
package io.uhndata.cards.subjects.internal.export;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.uhndata.cards.export.ExportConfigDefinition;
import io.uhndata.cards.export.spi.DataPipelineStep.ResourceIdentifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubjectsWithModifiedFormsRetriever}.
 *
 * @version $Id$
 */
public class SubjectsWithModifiedFormsRetrieverTest
{
    private static final String TEST_SUBJECT_PATH = "/Subjects/r1";

    private static final ZonedDateTime START_DATE = ZonedDateTime.of(2023, 1, 15, 0, 0, 0, 0, ZoneId.of("UTC"));

    private static final ZonedDateTime END_DATE = ZonedDateTime.of(2023, 1, 16, 0, 0, 0, 0, ZoneId.of("UTC"));

    private final SubjectsWithModifiedFormsRetriever retriever = new SubjectsWithModifiedFormsRetriever();

    private final ExportConfigDefinition config = mock(ExportConfigDefinition.class);

    private final ResourceResolver resolver = mock(ResourceResolver.class);

    @Test
    public void getNameIsNotEmpty()
    {
        assertFalse(this.retriever.getName().isEmpty());
    }

    @Test
    public void getResourcesToExportReturnsSubjectsWithModifiedForms() throws RepositoryException
    {
        when(this.config.retrieverParameters()).thenReturn(new String[] { "selectors=.labels" });
        Resource subject = mockSubject(TEST_SUBJECT_PATH, "AB123456");
        when(this.resolver.findResources(anyString(), eq("JCR-SQL2")))
            .thenReturn(List.of(subject).iterator());

        List<ResourceIdentifier> results = this.retriever.getResourcesToExport(this.config, START_DATE, END_DATE,
            this.resolver);

        assertEquals(1, results.size());
        assertEquals(TEST_SUBJECT_PATH, results.get(0).getPath());
        assertEquals("AB123456", results.get(0).getIdentifier());
        assertTrue(results.get(0).getExportPath().startsWith(TEST_SUBJECT_PATH + ".labels.data.deep"));
        assertTrue(results.get(0).getExportPath().contains("dataFilter:modifiedAfter="));
        assertTrue(results.get(0).getExportPath().contains("dataFilter:modifiedBefore="));

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(this.resolver).findResources(query.capture(), eq("JCR-SQL2"));
        assertTrue(query.getValue().contains("form.[jcr:lastModified] >= '2023-01-15T00:00:00.000+00:00'"));
        assertTrue(query.getValue().contains("form.[jcr:lastModified] < '2023-01-16T00:00:00.000+00:00'"));
    }

    @Test
    public void getResourcesToExportWithoutEndDateOmitsUpperBound() throws RepositoryException
    {
        when(this.config.retrieverParameters()).thenReturn(new String[0]);
        Resource subject = mockSubject(TEST_SUBJECT_PATH, "AB123456");
        when(this.resolver.findResources(anyString(), eq("JCR-SQL2")))
            .thenReturn(List.of(subject).iterator());

        List<ResourceIdentifier> results = this.retriever.getResourcesToExport(this.config, START_DATE, null,
            this.resolver);

        assertEquals(1, results.size());
        assertFalse(results.get(0).getExportPath().contains("dataFilter:modifiedBefore="));

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(this.resolver).findResources(query.capture(), eq("JCR-SQL2"));
        assertFalse(query.getValue().contains("form.[jcr:lastModified] <"));
    }

    @Test
    public void getResourcesToExportReportsEachSubjectOnce() throws RepositoryException
    {
        when(this.config.retrieverParameters()).thenReturn(new String[0]);
        Resource subject = mockSubject(TEST_SUBJECT_PATH, "AB123456");
        Resource sameSubject = mockSubject(TEST_SUBJECT_PATH, "AB123456");
        when(this.resolver.findResources(anyString(), eq("JCR-SQL2")))
            .thenReturn(List.of(subject, sameSubject).iterator());

        List<ResourceIdentifier> results = this.retriever.getResourcesToExport(this.config, START_DATE, END_DATE,
            this.resolver);

        assertEquals(1, results.size());
    }

    private Resource mockSubject(final String path, final String identifier)
    {
        Resource subject = mock(Resource.class);
        when(subject.getPath()).thenReturn(path);
        ValueMap valueMap = mock(ValueMap.class);
        when(subject.getValueMap()).thenReturn(valueMap);
        when(valueMap.get("identifier", String.class)).thenReturn(identifier);
        return subject;
    }
}
