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
import java.util.List;
import java.util.Map;

public class TreeGraph<T>
{
    private T data;
    private String id;
    private String uuid;
    private int level;
    private List<TreeGraph> children = new ArrayList<>();
    private TreeGraph parent;
    private Boolean isSection = false;
    private Boolean isRecurrentSection = false;

    public TreeGraph(String id, String uuid, T data, int level, Boolean isRecurrentSection, Boolean isSection)
    {
        // The unique ID of the node, in our case we use node {@name} attribute
        this.id = id;
        // Node's Section or question uuid
        this.uuid = uuid;
        // Answer data string to be printed in CSV
        this.data = data;
        // Depth level of the node in the tree
        this.level = level;
        this.isRecurrentSection = isRecurrentSection;
        this.isSection = isSection;
    }

    public String getUuid()
    {
        return this.uuid;
    }

    public String getId()
    {
        return this.id;
    }

    public int getLevel()
    {
        return this.level;
    }

    public void addChild(TreeGraph child)
    {
        child.setParent(this);
        this.children.add(child);
    }

    public void addChild(String id, String questionId, T data, int level, Boolean isRecurrentSection, Boolean isSection)
    {
        TreeGraph<T> newChild = new TreeGraph<>(id, questionId, data, level, isRecurrentSection, isSection);
        this.addChild(newChild);
    }

    public List<TreeGraph> getChildren()
    {
        return this.children;
    }

    public T getData()
    {
        return this.data;
    }

    public Boolean isRecurrentSection()
    {
        return this.isRecurrentSection;
    }

    public Boolean isSection()
    {
        return this.isSection;
    }

    private void setParent(TreeGraph parent)
    {
        this.parent = parent;
    }

    @SuppressWarnings({"checkstyle:CyclomaticComplexity", "checkstyle:NPathComplexity"})
    public void dfsRecursive(Map<String, TreeGraph> result)
    {
        if (this.getId() != null) {
            result.put(this.getId(), this);
        }

        // First add answers only
        for (Object child : this.getChildren()) {
            TreeGraph newChild = (TreeGraph) child;
            if (!result.containsKey(newChild.getId()) && !newChild.isSection()) {
                newChild.dfsRecursive(result);
            }
        }
        // Then add non-recurrent sections
        for (Object child : this.getChildren()) {
            TreeGraph newChild = (TreeGraph) child;
            if (!result.containsKey(newChild.getId()) && newChild.isSection()
                    && !newChild.isRecurrentSection()) {
                newChild.dfsRecursive(result);
            }
        }
        // Then add recurrent sections
        for (Object child : this.getChildren()) {
            TreeGraph newChild = (TreeGraph) child;
            if (!result.containsKey(newChild.getId()) && newChild.isRecurrentSection()) {
                newChild.dfsRecursive(result);
            }
        }
    }
}
