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
package io.uhndata.cards.dataentry.internal.serialize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TreeGraph
{
    private final String data;

    private final String elementId;

    private final String answeredElementId;

    private final List<TreeGraph> children = new ArrayList<>();

    private final Map<Integer, List<TreeGraph>> childrenByLevel = new LinkedHashMap<>();

    private final boolean isSection;

    private final boolean isRecurrentSection;

    private int childrenLevels;

    private int height;

    private int startingRow;

    public TreeGraph(final String elementId, final String answeredElementId, final String data,
        final boolean isRecurrentSection,
        final boolean isSection)
    {
        // The unique ID of the node, in our case we use node {@name} attribute
        this.elementId = elementId;
        // Node's Section or question uuid
        this.answeredElementId = answeredElementId;
        // Answer data string to be printed in CSV
        this.data = data;
        this.isRecurrentSection = isRecurrentSection;
        this.isSection = isSection;
    }

    public String getAnsweredElementId()
    {
        return this.answeredElementId;
    }

    public String getElementId()
    {
        return this.elementId;
    }

    public int getLevels()
    {
        return this.childrenLevels;
    }

    public int getHeight()
    {
        return this.height;
    }

    public void addChild(final TreeGraph child)
    {
        this.children.add(child);
    }

    public void addChild(final String elementId, final String answeredElementId, final String data,
        final boolean isRecurrentSection,
        final boolean isSection)
    {
        final TreeGraph newChild = new TreeGraph(elementId, answeredElementId, data, isRecurrentSection, isSection);
        this.addChild(newChild);
    }

    public List<TreeGraph> getChildren()
    {
        return this.children;
    }

    public String getData()
    {
        return this.data;
    }

    public boolean isRecurrentSection()
    {
        return this.isRecurrentSection;
    }

    public boolean isSection()
    {
        return this.isSection;
    }

    public void computeHeight()
    {
        if (!this.isSection) {
            this.height = 1;
            this.childrenLevels = 0;
        } else {
            // Compute the height/levels of each child recursively
            this.children.forEach(TreeGraph::computeHeight);
            // Group together elements answering the same section (or question)
            final Map<String, List<TreeGraph>> childrenByAnsweredElement = new HashMap<>();
            this.children.stream().forEach(child -> childrenByAnsweredElement
                .computeIfAbsent(child.getAnsweredElementId(), k -> new LinkedList<>()).add(child));
            // Compute how many levels are needed: the maximum number of repeats of the same element
            this.childrenLevels =
                childrenByAnsweredElement.values().stream().map(List::size).max(Integer::compare).orElse(0);
            // Group together elements on each level
            childrenByAnsweredElement.values().stream().forEach(list -> {
                for (int i = 0; i < list.size(); ++i) {
                    this.childrenByLevel.computeIfAbsent(i, index -> new LinkedList<>()).add(list.get(i));
                }
            });
            // Compute the total height of this section: the sum of the maximum height on each level
            this.height = this.childrenByLevel.values().stream().map(
                elementsOnLevel -> elementsOnLevel.stream().map(TreeGraph::getHeight).max(Integer::compare).orElse(0))
                .reduce(Integer::sum).orElse(0);
        }
    }

    public void assignStartingRow(final int rowNumber)
    {
        this.startingRow = rowNumber;
        int levelRow = rowNumber;
        for (int level = 0; level < this.childrenLevels; ++level) {
            int levelHeight = 0;
            for (TreeGraph child : this.childrenByLevel.get(level)) {
                child.assignStartingRow(levelRow);
                levelHeight = Math.max(levelHeight, child.getHeight());
            }
            levelRow += levelHeight;
        }
    }

    public void tabulateData(final Map<String, Map<Integer, String>> csvData)
    {
        if (this.isSection) {
            this.children.forEach(child -> child.tabulateData(csvData));
        } else {
            csvData.computeIfAbsent(this.answeredElementId, key -> new HashMap<>()).put(this.startingRow, this.data);
        }
    }
}
