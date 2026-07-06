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

// The questionnaire tree model: pure functions that turn the questionnaire's deep JCR JSON
// into the flat node map the editor works with, splice/remove subtrees on edit/create/delete,
// flag a node for post-reload highlight, and validate the structure. Nothing here touches
// React — the context layer (QuestionnaireTreeContext) owns the state and calls into this.
//
// Node shape produced by jcrToNode:
// @typedef {Object} Node
// @property {string} value - The ID of the node.
// @property {string} parent - The ID of the parent node.
// @property {Array} children - The IDs of the children nodes.
// @property {string} jcrPrimaryType - The jcr primary type of the node.
// @property {string} name - The name of the node.
// @property {string} title - The title of the node.
// @property {string} path - The path of the node.
// @property {string} relativePath - The relative path of the node.

import { ENTRY_TITLE_FIELD_SPEC } from "./entryDisplay";

/**
 * Gets the title field of a jcr node
 *
 * @param {Object} jcrData - The jcr data object to get the title field from.
 * @returns {string} - Returns the title field of the jcr node.
 */
const getTitleField = (jcrData) => {
  const jcrPrimaryType = jcrData['jcr:primaryType'];

  if (['cards:Conditional', 'cards:ConditionalGroup'].includes(jcrPrimaryType)) {
    const title = JSON.stringify(jcrData);
    return { title };
  }

  const specHasTitleField = Object.prototype.hasOwnProperty.call(ENTRY_TITLE_FIELD_SPEC[jcrPrimaryType] || {}, 'titleField');
  if (!specHasTitleField) {
    return { title: '' };
  }
  const titleField = ENTRY_TITLE_FIELD_SPEC[jcrPrimaryType]?.['titleField'];

  if (!Object.prototype.hasOwnProperty.call(jcrData, titleField)) {
    return { title: jcrData['@name'] };
  } else {
    return { title: jcrData[titleField] };
  }
}

/**
 * Gets the children of a jcr node
 *
 * @param {Object} jcrData - The jcr data object to get children from.
 * @returns {Array} - Returns an array of children nodes.
 */
function jcrGetChildren(jcrData) {
  if (!jcrData || typeof jcrData !== 'object' || !jcrData['jcr:primaryType']) {
    throw new Error("jcrGetChildren called with invalid jcrData");
  }

  // If current jcrData has children we want excluded then filter them out
  if (['cards:Conditional', 'cards:ConditionalGroup', 'cards:Question'].includes(jcrData['jcr:primaryType'])) {
    return [];
  }

  const children = [];
  for (const key in jcrData) {
    const isChild = jcrData[key]?.['jcr:primaryType'] !== undefined;
    if (isChild) {
      children.push(jcrData[key]);
    }
  }
  return children;
}

/**
 * Traverses the jcr data object down
 *
 * @param {*} jcrData
 * @returns
 */
function jcrGetDescendents(jcrData) {
  let children = [];
  let stack = jcrGetChildren(jcrData);
  while (stack.length > 0) {
    const current = stack.pop();
    children.push(current);
    stack = stack.concat(jcrGetChildren(current));
  }
  return children;
}

/**
 * If jcrData has no jcr:uuid, then use the path as the id
 *
 * @param {*} jcrData
 * @returns {string} - Returns the unique id of the jcr data.
 */
const jcrGetUniqueId = (jcrData) => {
  const id = jcrData['jcr:uuid'];
  if (!id) {
    return `${jcrData['@path']}`;
  } else {
    return id;
  }
}

/**
 * Converts a jcr node to a node object
 *
 * @param {Object} jcrData - The jcr data object to convert to a node.
 * @param {string} rootPath - The path of the root node.
 * @param {string} nodeParent - The ID of the parent node.
 * @returns {Object} - Returns a node object.
 *
*/
function jcrToNode(jcrData, rootPath, nodeParent) {
  // Note: root node has itself as null
  const {
    // Note: conditionals + groups + information entries have no jcr:uuid field
    ['jcr:primaryType']: jcrPrimaryType,
    ['@name']: name,
    ['@path']: path,
  } = jcrData;
  // id must be unique and not null/undefined
  const id = jcrGetUniqueId(jcrData);
  // `value` is a duplicate of `id`, kept only because some consumers still read node.value
  // (notably ReorderForm's autocomplete getOptionValue). Prefer `id`; `value` can be dropped
  // once those callers are switched over.
  let value = id;
  // title may be null
  let { title } = getTitleField(jcrData);

  const nodeChildren = jcrGetChildren(jcrData).map(jcrChild => jcrGetUniqueId(jcrChild));
  const isRootNode = !nodeParent && rootPath === path;
  return {
    value,
    id,
    ...isRootNode ? {
      parent: null,
      relativePath: ''
    } : {
      parent: nodeParent,
      relativePath: (() => {
        let relativePath = path?.replace(`/Questionnaires/`, '') || '';
        relativePath = relativePath.substring(0, relativePath.lastIndexOf("/") + 1);
        return relativePath
      })()
    },
    children: nodeChildren,
    jcrPrimaryType,
    name,
    title,
    path,
  };
}

