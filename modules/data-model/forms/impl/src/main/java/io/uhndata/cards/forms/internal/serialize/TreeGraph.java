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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Helper class used by {@link QuestionnaireToCsvProcessor} for flattening a tree-like form into a grid. A
 * {@code TreeGraph} represents an element from a {@code Form}, either a {@code AnswerSection} or an {@code Answer}.
 *
 * @version $Id$
 */
public class TreeGraph
{
    /**
     * The internal identifier of the questionnaire element answered by this element, either a Section or a Question.
     */
    private final String answeredElementId;

    /** The answer value, if this is an Answer. */
    private final String data;

    /** The list of child nodes, if this is an AnswerSection. */
    private final List<TreeGraph> children = new ArrayList<>();

    /** The list of children, grouped by their level. */
    private final Map<Integer, List<TreeGraph>> childrenByLevel = new LinkedHashMap<>();

    /** Whether this is an answer section ({@code true}) or a simple answer. */
    private final boolean isSection;

    /** The number of rows needed to print this element. */
    private int height;

    /**
     * The row number on which this question will be printed on, if this is a question, or from which its children will
     * be printed on, if this is a section.
     */
    private int startingRow;

    /**
     * Basic constructor receiving all the needed data from a form element. The object is not yet finalized, its
     * children must be {@link TreeGraph#addChild added}, its height must be {@link #computeHeight() computed}, its
     * starting row must be {@link #assignStartingRow(int) assigned}, and then the data can be {@link #tabulateData(Map)
     * extracted into a grid}.
     *
     * @param answeredElementId see {@link #answeredElementId}
     * @param data see {@link #data}
     * @param isSection see {@link #isSection}
     */
    public TreeGraph(final String answeredElementId, final String data, final boolean isSection)
    {
        this.answeredElementId = answeredElementId;
        this.data = data;
        this.isSection = isSection;
    }

    /**
     * Add a child node under this element.
     *
     * @param child the child to add
     */
    public void addChild(final TreeGraph child)
    {
        this.children.add(child);
    }

    /**
     * Compute the height of this element and its children. Also computes {@link #childrenByLevel}. Must be called after
     * {@link #addChild(TreeGraph) all the children have been added}, and before {@link #assignStartingRow(int)} and
     * {@link #tabulateData(Map)}.
     */
    public void computeHeight()
    {
        if (!this.isSection) {
            this.height = 1;
        } else {
            // Compute the height/levels of each child recursively
            this.children.forEach(TreeGraph::computeHeight);
            // Group together elements answering the same section (or question)
            final Map<String, List<TreeGraph>> childrenByAnsweredElement = new HashMap<>();
            this.children.stream().forEach(child -> childrenByAnsweredElement
                .computeIfAbsent(child.answeredElementId, k -> new LinkedList<>()).add(child));
            // Group together elements on each level
            childrenByAnsweredElement.values().stream().forEach(list -> {
                for (int i = 0; i < list.size(); ++i) {
                    this.childrenByLevel.computeIfAbsent(i, index -> new LinkedList<>()).add(list.get(i));
                }
            });
            // Compute the total height of this section: the sum of the maximum height on each level
            this.height = this.childrenByLevel.values().stream().map(
                elementsOnLevel -> elementsOnLevel.stream().map(e -> e.height).max(Integer::compare).orElse(0))
                .reduce(Integer::sum).orElse(0);
        }
    }

    /**
     * Assign the row number for this element, and recursively for all its children. Must be called after
     * {@link #addChild(TreeGraph) all the children have been added} and {@link #computeHeight() the height has been
     * computed}, and before {@link #tabulateData(Map) the data is tabulated}.
     *
     * @param rowNumber the row number on which this element is to be printed, 0-based
     */
    public void assignStartingRow(final int rowNumber)
    {
        this.startingRow = rowNumber;
        int levelRow = rowNumber;
        for (List<TreeGraph> childrenOnLevel : this.childrenByLevel.values()) {
            int levelHeight = 0;
            for (TreeGraph child : childrenOnLevel) {
                child.assignStartingRow(levelRow);
                levelHeight = Math.max(levelHeight, child.height);
            }
            levelRow += levelHeight;
        }
    }

    /**
     * Copy the data from this subtree into a grid. Must be called last, after all the other methods.
     *
     * @param csvData the tabular data being aggregated
     */
    public void tabulateData(final Map<String, Map<Integer, String>> csvData)
    {
        if (this.isSection) {
            this.children.forEach(child -> child.tabulateData(csvData));
        } else if (csvData.containsKey(this.answeredElementId)) {
            // We first check if csvData already knows about this element to avoid adding data for unknown columns
            csvData.computeIfAbsent(this.answeredElementId, key -> new HashMap<>()).put(this.startingRow, this.data);
        }
    }
}
