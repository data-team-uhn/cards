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
import React, { useState } from 'react';

import {

    Dialog, DialogTitle,
    IconButton,
    Tooltip,
} from '@mui/material';

import MoveDownIcon from '@mui/icons-material/MoveDown';
import ReorderForm from './ReorderForm';

import { useQuestionnaireTreeContext } from './QuestionnaireTreeContext';

// If no entry data is provided then reorderSource can be selected
export function ReorderModal(props) {
    const { entryData } = props;
    const noEntryData = [undefined, null].includes(entryData);
    const [open, setOpen] = useState(false);
    const treeContext = useQuestionnaireTreeContext()
    const title = treeContext?.state?.nodes[entryData?.['jcr:uuid']]?.title

    const handleClickOpen = () => {
        setOpen(true);
    };
    const handleClickClose = () => {
        setOpen(false)
    };

    return (
        <>
            <Tooltip title={"Reorder entries"}>
                <IconButton onClick={handleClickOpen} size="large">
                    <MoveDownIcon />
                </IconButton>
            </Tooltip>

            <Dialog
                open={open} onClose={(event, reason) => { handleClickClose() }}
                maxWidth="md"
                fullWidth
            >
                <DialogTitle>
                    {noEntryData ? "Select position of an entry" : `Select position of '${title}'`}
                </DialogTitle>
                <ReorderForm
                    onClose={handleClickClose}
                    data={noEntryData ? null : entryData}
                />
            </Dialog>
        </>
    );
}
