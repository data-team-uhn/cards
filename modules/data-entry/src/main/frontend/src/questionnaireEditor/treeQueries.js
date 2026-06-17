//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//

// Pure read-only queries over the flat node map produced by the tree model (see jcrToNode).
// They take `nodes` and a node id and never mutate; the reorder UIs and the breadcrumb use
// them to locate positions, depths, ancestors and entry-type children.

import { ENTRY_TYPES } from "../questionnaire/FormEntry";

/**
 * Checks if the target parent node is a descendant of the node to be moved.
 *
 * @param {Object} nodes - The flat map representing the tree structure.
 * @param {string} nodeId - The ID of the node to be moved.
 * @param {string} targetParentId - The ID of the target parent node.
 * @returns {boolean} - Returns true if the target parent node is a descendant of the node to be moved, otherwise false.
 */
export function isDescendant(nodes, nodeId, targetParentId) {
  // A recursive helper function to traverse the tree and check descendants
  function checkDescendants(currentId) {
    // If the current node is the target parent, return true
    if (currentId === targetParentId) {
      return true;
    }
    // Get the children of the current node
    const children = nodes[currentId]?.children || [];
    // Recursively check each child node
    for (const childId of children) {
      if (checkDescendants(childId)) {
        return true;
      }
    }
    // If none of the descendants matched, return false
    return false;
  }
  // Start checking from the node to be moved
  return checkDescendants(nodeId);
}

export function findNodePosition(nodes, nodeId) {
  const child = nodes[nodeId];
  const parent = nodes[child.parent];
  return parent.children.indexOf(nodeId);
}

export function findNodeDepth(nodes, nodeId) {
  // Get the depth of the node by traversing up the tree
  let depth = 0;
  let current = nodes[nodeId];
  while (current.parent) {
    depth++;
    current = nodes[current.parent];
  }
  return depth;
}

/**
 * Traverse the tree and return all nodes with matching entryTypes
 *
 * @param {Object} nodes - The flat map representing the tree structure.
 * @param {Array} entryTypes - The list of entry types to search for.
 * @returns {Array} - Returns an array of nodes with matching entryTypes.
 *
 */
export function findTreeEntries(nodes, entryTypes = []) {
  if (!Object.keys(nodes).length) {
    // Return empty array if no nodes
    return [];
  }
  // Recursively traverse the tree and return all nodes with matching entryTypes
  const entries = []
  const traverseTree = (node, entries) => {
    if (entryTypes.includes(node.jcrPrimaryType)) {
      entries.push(node);
    }
    for (const childId of node.children) {
      traverseTree(nodes[childId], entries);
    }
  }
  const rootNode = Object.values(nodes).find(node => node.parent === null);
  if (!rootNode) return entries;
  traverseTree(rootNode, entries);
  return entries;
}

/**
 * Returns the IDs of a parent's children that are reorderable entries (questions, sections,
 * information), in order — filtering out conditionals and conditional groups. Used wherever
 * the reorder UIs need a parent's entry children, e.g. to index, count, or build options.
 *
 * @param {Object} nodes - The flat node map from the tree.
 * @param {string} parentId - The ID of the parent node.
 * @returns {Array.<string>} - The entry child IDs, in order (empty if the parent is unknown).
 */
export function getEntryChildIds(nodes, parentId) {
  return (nodes[parentId]?.children || []).filter(id => ENTRY_TYPES.includes(nodes[id]?.jcrPrimaryType));
}

/**
 * Builds the ancestor path of a node, from the root down to and including the node.
 * @param {Object} nodes - the flat node map from the tree context
 * @param {string} id - the id of the node currently in view
 * @returns {Array.<Object>} ancestor nodes, root first and the node itself last
 */
export function getAncestorPath(nodes, id) {
  const path = [];
  let current = nodes?.[id];
  while (current) {
    path.unshift(current);
    current = current.parent ? nodes[current.parent] : null;
  }
  return path;
}
