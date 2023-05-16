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
 * A processor that excludes or includes answer or answer section nodes based on the question/section they refer to, and
 * a list of allowed or excluded question/section paths. The list of included nodes is passed as
 * {@code answerFilter:include=/Questionnaires/Path/To/Question} and the list of excluded nodes is passed as
 * {@code answerFilter:exclude=/Questionnaires/Path/To/Question}, repeated as needed for each targeted node. If there
 * are only include nodes specified, only those nodes and their descendants are included. If there are only exclude
 * nodes specified, all other nodes are included. If both include and exclude nodes are specified, then for a node to
 * end up in the JSON it must be answering a node or a descendant node specified in the include list, but it must not
 * answer a node or a descendant node on the exclude list. It is not possible to include a descendant of an excluded
 * node. The name of this processor is {@code answerFilter} and it is not enabled by default.
 *
 * @version $Id$
 */
@Component
public class FormAnswerFilterProcessor implements ResourceJsonProcessor
{
    @Reference
    private FormUtils formUtils;

    private ThreadLocal<Set<String>> exclude = new ThreadLocal<>();

    private ThreadLocal<Set<String>> include = new ThreadLocal<>();

    @Override
    public boolean canProcess(final Resource resource)
    {
        // This only works on forms
        return resource.isResourceType("cards/Form");
    }

    @Override
    public String getName()
    {
        return "answerFilter";
    }

    @Override
    public int getPriority()
    {
        return 20;
    }

    @Override
    public void start(Resource resource)
    {
        final Set<String> excluded = new HashSet<>();
        final Set<String> included = new HashSet<>();
        // Split by unescaped dots. A backslash escapes a dot, but two backslashes are just one escaped backslash.
        // Match by:
        // - no preceding backslash, i.e. start counting at the first backslash (?<!\)
        // - an even number of backslashes, i.e. any number of groups of two backslashes (?:\\)*
        // - a literal dot \.
        // Each backslash, except the \., is escaped twice, once as a special escape char inside a Java string, and
        // once as a special escape char inside a RegExp. The one before the dot is escaped only once as a special
        // char inside a Java string, since it must retain its escaping meaning in the RegExp.
        // As a URL path segment, the value may also contain URL-escaped characters, which need to be unescaped.
        Arrays.asList(resource.getResourceMetadata().getResolutionPathInfo().split("(?<!\\\\)(?:\\\\\\\\)*\\."))
            .stream()
            .filter(s -> StringUtils.startsWith(s, "answerFilter:exclude="))
            .map(s -> StringUtils.substringAfter(s, "answerFilter:exclude="))
            .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
            .forEach(s -> excluded.add(s.replaceAll("\\\\\\.", ".")));
        this.exclude.set(excluded);

        Arrays.asList(resource.getResourceMetadata().getResolutionPathInfo().split("(?<!\\\\)(?:\\\\\\\\)*\\."))
            .stream()
            .filter(s -> StringUtils.startsWith(s, "answerFilter:include="))
            .map(s -> StringUtils.substringAfter(s, "answerFilter:include="))
            .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
            .forEach(s -> included.add(s.replaceAll("\\\\\\.", ".")));
        this.include.set(included);
    }

    @Override
    public void end(Resource resource)
    {
        this.include.remove();
        this.exclude.remove();
    }

    @Override
    public JsonValue processChild(Node node, Node child, JsonValue input, Function<Node, JsonValue> serializeNode)
    {
        if (input == null) {
            // No serialization coming from other processors, nothing to do
            return null;
        }
        try {
            if (this.formUtils.isAnswer(child)) {
                if (isExcluded(this.formUtils.getQuestion(child).getPath())) {
                    return null;
                }
            } else if (this.formUtils.isAnswerSection(child)
                && isExcluded(this.formUtils.getSection(child).getPath())) {
                return null;
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }
        // All other nodes are left as is
        return input;
    }

    private boolean isExcluded(final String nodePath)
    {
        // A node is excluded if:
        // it is in the list of excluded nodes
        // or include list is contains other paths, but not this node
        return isIn(nodePath, this.exclude.get())
            || !(this.include.get().isEmpty() || isIn(nodePath, this.include.get()));
    }

    private boolean isIn(final String nodePath, final Set<String> list)
    {
        return list.stream().anyMatch(item -> nodePath.equals(item) || nodePath.startsWith(item + "/"));
    }
}
