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
    Snackbar,

    ListSubheader,
    List,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    ListItemAvatar,
    Avatar,
    Collapse,
    Tooltip,
    Icon,
    Grid,
    Typography
} from '@mui/material';

import MoveDownIcon from '@mui/icons-material/MoveDown';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';
import InboxIcon from '@mui/icons-material/MoveToInbox';
import DraftsIcon from '@mui/icons-material/Drafts';
import SendIcon from '@mui/icons-material/Send';
import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';
import StarBorder from '@mui/icons-material/StarBorder';

import { useQuestionnaireTreeContext, isDescendant, findNodePosition, jcrActions } from './QuestionnaireTreeContext';
import QuestionnaireAutocomplete from '../questionnaire/QuestionnaireAutocomplete';

import { stripCardsNamespace } from '../questionnaire/QuestionnaireUtilities';

import { DragDropContext, Draggable, Droppable } from 'react-beautiful-dnd';

function MoveEntryModal(props) {
    const { data } = props
    const disableReorderSourceSelect = !!data
    const [open, setOpen] = useState(false);
    const [snackbar, setSnackbar] = useState({ open: false, error: false, message: '' });
    const treeContext = useQuestionnaireTreeContext();
    const { state: { nodes } } = treeContext

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
        const nodes = treeContext.state.nodes
        if (!!Object.keys(nodes).length) {
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
    }, [treeContext.state.nodes])
    // New parent is the entry to move the reorder source to
    const [newParentSelection, setNewParentSelection] = useState([]);
    const newParent = useMemo(() => (!!newParentSelection.length ? newParentSelection[0] : ''), [newParentSelection])
    // New position is the index of new parent's children to move the reorder source to
    const [newPositionSelection, setNewPositionSelection] = useState([]);
    const newPosition = useMemo(() => (!!newPositionSelection.length ? newPositionSelection[0] : ''), [newPositionSelection])
    // console.log('state', nodes, reorderSource, newParent, newPosition, nodes.hasOwnProperty(reorderSource))
    // Parent options for a given source node is any section or the root questionnaire
    const parentOptions = useMemo(() => {
        if (!!reorderSource && !!Object.keys(nodes).length) {
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
            // console.log('parentOptions', newParentOptions)
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
        if (!!newParent && !!Object.keys(nodes).length) {
            const parent = nodes[newParent]
            const newPositions = parent.children.map(id => (nodes[id]))
            return newPositions
        } else {
            return []
        }
    }, [nodes, newParent])
    useEffect(() => {
        if (Object.keys(nodes).length === 0) {
            // console.log('reset all on empty nodes')
            setNewParentSelection([])
            setNewPositionSelection([])
        } else if (!reorderSource && !disableReorderSourceSelect) {
            // console.log('reset all on empty reorderSource')
            setNewParentSelection([])
            setNewPositionSelection([])
        } else {
            if (open && disableReorderSourceSelect) {
                const originalParent = nodes[reorderSource].parent
                const originalPosition = nodes[originalParent].children.indexOf(reorderSource)
                setNewParentSelection([originalParent])
                setNewPositionSelection([originalPosition])
            } else if (open && !disableReorderSourceSelect) {
                // console.log('reset selectables')
                setNewParentSelection([])
                setNewPositionSelection([])
            } else if (!open) {
                if (!disableReorderSourceSelect) {
                    // console.log('reset all on close')
                    setReorderSourceSelection([])
                }
                setNewParentSelection([])
                setNewPositionSelection([])
            }
        }

    }, [nodes, reorderSource, open])
    useEffect(() => {
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
        treeContext.actions.refreshTree()
        setSnackbar({ open: true, error: false, message: 'Entry moved successfully' })
    }

    const handleSubmit = (e) => {
        console.log('reorder', reorderSource, newParent, newPosition)
        if (!reorderSource || !newParent || !newPosition) {
            throw new Error('Missing required fields for entry reorder')
        }
        e.preventDefault();
        // If reorderSource is child of newParent, just reorder
        // const submit = treeContext.actions.reorderNode(reorderSource, newParent, newPosition)
        // let submit
        // if (nodes[reorderSource].parent === newParent) {
        //     submit = jcrActions.reorderEntry(nodes[reorderSource], newPosition)
        // } else {
        //     submit = jcrActions.moveEntryNested(nodes[reorderSource], nodes[newParent], newPosition)
        // }
        // submit.then(response => response.ok ? response.json() : Promise.reject(response))
        treeContext.actions.reorderNode(reorderSource, newParent, newPosition)
            .then(handleSuccess)
            .catch(handleError)
            .finally(handleClose);
    }

    const MoveEntryDialogText = (props) => {
        if (disableReorderSourceSelect) {
            const node = nodes[reorderSource]
            // console.log(node)

            return (
                <DialogContentText>
                    To move entry with name <b>{node.name}</b>, please select a new parent and a new position.
                    {/* Positions selected beside 'Bottom' will move the entry to before the selected position entry. */}
                </DialogContentText>
            )
        } else {
            return (
                <>
                    <DialogContentText>
                        Select an entry to move, a new parent, and a new position.
                    </DialogContentText>
                    <Grid container alignItems='baseline' spacing={2} direction="row">
                        <Grid item xs={4}>
                            <Typography variant="subtitle2">Select an entry to move</Typography>
                        </Grid>
                        <Grid item xs={8}>
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
                                placeholderText="Select questions/sections/information from this questionnaire"
                            />
                        </Grid>
                    </Grid>
                </>
            )
        }
    }

    const ReorderForm = (props) => {
        return (
            <DialogContent>
                <MoveEntryDialogText />
                <Grid container alignItems='baseline' spacing={2} direction="row">
                    <Grid item xs={4}>
                        <Typography variant="subtitle2">Select a new parent</Typography>
                    </Grid>
                    <Grid item xs={8}>
                        <QuestionnaireAutocomplete
                            multiple={false}
                            entities={parentOptions.map((node) => {
                                // console.log(node)
                                const { value, name, title, path, relativePath, jcrPrimaryType } = node
                                return {
                                    value: value,
                                    name: name,
                                    text: title,
                                    path: path,
                                    relativePath: relativePath,
                                    type: stripCardsNamespace(jcrPrimaryType),
                                    ... (jcrPrimaryType === 'cards:Questionnaire' && {
                                        // type: "!",
                                        name: 'Move to top level',
                                    })
                                }
                            })}
                            getOptionDisabled={getParentOptionDisabled}
                            selection={newParentSelection}
                            onSelectionChanged={setNewParentSelection}
                            getOptionValue={(option) => option.value}
                            placeholderText="Select sections from this questionnaire"
                            id="newParent"
                            disabled={!reorderSource}
                        />
                    </Grid>
                </Grid>
                <Grid container alignItems='baseline' spacing={2} direction="row">
                    <Grid item xs={4}>
                        <Typography variant="subtitle2">Select a new position</Typography>
                    </Grid>
                    <Grid item xs={8}>
                        <QuestionnaireAutocomplete
                            multiple={false}
                            entities={positionOptions.map((node, index) => {
                                // console.log(positionOptions, node)
                                const { value, name, title, path, relativePath, jcrPrimaryType } = node
                                // console.log(nodes, newParent, nodes[newParent])
                                return {
                                    value: index,
                                    name: name,
                                    text: title,
                                    path: path,
                                    relativePath: relativePath,
                                    type: stripCardsNamespace(jcrPrimaryType)
                                }
                            })
                                .concat(
                                    // !nodes[newParent] ? [] :
                                    [{
                                        value: -1,
                                        path: nodes[newParent]?.path,
                                        relativePath: nodes[newParent]?.relativePath,
                                        // type: stripCardsNamespace(nodes[reorderSource]?.jcrPrimaryType),
                                        type: "!",
                                        ... (!!positionOptions.length ? {
                                            name: 'Moved entry will be last',
                                            text: 'Bottom',
                                        } : {
                                            name: 'No entries in parent, moved entry will be first',
                                            text: 'Beginning',
                                        })
                                    }]
                                )
                            }
                            getOptionDisabled={(option) => option.path === nodes[reorderSource]?.path}
                            selection={newPositionSelection}
                            onSelectionChanged={setNewPositionSelection}
                            placeholderText="Select entries to move next to"
                            getOptionValue={(option) => option.value}
                            id="newPosition"
                            disabled={!newParent}
                        />
                    </Grid>
                </Grid>
                {/* <DialogContentText>
                {!!reorderSource && `Moving entry ${nodes[reorderSource].path} \n`}
                {!!newParent && `to new parent ${newParent} \n`}
                {!!`${newPosition}` && `with new position ${newPosition}`}
            </DialogContentText> */}
            </DialogContent>
        )
    }

    const emptyNodes = !Object.keys(nodes).length
    const nodeNotInTree = disableReorderSourceSelect && !nodes.hasOwnProperty(reorderSource)
    // Wait until nodes is loaded into context
    if (emptyNodes) {
        console.log('No nodes, not rendering MoveEntryModal')
        return null
    }
    if (nodeNotInTree) {
        console.error('Node not in tree, not rendering MoveEntryModal', data)
        return null
    }
    return (
        <>
            <Button onClick={handleClickOpen}>
                {disableReorderSourceSelect ? 'Move' : <MoveDownIcon />}
            </Button >
            <Dialog
                maxWidth="md"
                fullWidth
                open={open}
                onClose={handleClose}
                PaperProps={{
                    component: 'form',
                    onSubmit: handleSubmit
                }}
            >
                {/* TODO: put data entry info in title  */}
                <DialogTitle>Reorder questionnaire entries</DialogTitle>
                <ReorderForm />
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

import { deepPurple, orange } from '@mui/material/colors';
import { makeStyles } from '@mui/styles';
import { ENTRY_TYPES } from '../questionnaire/FormEntry';
const entitySpecs = {
    Question: {
        color: deepPurple[700]
    },
    Section: {
        icon: "view_stream",
        color: orange[800]
    }
}

const useStyles = makeStyles(theme => ({
    selectionList: {
        "& .MuiListItem-root": {
            paddingLeft: 0,
        },
        "& .MuiDivider-root": {
            marginLeft: theme.spacing(7),
        },
    },
    avatar: {
        backgroundColor: theme.palette.action.hover,
        color: theme.palette.text.primary,
        fontWeight: "bold",
    },
    optionText: {
        "& .MuiListItemText-secondary": {
            wordBreak: "break-word",
            "& > *": {
                color: theme.palette.text.disabled,
            },
        },
    },
}));

function DragDropMoveEntryModal(props) {
    const classes = useStyles();
    const [open, setOpen] = useState(false);
    const [snackbar, setSnackbar] = useState({ open: false, error: false, message: '' });


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
        treeContext.actions.refreshTree()
        setSnackbar({ open: true, error: false, message: 'Entry moved successfully' })
    }

    const treeContext = useQuestionnaireTreeContext();
    const nodes = treeContext.state.nodes;
    const rootNodeId = Object.entries(nodes).find(([, node]) => node.parent === null)?.[0];
    
    const [reorderState, setReorderState] = useState({
        reorderSourceId: null,
        newParenIdt: null,
        newPosition: null,
    })
    const handleSelectReorderSource = (nodeId) => {
        const alreadySelected = reorderState.reorderSourceId === nodeId
        if (alreadySelected) {
            setReorderState({ ...reorderState, reorderSourceId: null })
        } else {
            setReorderState({ ...reorderState, reorderSourceId: nodeId })
        }
    }
    const handleSelectTarget = (nodeId) => {
        // const 
    }
    const handleNodeClick = (nodeId) => {
        console.log('click', nodeId)
        const { reorderSourceId, newParentId, newPosition } = reorderState
        if (!reorderSourceId) {
            // Select node
            setReorderState({ ...reorderState, reorderSourceId: nodeId })
            return
        } else if (reorderSourceId === nodeId) {
            // Deselect node
            setReorderState({ ...reorderState, reorderSourceId: null })
            return
        } else {
            // Select target

            console.log('reorder', reorderSourceId, newParentId, newPosition)
        }
    }

    function RecursiveDragList(props) {
        const { nodeId,
            // level = 0
        } = props
        const treeContext = useQuestionnaireTreeContext();
        const nodes = treeContext.state.nodes;
        const node = nodes[nodeId];
        const type = stripCardsNamespace(node.jcrPrimaryType)
        const collapsible = ['cards:Section', 'cards:Questionnaire'].includes(node.jcrPrimaryType)
        const enableCollapse = collapsible && node.children?.length > 0
        const [collapsed, setCollapsed] = useState(false);

        const handleClickCollapse = () => {
            if (enableCollapse) {
                setCollapsed(!collapsed);
            }
        }
        const handleClickReorder = () => {
            handleNodeClick(nodeId)
        }



        return (
            <>
                <Draggable
                    draggableId={nodeId}
                    index={node.parent ? nodes[node.parent].children.indexOf(nodeId) : 0}
                    isDragDisabled={node.parent === null} // Don't allow root node to be dragged
                >
                    {(providedDraggable, snapshotDraggable) => (
                        <div ref={providedDraggable.innerRef} {...providedDraggable.draggableProps}>
                            <ListItemButton
                                selected={reorderState.reorderSourceId === nodeId}
                                // sx={{
                                //     backgroundColor: snapshotDraggable.isDragging ? 'rgba(0, 0, 0, 0.1)' : 'inherit',
                                // }}
                            >
                                <ListItemAvatar
                                    onClick={handleClickReorder}
                                >
                                        {/* <div 
                                            // ref={providedDroppable.innerRef}
                                            // {...providedDroppable.droppableProps}
                                            {...providedDraggable.dragHandleProps}
                                        > */}
                                            <Avatar
                                                style={{ color: entitySpecs[type]?.color }}
                                                className={classes.avatar}
                                                // {...providedDraggable.dragHandleProps}
                                                // sx={{ cursor: 'grab' }}
                                            >
                                                {
                                                    reorderState.reorderSourceId === nodeId ? <Icon>check_box</Icon>
                                                    : entitySpecs[type]?.icon ? <Icon>{entitySpecs[type].icon}</Icon>
                                                    : type?.charAt(0)
                                                }
                                            </Avatar>
                                        {/* </div> */}
                                </ListItemAvatar>
                                <ListItemText
                                    primary={node.title} secondary={node.name}
                                />
                                {collapsible && (
                                    <ListItemAvatar onClick={handleClickCollapse} >
                                        <Icon color={enableCollapse ? 'inherit' : 'disabled'}>
                                        {collapsed ? 'expand_more' : 'expand_less'}
                                        </Icon>
                                        {/* {collapsed ? <ExpandMore /> : <ExpandLess />} */}
                                    </ListItemAvatar>
                                )}
                            </ListItemButton>
                            <Droppable droppableId={nodeId}>
                                {(providedDroppable, snapshotDroppable) => (
                                    <>
                                        <div ref={providedDroppable.innerRef} {...providedDroppable.droppableProps}>
                                        {/* If the node has children, render them recursively */}
                                        {collapsible && !collapsed && (
                                            <Collapse in={true} timeout="auto" unmountOnExit>
                                                <List component="div" disablePadding
                                                    sx={{
                                                        pl: 4,
                                                        backgroundColor: snapshotDroppable.isDraggingOver ? 'rgba(0, 0, 0, 0.1)' : 'inherit'
                                                    }}
                                                >
                                                    {node.children.map((childId) => (
                                                        <RecursiveDragList key={childId} nodeId={childId} />
                                                    ))}
                                                </List>
                                            </Collapse>
                                        )}
                                        {providedDroppable.placeholder}
                                        </div>
                                    </>
                                )}
                            </Droppable>
                        </div>
                    )}
                </Draggable>
            </>
        )
    }


    const onDragEnd = useCallback((result, provided) => {
        console.log(result, provided)
        // const { draggableId, type, source, destination, reason, mode } = result;
        const { source, destination, draggableId } = result
        // If no destination, do nothing
        if (!destination) {
            throw new Error('No destination for drag')
        }
        // If source and destination are the same, do nothing
        if (source.droppableId === destination.droppableId && source.index === destination.index) {
            throw new Error('Source and destination are the same')
        }
        // const reorderSourceNode = nodes[draggableId]
        // const dropTargetNode = nodes[destination.droppableId]
        // const newParentNode = nodes[dropTargetNode.parent]
        // const newPosition = findNodePosition(nodes, dropTargetNode.value)
        console.log('reorder', draggableId, source, destination)
        console.log('reorder', nodes[draggableId].name, nodes[source.droppableId].name, nodes[destination.droppableId].name)
        // TODO: process source, destination and draggableId to determine what kind of reorder

        // console.log('reorder', reorderSourceNode.name, newParentNode.name, newPosition)
        // console.log('reorder', reorderSourceNode.name, newParentNode.name, newPosition)
        // let submit
        // if (reorderSourceNode.parent === newParentNode.value) {
        //     submit = jcrActions.reorderEntry(reorderSourceNode, newPosition)
        // } else {
        //     submit = jcrActions.moveEntryNested(reorderSourceNode, newParentNode, newPosition)
        // }
        // treeContext.actions.reorderNode(reorderSourceNode.value, newParentNode.value, newPosition)
        //     .then(handleSuccess)
        //     .catch(handleError)
    }, [nodes])
    return (
        <>
            <Button onClick={handleClickOpen}>
                <DragIndicatorIcon />
            </Button>
            <Dialog
                maxWidth="md"
                fullWidth
                open={open}
                onClose={handleClose}
                PaperProps={{
                    component: 'form',
                    // onSubmit: handleSubmit
                }}
            >
                <DialogTitle>Reorder questionnaire entries</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        To move an entry, please drag and drop the entry to a new position.
                    </DialogContentText>
                    {/* List here */}
                    <DragDropContext
                        // onBeforeCapture={}
                        // onBeforeDragStart={}
                        // onDragStart={}
                        // onDragUpdate={}
                        onDragEnd={onDragEnd}
                    >
                        <Droppable droppableId={rootNodeId}>
                            {(providedDroppable) => (
                                <div ref={providedDroppable.innerRef} {...providedDroppable.droppableProps}>
                                    <List dense className={classes.selectionList}>
                                        <RecursiveDragList key={rootNodeId} nodeId={rootNodeId} />
                                        {/* {nodes[rootNodeId].children.map((childId) => (
                                            <RecursiveDragList key={childId} nodeId={childId}/>
                                        ))} */}
                                    </List>
                                    {providedDroppable.placeholder}
                                </div>
                            )}
                        </Droppable>
                        {/* <Droppable droppableId={rootNodeId}>
                            {(provided) => (
                                <List
                                    sx={{ width: '100%', bgcolor: 'background.paper' }}
                                    ref={provided.innerRef}
                                // subheader={
                                //     <ListSubheader component="div" id="nested-list-subheader">
                                //         Nested List Items
                                //     </ListSubheader>
                                // }
                                >
                                    <RecursiveDragList nodeId={rootNodeId} />
                                    {provided.placeholder}
                                </List>
                            )}
                        </Droppable> */}
                    </DragDropContext>
                </DialogContent>
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

export {
    MoveEntryModal,
    DragDropMoveEntryModal
}