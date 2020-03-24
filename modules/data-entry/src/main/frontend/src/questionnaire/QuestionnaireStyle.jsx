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

// Props used in grid containers for displaying Form entries
export const FORM_ENTRY_CONTAINER_PROPS = {
    direction: "column",
    spacing: 4,
    alignItems: "stretch",
    justify: "space-between",
    wrap: "nowrap"
  };

const GRID_SPACE_UNIT = FORM_ENTRY_CONTAINER_PROPS.spacing/2;

const questionnaireStyle = theme => ({
    checkbox: {
        margin: theme.spacing(-2,0),
    },
    radiobox: {
        margin: theme.spacing(-2,0),
    },
    ghostRadiobox: {
        margin: theme.spacing(0,0,-5,0),
    },
    ghostFormControl: {
        height: "0px",
    },
    ghostListItem: {
        padding: theme.spacing(0, 2, 0, 2),
    },
    searchWrapper: {
        margin: theme.spacing(0, 0, 0, 6),
        position: 'relative',
        display: 'inline-block',
        paddingBottom: "0px",
        paddingTop: theme.spacing(1)
    },
    answerField: {
        margin: theme.spacing(0, 0, 0, 6),
        position: 'relative',
    },
    textField: {
        // Differing input types have differing widths, so setting width:100%
        // is insufficient in making sure all components are the same size
        minWidth: "250px",
    },
    checkboxList: {
        padding: theme.spacing(0),
    },
    deleteButton: {
        padding: theme.spacing(1,0),
        margin: theme.spacing(-1,0,-1,-1.5),
        fontSize: "10px",
        minWidth: "42px",
    },
    mdash: {
        padding: theme.spacing(0, 1),
    },
    cardHeaderButton: {
        // No styles here yet
    },
    questionHeader: {
        paddingBottom: theme.spacing(0),
    },
    warningTypography: {
        padding: theme.spacing(1, 1),
    },
    pedigreeSmall: {
        width: "100%",
        height: "100%"
    },
    saveButton: {
        position: "fixed",
        top: 'auto',
        bottom: theme.spacing(1),
        right: theme.spacing(6.5)
    },
    newFormButtonWrapper: {
        margin: theme.spacing(1),
        position: "relative"
    },
    newFormTypePlaceholder: {
        position: 'relative',
        textAlign: 'center'
    },
    newFormLoadingIndicator: {
        position: 'absolute',
        top: '50%',
        left: '50%',
        marginLeft: "-50%",
        marginTop: "-50%"
    },
    labeledSection: {
        marginTop: theme.spacing(GRID_SPACE_UNIT)
    },
    sectionHeader: {
        paddingBottom: "0 !important",
        marginBottom: theme.spacing(-1)
    },
    collapsedSection: {
        padding: "0 !important"
    },
    addSectionButton: {
        marginTop: theme.spacing(GRID_SPACE_UNIT * 2)
    },
    childSection: {
        paddingLeft: theme.spacing(GRID_SPACE_UNIT)
    },
    entryActionIcon: {
        padding: theme.spacing(1),
        verticalAlign: "baseline",
        marginRight: theme.spacing(-1),
        float: "right",
        color: theme.palette.text.primary
    },
    recurrentSection: {
        marginLeft: theme.spacing(GRID_SPACE_UNIT),
        paddingLeft: "0 !important",
        width: "auto"
    },
    recurrentHeader: {
        marginLeft: theme.spacing(-GRID_SPACE_UNIT) + " !important",
        paddingLeft: "0 !important"
    },
    collapseWrapper: {
        // Select only questions that occur immediately after padded sections,
        // and add a large margin before them
        "& +.questionContainer": {
            marginTop: theme.spacing(GRID_SPACE_UNIT * 2)
        },
        "& .recurrentSectionInstance:not(:first-child)": {
            marginTop: theme.spacing(GRID_SPACE_UNIT * 3)
        }
    },
    recurrentSectionInstance: {
        // Select the add section button that occurs immediately after padded sections,
        // and add a large margin before it
        "& +.addSectionContainer": {
            marginTop: theme.spacing(GRID_SPACE_UNIT * 2)
        }
    },
    // When the user is deleting a section, highlight it via a border on the left
   
    highlightedSection: {
        borderLeftWidth: theme.spacing(GRID_SPACE_UNIT),
        borderLeftColor: theme.palette.primary.light,
        borderLeftStyle: "solid",
        marginLeft: theme.spacing(-GRID_SPACE_UNIT),
        marginTop: theme.spacing(GRID_SPACE_UNIT),
        marginBottom: theme.spacing(GRID_SPACE_UNIT),
        paddingTop: theme.spacing(0) + " !important",
        paddingBottom: theme.spacing(0) + " !important"
    },
    highlightedTitle: {
        color: theme.palette.primary.light,
    },
    toggleNotesContainer: {
        padding: theme.spacing(1, 0, 1, 2)
    },
    toggleNotesButton: {
        textTransform: "none"
    },
    noteSection: {
        display: "block",
        marginLeft: theme.spacing(6)
    },
    addNewSubjectButton: {
        width: "250px"
    },
    subjectFilterInput: {
        width: "100%"
    }
});

export default questionnaireStyle;
