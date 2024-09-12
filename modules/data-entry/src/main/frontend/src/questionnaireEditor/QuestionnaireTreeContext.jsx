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
import { stripCardsNamespace } from "../questionnaire/QuestionnaireUtilities";
import { ENTRY_TYPES } from "../questionnaire/FormEntry";

// Action Types
const INITIALIZE_ROOT = 'INITIALIZE_ROOT';
const CLEAR_TREE = 'CLEAR_TREE';
const REMOVE_NODE = 'REMOVE_NODE';
// Initial State
const initialState = {
    nodes: {
        // Format: { id: { value: '...', parent: '...', children: [...], jcrPrimaryType: '...' } }
        // Note: root is node with jcrPrimaryType == 'cards:Questionnaire', parent == null
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

function jcrFindEntries(jcrData, entryTypes = ENTRY_TYPES, nodes) {
    if (!jcrData || typeof jcrData !== 'object' || !jcrData['jcr:primaryType']) {
        throw new Error("jcrFindEntries called with invalid jcrData")
        // return [];
    }
    const children = jcrGetChildren(jcrData);
    const nodeParent = jcrData['jcr:uuid'];
    for (const child of children) {
        if (entryTypes.includes(child['jcr:primaryType'])) {
            console.log('child', child)
            const {
                ['jcr:uuid']: id,
                ['jcr:primaryType']: jcrPrimaryType,
                ['@name']: name,
                ['@path']: path
            } = child; 
            const nodeChildren = jcrGetChildren(child).map(jcrChild => jcrChild['jcr:uuid']);
            nodes[id] = {
                value: id,
                parent: nodeParent,
                children: nodeChildren,
                jcrPrimaryType,
                name,
                path
            };
        }
        jcrFindEntries(child, entryTypes, nodes);
    }
    return nodes
}


// export function findParentInTree(nodes, nodeId) {
//     for (const parentId in nodes) {
//         if (nodes.hasOwnProperty(parentId) && nodes[parentId].children.includes(nodeId)) {
//             return parentId;
//         }
//     }
//     console.log(nodes, nodeId)
//     throw Error('Node not found in tree')
//     return null;
// }

// TODO use titleField in types
function initializeRoot(jcrData) {
    let nodes = {}
    // Root node has itself as parent
    const {
        ['jcr:uuid']: rootNodeId,
        ['jcr:primaryType']: jcrPrimaryType,
        ['@name']: name,
        ['@path']: path
    } = jcrData;
    const rootNode = {
        value: rootNodeId,
        parent: null,
        children: jcrGetChildren(jcrData).map(jcrChild => jcrChild['jcr:uuid']),
        jcrPrimaryType,
        name,
        path
    };
    nodes[rootNodeId] = rootNode
    // Entries
    nodes = jcrFindEntries(jcrData, ENTRY_TYPES, nodes);
    console.log('init nodes', nodes)
    return nodes
}

function removeNode(nodeId, nodes) {
    const node = nodes[nodeId];
    // Remove node from parent's children array
    const parentId = node.parent;
    const parent = nodes[parentId];
    console.log('removeNode', node, parent, nodes)
    parent.children = parent.children.filter(childId => childId !== nodeId);
    // Remove children of node from tree
    for (const k of nodes) {
        if (nodes.hasOwnProperty(k) && nodes[k].parent === nodeId) {
            delete nodes[k];
        }
    }
    // Remove node from tree
    delete nodes[id];
    return nodes;
}

// Reducer Function
const treeReducer = (state, action) => {
    // Need some kind of check that state is in the correct format?
    console.log(state, action)
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
            return { nodes: initializeRoot(jcrData) };
        }
        case REMOVE_NODE: {
            const { nodeId } = action.payload;
            const newNodes = removeNode(nodeId, state.nodes) 
            return { nodes: newNodes };
        }

        // case ADD_NODE: {
        //     const { node, parentId } = action.payload;
        //     const nodes = { ...state.nodes };

        //     // Add the new node to the nodes list
        //     nodes[node.value] = node;

        //     // If a parentId is provided, add the node to the parent's children
        //     if (parentId) {
        //         nodes[parentId].children.push(node.value);
        //     }

        //     return { ...state, nodes };
        // }
        // case MOVE_NODE: {
        //     const { jcrId, newParentId } = action.payload;
        //     const nodes = { ...state.nodes };
        //     //   const node = nodes[nodeId];
        //     const oldParentId = Object.keys(nodes).find(id => nodes[id].children.includes(nodeId));

        //     if (oldParentId) {
        //         nodes[oldParentId].children = nodes[oldParentId].children.filter(childId => childId !== nodeId);
        //     }

        //     if (newParentId) {
        //         nodes[newParentId].children.push(nodeId);
        //     }

        //     return { ...state, nodes };
        // }

        // case REORDER_NODE: {
        //     const { nodeId, newIndex } = action.payload;
        //     const nodes = { ...state.nodes };
        //     const parentId = Object.keys(nodes).find(id => nodes[id].children.includes(nodeId));

        //     if (parentId) {
        //         const children = nodes[parentId].children;
        //         const nodeIndex = children.indexOf(nodeId);
        //         if (nodeIndex === -1) return state;
        //         children.splice(nodeIndex, 1);
        //         children.splice(newIndex, 0, nodeId);
        //         return { ...state, nodes };
        //     }

        //     return state;
        // }
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

