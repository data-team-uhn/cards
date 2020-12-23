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
package ca.sickkids.ccm.lfs.dataentry.internal.serialize;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import ca.sickkids.ccm.lfs.serialize.spi.ResourceJsonProcessor;

/**
 * Gets the file name of the file question answer.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class FileLabelProcessor extends SimpleAnswerLabelProcessor implements ResourceJsonProcessor
{
    @Override
    public String getName()
    {
        return "labels";
    }

    @Override
    public int getPriority()
    {
        return 75;
    }

    @Override
    public boolean isEnabledByDefault(Resource resource)
    {
        return true;
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        return resource.isResourceType("lfs/Form");
    }

    @Override
    public JsonValue processChild(final Node node, final Node child, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        try {
            if (child.isNodeType("lfs:FileResourceAnswer") || node.isNodeType("lfs:SomaticVariantsAnswer")) {
                return serializeNode.apply(child);
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType("lfs:FileResourceAnswer") || node.isNodeType("lfs:SomaticVariantsAnswer")) {
                addProperty(node, json, serializeNode);
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    @Override
    public void addProperty(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        final Node question = getQuestionNode(node);
        json.add(PROP_DISPLAYED_VALUE, getAnswerLabel(node, question));
    }

    @Override
    public JsonValue getAnswerLabel(final Node node, final Node question)
    {
        try {
            List<String> names = new ArrayList<>();
            NodeIterator childNodes = node.getNodes("*.*");
            while (childNodes.hasNext()) {
                Node childNode = childNodes.nextNode();
                if (childNode.isNodeType("nt:file")) {
                    names.add(childNode.getName());
                }
            }
            return createJsonArrayFromList(names);
        } catch (final RepositoryException ex) {
            // Really shouldn't happen
        }
        return null;
    }
}
