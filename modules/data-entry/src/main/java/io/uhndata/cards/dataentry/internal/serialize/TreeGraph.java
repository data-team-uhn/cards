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
    private String questionId;
    private int level;
    private List<TreeGraph> children = new ArrayList<>();
    private TreeGraph parent;

    public TreeGraph(String id, String questionId, T data, int level)
    {
        this.id = id;
        this.questionId = questionId;
        this.data = data;
        this.level = level;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getQuestionId()
    {
        return this.questionId;
    }

    public String getId()
    {
        return this.id;
    }

    public void setLevel(int level)
    {
        this.level = level;
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

    public void addChild(String id, String questionId, T data, int level)
    {
        TreeGraph<T> newChild = new TreeGraph<>(id, questionId, data, level);
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

    public void setData(T data)
    {
        this.data = data;
    }

    private void setParent(TreeGraph parent)
    {
        this.parent = parent;
    }

    public TreeGraph getParent()
    {
        return this.parent;
    }

    public boolean hasChildren()
    {
        return this.children.size() > 0;
    }

    public void dfsRecursive(Map<String, TreeGraph> result)
    {
        if (this.getId() != null) {
            result.put(this.getId(), this);
        }

        // First add answers only
        for (Object child : this.getChildren()) {
            TreeGraph newChild = (TreeGraph) child;
            if (!result.containsKey(newChild.getId()) && !newChild.hasChildren()) {
                newChild.dfsRecursive(result);
            }
        }
        // Then add sections
        for (Object child : this.getChildren()) {
            TreeGraph newChild = (TreeGraph) child;
            if (!result.containsKey(newChild.getId()) && newChild.hasChildren()) {
                newChild.dfsRecursive(result);
            }
        }
    }
}
