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
package io.uhndata.cards.forms.internal.serialize;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.JsonValue;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * A processor that excludes the files content from the export. By default it excludes all files, but it can be tailored
 * to exclude only certain files by using selectors in the format
 * {@code .excludeFiles:exclude=/Questionnaires/Path/To/Question}. The name of this processor is {@code excludeFiles}
 * and it is enabled by default.
 *
 * @version $Id$
 */
@Component
public class ExcludeFilesProcessor implements ResourceJsonProcessor
{
    @Reference
    private FormUtils formUtils;

    private ThreadLocal<Set<String>> exclude = new ThreadLocal<>();

    @Override
    public boolean canProcess(final Resource resource)
    {
        // This works on all resource types
        return true;
    }

    @Override
    public String getName()
    {
        return "excludeFiles";
    }

    @Override
    public boolean isEnabledByDefault(Resource resource)
    {
        return true;
    }

    @Override
    public int getPriority()
    {
        return 0;
    }

    @Override
    public void start(Resource resource)
    {
        final Set<String> excluded = new HashSet<>();
        // Split by unescaped dots. A backslash escapes a dot, but two backslashes are just one escaped backslash.
        // Match by:
        // - no preceding backslash, i.e. start counting at the first backslash (?<!\)
        // - an even number of backslashes, i.e. any number of groups of two backslashes (?:\\)*
        // - a literal dot \.
        // Each backslash, except the \., is escaped twice, once as a special escape char inside a Java string, and
        // once as a special escape char inside a RegExp. The one before the dot is escaped only once as a special
        // char inside a Java string, since it must retain its escaping meaning in the RegExp.
        // As a URL path segment, the value may also contain URL-escaped characters, which need to be unescaped.
        if (resource.getResourceMetadata().getResolutionPathInfo() != null) {
            Arrays.asList(resource.getResourceMetadata().getResolutionPathInfo().split("(?<!\\\\)(?:\\\\\\\\)*\\."))
                .stream()
                .filter(s -> StringUtils.startsWith(s, "excludeFiles:exclude="))
                .map(s -> StringUtils.substringAfter(s, "excludeFiles:exclude="))
                .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
                .forEach(s -> excluded.add(s.replaceAll("\\\\\\.", ".")));
        }
        this.exclude.set(excluded);
    }

    @Override
    public void end(Resource resource)
    {
        this.exclude.remove();
    }

    @Override
    public JsonValue processChild(Node node, Node child, JsonValue input, Function<Node, JsonValue> serializeNode)
    {
        try {
            if (child.isNodeType("nt:file")
                && this.formUtils.isAnswer(node)
                && (this.exclude.get().isEmpty()
                    || this.exclude.get().contains(this.formUtils.getQuestion(node).getPath()))) {
                return JsonValue.NULL;
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
        return null;
    }
}