/**
 * Recursively finds all entries in the jcrData object that match the entryTypes
 *
 * @param {Object} jcrData - The jcr data object to search for entries.
 * @param {string} rootPath - The path of the root node.
 * @param {Object} nodes - The flat map representing the tree structure.
 * @returns {Object} - Returns a flat map representing the tree structure.
*/
function jcrFindEntries(jcrData, rootPath = (jcrData['@path'] || ''), nodes) {
  if (!jcrData || typeof jcrData !== 'object' || !jcrData['jcr:primaryType']) {
    throw new Error("jcrFindEntries called with invalid jcrData");
  }
  const children = jcrGetChildren(jcrData);
  const nodeParent = jcrGetUniqueId(jcrData);

  for (const child of children) {
    const uniqueNodeId = jcrGetUniqueId(child);
    nodes[uniqueNodeId] = jcrToNode(child, rootPath, nodeParent);
    jcrFindEntries(child, rootPath, nodes);
  }
  return nodes;
}

/**
 * Initializes tree using cards:Questionnaire jcr data as root node
 *
 * @param {*} jcrData
 * @returns {Object} - Returns a flat map representing the tree structure.
 */
export function initializeRoot(jcrData) {
  let nodes = {};
  const rootPath = jcrData['@path'];
  const rootNode = jcrToNode(jcrData, rootPath, null);
  nodes[jcrData['jcr:uuid']] = rootNode;
  // Entries
  nodes = jcrFindEntries(jcrData, rootPath, nodes);
  return nodes;
}

/**
 * Builds the flat node map from the questionnaire JCR data.
 * `nodes` is a pure projection of `data`: this is the single derivation used both to
 * (eventually) expose `nodes` from the provider and to validate the reducer's copy.
 *
 * @param {Object|null} data - The questionnaire (root) JCR data, or null before load.
 * @returns {Object} - The flat node map (empty when data is null).
 */
export function buildNodes(data) {
  return data == null ? {} : initializeRoot(data);
}

// For ensuring state is consistent and valid
export const stateValidators = {
  hasOneRootNode: (nodes) => {
    if (Object.keys(nodes).length === 0) {
      return true;
    }
    const rootNodes = Object.values(nodes).filter(node => node.parent === null);
    if (rootNodes.length === 1) {
      return true;
    }
    throw new Error("Tree must have exactly one root node");
  },
  hasValidParents: (nodes) => {
    for (const node of Object.values(nodes)) {
      if (node.parent && !nodes[node.parent]) {
        throw new Error(`Node ${node.value} has invalid parent ${node.parent}`);
      }
    }
    return true;
  },
  hasValidChildren: (nodes) => {
    for (const node of Object.values(nodes)) {
      for (const childId of node.children) {
        if (!nodes[childId]) {
          throw new Error(`Node ${node.value} has invalid child ${childId}`);
        }
      }
    }
    return true;
  },
  hasNoDisconnectedNodes: (nodes) => {
    const ids = Object.keys(nodes);
    const disconnected = ids.filter(id => nodes[id].parent !== null && !nodes[nodes[id].parent]);
    if (disconnected.length === 0) {
      return true;
    }
    throw new Error("There are disconnected nodes in the tree.");
  },
}

