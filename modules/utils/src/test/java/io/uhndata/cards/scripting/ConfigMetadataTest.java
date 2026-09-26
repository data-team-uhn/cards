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

    /** A {@code cards:Configuration} node, whose properties are collected. */
    private static Resource config(final Map<String, Object> properties, final Resource... children)
    {
        return resource(true, properties, children);
    }

    /** A node of any other type, such as the one holding an API key, whose properties are not collected. */
    private static Resource other(final Map<String, Object> properties, final Resource... children)
    {
        return resource(false, properties, children);
    }

    private static Resource resource(final boolean isConfiguration, final Map<String, Object> properties,
        final Resource... children)
    {
        final Resource resource = Mockito.mock(Resource.class);
        final ValueMap values = new ValueMapDecorator(new LinkedHashMap<>(properties));
        Mockito.when(resource.isResourceType(ConfigMetadata.CONF_RESOURCE_TYPE)).thenReturn(isConfiguration);
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
    public void theRootIsTheWholeConfigurationTree()
    {
        Assert.assertEquals("/libs/cards/conf", ConfigMetadata.CONF_ROOT);
    }

    @Test
    public void propertiesOfEveryConfigurationNodeAreFlattenedIntoOneMap()
    {
        givenRoot(other(props(),
            config(props("title", "My App")),
            config(props("themeColor", "blue", "primaryColor", "#003366"))));

        final Map<String, String> collected = this.metadata.getProperties();
        Assert.assertEquals(3, collected.size());
        Assert.assertEquals("My App", collected.get("title"));
        Assert.assertEquals("blue", collected.get("themeColor"));
        Assert.assertEquals("#003366", collected.get("primaryColor"));
    }

    /** The API keys live next to the configuration, so the type is all that keeps them off every page. */
    @Test
    public void nodesOfOtherTypesAreSkipped()
    {
        givenRoot(other(props(),
            config(props("title", "My App")),
            other(props("key", "secret-google-key")),
            other(props("key", "secret-bioportal-key"))));

        Assert.assertEquals(Collections.singletonMap("title", "My App"), this.metadata.getProperties());
    }

    @Test
    public void theUntypedRootIsNotCollected()
    {
        givenRoot(other(props("stray", "value"), config(props("title", "My App"))));
        Assert.assertEquals(Collections.singletonMap("title", "My App"), this.metadata.getProperties());
    }

    @Test
    public void configurationNodesAreFoundAtAnyDepth()
    {
        givenRoot(other(props(),
            config(props("logoDark", "/dark.png"), config(props("logoLight", "/light.png"))),
            other(props(), config(props("themeColor", "blue")))));

        Assert.assertEquals("/dark.png", this.metadata.getProperties().get("logoDark"));
        Assert.assertEquals("/light.png", this.metadata.getProperties().get("logoLight"));
        Assert.assertEquals("blue", this.metadata.getProperties().get("themeColor"));
    }

    /** The node type autocreates sling:resourceType and sling:resourceSuperType, which are not configuration. */
    @Test
    public void namespacedPropertiesAreSkipped()
    {
        givenRoot(other(props(),
            config(props("jcr:primaryType", "cards:Configuration", "jcr:createdBy", "admin",
                "sling:resourceType", "cards/Configuration", "sling:resourceSuperType", "cards/Resource",
                "title", "My App"))));

        Assert.assertEquals(Collections.singletonMap("title", "My App"), this.metadata.getProperties());
    }

    /** The old template guarded these with data-sly-test, so a blank value must not become an empty meta tag. */
    @Test
    public void blankPropertiesAreSkipped()
    {
        givenRoot(other(props(),
            config(props("loginTitle", "", "loginDescription", "   ", "title", "My App"))));

        Assert.assertEquals(Collections.singletonMap("title", "My App"), this.metadata.getProperties());
    }

    @Test
    public void anEmptyTreeGivesAnEmptyMap()
    {
        givenRoot(other(props()));
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
        givenRoot(other(props(),
            config(props("first", "1")), config(props("second", "2")), config(props("third", "3"))));

        Assert.assertEquals(Arrays.asList("first", "second", "third"),
            Arrays.asList(this.metadata.getProperties().keySet().toArray()));
    }
}
