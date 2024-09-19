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

import React, { useEffect, useMemo, useCallback, useReducer, useState } from 'react';
import {
    Alert,
    Button,
    Dialog,
    DialogActions,
    DialogContent, DialogContentText,
    DialogTitle,
    ListItemText,
    MenuItem,
    Snackbar,
    TextField
} from '@mui/material';
import MoveDownIcon from '@mui/icons-material/MoveDown';

import { useQuestionnaireTreeContext, isDescendant } from './QuestionnaireTreeContext';
import QuestionnaireAutocomplete from '../questionnaire/QuestionnaireAutocomplete';

import { stripCardsNamespace } from '../questionnaire/QuestionnaireUtilities';

const jcrActions = {
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
        console.log(reorderSourceNode, newParentNode, newPosition)
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

const DEFAULT_STATE = {
    enabled: false,
    snackbar: { open: false, message: '' },
};

export const DndStateContext = React.createContext(DEFAULT_STATE);
export const DndDispatchContext = React.createContext(null);

function dndReducer(dndState, action) {
    console.log('dispatch dnd', dndState, action)
    switch (action.type) {
        case 'setEnabled': {
            return { ...dndState, enabled: action.payload.enabled }
        }
        case 'setSnackbar': {
            const { snackbar } = dndState
            return { ...dndState, snackbar: { ...snackbar, ...action.payload } }
        }
        case 'reset': {
            return DEFAULT_STATE
        }
        default: {
            throw Error('Unknown action: ' + action.type);
        }
    }
}

export function MoveEntryModal(props) {
    const { data } = props
    const disableReorderSourceSelect = !!data
    const [open, setOpen] = useState(false);
    const [snackbar, setSnackbar] = useState({ open: false, error: false, message: '' });
    const treeContext = useQuestionnaireTreeContext()
    const nodes = treeContext.state.nodes
    // Reorder source is the entry to be moved, array used for autocomplete
    const [reorderSourceSelection, setReorderSourceSelection] = useState([]);
    const reorderSource = useMemo(() => {
        if (disableReorderSourceSelect) {
            return data['jcr:uuid']
        } else {
            if (!!reorderSourceSelection.length) {
                return reorderSourceSelection[0]
            } else {
                return ''
            }
        }
    }, [reorderSourceSelection]);
    const sourceOptions = useMemo(() => {
        if (Object.keys(nodes).length !== 0) {
            const newSourceOptions = []
            for (const id in nodes) {
                if (nodes.hasOwnProperty(id)) {
                    const node = nodes[id];
                    if (!['cards:Questionnaire'].includes(node.jcrPrimaryType)) {
                        newSourceOptions.push(node);
                    }
                }
            }
            return newSourceOptions
        } else {
            return []
        }
    }, [nodes])
    // New parent is the entry to move the reorder source to
    const [newParentSelection, setNewParentSelection] = useState([]);
    const newParent = useMemo(() => (!!newParentSelection.length ? newParentSelection[0] : ''), [newParentSelection])
    // New position is the index of new parent's children to move the reorder source to
    const [newPositionSelection, setNewPositionSelection] = useState([]);
    const newPosition = useMemo(() => (!!newPositionSelection.length ? newPositionSelection[0] : ''), [newPositionSelection])
    console.log('state', reorderSource, newParent, newPosition)
    // Parent options for a given source node is any section or the root questionnaire
    const parentOptions = useMemo(() => {
        if (!!reorderSource) {
            const newParentOptions = []
            for (const id in nodes) {
                if (nodes.hasOwnProperty(id)) {
                    const node = nodes[id];
                    // Only sections and questionnaires can be new parents
                    if (['cards:Section', 'cards:Questionnaire'].includes(node.jcrPrimaryType)) {
                        newParentOptions.push(node);
                    }
                }
            }
            console.log('parentOptions', newParentOptions)
            return newParentOptions
        } else {
            return []
        }
    }, [nodes, reorderSource])
    const getParentOptionDisabled = useCallback((option) => {
        // Exclude current node (can't reassign node as parent to itself)
        // Exclude children of current node (can't reassign parent to child)
        const isReorderSource = reorderSource === option.value
        const isDescendantOfReorderSource = isDescendant(nodes, reorderSource, option.value)
        const disabled = isReorderSource || isDescendantOfReorderSource
        return disabled
    }, [nodes, reorderSource])
    const positionOptions = useMemo(() => {
        if (!Object.keys(nodes).length) {
            return []
        }
        if (!!newParent) {
            const parent = nodes[newParent]
            const newPositions = parent.children.map(id => (nodes[id]))
            // Use reorderSourceNode as element for moving to the bottom
            // const reorderSourceNode = nodes[reorderSource]
            // .map((node, index) => {
            //     const { value, jcrPrimaryType, name: label, path: sublabel } = node
            //     const disabled = value === reorderSource;
            //     return { value: index, label, sublabel, disabled };
            // })
            // const bottomPosition = newPositions.length === 0 ?
            //     { value: -1, label: 'Beginning', sublabel: "No entries in parent, moved entry will be first" }
            //     : { value: -1, label: 'Bottom', sublabel: "Moved entry will be last" };
            // newPositions.push(reorderSourceNode);
            console.log('positionOptions', newPositions, nodes)
            return newPositions
        } else {
            return []
        }
    }, [nodes, reorderSource, newParent])
    useEffect(() => {
        if (Object.keys(nodes).length === 0 || !reorderSource) {
            console.log('reset all on empty nodes')
            setNewParentSelection([])
            // setNewPosition('')
            setNewPositionSelection([])
        } else {
            if (!open) {
                if (!disableReorderSourceSelect) {
                    console.log('reset selectable on closed')
                    setReorderSourceSelection([])
                    setNewParentSelection([])
                    setNewPositionSelection([])
                } else {
                    console.log(nodes)
                    const originalParent = nodes[reorderSource].parent
                    const originalPosition = nodes[originalParent].children.indexOf(reorderSource)
                    console.log('originalParent', originalParent, 'originalPosition', originalPosition)
                    setNewParentSelection([originalParent])
                    setNewPositionSelection([originalPosition])
                }

            }
        }

    }, [nodes, reorderSource, open])
    useEffect(() => {
        console.log('reset newPosition from newParent')
        // setNewPosition('')
        setNewPositionSelection([])
    }, [newParent])


    // const handleReorderSourceChange = (e) => {
    //     const reorderSource = e.target.value
    //     setReorderSource(reorderSource)
    // }
    // const handleNewParentChange = (e) => {
    //     const newParent = e.target.value
    //     setNewParent(newParent)
    // }
    // const handleNewPositionChange = (e) => {
    //     const newPosition = e.target.value
    //     setNewPosition(newPosition)
    // }

    const handleClickOpen = () => {
        setOpen(true);
    };

    const handleClose = () => {
        setOpen(false);
    };
    const handleError = (e) => {
        console.log(e)
        setSnackbar({ open: true, error: true, message: 'Error moving entry' })
    }
    const handleSuccess = (data) => {
        console.log('reorder success', data)
        setSnackbar({ open: true, error: false, message: 'Entry moved successfully' })
    }

    const handleSubmit = (e) => {
        console.log('reorder', reorderSource, newParent, newPosition)
        if (!reorderSource || !newParent || !newPosition) {

        }
        e.preventDefault();
        let submit
        if (nodes[reorderSource].parent === newParent) {
            submit = jcrActions.reorderEntry(nodes[reorderSource], newPosition)
        } else {
            submit = jcrActions.moveEntryNested(nodes[reorderSource], nodes[newParent], newPosition)
        }
        submit.then(response => response.ok ? response.json() : Promise.reject(response))
            .then(handleSuccess)
            .catch(handleError)
            .finally(handleClose);
    }

    const MoveEntryDialogText = (props) => {
        if (disableReorderSourceSelect) {
            const node = nodes[reorderSource]
            console.log(node)

            return (
                <DialogContentText>
                    To move entry with name <b>{node.name}</b>, please select a new parent and a new position.
                    Positions selected beside 'Bottom' will move the entry to before the selected position entry.
                </DialogContentText>
            )
        } else {
            return (
                <>
                    <DialogContentText>
                        To move an entry, please select an entry to move, a new parent, and a new position.
                    </DialogContentText>
                    <QuestionnaireAutocomplete
                        multiple={false}
                        entities={sourceOptions.map((node) => {
                            //   { uuid: string, name: string, text: string, path: string, relativePath: string }
                            const { value, name, title, path, relativePath, jcrPrimaryType } = node
                            return {
                                value: value,
                                name: name,
                                text: title,
                                path: path,
                                relativePath: relativePath,
                                type: stripCardsNamespace(jcrPrimaryType)
                            }
                        })}
                        // selection={!reorderSource ? [] : [reorderSource]}
                        selection={reorderSourceSelection}
                        onSelectionChanged={setReorderSourceSelection}
                        getOptionValue={(option) => option.value}
                        id="reorderSource"
                    />
                    {/* <TextField
                    select
                    disabled={disableReorderSourceSelect}
                    value={reorderSource}
                    onChange={handleReorderSourceChange}
                    autoComplete='off'
                    required
                    margin="dense"
                    id="reorderSource"
                    name="reorderSource"
                    label="Select an entry to move"
                    fullWidth
                    variant="standard"
                >
                    {sourceOptions.map((option) => (
                        <MenuItem key={option.value} value={option.value}>
                            <ListItemText primary={option.label} secondary={option.sublabel} />
                        </MenuItem>
                    ))}
                </TextField> */}
                </>
            )
        }
    }

    // Wait until nodes is loaded into context
    if (Object.keys(nodes).length === 0) {
        return null
    }
    return (
        <>
            <Button onClick={handleClickOpen}>
                {disableReorderSourceSelect ? 'Move' : <MoveDownIcon />}
            </Button >
            <Dialog
                open={open}
                onClose={handleClose}
                PaperProps={{
                    component: 'form',
                    onSubmit: handleSubmit
                }}
            >
                {/* TODO: put data entry info in title  */}
                <DialogTitle>Reorder questionnaire entries</DialogTitle>
                <DialogContent>
                    <MoveEntryDialogText />
                    <QuestionnaireAutocomplete
                        multiple={false}
                        entities={parentOptions.map((node) => {
                            const {value, name, title, path, relativePath, jcrPrimaryType} = node
                            return {
                                value: value,
                                name:  jcrPrimaryType === 'cards:Questionnaire' ? 'Move to top level' : name,
                                text: title,
                                path: path,
                                relativePath: relativePath,
                                type: stripCardsNamespace(jcrPrimaryType)
                            }
                        })}
                        getOptionDisabled={getParentOptionDisabled}
                        selection={newParentSelection}
                        onSelectionChanged={setNewParentSelection}
                        getOptionValue={(option) => option.value}
                        id="newParent"
                        disabled={!reorderSource}
                    />
                    <QuestionnaireAutocomplete
                        multiple={false}
                        entities={positionOptions.map((node, index) => {
                            console.log(positionOptions)
                            const {value, name, title, path, relativePath, jcrPrimaryType} = node
                            return {
                                value: index,
                                name: name,
                                text: title,
                                path: path,
                                relativePath: relativePath,
                                type: stripCardsNamespace(jcrPrimaryType)
                            }
                        }).concat([
                            {
                                value: -1,
                                path: nodes[newParent]?.path,
                                relativePath: nodes[newParent]?.relativePath,
                                type: stripCardsNamespace(nodes[reorderSource]?.jcrPrimaryType),
                                ... (!!positionOptions.length ? {
                                    name: 'Moved entry will be last',
                                    text: 'Bottom',
                                } : {
                                    name: 'No entries in parent, moved entry will be first',
                                    text: 'Beginning',
                                })
                            }
                        ])}
                        getOptionDisabled={(option) => option.path === nodes[reorderSource]?.path}
                        selection={newPositionSelection}
                        onSelectionChanged={setNewPositionSelection}
                        getOptionValue={(option) => option.value}
                        id="newPosition"
                        disabled={!newParent}
                    />
                    {/* <TextField
                        select
                        value={newParent}
                        onChange={handleNewParentChange}
                        autoComplete='off'
                        required
                        margin="dense"
                        id="parent"
                        name="parent"
                        label="Select a new parent for the entry"
                        fullWidth
                        variant="standard"
                    >
                        {parentOptions.map((option) => (
                            <MenuItem key={option.value} value={option.value} disabled={option.disabled}>
                                <ListItemText primary={option.label} secondary={option.sublabel} />
                            </MenuItem>
                        ))}
                    </TextField> */}
                    {/* <TextField
                        value={newPosition}
                        onChange={handleNewPositionChange}
                        select
                        // autoFocus
                        required
                        autoComplete='off'
                        margin="dense"
                        id="position"
                        name="position"
                        label="Select a new position for the entry"
                        fullWidth
                        variant="standard"
                    >
                        {positionOptions.map((option) => (
                            <MenuItem key={option.value} value={option.value} disabled={option.disabled}>
                                <ListItemText primary={option.label} secondary={option.sublabel} />
                            </MenuItem>
                        ))}
                    </TextField> */}
                    <DialogContentText>
                        {!!reorderSource && `Moving entry ${reorderSource}`}
                        {!!newParent && `to new parent ${newParent}`}
                        {!!`${newPosition}` && `with new position ${newPosition}`}
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleClose}>Cancel</Button>
                    <Button type="submit">Move entry</Button>
                </DialogActions>
            </Dialog>
            <Snackbar
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
                open={snackbar.open}
                autoHideDuration={2000}
                onClose={() => { setSnackbar({ open: false, error: false, message: '' }) }}
            >
                <Alert
                    severity={snackbar.error ? "error" : "success"}
                    variant="filled"
                    sx={{ width: '100%' }}
                >
                    {snackbar.message}
                </Alert>
            </Snackbar>
        </>
    )
}

/**
 * A context provider for managing drag and drop
 * @param {Object} props the props to pass onwards to the child, generally its children
 * @returns {Object} a React component with the form provider
 */
export function DragDropProvider(props) {
    const { ...rest } = props
    const [dndState, dndDispatch] = useReducer(dndReducer, DEFAULT_STATE);
    // const questionnaireContext = useQuestionnaireReaderContext()

    // console.log('dnd provider', dndState, questionnaireContext)
    return (
        // <DragDropContext onDragEnd={onDragEnd}>
        <>
            <DndStateContext.Provider value={dndState}>
                <DndDispatchContext.Provider value={dndDispatch} {...rest} />
            </DndStateContext.Provider>
            {/* <Snackbar
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
                open={dndState.snackbar.open}
                autoHideDuration={2000}
                onClose={() => dndDispatch({ type: 'setSnackbar', payload: DEFAULT_STATE.snackbar })}
            >
                <Alert
                    severity="success"
                    variant="filled"
                    sx={{ width: '100%' }}
                >
                    {dndState.snackbar.message}
                </Alert>
            </Snackbar> */}
        </>
        // </DragDropContext>
    );
}