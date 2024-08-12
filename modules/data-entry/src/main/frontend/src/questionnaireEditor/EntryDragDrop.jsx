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

import React from 'react'

import { useState, useReducer, useContext } from "react";
// import { DragDropContext, Droppable, Draggable } from "react-beautiful-dnd";
import {
    Tooltip,
    Button,
    Modal,
    Snackbar,
    Alert,
    Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions,
    TextField,
} from '@mui/material'

import { useQuestionnaireReaderContext } from '../questionnaire/QuestionnaireContext';

const DEFAULT_STATE = {
    enabled: false,
    snackbar: { open: false, message: '' },
    // TODO: use questionnaire context to construct tree
    tree: null,
    entries: null
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
    const {data} = props
    const [open, setOpen] = useState(false);
    const [reorderTarget, setReorderTarget] = useState({parent: '', position: ''})
    // TODO parse as search tree to filter possible options
    const questionnaireContext = useQuestionnaireReaderContext()
    console.log(questionnaireContext)
    // Any questions that are ENTRY_TYPE (?)
    const parentOptions = []
    // For a selected parent, any number up to length of children (disabled if no parent selected)
    const positionOptions =[]

    const handleClickOpen = () => {
        setOpen(true);
    };

    const handleClose = () => {
        setOpen(false);
    };
    return (
        <>
            {/* <Tooltip title="Click to reorder entry" // onClick={() => console.log('activate modal')}
            >
            </Tooltip> */}
            <Button onClick={handleClickOpen}>
                Move
            </Button>
            <Dialog
                open={open}
                onClose={handleClose}
                PaperProps={{
                    component: 'form',
                    onSubmit: (event) => {
                        event.preventDefault();
                        console.log('reorder target', reorderTarget)
                        handleClose();
                    }
                }}
            >
                {/* TODO: put data entry info in title  */}
                <DialogTitle>Move entry</DialogTitle>    
                <DialogContent>
                    <DialogContentText>
                        To move the selected entry, please select a new parent and a new position. 
                    </DialogContentText>
                    <TextField
                        select
                        value={reorderTarget.parent}
                        onChange={e => setReorderTarget({...reorderTarget, parent: e.target.value})}
                        autoFocus
                        required
                        margin="dense"
                        id="parent"
                        name="parent"
                        label="Parent"
                        fullWidth
                        variant="standard"
                    />
                    <TextField
                        value={reorderTarget.position}
                        onChange={e => setReorderTarget({...reorderTarget, position: e.target.value})}
                        select
                        // autoFocus
                        required
                        margin="dense"
                        id="position"
                        name="position"
                        label="Position"
                        fullWidth
                        variant="standard"
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleClose}>Cancel</Button>
                    <Button type="submit">Move entry</Button>
                </DialogActions>
            </Dialog>
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
    const questionnaireContext = useQuestionnaireReaderContext()
    
    console.log('dnd provider', dndState, questionnaireContext)


    return (
        // <DragDropContext onDragEnd={onDragEnd}>
        <>
            <DndStateContext.Provider value={dndState}>
                <DndDispatchContext.Provider value={dndDispatch} {...rest} />
            </DndStateContext.Provider>
            <Snackbar
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
                open={dndState.snackbar.open}
                autoHideDuration={2000}
                onClose={() => dndDispatch({type: 'setSnackbar', payload: DEFAULT_STATE.snackbar})}
            >
                <Alert
                    severity="success"
                    variant="filled"
                    sx={{ width: '100%' }}
                >
                    {dndState.snackbar.message}
                </Alert>
            </Snackbar>
        </>
        // </DragDropContext>
    );
}