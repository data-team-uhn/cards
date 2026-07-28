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

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;

/**
 * A Sling Model that gathers all the metadata to be exposed as {@code <meta>} tags in the HTML source.
 *
 * <p>
 * This collects every property, from every node in the {@code /libs/cards/conf/meta} tree, into a single flat map,
 * where each property name becomes the name of a {@code <meta>} tag and its value the tag's content. Properties in
 * the {@code jcr:} namespace, and blank properties, are skipped.
 * </p>
 * <p>
 * Only that subtree is collected, and deliberately not the whole of {@code /libs/cards/conf}: the configuration tree
 * also holds settings that must not reach the browser, such as the BioPortal and Google API keys. Placing a node under
 * {@code meta} is what declares its properties to be public metadata.
 * </p>
 * <p>
 * As a Sling Model, it can be adapted from any {@code Resource}, in HTL as well as in Java or ESP code. For example, to
 * use from HTL:
 * </p>
 *
 * <p>
 * <code>
 * &lt;sly data-sly-use.config="io.uhndata.cards.scripting.ConfigMetadata"&gt;
 *   &lt;sly data-sly-repeat="${config.properties.entrySet.iterator}"&gt;
 *     &lt;meta name="${item.key}" content="${item.value}"&gt;
 *   &lt;/sly&gt;
 * &lt;/sly&gt;
 * </code>
 * </p>
 *
 * @version $Id$
 * @since 0.9.41
 */
@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class ConfigMetadata
{
    /** The JCR node under which all the config nodes to be collected live. */
    public static final String CONF_ROOT = "/libs/cards/conf/meta";

    @SlingObject
    private ResourceResolver resourceResolver;

    private Map<String, String> properties;

    @PostConstruct
    protected void init()
    {
        this.properties = new LinkedHashMap<>();
        final Resource root = this.resourceResolver.getResource(CONF_ROOT);
        if (root != null) {
            collect(root, this.properties);
        }
    }

    private void collect(final Resource resource, final Map<String, String> out)
    {
        final ValueMap values = resource.getValueMap();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            final String name = entry.getKey();
            if (name.startsWith("jcr:")) {
                continue;
            }
            final String value = values.get(name, String.class);
            if (StringUtils.isNotBlank(value)) {
                out.put(name, value);
            }
        }
        for (Resource child : resource.getChildren()) {
            collect(child, out);
        }
    }

    /**
     * The collected properties, flattened from every node under {@link #CONF_ROOT}.
     *
     * @return a map of property name to property value
     */
    public Map<String, String> getProperties()
    {
        return this.properties;
    }
}
