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

import React, { useReducer } from "react";

// Action Types
const MOVE_NODE = 'MOVE_NODE';
const REORDER_NODE = 'REORDER_NODE';
const INITIALIZE_ROOT = 'INITIALIZE_ROOT';
const ADD_NODE = 'ADD_NODE'

// Initial State
const initialState = {
    nodes: {
        // Format: { id: { value: '...', children: [...], jcrPrimaryType: '...' } }
        // Note: always starts with root
        // 'root': { value: 'root', children: ['someJCRid'], jcrPrimaryType: '...' },
    }
};

// Reducer Function
const treeReducer = (state, action) => {
    // Need some kind of check that state is in the correct format?
    switch (action.type) {
        case INITIALIZE_ROOT: {
            // const { rootQuestionnaireId } = action.payload
            // const rootNode = {value: rootQuestionnaireId, children: [], jcr}
            const { rootNode } = action.payload
            return { nodes: { root: rootNode } }
        }
        case ADD_NODE: {
            const { node, parentId } = action.payload;
            const nodes = { ...state.nodes };

            // Add the new node to the nodes list
            nodes[node.value] = node;

            // If a parentId is provided, add the node to the parent's children
            if (parentId) {
                nodes[parentId].children.push(node.value);
            }

            return { ...state, nodes };
        }
        case MOVE_NODE: {
            const { nodeId, newParentId } = action.payload;
            const nodes = { ...state.nodes };
            //   const node = nodes[nodeId];
            const oldParentId = Object.keys(nodes).find(id => nodes[id].children.includes(nodeId));

            if (oldParentId) {
                nodes[oldParentId].children = nodes[oldParentId].children.filter(childId => childId !== nodeId);
            }

            if (newParentId) {
                nodes[newParentId].children.push(nodeId);
            }

            return { ...state, nodes };
        }

        case REORDER_NODE: {
            const { nodeId, newIndex } = action.payload;
            const nodes = { ...state.nodes };
            const parentId = Object.keys(nodes).find(id => nodes[id].children.includes(nodeId));

            if (parentId) {
                const children = nodes[parentId].children;
                const nodeIndex = children.indexOf(nodeId);
                if (nodeIndex === -1) return state;
                children.splice(nodeIndex, 1);
                children.splice(newIndex, 0, nodeId);
                return { ...state, nodes };
            }

            return state;
        }

        default:
            return state;
    }
};


// const DEFAULT_STATE = null;

export const QuestionnaireTreeContext = React.createContext();

export function QuestionnaireTreeProvider(props) {
    const [ state , dispatch ] = useReducer(treeReducer, initialState)
    return (
        <QuestionnaireTreeContext.Provider value={{ state, dispatch }} />
    );
}

/**
 * Obtain context holding the questionnaire structure
 * @returns {Object} a React context of tree state and reducer
 * @throws an error if it is not within a QuestionnaireTreeProvider
 */
export function useQuestionnaireTreeContext() {
    const context = React.useContext(QuestionnaireTreeContext);
  
    if (context == undefined) {
      throw new Error("useQuestionnaireTreeContext must be used within a QuestionnaireTreeProvider")
    }
  
    return context;
  }

