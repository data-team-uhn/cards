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

const liveTableStyle = theme => ({
    tableHeader: {
        fontWeight: "300"
    },
    tableActionsHeader: {
        "textAlign": "right"
    },
    filterLabel: {
        margin: theme.spacing(0, 1, 0, 0)
    },
    filterContainer: {
        padding: theme.spacing(0, 2),
    },
    addFilterButton: {
        minWidth: 0,
        padding: 0,
        borderRadius: "50%",
        height: "24px",
        width: "24px",
        margin: theme.spacing(0.5, 0)
    },
    filterChips: {
        marginRight: theme.spacing(0.5),
        marginTop: theme.spacing(0.5),
        marginBottom: theme.spacing(0.5),
        '& >span': {
            margin: theme.spacing(0.5),
        },
    },
    saveButton: {
        position: 'absolute',
        right: theme.spacing(2)
    },
    answerField: {
        width: "100%",
    },
    categoryOption: {
        whiteSpace: "normal",
        padding: theme.spacing(0, 2),
    },
    categoryHeader: {
        backgroundColor: theme.palette.background.paper,
        lineHeight: 2,
        fontSize: "1em",
        paddingTop: theme.spacing(2),
        paddingBottom: theme.spacing(1),
        color: theme.palette.primary.main,
        top: 0,
        zIndex: 1,
        position: "sticky",
        opacity: "1 !important",
    },
    hidden: {
        visibility: "hidden"
    },
    selectPlaceholder: {
        opacity: 0.3
    },
    deleteButton: {
        width: "100%"
    },
    tableActions: {
        "& .MuiIconButton-root": {
            float : "right"
        }
    },
    nestedSelectOption: {
        paddingLeft: theme.spacing(4)
    },
});

export default liveTableStyle;
