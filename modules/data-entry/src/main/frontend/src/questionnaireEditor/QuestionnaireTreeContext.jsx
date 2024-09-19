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
import { ENTRY_TYPES } from "../questionnaire/FormEntry";

/**
 * Gets the title field of a jcr node
 * 
 * @param {Object} jcrData - The jcr data object to get the title field from.
 * @returns {string} - Returns the title field of the jcr node.
 */
// From Questionnaire.jsx Entry defaultProps
// (importing component to get defaultProp.titleField value is circular dependency)
const getTitleField = (jcrData) => {
    const possibleTitleFields = {
        'cards:Questionnaire': 'title',
        'cards:Section': 'label',
        'cards:Question': 'text',
    }
    let titleField = possibleTitleFields[jcrData['jcr:primaryType']] ?? '@name';
    const title = jcrData[titleField] ?? jcrData['@name'];
    return title;
}

// Action Types
const INITIALIZE_ROOT = 'INITIALIZE_ROOT';
const CLEAR_TREE = 'CLEAR_TREE';
const REMOVE_NODE = 'REMOVE_NODE';

/**
 *  The initial state of the tree context
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
    nodes: {
        // Format: { id: { value: '...', parent: '...', children: [...], jcrPrimaryType: '...' } }
        // Note: root is node with jcrPrimaryType == 'cards:Questionnaire', parent == null
    }
};

/**
 * Gets the children of a jcr node
 * 
 * @param {Object} jcrData - The jcr data object to get children from.
 * @returns {Array} - Returns an array of children nodes.
 */
function jcrGetChildren(jcrData) {
    if (!jcrData || typeof jcrData !== 'object' || !jcrData['jcr:primaryType']) {
        throw new Error("jcrGetChildren called with invalid jcrData")
    }
    const children = [];
    for (const key in jcrData) {
        const isProperty = jcrData.hasOwnProperty(key)
        const isObject = typeof jcrData[key] === 'object'
        // This will filter out conditional groups and conditionals
        // Don't need to add 'cards:Questionnaire' since its the root and no other entry will have that type
        const isEntry = ENTRY_TYPES.includes(jcrData[key]['jcr:primaryType'])
        if (isProperty && isObject && isEntry) {
            console.log('jcrData', jcrData, key)
            children.push(jcrData[key]);
        }
    };
    return children;
};


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
    // Root node has itself as null
    const {
        ['jcr:uuid']: id,
        ['jcr:primaryType']: jcrPrimaryType,
        ['@name']: name,
        ['@path']: path
    } = jcrData;
    const title = getTitleField(jcrData);
    const nodeChildren = jcrGetChildren(jcrData).map(jcrChild => jcrChild['jcr:uuid'])
    const isRootNode = !nodeParent && rootPath === path
    if (isRootNode) {
        const rootNode = {
            value: id,
            parent: null,
            children: nodeChildren,
            jcrPrimaryType,
            name,
            title,
            path,
            relativePath: ''
        };
        return rootNode
    } else {
        let relativePath = path?.replace(`${rootPath}/`, '') || ''
        relativePath = relativePath.substring(0, relativePath.lastIndexOf("/") + 1);
        const node = {
            value: id,
            parent: nodeParent,
            children: nodeChildren,
            jcrPrimaryType,
            name,
            title,
            path,
            relativePath
        };
        return node
    }
}

/**
 * Recursively finds all entries in the jcrData object that match the entryTypes
 * 
 * @param {Object} jcrData - The jcr data object to search for entries.
 * @param {Array} entryTypes - The list of entry types to search for.
 * @param {string} rootPath - The path of the root node.
 * @param {Object} nodes - The flat map representing the tree structure.
 * @returns {Object} - Returns a flat map representing the tree structure. 
*/
function jcrFindEntries(jcrData, entryTypes = ENTRY_TYPES, rootPath = (jcrData['@path'] || ''), nodes) {
    if (!jcrData || typeof jcrData !== 'object' || !jcrData['jcr:primaryType']) {
        throw new Error("jcrFindEntries called with invalid jcrData")
        // return [];
    }
    const children = jcrGetChildren(jcrData);
    const nodeParent = jcrData['jcr:uuid'];
    for (const child of children) {
        if (entryTypes.includes(child['jcr:primaryType'])) {
            nodes[child['jcr:uuid']] = jcrToNode(child, rootPath, nodeParent)
        }
        jcrFindEntries(child, entryTypes, rootPath, nodes);
    }
    return nodes
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


/**
 * Initializes tree using cards:Questionnaire jcr data as root node
 * 
 * @param {*} jcrData 
 * @returns {Object} - Returns a flat map representing the tree structure.
 */
function initializeRoot(jcrData) {
    let nodes = {}
    const rootPath = jcrData['@path'];
    const rootNode = jcrToNode(jcrData, rootPath, null);
    nodes[jcrData['jcr:uuid']] = rootNode
    // Entries
    nodes = jcrFindEntries(jcrData, ENTRY_TYPES, rootPath, nodes);
    console.log('init nodes', nodes)
    return nodes
}

/**
 * Removes a node from the tree
 * 
 * @param {string} nodeId - The ID of the node to be removed.
 * @param {Object} nodes - The flat map representing the tree structure.
 * @returns {Object} - Returns a new map with the node removed.
*/
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

