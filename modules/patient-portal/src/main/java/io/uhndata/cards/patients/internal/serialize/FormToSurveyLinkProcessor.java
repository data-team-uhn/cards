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
package io.uhndata.cards.patients.internal.serialize;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.links.api.Link;
import io.uhndata.cards.links.api.LinkUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * If there is a belongsToSurvey link, include the name of the linked form in the root of the source form.
 * Otherwise, omit the link. The name of this processor is {@code formToSurveyLinks}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class FormToSurveyLinkProcessor implements ResourceJsonProcessor
{
    @Reference
    private LinkUtils linkUtils;

    @Override
    public String getName()
    {
        return "formToSurveyLinks";
    }

    @Override
    public String getDescription()
    {
        return "If a form has a `belongsToSurvey` link, include the name of the linked form in the root of the "
            + "linking form's serialization JSON.";
    }

    @Override
    public int getPriority()
    {
        return 100;
    }

    @Override
    public void leave(final Node node, final JsonObjectBuilder json,
        final Function<Node, JsonValue> serializeNode)
    {
        try {
            if (node.isNodeType(FormUtils.FORM_NODETYPE) && node.hasNode(LinkUtils.LINKS_CONTAINER)) {
                Link link = this.linkUtils.getLinksOfType(node, "belongsToSurvey").stream().findFirst().orElse(null);
                if (link != null) {
                    json.add("@survey", link.getLinkedResource().getName());
                }
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }
}
