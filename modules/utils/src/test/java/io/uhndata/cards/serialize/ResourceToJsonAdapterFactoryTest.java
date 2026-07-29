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
package io.uhndata.cards.serialize;

import java.util.List;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResourceToJsonAdapterFactory}.
 *
 * @version $Id$
 */
public class ResourceToJsonAdapterFactoryTest
{
    private static final String NODE_TYPE = "jcr:primaryType";
    private static final String SUBJECT_TYPE = "cards:Subject";
    private static final String FORM_TYPE = "cards:Form";
    private static final String ANSWER_TYPE = "cards:TextAnswer";
    private static final String TYPE_PROPERTY = "type";
    private static final String QUESTIONNAIRE_PROPERTY = "questionnaire";
    private static final String QUESTION_PROPERTY = "question";
    private static final String SUBJECT_PROPERTY = "subject";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final String TEST_PROCESSOR_NAME = "test";
    private static final String TEST_FORM_PATH = "/Forms/f1";
    private static final String TEST_SUBJECT_PATH = "/Subjects/r1";
    private static final String TEST_QUESTIONNAIRE_PATH = "/Questionnaires/TestSerializableQuestionnaire";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private final ResourceToJsonAdapterFactory factory = new ResourceToJsonAdapterFactory();

    @Test
    public void getAdapterForNullAdaptableObjectReturnsNull()
    {
        assertNull(this.factory.getAdapter(null, JsonObject.class));
    }

    @Test
    public void getAdapterForResourceAdaptableObjectReturnsSerializedAdapter()
    {
        Resource adaptable = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);

