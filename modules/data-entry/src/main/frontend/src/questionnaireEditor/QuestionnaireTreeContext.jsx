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

import React, { useCallback, useEffect, useReducer } from "react";
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
const UPDATE_ONDATA = 'UPDATE_ONDATA';
const CLEAR_TREE = 'CLEAR_TREE';
const REMOVE_NODE = 'REMOVE_NODE';
const ACTIONS = [INITIALIZE_ROOT, UPDATE_ONDATA, CLEAR_TREE, REMOVE_NODE];

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
    // Data from JCR used to initialize tree
    data: null,
    // Format: { id: { value: '...', parent: '...', children: [...], jcrPrimaryType: '...' } }
    // Note: root is node with jcrPrimaryType == 'cards:Questionnaire', parent == null
    nodes: {}
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
            // console.log('jcrData', jcrData, key)
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

export function findNodePosition(nodes, nodeId) {
    const child = nodes[nodeId];
    const parent = nodes[child.parent];
    return parent.children.indexOf(child.value);
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
 * 
 * @param {Object} jcrData - The jcr data object to update the tree with.
 * @param {Object} nodes - The flat map representing the tree structure.
 * @returns {Object} - Returns a new map with the updated nodes.
 * 
 */
function updateNodesOnData(jcrData, nodes) {
    if (!jcrData || typeof jcrData !== 'object' || !jcrData['jcr:uuid']) {
        throw new Error("updateNodesOnCreated called with invalid jcrData")
    }
    const rootNode = Object.values(nodes).find(node => node.parent === null)
    const rootPath = rootNode?.path;
    const updatedNodes = jcrFindEntries(jcrData, ENTRY_TYPES, rootPath, {});
    console.log('updateNodesOnCreated', nodes, updatedNodes, jcrData)
    // Update the tree with the new nodes
    function updateTree(originalTree, jcrDataSubtree) {
        const nodes = structuredClone(originalTree);
        // Find added and updated nodes
        for (const [id, newNode] of Object.entries(jcrDataSubtree)) {
            const existingNode = nodes[id];
            // If the node doesn't exist in the original tree, add it
            if (!existingNode) {
                nodes[id] = newNode;
                const parentId = newNode.parent;
                if (parentId && nodes[parentId]) {
                    nodes[parentId].children.push(id);
                }
            } else {
                // Update existing node with any changes
                nodes[id] = newNode
            }
        }

        // // Find removed nodes
        // for (const [id, originalNode] of Object.entries(originalTree)) {
        //     // if (!(id in jcrDataSubtree)) {
        //     //     // Remove the node
        //     //     const parentId = originalNode.parent;
        //     //     if (parentId && originalTree[parentId]) {
        //     //         // Remove the reference to this node from the parent's children
        //     //         originalTree[parentId].children = originalTree[parentId].children.filter(childId => childId !== id);
        //     //     }
        //     //     delete originalTree[id];
        //     // }
        // }

        return nodes;
    }
    return updateTree(nodes, updatedNodes)
}

/**
 * Removes a node from the tree
 * 
 * @param {string} nodeId - The ID of the node to be removed.
 * @param {Object} nodes - The flat map representing the tree structure.
 * @returns {Object} - Returns a new map with the node removed.
*/
function removeNode(nodeId, nodes) {
    const newNodes = structuredClone(nodes)
    const node = newNodes[nodeId];
    // Remove node from parent's children array
    const parentId = node.parent;
    const parent = newNodes[parentId];
    newNodes[parentId].children = parent.children.filter(childId => childId !== nodeId);
    // console.log('removeNode', node, parent, nodes)
    // Remove children of node from tree
    for (const k in newNodes) {
        if (newNodes.hasOwnProperty(k) && newNodes[k].parent === nodeId) {
            delete newNodes[k];
        }
    }
    // Remove node from tree
    delete newNodes[nodeId];
    return newNodes;
}

const stateValidators = {
    hasOneRootNode: (nodes) => {
        if (Object.keys(nodes).length === 0) {
            return true
        }
        const rootNodes = Object.values(nodes).filter(node => node.parent === null)
        if (rootNodes.length === 1) {
            return true
        }
        throw new Error("Tree must have exactly one root node")
    },
    hasValidParents: (nodes) => {
        for (const node of Object.values(nodes)) {
            if (node.parent && !nodes[node.parent]) {
                throw new Error(`Node ${node.value} has invalid parent ${node.parent}`)
            }
        }
        return true
    },
    hasValidChildren: (nodes) => {
        for (const node of Object.values(nodes)) {
            for (const childId of node.children) {
                if (!nodes[childId]) {
                    throw new Error(`Node ${node.value} has invalid child ${childId}`)
                }
            }
        }
        return true
    },
    hasNoDisconnectedNodes: (nodes) => {
        const ids = Object.keys(nodes);
        const disconnected = ids.filter(id => nodes[id].parent !== null && !nodes[nodes[id].parent]);
        if (disconnected.length === 0) {
            return true
        }
        throw new Error("There are disconnected nodes in the tree.")
    },
}

// Reducer Function
const treeReducer = (state, action) => {
    // Need some kind of check that state is in the correct format?
    console.log(state, action)
    if (!action.type || !ACTIONS.includes(action.type)) {
        throw new Error("Invalid action type in treeReducer")
    }
    let newState = structuredClone(state);
    switch (action.type) {
        case CLEAR_TREE: {
            newState = initialState
            break;
        }
        case INITIALIZE_ROOT: {
            // jcrData is questionnaire node
            const { jcrData } = action.payload
            if (jcrData['jcr:primaryType'] !== 'cards:Questionnaire') {
                throw new Error("QuestionnaireTreeContext initialized with a node that is not a questionnaire")
            };
            newState = { ...state, data: jcrData, nodes: initializeRoot(jcrData) };
            break;
        }
        case UPDATE_ONDATA: {
            const { jcrData } = action.payload;
            const { nodes } = state;
            const isRootNode = jcrData['jcr:primaryType'] === 'cards:Questionnaire';
            newState = {
                ...state,
                ...isRootNode && { data: jcrData },
                nodes: updateNodesOnData(jcrData, nodes)
            };
            break;
        }
        case REMOVE_NODE: {
            const { nodeId } = action.payload;
            const { nodes } = state;
            newState = { ...state, nodes: removeNode(nodeId, nodes) };
            break;
        }
        default:
            throw new Error("Invalid action type in treeReducer");
        // return state;
    }
    // Validate newState
    const stateIsValid = Object.values(stateValidators).map(validator => validator(newState.nodes)).reduce((a, b) => a && b, true)
    if (!stateIsValid) {
        throw new Error("Invalid state in treeReducer")
    }
    return newState;
};

export const QuestionnaireTreeContext = React.createContext();

export const jcrActions = {
    checkIn: (id) => {
        let checkinForm = new FormData();
        checkinForm.set(":operation", "checkin");
        return fetch(`/Questionnaires/${id}`, {
            method: "POST",
            body: checkinForm
        });
    },
    checkOut: (id) => {
        let checkoutForm = new FormData();
        checkoutForm.set(":operation", "checkout");
        return fetch(`/Questionnaires/${id}`, {
            method: "POST",
            body: checkoutForm
        });
    },
    fetchQuestionnaireData: (id) => { return fetch(`/Questionnaires/${id}.deep.json`) },
    fetchResourceJSON: (data) => { return fetch(`${data["@path"]}.deep.json`) },

    // https://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html#order-1
    // :order index
    reorderEntry: (reorderSourceNode, newPosition) => {
        let reorderForm = new FormData();
        const order = newPosition === -1 ? 'last' : `${newPosition}`
        const path = reorderSourceNode.path
        reorderForm.set(":order", order);
        reorderForm.set(":http-equiv-accept", "application/json");
        return fetch(path, {
            method: "POST",
            body: reorderForm
        });
    },

    // https://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html#order-1
    // POST /content/oldParentNode/childNode
    // :operation=move
    // :dest=/content/newParentNode/childNode
    // :order=before siblingNodeName || index
    moveEntryNested: (reorderSourceNode, newParentNode, newPosition) => {
        const reorderForm = new FormData();
        // console.log(reorderSourceNode, newParentNode, newPosition)
        // Use numeric 'order' value to move to specific position
        // If newPosition -1, then move to top of parent's children
        const order = newPosition === -1 ? 'last' : `${newPosition}`
        const dest = newParentNode.path.concat('/')
        const path = reorderSourceNode.path
        reorderForm.set(":operation", "move");
        reorderForm.set(":order", order);
        reorderForm.set(":dest", dest);
        // For the case of reordering within same parent
        // reorderForm.set(":replace", "true");
        reorderForm.set(":http-equiv-accept", "application/json");
        return fetch(path, {
            method: "POST",
            body: reorderForm,
        });
    },
}

/**
 * A context provider for a questionnaire tree, which contains the tree structure and actions to manipulate it
 * @param {Object} props the props to pass onwards to the child, generally its children. Has questionnaireId as a required prop.
 * @returns {Object} a React component with the questionnaire tree provider
 */
export function QuestionnaireTreeProvider(props) {
    const { questionnaireId, ...rest } = props
    if (!questionnaireId) {
        throw new Error("QuestionnaireTreeProvider must be initialized with a questionnaireId")
    }

    const [state, dispatch] = useReducer(treeReducer, initialState)

    // Actions
    const fetchRootData = useCallback(() => {
        return jcrActions.fetchQuestionnaireData(questionnaireId)
            .then(response => response.json())
            .then(data => {
                dispatch({ type: INITIALIZE_ROOT, payload: { jcrData: data } })
            })
            // .finally(() => {
            //     console.log('fetchRootData finally')
            // })
    }, [])

    const clearTree = useCallback(() => {
        dispatch({ type: CLEAR_TREE })
    }, [])

    const refreshTree = useCallback(() => {
        clearTree()
        fetchRootData()
    }, [])

    const removeNode = useCallback((nodeId) => {
        dispatch({ type: REMOVE_NODE, payload: { nodeId } })
    }, [])

    const updateNodeData = useCallback((jcrData) => {
        dispatch({ type: UPDATE_ONDATA, payload: { jcrData } })
    }, [])

    const reorderNode = useCallback((reorderSourceId, newParentId, newPosition) => {
        const nodes = state.nodes
        const reorderValidators = {
            sourceNodeExists: (reorderSourceId, newParentId, newPosition) => {
                return nodes[reorderSourceId] ? true : 'Source node does not exist.';
            },
            newParentExists: (reorderSourceId, newParentId, newPosition) => {
                return nodes[newParentId] ? true : 'New parent node does not exist.';
            },
            notMovingToSelfOrDescendant: (reorderSourceId, newParentId, newPosition) => {
                // const sourceNode = nodes[sourceId];
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
                // Return error if reorderSourceNode has same name as any of the children of newParentNode
                // TODO: on reorder to parent with child with same name, does it replace the child?
                const sourceNode = nodes[reorderSourceId];
                const newParentNode = nodes[newParentId];
                const newParentChildren = newParentNode.children.map(id => nodes[id].name);
                const sourceNodeName = sourceNode.name;
                const isNewParent = sourceNode.parent !== newParentId;
                if (isNewParent && newParentChildren.includes(sourceNodeName)) {
                    return 'New parent node already has a child with the same name.';
                }
                return true
                
            }
        }
        // Check if any validators return strings, indicating an error
        const validation = Object.values(reorderValidators).map(validator => validator(reorderSourceId, newParentId, newPosition))
        const isNotValid = validation.some(result => typeof result === 'string');
        if (isNotValid) {
            const validationErrors = validation.filter(result => typeof result === 'string').join('\n');
            throw new Error("Invalid reorder operation ".concat(validationErrors))
        }

        const isNewParent = newParentId !== nodes[reorderSourceId].parent
        const submit = isNewParent ? 
            jcrActions.moveEntryNested(nodes[reorderSourceId], nodes[newParentId], newPosition)
            : jcrActions.reorderEntry(nodes[reorderSourceId], newPosition)
        return submit
    }, [state.nodes])


    useEffect(() => {
        return () => {
            clearTree()
        }
    }, [])
    console.log(state)
    const actions = {
        fetchRootData,
        clearTree,
        refreshTree,
        removeNode,
        updateNodeData,
        reorderNode
    }

    const context = { state, dispatch, actions }
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
    const context = React.useContext(QuestionnaireTreeContext);

    if (context == undefined) {
        throw new Error("useQuestionnaireTreeContext must be used within a QuestionnaireTreeProvider")
    }

    return context;
}

export function findTreeEntries(nodes, entryTypes = []) {
    console.log(nodes, entryTypes)
    if (!Object.keys(nodes).length) {
        // Return empty array if no nodes
        return [];
    }
    // Recurisvely traverse the tree and return all nodes with matching entryTypes
    const entries = []
    const traverseTree = (node, entries) => {
        if (entryTypes.includes(node.jcrPrimaryType)) {
            entries.push(node)
        }
        for (const childId of node.children) {
            traverseTree(nodes[childId], entries)
        }
    }
    const rootNode = Object.values(nodes).find(node => node.parent === null)
    traverseTree(rootNode, entries)
    return entries
}