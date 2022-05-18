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

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Fills in the clinic answer for a given visit information clinic node.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class ClinicLabelProcessor extends SimpleAnswerLabelProcessor implements ResourceJsonProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicLabelProcessor.class);

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if ("/Questionnaires/Visit information/clinic".equals(
                node.getProperty("question").getNode().getPath())) {
                addProperty(node, json, serializeNode);
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
    }

    @Override
    public JsonValue getAnswerLabel(final Node node, final Node question)
    {
        try {
            // Determine which clinic this questionnaire is for
            ResourceResolver resolver = this.resolverFactory.getThreadResourceResolver();

            final Resource mapping = resolver.getResource(node.getProperty("value").getString());
            if (mapping != null) {
                return Json.createValue(
                    mapping.adaptTo(Node.class).getProperty("clinicName").getString());
            } else {
                // No mapping found
                return null;
            }
        } catch (final RepositoryException ex) {
            // Shouldn't be happening
        }
        return null;
    }
}
