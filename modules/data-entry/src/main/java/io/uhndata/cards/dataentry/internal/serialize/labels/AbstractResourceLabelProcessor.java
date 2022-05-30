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
package io.uhndata.cards.dataentry.internal.serialize.labels;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Base class for processors that fill in the human-readable question answer or answer options for resource questions
 * with the value of the resource node property specified in the question definition by `labelProperty`.
 *
 * @version $Id$
 */
@Component(immediate = true)
public abstract class AbstractResourceLabelProcessor extends SimpleAnswerLabelProcessor implements ResourceJsonProcessor
{
    private static final String PROP_RESOURCE_LABEL = "labelProperty";

    /**
     * Given a resource question definition, find the resource property that should be used as the label for
     * answers of that type, if it exists.
     * @param question the Question node
     * @return the property name as a string, or null if the question definition doesn't have the labelProperty
     *     defined or its value is blank
     */
    protected String getLabelPropertyName(final Node question)
    {
        try {
            if (question != null
                && question.hasProperty(PROP_RESOURCE_LABEL)
                && !StringUtils.isBlank(question.getProperty(PROP_RESOURCE_LABEL).getString())) {
                return question.getProperty(PROP_RESOURCE_LABEL).getString();
            }
        } catch (final RepositoryException ex) {
            // Shouldn't be happening
        }
        return null;
    }

    /**
     * Given a resource path as a String, extract the value indicated by labelPropertyName
     * from the resource node and return it as the label.
     */
    protected String getLabelForResource(final String resourcePath, final ResourceResolver resolver,
        final String labelPropertyName)
    {
        try {
            // Determine the resource which is the answer to this question
            final Resource resource = resolver.getResource(resourcePath);
            if (resource != null) {
                final Node resourceNode = resource.adaptTo(Node.class);
                // Default the label to the resource name if the value for labelProperty is missing
                if (labelPropertyName != null
                    && resourceNode.hasProperty(labelPropertyName)
                    && !StringUtils.isBlank(resourceNode.getProperty(labelPropertyName).getString())) {
                    return resourceNode.getProperty(labelPropertyName).getString();
                } else {
                    return resource.getName();
                }
            } else {
                // No resource found
                return resourcePath;
            }
        } catch (final RepositoryException ex) {
            // Shouldn't be happening
        }
        return resourcePath;
    }
}
