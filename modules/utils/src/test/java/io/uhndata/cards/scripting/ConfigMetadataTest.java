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
package io.uhndata.cards.scripting;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link ConfigMetadata}.
 *
 * @version $Id$
 */
public class ConfigMetadataTest
{
    private ResourceResolver resolver;

    private ConfigMetadata metadata;

    @Before
    public void setUp() throws Exception
    {
        this.resolver = Mockito.mock(ResourceResolver.class);
        this.metadata = new ConfigMetadata();
        final Field field = ConfigMetadata.class.getDeclaredField("resourceResolver");
        field.setAccessible(true);
        field.set(this.metadata, this.resolver);
    }

    /** Build a resource whose value map holds the given properties, and which has the given children. */
    private static Resource resource(final Map<String, Object> properties, final Resource... children)
    {
        final Resource resource = Mockito.mock(Resource.class);
        final ValueMap values = new ValueMapDecorator(new LinkedHashMap<>(properties));
        Mockito.when(resource.getValueMap()).thenReturn(values);
        Mockito.when(resource.getChildren()).thenReturn(Arrays.asList(children));
        return resource;
    }

    private static Map<String, Object> props(final String... keysAndValues)
    {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private void givenRoot(final Resource root)
    {
        Mockito.when(this.resolver.getResource(ConfigMetadata.CONF_ROOT)).thenReturn(root);
        this.metadata.init();
    }

    @Test
    public void theRootIsTheMetaSubtreeAndNotTheWholeConfigTree()
    {
        // Collecting all of /libs/cards/conf would publish the API keys kept there
        Assert.assertEquals("/libs/cards/conf/meta", ConfigMetadata.CONF_ROOT);
    }

    @Test
    public void propertiesOfEveryChildAreFlattenedIntoOneMap()
    {
        givenRoot(resource(props(),
            resource(props("title", "My App")),
            resource(props("themeColor", "blue", "primaryColor", "#003366"))));

        final Map<String, String> collected = this.metadata.getProperties();
        Assert.assertEquals(3, collected.size());
        Assert.assertEquals("My App", collected.get("title"));
        Assert.assertEquals("blue", collected.get("themeColor"));
        Assert.assertEquals("#003366", collected.get("primaryColor"));
    }

    @Test
    public void propertiesOnTheRootItselfAreCollectedToo()
    {
        givenRoot(resource(props("title", "My App")));
        Assert.assertEquals("My App", this.metadata.getProperties().get("title"));
    }

    @Test
    public void nestedChildrenAreCollectedRecursively()
    {
        givenRoot(resource(props(),
            resource(props("logoDark", "/dark.png"), resource(props("logoLight", "/light.png")))));

        Assert.assertEquals("/dark.png", this.metadata.getProperties().get("logoDark"));
        Assert.assertEquals("/light.png", this.metadata.getProperties().get("logoLight"));
    }

    @Test
    public void jcrPropertiesAreSkipped()
    {
        givenRoot(resource(props(),
            resource(props("jcr:primaryType", "nt:unstructured", "jcr:createdBy", "admin", "title", "My App"))));

        Assert.assertEquals(Collections.singletonMap("title", "My App"), this.metadata.getProperties());
    }

    /** The old template guarded these with data-sly-test, so a blank value must not become an empty meta tag. */
    @Test
    public void blankPropertiesAreSkipped()
    {
        givenRoot(resource(props(),
            resource(props("loginTitle", "", "loginDescription", "   ", "title", "My App"))));

        Assert.assertEquals(Collections.singletonMap("title", "My App"), this.metadata.getProperties());
    }

    @Test
    public void anEmptyTreeGivesAnEmptyMap()
    {
        givenRoot(resource(props()));
        Assert.assertTrue(this.metadata.getProperties().isEmpty());
    }

    @Test
    public void aMissingRootGivesAnEmptyMapRatherThanFailing()
    {
        Mockito.when(this.resolver.getResource(ConfigMetadata.CONF_ROOT)).thenReturn(null);
        this.metadata.init();
        Assert.assertNotNull(this.metadata.getProperties());
        Assert.assertTrue(this.metadata.getProperties().isEmpty());
    }

    /** Insertion order is kept, so the generated meta tags come out in a stable order. */
    @Test
    public void theCollectionOrderIsStable()
    {
        givenRoot(resource(props(),
            resource(props("first", "1")), resource(props("second", "2")), resource(props("third", "3"))));

        Assert.assertEquals(Arrays.asList("first", "second", "third"),
            Arrays.asList(this.metadata.getProperties().keySet().toArray()));
    }
}