// For warning users about nodes that are not in the correct format etc
// These warnings should be about the usability of nodes and not validity of the tree
// E.g. a node with no title even though it has a titleField
// E.g. a conditional group with no children
const warningValidators = {
  countEntryTypes: (jcrData) => {
    // Recursively count the types of entries using while loop
    const counts = {};
    let children = jcrGetChildren(jcrData);
    while (children.length > 0) {
      const currentChild = children.pop();
      // Add the current child to the counts
      const type = currentChild['jcr:primaryType'];
      counts[type] = (counts[type] || 0) + 1;
      // Add the children of the current child to the stack
      const currentChildren = jcrGetChildren(currentChild);
      children = children.concat(currentChildren);
    }
    return counts;
  },

  missingTitles: (jcrRootData) => {
    const allDescendents = jcrGetDescendents(jcrRootData);
    // Get all entries expecting a titleField
    const hasTitleField = allDescendents.filter(jcrData => {
      const titleField = ENTRY_TITLE_FIELD_SPEC[jcrData['jcr:primaryType']]?.['titleField'];
      return titleField !== undefined;
    })
    const missingTitleValue = hasTitleField.filter(jcrData => {
      const titleValue = jcrData[ENTRY_TITLE_FIELD_SPEC[jcrData['jcr:primaryType']]?.['titleField']];
      return titleValue === undefined;
    })
    return Object.fromEntries(missingTitleValue.map(jcrData => [jcrData['jcr:uuid'], jcrData]));
  },
}

/**
 * Builds the warnings object from the questionnaire JCR data.
 * Like `nodes`, `warnings` is a pure projection of `data`.
 *
 * @param {Object|null} data - The questionnaire (root) JCR data, or null before load.
 * @returns {Object} - The warnings map (empty when data is null).
 */
export function buildWarnings(data) {
  if (data == null) return {};
  return Object.fromEntries(Object.entries(warningValidators)
    .map(([key, validator]) => [key, validator(data)]));
}

/**
 * Immutably replaces (or inserts) a subtree within the questionnaire `data` tree at the
 * subtree's own JCR path. Used to keep `data` authoritative on edit/create, so that the
 * `nodes`/`warnings` derived from it stay correct without a server reload.
 *
 * JCR children are nested under object keys equal to their node name (= last @path
 * segment), so we locate the target by walking the path segments relative to the root.
 * If the key already exists it is replaced (edit); if not, it is appended to its parent
 * (create — matching Sling's default "new node goes last" placement).
 *
 * @param {Object} data - The questionnaire (root) JCR data.
 * @param {Object} subtree - A node's deep JCR data; placed at subtree['@path'].
 * @returns {Object} - New `data` with the subtree spliced in (untouched branches shared).
 */
export function setSubtreeAtPath(data, subtree) {
  const rootPath = data['@path'];
  const segments = subtree['@path'].slice(rootPath.length).split('/').filter(Boolean);
  // The subtree is the root itself
  if (segments.length === 0) return subtree;
  const splice = (node, [head, ...rest]) => {
    if (rest.length === 0) return { ...node, [head]: subtree };
    // Intermediate node missing (e.g. stale data): leave the tree unchanged
    if (!node[head]) return node;
    return { ...node, [head]: splice(node[head], rest) };
  };
  return splice(data, segments);
}

/**
 * Immutably removes the subtree at the given JCR path from the questionnaire `data`
 * tree. Used to keep `data` authoritative on delete.
 *
 * @param {Object} data - The questionnaire (root) JCR data.
 * @param {string} path - The JCR @path of the node to remove.
 * @returns {Object} - New `data` without that node (untouched branches shared).
 */
export function removeSubtreeAtPath(data, path) {
  const rootPath = data['@path'];
  const segments = path.slice(rootPath.length).split('/').filter(Boolean);
  // Refuse to remove the root
  if (segments.length === 0) return data;
  const splice = (node, [head, ...rest]) => {
    if (!node[head]) return node; // not found — no-op
    if (rest.length === 0) {
      const remaining = { ...node };
      delete remaining[head];
      return remaining;
    }
    return { ...node, [head]: splice(node[head], rest) };
  };
  return splice(data, segments);
}

/**
 * Marks the node at the given JCR path with a transient `doHighlight` flag, so its card
 * highlights and scrolls into view after a reload — the same cue used on create/edit.
 * Mutates `data`; only call on freshly fetched data before it enters state.
 *
 * @param {Object} data - The questionnaire (root) JCR data, freshly fetched.
 * @param {string} path - The JCR @path of the node to flag.
 */
export function flagNodeForHighlight(data, path) {
  const rootPath = data['@path'];
  const segments = path.slice(rootPath.length).split('/').filter(Boolean);
  let node = data;
  for (const segment of segments) {
    node = node?.[segment];
    if (!node) return; // path not found — nothing to flag
  }
  node.doHighlight = true;
}
