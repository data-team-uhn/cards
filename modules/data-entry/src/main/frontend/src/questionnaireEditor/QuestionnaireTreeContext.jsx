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

import { useCallback, useEffect, useMemo, useReducer, useContext, useRef, createContext } from "react";

import { jcrActions } from "./questionnaireApi";
import {
  buildNodes,
  buildWarnings,
  flagNodeForHighlight,
  initializeRoot,
  removeSubtreeAtPath,
  setSubtreeAtPath,
  stateValidators,
} from "./questionnaireTreeModel";
import { getMoveValidity, isValidIndex } from "./reorderModel";
import { GlobalLoginContext } from "../login/ReLoginDialog.js";

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
  const fetchRootData = useCallback((highlightPath = null) => {
    return jcrActions.fetchQuestionnaireData(globalLoginDisplay, { id: questionnaireId })
      .then(response => response.ok ? response.json() : Promise.reject(response))
      .then(data => {
        if (highlightPath) {
          flagNodeForHighlight(data, highlightPath);
        }
        dispatch({ type: INITIALIZE_ROOT, payload: { jcrData: data } })
      })
  }, [globalLoginDisplay, questionnaireId]);

  const fetchRootNodes = useCallback(() => {
    return jcrActions.fetchQuestionnaireData(globalLoginDisplay, { id: questionnaireId })
      .then(response => response.ok ? response.json() : Promise.reject(response))
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
    // Structural legality (source/parent exist, not into self or a descendant, no name clash)
    // is checked first; it also guarantees both nodes exist before the position is range-checked.
    const validity = getMoveValidity(nodes, reorderSourceId, newParentId);
    if (!validity.valid) {
      throw new Error("Invalid reorder operation: ".concat(validity.message));
    }
    if (!isValidIndex(nodes, newParentId, newPosition)) {
      throw new Error("Invalid reorder operation: Invalid new position.");
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
