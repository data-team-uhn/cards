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
import { findQuestionnaireEntries } from "../questionnaire/QuestionnaireUtilities";

// Action Types
const MOVE_NODE = 'MOVE_NODE';
const REORDER_NODE = 'REORDER_NODE';
const INITIALIZE_ROOT = 'INITIALIZE_ROOT';
const ADD_NODE = 'ADD_NODE'
const CLEAR_TREE = 'CLEAR_TREE'
// Initial State
const initialState = {
    nodes: {
        // Format: { id: { value: '...', children: [...], jcrPrimaryType: '...' } }
        // Note: always starts with root
        // 'root': { value: 'root', children: ['someJCRid'], jcrPrimaryType: '...' },
    }
};

function jcrGetChildren(jcrData) {
    if (!jcrData || typeof jcrData !== 'object' || !jcrData['jcr:primaryType']) {
        throw new Error("jcrGetChildren called with invalid jcrData")
        // return [];
    }

    const children = [];

    for (const key in jcrData) {
        if (jcrData.hasOwnProperty(key) && typeof jcrData[key] === 'object' && jcrData[key]['jcr:primaryType']) {
            children.push(jcrData[key]);
        }
    }

    return children;
}

function loadEntries(entries, rootNode) {
    // Initialize nodes with the root node
    let nodes = { 'root' : rootNode };

    // Helper function to add children to a node
    const addChildren = (node) => {
        console.log('addChildren node', node)
        const children = node.children.map(childId => {
            const childIndex = entries.findIndex(entry => entry['jcr:uuid'] === childId);
            if (childIndex !== -1) {
                const childEntry = entries.splice(childIndex, 1)[0];
                const { ['jcr:uuid']: id, ['jcr:primaryType']: jcrPrimaryType, ['@name']: name, ['@path']: path } = childEntry;
                nodes[id] = { value: id, children: null, jcrPrimaryType };
                return id;
            }
            return null;
        }).filter(id => id !== null);
        node.children = children;
    };

    // Initial population of root node's children
    addChildren(rootNode);

    // Iterate over nodes to populate children
    let nodesToProcess = Object.values(nodes).filter(node => node.children === null);
    while (nodesToProcess.length > 0) {
        nodesToProcess.forEach(node => {
            const children = jcrGetChildren(entries.find(entry => entry['jcr:uuid'] === node.value)).map(child => child['jcr:uuid']);
            node.children = children;
            addChildren(node);
        });
        nodesToProcess = Object.values(nodes).filter(node => node.children === null);
    }

    return nodes;
}

// Reducer Function
const treeReducer = (state, action) => {
    // Need some kind of check that state is in the correct format?
    switch (action.type) {
        case CLEAR_TREE: {
            return initialState
        }
        case INITIALIZE_ROOT: {
            // jcrData is questionnaire node
            const { jcrData } = action.payload
            if (jcrData['jcr:primaryType'] !== 'cards:Questionnaire') {
                throw new Error("QuestionnaireTreeContext initialized with a node that is not a questionnaire")
            };
            // let children = jcrGetChildren(jcrData).map(jcrChild => jcrChild['@name']);
            let entries = findQuestionnaireEntries(jcrData);
            console.log('entries', entries)
            // const rootNodeId = jcrData['jcr:uuid'];
            const { ['jcr:uuid']: rootNodeId, ['jcr:primaryType']: jcrPrimaryType } = jcrData;
            const rootChildren = jcrGetChildren(jcrData).map(jcrChild => jcrChild['jcr:uuid']);
            const rootNode = {value: rootNodeId, children: rootChildren, jcrPrimaryType};
            const nodes = loadEntries(entries, rootNode);
            // Load the rest of the nodes
            return { nodes };
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
            throw new Error("Invalid action type in treeReducer");
            // return state;
    }
};


// const DEFAULT_STATE = null;

export const QuestionnaireTreeContext = React.createContext();

export function QuestionnaireTreeProvider(props) {
    const [state, dispatch] = useReducer(treeReducer, initialState)
    return (
        <QuestionnaireTreeContext.Provider value={{ state, dispatch }} {...props} />
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

