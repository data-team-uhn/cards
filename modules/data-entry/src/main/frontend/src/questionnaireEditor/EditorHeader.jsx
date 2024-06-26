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

import React, { useEffect, useRef, useState } from "react";
import PropTypes from "prop-types";

import {
    Chip,
    Popover,
    Stack,
    Typography,
} from "@mui/material";


import { useQuestionnaireTreeContext } from "./QuestionnaireTreeContext.jsx";

import { ENTRY_TITLE_FIELD_SPEC } from "./QuestionnaireTreeContext.jsx";
import { CONDITIONAL_TYPES } from "../questionnaire/FormEntry.jsx";
import { stripCardsNamespace } from "../questionnaire/QuestionnaireUtilities.jsx";

/**
 * 
 */
function EditorHeader(props) {
    const treeContext = useQuestionnaireTreeContext()

    const { state: { warnings, nodes } } = treeContext

    const [anchorEl, setAnchorEl] = React.useState(null);

    const handlePopoverOpen = (entryType) => {
        setAnchorEl(entryType);
    };

    const handlePopoverClose = () => {
        setAnchorEl(null);
    };

    if (!warnings || nodes.length === 0) {
        return null
    }

    const missingTitlesByEntryType = Object.entries(warnings.missingTitles).reduce((acc, [id, jcrData]) => {
        const entryType = jcrData['jcr:primaryType']
        if (!acc[entryType]) {
            acc[entryType] = []
        }
        acc[entryType].push(jcrData)
        return acc
    }, {})

    return (
        <>
            <Stack direction="row" spacing={2}>
                {
                    Object.entries(ENTRY_TITLE_FIELD_SPEC)
                        .filter(([entryType, entrySpec]) =>
                            !CONDITIONAL_TYPES.includes(entryType)
                        )
                        .filter(([entryType, entrySpec]) =>
                            Object.keys(warnings.countEntryTypes).includes(entryType)
                        )
                        .map(([entryType, entrySpec]) => {
                            const { icon, color } = entrySpec
                            const totalCount = warnings.countEntryTypes[entryType]
                            // MUI Chip to display count
                            return (
                                <React.Fragment key={entryType}>
                                    <Chip label={`${totalCount} ${stripCardsNamespace(entryType)}${totalCount > 1 ? 's' : ''}`}
                                        onMouseEnter={handlePopoverOpen}
                                        onMouseLeave={handlePopoverClose}
                                        variant='outlined'
                                        size='small'
                                        style={{
                                            // TODO: Color is taken from entry spec, move into styles somehow 
                                            background: '#fff',
                                            border: `2px solid ${color || "black"}`,
                                            color: `${color}`,
                                            fontWeight: 'bold'
                                        }}
                                    />
                                    <Popover open={anchorEl === entryType}>
                                        <Typography>{`${missingTitlesByEntryType[entryType]} missing titles`}</Typography>
                                    </Popover>
                                </React.Fragment>
                            )
                        })
                }
            </Stack>
        </>
    )
};

export default EditorHeader;