        mockWorkingProcessor(processor, adaptable, true, true, TEST_PROCESSOR_NAME);
        setProcessors(List.of(processor));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);
        verifyProcessorMethodsInvocation(processor, 1, 1, 15, 2);
        assertNotNull(adapter);
    }

    @Test
    public void getAdapterForNullNodeReturnsNull()
    {
        Resource adaptable = mock(Resource.class);
        when(adaptable.adaptTo(Node.class)).thenReturn(null);
        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);

        setProcessors(List.of(processor));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);
        assertNull(adapter);
    }

    @Test
    public void getAdapterCatchesRepositoryExceptionReturnsNull() throws RepositoryException
    {
        Resource adaptable = mock(Resource.class);
        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        Node node = mock(Node.class);
        when(adaptable.getResourceMetadata()).thenReturn(resourceMetadata);
        when(adaptable.adaptTo(Node.class)).thenReturn(node);

        when(node.getPath()).thenReturn(TEST_FORM_PATH);
        when(node.getProperties()).thenThrow(new RepositoryException());
        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);

        mockWorkingProcessor(processor, adaptable, true, true, TEST_PROCESSOR_NAME);
        setProcessors(List.of(processor));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);
        assertNull(adapter);
    }

    @Test
    public void getAdapterUsesAllSupportedAndEnabledProcessors()
    {
        Resource adaptable = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        ResourceJsonProcessor processor1 = mock(ResourceJsonProcessor.class);
        mockWorkingProcessor(processor1, adaptable, false, false, TEST_PROCESSOR_NAME + "1");

        ResourceJsonProcessor processor2 = mock(ResourceJsonProcessor.class);
        mockWorkingProcessor(processor2, adaptable, true, true, TEST_PROCESSOR_NAME + "2");

        ResourceJsonProcessor processor3 = mock(ResourceJsonProcessor.class);
        mockWorkingProcessor(processor3, adaptable, false, true, TEST_PROCESSOR_NAME + "3");

        setProcessors(List.of(processor1, processor2, processor3));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);
        verifyProcessorMethodsInvocation(processor1, 0, 0, 0, 0);
        verifyProcessorMethodsInvocation(processor2, 1, 1, 15, 2);
        verifyProcessorMethodsInvocation(processor3, 0, 0, 0, 0);

        assertNotNull(adapter);
    }

    @Test
    public void getAdapterSortsProcessors()
    {
        Resource adaptable = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        TestResourceJsonProcessor processor1 = new TestResourceJsonProcessor(TEST_PROCESSOR_NAME + "1", 1, true, 1, 4);
        TestResourceJsonProcessor processor2 = new TestResourceJsonProcessor(TEST_PROCESSOR_NAME + "2", 2, true, 2, 3);
        TestResourceJsonProcessor processor3 = new TestResourceJsonProcessor(TEST_PROCESSOR_NAME + "3", 3, true, 3, 2);
        TestResourceJsonProcessor processor4 = new TestResourceJsonProcessor(TEST_PROCESSOR_NAME + "4", 4, true, 4, 1);

        setProcessors(List.of(processor1, processor3, processor4, processor2));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);
        assertNotNull(adapter);
        assertEquals(141, adapter.getInt(NODE_TYPE));
    }

    @Test
    public void getAdapterWithNoProcessorsReturnsEmptyJsonObject()
    {
        Resource adaptable = this.context.resourceResolver().getResource(TEST_FORM_PATH);

        setProcessors(List.of());
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);
        assertNotNull(adapter);
        assertTrue(adapter.isEmpty());
    }

    @Test
    public void getAdapterWithNoSupportedProcessorsReturnsEmptyJsonObject()
    {
        Resource adaptable = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);
        mockWorkingProcessor(processor, adaptable, true, false, TEST_PROCESSOR_NAME);

        setProcessors(List.of(processor));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);
        assertNotNull(adapter);
        assertTrue(adapter.isEmpty());
    }

    @Test
    public void getAdapterUsesResourceSelectorsToDisableDefaultProcessors()
    {
        Resource adaptable = mock(Resource.class);
        mockAdaptableResource(adaptable, TEST_FORM_PATH, "-" + TEST_PROCESSOR_NAME);

        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);
        mockWorkingProcessor(processor, adaptable, true, true, TEST_PROCESSOR_NAME);
        setProcessors(List.of(processor));
        this.factory.getAdapter(adaptable, JsonObject.class);

        // There is no enabled processor, so these methods are not invoked
        verifyProcessorMethodsInvocation(processor, 0, 0, 0, 0);
    }

    @Test
    public void getAdapterUsesResourceSelectorsToEnableProcessors()
    {
        Resource adaptable = mock(Resource.class);
        mockAdaptableResource(adaptable, TEST_FORM_PATH, TEST_PROCESSOR_NAME);

        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);
        mockWorkingProcessor(processor, adaptable, false, true, TEST_PROCESSOR_NAME);
        setProcessors(List.of(processor));
        this.factory.getAdapter(adaptable, JsonObject.class);

        // Methods of not enabledByDefault processor are invoked
        verifyProcessorMethodsInvocation(processor, 1, 1, 15, 2);
    }

    @Test
    public void getAdapterWithBothEnableAndDisableSelectorsPrioritizesEnable()
    {
        Resource adaptable = mock(Resource.class);
        mockAdaptableResource(adaptable, TEST_FORM_PATH, TEST_PROCESSOR_NAME + ".-" + TEST_PROCESSOR_NAME);

        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);
        mockWorkingProcessor(processor, adaptable, false, true, TEST_PROCESSOR_NAME);
        setProcessors(List.of(processor));
        this.factory.getAdapter(adaptable, JsonObject.class);

        verifyProcessorMethodsInvocation(processor, 1, 1, 15, 2);
    }

    @Test
    public void getAdapterWithRecursiveReferencesUsesResourcePathForNestedReferences() throws RepositoryException
    {
        Resource adaptable = this.context.resourceResolver().getResource(TEST_FORM_PATH);
        Node adaptableNode = adaptable.adaptTo(Node.class);
        adaptableNode.setProperty("form", adaptableNode.getIdentifier(), Type.REFERENCE.tag());

        ResourceJsonProcessor processor = new FormRecursiveTestResourceJsonProcessor();
        setProcessors(List.of(processor));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);

        assertEquals(TEST_FORM_PATH, adapter.getString("form"));
    }

    @Test
    public void getAdapterOffersTheNodeSerializerInAllLifecyclePhases()
    {
        Resource adaptable = this.context.resourceResolver().getResource(TEST_FORM_PATH);

        setProcessors(List.of(new CallbackTestResourceJsonProcessor()));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);

        assertNotNull(adapter);
        // Serializing a null node through the callback yields null
        assertTrue(adapter.getBoolean("@nullSerializedAsNull"));
        // The callback offered to leave() serialized the answer child
        assertTrue(adapter.containsKey("@leaveChild"));
    }

    @Test
    public void getAdapterWithProcessedAnswerChildNode()
    {
        Resource adaptable = this.context.resourceResolver().getResource(TEST_FORM_PATH);

        ResourceJsonProcessor processor = new ChildNodeTestResourceJsonProcessor();
        setProcessors(List.of(processor));
        JsonObject adapter = this.factory.getAdapter(adaptable, JsonObject.class);

        assertTrue(adapter.containsKey("a1"));
    }

    @Test
    public void getAdapterWithBothInvokedAndNotInvokedDefaultProcessors()
    {
        Resource adaptable = mock(Resource.class);
        mockAdaptableResource(adaptable, TEST_FORM_PATH, TEST_PROCESSOR_NAME);

        ResourceJsonProcessor processor = mock(ResourceJsonProcessor.class);
        mockWorkingProcessor(processor, adaptable, true, true, TEST_PROCESSOR_NAME);

        ResourceJsonProcessor processorNotInvoked = mock(ResourceJsonProcessor.class);
        mockWorkingProcessor(processorNotInvoked, adaptable, true, true, "test_not_invoked");
        setProcessors(List.of(processor, processorNotInvoked));
        this.factory.getAdapter(adaptable, JsonObject.class);

        verifyProcessorMethodsInvocation(processor, 1, 1, 15, 2);
        verifyProcessorMethodsInvocation(processorNotInvoked, 1, 1, 15, 2);
    }

    @Before
    public void setUp() throws RepositoryException
    {
        this.context.build()
                .resource("/Questionnaires", NODE_TYPE, "cards:QuestionnairesHomepage")
                .resource("/SubjectTypes", NODE_TYPE, "cards:SubjectTypesHomepage")
                .resource("/Subjects", NODE_TYPE, "cards:SubjectsHomepage")
                .resource("/Forms", NODE_TYPE, "cards:FormsHomepage")
                .commit();
        this.context.load().json("/Questionnaires.json", TEST_QUESTIONNAIRE_PATH);
        this.context.load().json("/SubjectTypes.json", "/SubjectTypes/Root");
        this.context.build()
                .resource(TEST_SUBJECT_PATH,
                        NODE_TYPE, SUBJECT_TYPE,
                        TYPE_PROPERTY,
                        this.context.resourceResolver().getResource("/SubjectTypes/Root").adaptTo(Node.class),
                        IDENTIFIER_PROPERTY, "Root subject1")
                .commit();
        final Session session = this.context.resourceResolver().adaptTo(Session.class);
        Node subject = session.getNode(TEST_SUBJECT_PATH);
        Node questionnaire = session.getNode(TEST_QUESTIONNAIRE_PATH);
        Node question = session.getNode(TEST_QUESTIONNAIRE_PATH + "/question_1");

        this.context.build()
                .resource(TEST_FORM_PATH,
                        NODE_TYPE, FORM_TYPE,
                        SUBJECT_PROPERTY, subject,
                        QUESTIONNAIRE_PROPERTY, questionnaire)
                .resource(TEST_FORM_PATH + "/a1",
                        NODE_TYPE, ANSWER_TYPE,
                        QUESTION_PROPERTY, question)
                .commit();
    }

    private void mockAdaptableResource(Resource adaptable, String path, String resolutionPathInfo)
    {
        Node adaptableNode = this.context.resourceResolver().getResource(path).adaptTo(Node.class);
        when(adaptable.adaptTo(Node.class)).thenReturn(adaptableNode);

        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        when(adaptable.getResourceMetadata()).thenReturn(resourceMetadata);
        when(resourceMetadata.getResolutionPathInfo()).thenReturn(resolutionPathInfo);
    }

    private void mockWorkingProcessor(ResourceJsonProcessor processor, Resource adaptable, boolean isEnabled,
                                      boolean canProcess, String processorName)
    {
        when(processor.isEnabledByDefault(adaptable)).thenReturn(isEnabled);
        when(processor.getName()).thenReturn(processorName);
        when(processor.canProcess(adaptable)).thenReturn(canProcess);
    }

    private void setProcessors(final List<ResourceJsonProcessor> processors)
    {
        try {
            FieldUtils.writeField(this.factory, "allProcessors", processors, true);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private void verifyProcessorMethodsInvocation(ResourceJsonProcessor processor, int startAndEndProcess,
                                                  int enterAndLeaveProcess, int processProperty, int processChild)
    {
        verify(processor, times(startAndEndProcess)).start(any());
        verify(processor, times(enterAndLeaveProcess)).enter(any(), any(), any());
        verify(processor, times(processProperty)).processProperty(any(), any(), any(), any());
        verify(processor, times(processChild)).processChild(any(), any(), any(), any());
        verify(processor, times(enterAndLeaveProcess)).leave(any(), any(), any());
        verify(processor, times(startAndEndProcess)).end(any());
    }

    private static final class TestResourceJsonProcessor implements ResourceJsonProcessor
    {
        private final String name;
        private final int priority;
        private final boolean isEnabledByDefault;
        private final int factor;
        private final int offset;

        TestResourceJsonProcessor(String name, int priority, boolean isEnabledByDefault, int factor, int offset)
        {
            this.name = name;
            this.priority = priority;
            this.isEnabledByDefault = isEnabledByDefault;
            this.factor = factor;
            this.offset = offset;
        }

        @Override
        public String getName()
        {
            return this.name;
        }

        @Override
        public int getPriority()
        {
            return this.priority;
        }

        @Override
        public boolean isEnabledByDefault(final Resource resource)
        {
            return this.isEnabledByDefault;
        }

        @Override
        public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
                                         final Function<Node, JsonValue> serializeNode)
        {
            return Json.createValue(
                input == null ? this.offset : ((JsonNumber) input).intValue() * this.factor + this.offset);
        }

        @Override
        public String getDescription()
        {
            return "TestResourceJsonProcessor";
        }
    }

    private static final class CallbackTestResourceJsonProcessor implements ResourceJsonProcessor
    {
        @Override
        public String getName()
        {
            return "callback";
        }

        @Override
        public int getPriority()
        {
            return 1;
        }

        @Override
        public boolean isEnabledByDefault(final Resource resource)
        {
            return true;
        }

        @Override
        public void enter(final Node node, final JsonObjectBuilder json,
            final Function<Node, JsonValue> serializeNode)
        {
            json.add("@nullSerializedAsNull", serializeNode.apply(null) == null);
        }

        @Override
        public void leave(final Node node, final JsonObjectBuilder json,
            final Function<Node, JsonValue> serializeNode)
        {
            try {
                if (node.hasNode("a1")) {
                    json.add("@leaveChild", serializeNode.apply(node.getNode("a1")));
                }
            } catch (RepositoryException e) {
                // Should not happen
            }
        }

        @Override
        public String getDescription()
        {
            return "CallbackTestResourceJsonProcessor";
        }
    }

    private static final class FormRecursiveTestResourceJsonProcessor implements ResourceJsonProcessor
    {
        @Override
        public String getName()
        {
            return "formRecursive";
        }

        @Override
        public int getPriority()
        {
            return 1;
        }

        @Override
        public boolean isEnabledByDefault(Resource resource)
        {
            return true;
        }

        @Override
        public boolean canProcess(Resource resource)
        {
            return true;
        }

        @Override
        public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
                                         final Function<Node, JsonValue> serializeNode)
        {
            try {
                if ("form".equals(property.getName())) {
                    Session session = property.getSession();
                    Node form = session.getNodeByIdentifier(property.getString());
                    return serializeNode.apply(form);
                }
            } catch (RepositoryException e) {
                // Should not happen
            }
            return input;
        }

        @Override
        public String getDescription()
        {
            return "FormRecursiveTestResourceJsonProcessor";
        }
    }

    private static final class ChildNodeTestResourceJsonProcessor implements ResourceJsonProcessor
    {
        @Override
        public String getName()
        {
            return "childNode";
        }

        @Override
        public int getPriority()
        {
            return 1;
        }

        @Override
        public boolean isEnabledByDefault(Resource resource)
        {
            return true;
        }

        @Override
        public boolean canProcess(Resource resource)
        {
            return true;
        }

        @Override
        public JsonValue processChild(final Node node, final Node child, final JsonValue input,
                                      final Function<Node, JsonValue> serializeNode)
        {
            try {
                if (child.getName().contains("a")) {
                    Session session = child.getSession();
                    Node answer = session.getNodeByIdentifier(child.getIdentifier());
                    return serializeNode.apply(answer);
                }
            } catch (RepositoryException e) {
                // Should not happen
            }
            return input;
        }

        @Override
        public String getDescription()
        {
            return "ChildNodeTestResourceJsonProcessor";
        }
    }

}
