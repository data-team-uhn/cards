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

package io.uhndata.cards.export.internal.formatters;

import java.time.ZonedDateTime;
import java.util.Collections;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.export.ExportConfigDefinition;
import io.uhndata.cards.export.spi.DataFormatter;

@Component(immediate = true, service = DataFormatter.class)
public class RawFileDataFormatter implements DataFormatter
{
    @Override
    public String getName()
    {
        return "rawFile";
    }

    @Override
    public ResourceRepresentation format(final ResourceIdentifier what,
        final ZonedDateTime startDate, final ZonedDateTime endDate,
        final ExportConfigDefinition config, final ResourceResolver resolver)
        throws RepositoryException
    {
        Node n = resolver.resolve(what.getExportPath()).adaptTo(Node.class);
        if (n.isNodeType("nt:file")) {
            n = n.getNode("jcr:content");
        }
        if (n.isNodeType("nt:resource") && n.hasProperty("jcr:data")) {
            return new ResourceRepresentation(what,
                n.getProperty("jcr:data").getBinary().getStream(),
                n.hasProperty("jcr:mimeType") ? n.getProperty("jcr:mimeType").getString() : "application/octet-stream",
                Collections.emptyList());
        } else {
            return null;
        }
    }
}
