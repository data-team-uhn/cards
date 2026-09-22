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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import jakarta.json.JsonValue;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;
import io.uhndata.cards.serialize.spi.SelectorDetails;
import io.uhndata.cards.utils.SelectorUtils;

/**
 * A processor that excludes or includes question and section nodes based on a list of allowed or excluded
 * question/section paths. The list of included nodes is passed as
 * {@code questionnaireFilter:include=/Questionnaires/Path/To/Question} and the list of excluded nodes is passed as
 * {@code questionnaireFilter:exclude=/Questionnaires/Path/To/Question}, repeated as needed for each targeted node. If
 * there are only include nodes specified, only those nodes and their descendants are included. If there are only
 * exclude nodes specified, all other nodes are included. If both include and exclude nodes are specified, then for a
 * node to end up in the JSON it must be a node or a descendant node specified in the include list, but it must not be a
 * node or a descendant node on the exclude list. It is not possible to include a descendant of an excluded node. The
 * name of this processor is {@code questionnaireFilter} and it is not enabled by default.
 *
 * @version $Id$
 */
@Component
public class QuestionnaireFilterProcessor implements ResourceJsonProcessor
{
    @Reference
    private QuestionnaireUtils questionnaireUtils;

    private ThreadLocal<Set<String>> exclude = new ThreadLocal<>();

    private ThreadLocal<Set<String>> include = new ThreadLocal<>();

    @Override
    public boolean canProcess(final Resource resource)
    {
        // This only works on forms
        return resource.isResourceType("cards/Questionnaire");
    }

    @Override
    public String getDescription()
    {
        return "Only runs on `Questionnaires`.\n"
            + "Include or exclude questions or sections.\n"
            + "If only `include` options are provided, only those items and any descendants will be included.\n"
            + "If only `exclude` options are provided, all other items will be included.\n"
            + "If both `include` and `exclude` options are provided, then for an item to be included it must be a "
            + "listed as an `include` item or descendent thereof and also not be listed as or a descendant of an "
            + "`exclude` item."
            + "\nIt is not possible to include a descendant of an excluded item.";
    }

    @Override
    public SelectorDetails getDetails()
    {
        return new SelectorDetails(getName(), getDescription(), isEnabledByDefault(null),
            "include", "A path to an included question or section. "
                + "e.g. `questionnaireFilter:include=/Questionnaires/Path/To/Question`",
            "exclude", "A path to an excluded question or section. "
                + "e.g. `questionnaireFilter:exclude=/Questionnaires/Path/To/Question`");
    }

    @Override
    public String getName()
    {
        return "questionnaireFilter";
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
        final String pathInfo = resource.getResourceMetadata().getResolutionPathInfo();
        SelectorUtils.parseOptions("questionnaireFilter", pathInfo).stream()
            .filter(option -> "exclude".equals(option.getKey()))
            .map(Pair::getValue)
            .forEach(excluded::add);
        this.exclude.set(excluded);

        SelectorUtils.parseOptions("questionnaireFilter", pathInfo).stream()
            .filter(option -> "include".equals(option.getKey()))
            .map(Pair::getValue)
            .forEach(included::add);
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
            if ((this.questionnaireUtils.isQuestion(child) || this.questionnaireUtils.isSection(child))
                && isExcluded(child.getPath())) {
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
        // or include list contains other paths, but not this node
        return isIn(nodePath, this.exclude.get(), false)
            || !(this.include.get().isEmpty() || isIn(nodePath, this.include.get(), true));
    }

    private boolean isIn(final String nodePath, final Set<String> list, boolean allowAncestors)
    {
        // For exclude filters, deny:
        // - the filtered node itself
        // - descendants of the filter node
        // For include filters, allow:
        // - the filtered node itself
        // - descendants of the filter node
        // - ancestors of the filtered node, otherwise the node would be skipped since its parent is not serialized
        return list.stream().anyMatch(
            item -> nodePath.equals(item) || nodePath.startsWith(item + '/')
                || (allowAncestors && item.startsWith(nodePath + '/')));
    }
}
