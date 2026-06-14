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

import React, { useCallback, useEffect, useMemo, useReducer, useContext, useRef, createContext } from "react";

import { deepPurple, orange, blueGrey, blue, purple, green } from '@mui/material/colors';
import { makeStyles } from 'tss-react/mui';

import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";

export const ENTRY_TITLE_FIELD_SPEC = {
  'cards:Questionnaire': {
    titleField: 'title',
    icon: 'assignment',
    color: blueGrey[700]
  },
  'cards:Section': {
    titleField: 'label',
    icon: 'view_stream',
    color: orange[800]
  },
  'cards:Question': {
    titleField: 'text',
    icon: 'Q',
    color: deepPurple[700]
  },
  'cards:Information': {
    // no title field should exclude it from title warning
    icon: 'info',
    color: blue[600]
  },
  'cards:ExternalLink': {
    icon: 'link',
    color: purple[300]
  },
  'cards:Conditional': {
    icon: 'C',
    color: green[800],
  },
  'cards:ConditionalGroup': {
    icon: 'C',
    color: green[800]
  },
}

// Hook for styling nodes using ENTRY_TITLE_FIELD_SPEC
export const useEntryStyles = makeStyles()(theme => {
  const entryColors = Object.fromEntries(Object.entries(ENTRY_TITLE_FIELD_SPEC)
    .map(([type, spec]) => [type, spec.color]));
  const styles = {};
  for (const [type, color] of Object.entries(entryColors)) {
    styles[type] = { color };
  }
  return styles;
});


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
export const jcrGetConditionalTitle = (jcrDataTitle) => {
  let jcrData;
  try {
    jcrData = JSON.parse(jcrDataTitle);
  } catch {
    return jcrDataTitle;
  }

  const stringifyConditionalOperand = (operand) => {
    const { value, isReference } = operand;

    if (typeof value == 'undefined') {
      return undefined;
    }
    if (isReference) {
      return <strong>{`${value[0]}`}</strong>;
    } else {
      return (
        <>
          {value.map((v, i) => { return (<span key={`${v}_${i}`}>&quot;{v}&quot;{i < value.length - 1 ? "," : ""}</span>) })}
        </>
      );
    }
  }

  const stringifyConditional = (jcrData) => {
    const operandA = jcrData['operandA'];
    const operandB = jcrData['operandB'];
    const comparator = jcrData['comparator'];

    const isUnary = typeof operandB.value == 'undefined';

    return (
      <>
        <span>
          {stringifyConditionalOperand(operandA)}
          {' '}
          {comparator}
          {!isUnary && ' '}
          {stringifyConditionalOperand(operandB)}
        </span>
      </>
    )
  }

  const stringifyConditionalGroup = (jcrData) => {
    const requireAll = jcrData['requireAll']
    const conditionalChildren = Object.keys(jcrData).filter(key => ['cards:Conditional', 'cards:ConditionalGroup'].includes(jcrData[key]['jcr:primaryType']))
    return (
      <>
        {conditionalChildren.map((conditionalKey, i) => {
          const primaryType = jcrData[conditionalKey]['jcr:primaryType'];
          return (
            <React.Fragment key={conditionalKey}>
              {i > 0 ? (requireAll ? ' AND ' : ' OR ') : ''}
              {primaryType === 'cards:Conditional'
                ? stringifyConditional(jcrData[conditionalKey])
                : <>({stringifyConditionalGroup(jcrData[conditionalKey])})</>}
            </React.Fragment>
          )
        })}
      </>
    )
  }

  if (['cards:Conditional'].includes(jcrData['jcr:primaryType'])) {
    return stringifyConditional(jcrData);
  } else if (['cards:ConditionalGroup'].includes(jcrData['jcr:primaryType'])) {
    return stringifyConditionalGroup(jcrData);
  }

  console.warn('jcrGetConditionalTitle called with invalid jcrData');
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
  // value should be unique and not null/undefined
  const id = jcrGetUniqueId(jcrData);
  let value = id;
  // title may be null
  let { title } = getTitleField(jcrData);

  const nodeChildren = jcrGetChildren(jcrData).map(jcrChild => jcrGetUniqueId(jcrChild));
  const isRootNode = !nodeParent && rootPath === path;
  return {
    value, //remove
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
  const nodeParent = jcrData['jcr:uuid'];

  for (const child of children) {
    const uniqueNodeId = jcrGetUniqueId(child);
    nodes[uniqueNodeId] = jcrToNode(child, rootPath, nodeParent);
    jcrFindEntries(child, rootPath, nodes);
  }
  return nodes;
}

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
 * Initializes tree using cards:Questionnaire jcr data as root node
 *
 * @param {*} jcrData
 * @returns {Object} - Returns a flat map representing the tree structure.
 */
function initializeRoot(jcrData) {
  let nodes = {};
  const rootPath = jcrData['@path'];
  const rootNode = jcrToNode(jcrData, rootPath, null);
  nodes[jcrData['jcr:uuid']] = rootNode;
  // Entries
  nodes = jcrFindEntries(jcrData, rootPath, nodes);
  return nodes;
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


// React context
/**
 *  The initial state of the tree context
 *
 *  Nodes in tree have structure (see jcrToNode):
 * @typedef {Object} Node
 * @property {string} value - The ID of the node.
 * @property {string} parent - The ID of the parent node.
 * @property {Array} children - The IDs of the children nodes.
 * @property {string} jcrPrimaryType - The jcr primary type of the node.
 * @property {string} name - The name of the node.
 * @property {string} title - The title of the node.
 * @property {string} path - The path of the node.
 * @property {string} relativePath - The relative path of the node.
 *
 */
const initialState = {
  // Data from JCR used to initialize tree
  data: null,
  timestamp: null,
  // Bumped on every (re)load of root data; used as a remount key so views re-seed from fresh data
  revision: 0,
};
// Action Types
const INITIALIZE_ROOT = 'INITIALIZE_ROOT';
const UPDATE_ONDATA = 'UPDATE_ONDATA';
const CLEAR_TREE = 'CLEAR_TREE';
const REMOVE_NODE = 'REMOVE_NODE';
const ACTIONS = [INITIALIZE_ROOT, UPDATE_ONDATA, CLEAR_TREE, REMOVE_NODE];
// For ensuring state is consistent and valid
const stateValidators = {
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
 * Builds the flat node map from the questionnaire JCR data.
 * `nodes` is a pure projection of `data`: this is the single derivation used both to
 * (eventually) expose `nodes` from the provider and to validate the reducer's copy.
 *
 * @param {Object|null} data - The questionnaire (root) JCR data, or null before load.
 * @returns {Object} - The flat node map (empty when data is null).
 */
function buildNodes(data) {
  return data == null ? {} : initializeRoot(data);
}

/**
 * Builds the warnings object from the questionnaire JCR data.
 * Like `nodes`, `warnings` is a pure projection of `data`.
 *
 * @param {Object|null} data - The questionnaire (root) JCR data, or null before load.
 * @returns {Object} - The warnings map (empty when data is null).
 */
function buildWarnings(data) {
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
function setSubtreeAtPath(data, subtree) {
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
function removeSubtreeAtPath(data, path) {
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

// Reducer Function
const treeReducer = (state, action) => {
  if (!action.type || !ACTIONS.includes(action.type)) {
    throw new Error("Invalid action type in treeReducer");
  }
  // Every case below assigns newState, so no need to initialize.
  let newState;
  switch (action.type) {
    case CLEAR_TREE: {
      newState = initialState;
      break;
    }
    case INITIALIZE_ROOT: {
      // jcrData is questionnaire node
      const { jcrData } = action.payload;
      if (jcrData['jcr:primaryType'] !== 'cards:Questionnaire') {
        throw new Error("QuestionnaireTreeContext initialized with a node that is not a questionnaire");
      }
      // Validate the tree structure when fresh data enters from the server. Mutations
      // transform already-valid data with structure-preserving helpers, so they don't
      // need re-validation on every dispatch.
      const validatorNodes = buildNodes(jcrData);
      const initialStateIsValid = Object.values(stateValidators)
        .map(validator => validator(validatorNodes)).reduce((a, b) => a && b, true);
      if (!initialStateIsValid) {
        throw new Error("Invalid questionnaire tree structure");
      }
      newState = { ...state,
        data: jcrData,
        timestamp: jcrData['jcr:lastCheckedOut'],
        revision: state.revision + 1
      };
      break;
    }
    case UPDATE_ONDATA: {
      // jcrData is the edited node (field change) or, on create, the parent node that now
      // contains the new child. Splice it into `data` at its own path — this handles the
      // root, an edited node, and a newly created child alike — then derive `nodes` from
      // the updated `data` so the two cannot diverge.
      const { jcrData } = action.payload;
      const data = setSubtreeAtPath(state.data, jcrData);
      newState = { ...state, data };
      break;
    }
    case REMOVE_NODE: {
      // Remove the deleted node from `data` by its path, then derive `nodes` from it.
      const { path } = action.payload;
      const data = removeSubtreeAtPath(state.data, path);
      newState = { ...state, data };
      break;
    }
    default:
      throw new Error("Invalid action type in treeReducer");
  }
  return newState;
}

export const QuestionnaireTreeContext = createContext();

export const jcrActions = {
  checkIn: (globalLoginDisplay, { id }) => {
    let checkinForm = new FormData();
    checkinForm.set(":operation", "checkin");
    return fetchWithReLogin(globalLoginDisplay, `/Questionnaires/${id}`, {
      method: "POST",
      body: checkinForm
    });
  },
  checkOut: (globalLoginDisplay, { id }) => {
    let checkoutForm = new FormData();
    checkoutForm.set(":operation", "checkout");
    return fetchWithReLogin(globalLoginDisplay, `/Questionnaires/${id}`, {
      method: "POST",
      body: checkoutForm
    });
  },

  fetchQuestionnaireData: (globalLoginDisplay, { id }) => {
    // 'links' is an implicit processor (called by default) so we don't use it to format our 'cards:Links' children
    // '.-links' formats as an object field with 'jcr:primaryType' property
    // '.links' formats as an array
    // 'deep' is an explicit processor
    return fetchWithReLogin(globalLoginDisplay, `/Questionnaires/${id}.-links.deep.json`);
  },

  fetchResourceJSON: (globalLoginDisplay, { data }) => {
    return fetchWithReLogin(globalLoginDisplay, `${data["@path"]}.deep.json`)
  },

  // https://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html#order-1
  // :order index
  reorderEntry: (globalLoginDisplay, { reorderSourceNode, newPosition }) => {
    let reorderForm = new FormData();
    const order = newPosition;
    const path = reorderSourceNode.path;
    reorderForm.set(":order", order);
    reorderForm.set(":http-equiv-accept", "application/json");
    return fetchWithReLogin(globalLoginDisplay, path, {
      method: "POST",
      body: reorderForm
    });
  },

  // https://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html#order-1
  // POST /content/oldParentNode/childNode
  // :operation=move
  // :dest=/content/newParentNode/childNode
  // :order=before siblingNodeName || index
  moveEntryNested: (globalLoginDisplay, { reorderSourceNode, newParentNode, newPosition }) => {
    const reorderForm = new FormData();
    // Use numeric 'order' value to move to specific position
    // If newPosition -1, then move to top of parent's children
    // const order = newPosition === -1 ? 'last' : `${newPosition}`
    const order = newPosition;
    const dest = newParentNode.path.concat('/');
    const path = reorderSourceNode.path;
    reorderForm.set(":operation", "move");
    reorderForm.set(":order", order);
    reorderForm.set(":dest", dest);
    // For the case of reordering within same parent
    // reorderForm.set(":replace", "true");
    reorderForm.set(":http-equiv-accept", "application/json");
    return fetchWithReLogin(globalLoginDisplay, path, {
      method: "POST",
      body: reorderForm,
    });
  },
}

/**
 * Utility function for formatting child index into ordinal string
 *
 */
export function getOrdinalString(number) {
  if (number < 0) return null; // Ensure the number is positive or zero

  number += 1; // Offset the number by 1

  const suffixes = ["th", "st", "nd", "rd"];
  const value = number % 100;

  return number + (suffixes[(value - 20) % 10] || suffixes[value] || suffixes[0]);
}

/**
 * A context provider for a questionnaire tree, which contains the tree structure and actions to manipulate it
 * @param {Object} props the props to pass onwards to the child, generally its children. Has questionnaireId as a required prop.
 * @returns {Object} a React component with the questionnaire tree provider
 */
export function QuestionnaireTreeProvider(props) {
  const { questionnaireId, ...rest } = props;
  if (!questionnaireId) {
    throw new Error("QuestionnaireTreeProvider must be initialized with a questionnaireId");
  }

  const [state, dispatch] = useReducer(treeReducer, initialState);

  // --- Refactor step 1 (single source of truth) ---------------------------------
  // `nodes` and `warnings` are pure projections of `data`, computed here and exposed on
  // the context's `state` (the reducer stores only `data`). Single source of truth:
  // there is no separate copy to keep in sync.
  const derivedNodes = useMemo(() => buildNodes(state.data), [state.data]);
  const derivedWarnings = useMemo(() => buildWarnings(state.data), [state.data]);

  // GlobalLoginContext for fetchWithReLogin
  const globalLoginDisplay = useContext(GlobalLoginContext);

  // Actions
  const fetchRootData = useCallback(() => {
    return jcrActions.fetchQuestionnaireData(globalLoginDisplay, { id: questionnaireId })
      .then(response => response.json())
      .then(data => {
        dispatch({ type: INITIALIZE_ROOT, payload: { jcrData: data } })
      })
  }, [globalLoginDisplay, questionnaireId]);

  const fetchRootNodes = useCallback(() => {
    return jcrActions.fetchQuestionnaireData(globalLoginDisplay, { id: questionnaireId })
      .then(response => response.json())
      .then(data => {
        const nodes = initializeRoot(data);
        return nodes;
      })
  }, [globalLoginDisplay, questionnaireId]);

  const clearTree = useCallback(() => {
    dispatch({ type: CLEAR_TREE });
  }, []);

  const refreshTree = useCallback(() => {
    clearTree();
    return fetchRootData();
  }, [clearTree, fetchRootData]);

  const removeNode = useCallback((path) => {
    dispatch({ type: REMOVE_NODE, payload: { path } });
  }, []);

  const updateNodeData = useCallback((jcrData) => {
    dispatch({ type: UPDATE_ONDATA, payload: { jcrData } });
  }, []);


  const nodesRef = useRef(derivedNodes);
  nodesRef.current = derivedNodes;

  const reorderNode = useCallback((reorderSourceId, newParentId, newPosition, tree = null) => {
    // Add tree as an optional parameter to allow moves in between without resetting tree in context
    // Otherwise uses the latest tree via ref to avoid stale closure
    const nodes = tree || nodesRef.current;
    const reorderValidators = {
      sourceNodeExists: (reorderSourceId, newParentId, newPosition) => {
        return nodes[reorderSourceId] ? true : 'Source node does not exist.';
      },
      newParentExists: (reorderSourceId, newParentId, newPosition) => {
        return nodes[newParentId] ? true : 'New parent node does not exist.';
      },
      notMovingToSelfOrDescendant: (reorderSourceId, newParentId, newPosition) => {
        let currentNodeId = newParentId;

        while (currentNodeId) {
          if (currentNodeId === reorderSourceId) {
            return 'Cannot move to self or a descendant.';
          }
          currentNodeId = nodes[currentNodeId]?.parent; // Move up to the parent
        }
        return true;
      },
      isValidNewPosition: (reorderSourceId, newParentId, newPosition) => {
        // Return error if newPosition is not a valid index
        const newParentNode = nodes[newParentId];
        const childrenCount = newParentNode.children.length;
        if (newPosition < -1 || newPosition > childrenCount) {
          return 'Invalid new position.';
        }
        return true;
      },
      newParentHasChildWithSameName: (reorderSourceId, newParentId, newPosition) => {
        const sourceNode = nodes[reorderSourceId];
        const newParentNode = nodes[newParentId];
        const newParentChildren = newParentNode.children.map(id => nodes[id]?.name);
        const sourceNodeName = sourceNode.name;
        const isNewParent = sourceNode.parent !== newParentId;
        if (isNewParent && newParentChildren.includes(sourceNodeName)) {
          return 'New parent node already has a child with the same name.';
        }
        return true;
      },
    };
    // Check if any validators return strings, indicating an error
    const validation = Object.values(reorderValidators)
      .map(validator => validator(reorderSourceId, newParentId, newPosition));
    const isNotValid = validation.some(result => typeof result === 'string');
    if (isNotValid) {
      const validationErrors = validation.filter(result => typeof result === 'string').join('\n');
      throw new Error("Invalid reorder operation ".concat(validationErrors));
    }

    const reorderSourceParentId = nodes[reorderSourceId].parent;
    const isNewParent = newParentId != reorderSourceParentId;
    const submit = isNewParent
      ? jcrActions.moveEntryNested(globalLoginDisplay,
        { reorderSourceNode: nodes[reorderSourceId], newParentNode: nodes[newParentId], newPosition })
      : jcrActions.reorderEntry(globalLoginDisplay, { reorderSourceNode: nodes[reorderSourceId], newPosition });
    return submit.then(response => {
      if (!response.ok) return Promise.reject(new Error(`Server error: ${response.status}`));
      return response;
    });
  }, [globalLoginDisplay]);


  useEffect(() => {
    return () => {
      clearTree();
    }
  }, []);

  const checkIn = useCallback((id) => {
    jcrActions.checkIn(globalLoginDisplay, { id });
  }, [globalLoginDisplay]);

  const checkOut = useCallback((id) => {
    jcrActions.checkOut(globalLoginDisplay, { id });
  }, [globalLoginDisplay]);

  const actions = useMemo(() => ({
    checkIn,
    checkOut,
    fetchRootData,
    clearTree,
    refreshTree,
    removeNode,
    updateNodeData,
    reorderNode,
    fetchRootNodes,
  }), [checkIn, checkOut, fetchRootData, clearTree, refreshTree, removeNode, updateNodeData, reorderNode,
    fetchRootNodes]);

  const context = useMemo(
    () => ({ state: { ...state, nodes: derivedNodes, warnings: derivedWarnings }, dispatch, actions }),
    [state, derivedNodes, derivedWarnings, actions]
  );
  return (
    <QuestionnaireTreeContext.Provider value={context} {...rest} />
  );
}

/**
 * Obtain context holding the questionnaire structure
 * @returns {Object} a React context of tree state and reducer
 * @throws an error if it is not within a QuestionnaireTreeProvider
 */
export function useQuestionnaireTreeContext() {
  const context = useContext(QuestionnaireTreeContext);

  if (context == undefined) {
    throw new Error("useQuestionnaireTreeContext must be used within a QuestionnaireTreeProvider");
  }

  return context;
}
