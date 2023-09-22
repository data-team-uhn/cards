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

import { orange, grey } from '@mui/material/colors';

// Props used in grid containers for displaying Form entries
export const FORM_ENTRY_CONTAINER_PROPS = {
    direction: "column",
    spacing: 4,
    alignItems: "stretch",
    justifyContent: "space-between",
    wrap: "nowrap"
  };

export const GRID_SPACE_UNIT = FORM_ENTRY_CONTAINER_PROPS.spacing/2;

const questionnaireStyle = theme => ({
    questionCard : {
	  overflow: "unset",
      "& .MuiCardHeader-root" : {
        padding: theme.spacing(1, 3, 0, 3),
        "& h6 ol" : {
           paddingLeft: 0,
        },
        "& h6 li" : {
           listStylePosition: "inside",
        },
      },
      "& .MuiCardContent-root" : {
        paddingLeft: theme.spacing(3),
        paddingRight: theme.spacing(3),
      },
      "& .MuiList-root": {
        marginLeft: theme.spacing(-2),
      },
      "& .cards-answerInstructions" : {
        margin: theme.spacing(-3,0,1),
        padding: theme.spacing(1, 0),
      },
    },
    hideAnswerInstructions: {
      "& .cards-answerInstructions" : {
        display: "none",
      }
    },
    editModeAnswers: {
      "& .MuiListItem-root:hover" : {
        background: theme.palette.action.hover,
        borderRadius: theme.spacing(0.5),
      }
    },
    informationCard: {
      "& .MuiCardContent-root" : {
        padding: theme.spacing(1.5, 2),
      },
    },
    viewModeAnswers :{
      "& .MuiList-root": {
        padding: theme.spacing(0),
      },
      "& .MuiListItem-root" : {
        paddingTop: theme.spacing(0),
        paddingBottom: theme.spacing(0),
      }
    },
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
        paddingTop: theme.spacing(0),
        alignItems: "flex-start",
    },
    searchWrapper: {
        margin: theme.spacing(0),
        position: 'relative',
        display: 'inline-block',
        padding: theme.spacing(.5, 0, 0, 0),
    },
    answerField: {
        position: 'relative',
        width: "100%",
    },
    answerDateField: {
        top: theme.spacing(-2),
        width: "100%",
    },
    nestedInput: {
        minWidth: "218px !important",
        marginLeft: theme.spacing(-2.5),
    },
    textField: {
        // Differing input types have differing widths, so setting width:100%
        // is insufficient in making sure all components are the same size
        minWidth: "250px",
    },
    selectMultiValues: {
        whiteSpace: "normal",
        "& .MuiChip-root" : {
          margin: "1px",
        },
    },
    noteTextField: {
        width: "100%",
    },
    optionsList: {
        padding: theme.spacing(0),
    },
    deleteButton: {
        padding: theme.spacing(1,0),
        margin: theme.spacing(-1,0,-1,-1.5),
        fontSize: "10px",
        minWidth: "42px",
        "& + div" : {
            marginRight: theme.spacing(2),
        },
    },
    range: {
        display: "flex",
        alignItems: "flex-start",
        "& .MuiInputBase-root" : {
            minWidth: "110px !important",
            width: "110px",
        },
        "& .separator" : {
            padding: theme.spacing(0.5, 1),
        }
    },
    cardHeaderButton: {
        // No styles here yet
    },
    hiddenQuestion: {
        display: "none"
    },
    thumbnail: {
        border: "1px solid " + theme.palette.divider,
    },
    thumbnailLink: {
        cursor: "pointer",
        "& div:hover" : {
          borderColor: "inherit !important",
        }
    },
    formBottom: {
        minHeight: theme.spacing(8),
    },
    dashboardContainer: {
        marginTop: theme.spacing(2),
        marginBottom: theme.spacing(6),
    },
    dashboardEntry: {
        "& > *": {
            height: "100%",
        },
        "& .MuiCardHeader-root" : {
            paddingBottom: 0,
        },
        "& .MuiCardHeader-avatar" : {
            zoom: "75%",
            marginTop: theme.spacing(-1.5),
        },
        "& .MuiTab-root": {
            width: "auto",
            minWidth: theme.spacing(10),
            paddingBottom: theme.spacing(1.5),
            paddingLeft: theme.spacing(2),
            paddingRight: theme.spacing(2),
            textTransform: "none",
            fontSize: "150%",
            letterSpacing: "unset",
         },
         "& .MuiTabs-indicator" : {
             height: theme.spacing(.5),
         },
         "& .MuiCardContent-root": {
            padding: theme.spacing(3, 0, 1, 0),
         },
         "& .MuiTableCell-body": {
            padding: theme.spacing(0, 2),
         },
    },
    subjectView : {
        "& .MuiTabs-indicator": {
            background: theme.palette.secondary.main,
        },
    },
    subjectViewAvatar: {
        background: theme.palette.secondary.main,
    },
    formView: {
        "& .MuiTabs-indicator": {
            background: theme.palette.primary.main,
        },
    },
    formViewAvatar: {
        background: theme.palette.primary.main,
    },
    newFormTypePlaceholder: {
        position: 'relative',
        textAlign: 'center'
    },
    sectionHeader: {
        paddingBottom: "0 !important",
        "& > h5" : {
          padding: theme.spacing(1, GRID_SPACE_UNIT),
          background: theme.palette.action.hover,
          borderBottom: "1px solid transparent",
        },
        "& > .MuiTypography-caption" : {
          padding: theme.spacing(0, GRID_SPACE_UNIT),
        }
    },
    subjectAvatar : {
        backgroundColor: theme.palette.secondary.main,
        marginLeft: theme.spacing(-1),
        marginRight: theme.spacing(1.5),
        zoom: .75,
    },
    subjectFormAvatar : {
        backgroundColor: theme.palette.primary.main,
        marginTop: theme.spacing(-.5),
        marginRight: theme.spacing(1.5),
        zoom: .75,
    },
    childSubjectHeader: {
        marginLeft: theme.spacing(-5),
        "& > *" : {
          alignItems: "center",
        },
    },
    subjectContainer: {
        flexWrap: "nowrap" ,
        marginBottom: theme.spacing(4),
    },
    subjectNestedContainer: {
        marginLeft: theme.spacing(5),
        "& .MuiGrid-container:last-child" : {
          marginBottom: "0 !important",
        }
    },
    circularProgressContainer: {
        marginTop: theme.spacing(5),
    },
    compactLayout : {
      "& .MuiList-root" : {
        [theme.breakpoints.up('sm')]: {
          display: "inline-block",
        },
      },
      "& .MuiListItem-root" : {
        [theme.breakpoints.up('sm')]: {
          display: "inline-flex",
          width: "auto",
        },
      },
      "& .MuiListItem-root > div:first-child > .MuiTextField-root" : {
        [theme.breakpoints.up('sm')]: {
          minWidth: "100px",
          marginTop: theme.spacing(-1.5),
        },
      },
    },
    subjectTabs: {
      "& .MuiTab-root" : {
        minWidth: "auto",
        textTransform: "initial",
      },
      "& .MuiTabs-indicator": {
        background: theme.palette.secondary.main,
      },
    },
    timelineContainer: {
        alignItems: "center",
    },
    timeline: {
        maxWidth: "1000px",
        margin: "auto"
    },
    timelineContent: {
        padding: theme.spacing(1,3,3),
    },
    timelinePaper: {
        padding: theme.spacing(1,2),
    },
    timelineAncestor: {
        opacity: 0.3,
        "&:hover": {
          opacity: 1,
        },
    },
    timelineDate: {
        lineHeight: "1em",
        marginLeft: theme.spacing(-2),
        marginRight: theme.spacing(-2),
        color: theme.palette.primary.main
    },
    timelineDateEntry: {
        paddingBottom: theme.spacing(2),
    },
    timelineDateEntryFinal: {
        paddingBottom: 0,
    },
    timelineConnectorGroup: {
        display: "flex",
        alignItems: "center",
        flexDirection: "column",
        flexGrow: 1
    },
    timelineConnectorLine: {
        backgroundColor: theme.palette.grey["200"]
    },
    timelineCircle: {
        background: theme.palette.grey["200"],
        position: "absolute",
        top: "50%",
        transform: "translateY(calc(-50% + 14px))",
        width: "35px",
        height: "35px",
        borderRadius: "50%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        "&:before": {
            content: "''",
            display: "block",
            borderBottom: "12px solid",
            borderBottomColor: theme.palette.grey["200"],
            borderLeft: "12px solid transparent",
            borderRight: "12px solid transparent",
            position: "absolute",
            top: "-7px"
        },
        "&:after": {
            content: "''",
            display: "block",
            borderTop: "12px solid",
            borderTopColor: theme.palette.grey["200"],
            borderLeft: "12px solid transparent",
            borderRight: "12px solid transparent",
            position: "absolute",
            bottom: "-7px"
        }
    },
    timelineSeparator: {
        minHeight: theme.spacing(12)
    },
    collapsedSection: {
        padding: "0 !important"
    },
    hiddenSection: {
        display: "none"
    },
    headerSection : {
      "&.cards-edit-section" : {
        position: "sticky",
        top: 0,
        zIndex: 2,
        marginTop: theme.spacing(2*GRID_SPACE_UNIT),
        paddingTop: 0,
      },
      "& > .MuiCollapse-wrapper" : {
        border: "1px solid " + theme.palette.primary.light,
      },
      "& .MuiGrid-item:not(:first-child)": {
        paddingTop: 0,
      },
      "& .MuiGrid-item:not(:last-child)": {
        paddingBottom: 0,
      },
      "& .MuiGrid-item:not(.MuiCollapse-container) > *": {
        background: grey[100],
      },
      "& .MuiCard-root" : {
        borderColor: "transparent",
      },
    },
    footerSection : {
      "&.cards-edit-section" : {
        position: "sticky",
        bottom: 0,
        paddingBottom: "0 !important",
      },
      "& > .MuiCollapse-wrapper" : {
        border: "1px solid " + theme.palette.primary.light,
      },
      "& .MuiGrid-item:not(:first-child)": {
        paddingTop: 0,
      },
      "& .MuiGrid-item:not(:last-child)": {
        paddingBottom: 0,
      },
      "& .MuiGrid-item:not(.MuiCollapse-container) > *": {
        background: grey[100],
      },
      "& .MuiCard-root" : {
        borderColor: "transparent",
      },
    },
    entryActionIcon: {
        float: "right",
        marginRight: theme.spacing(1),
    },
    recurrentSectionInstance: {
        marginBottom: theme.spacing(2*GRID_SPACE_UNIT),
    },
    // When the user is deleting a section, highlight it with a border
    highlightedSection: {
        "& .MuiGrid-item > .MuiCard-root, .MuiGrid-item > .MuiTypography-h5": {
          borderColor: theme.palette.warning.main,
          boxShadow: `1px 1px 2px ${theme.palette.warning.main}`,
        },
    },
    notesContainer: {
        whiteSpace: "pre-wrap",
        padding: theme.spacing(3, 0, 1)
    },
    toggleNotesButton: {
        textTransform: "none"
    },
    noteSection: {
        display: "block",
        marginLeft: theme.spacing(0)
    },
    formFooter: {
        position: "relative",
    },
    hiddenFooter: {
        display: "none",
    },
    formStepper: {
        position: "relative",
        margin: theme.spacing(0, -2),
        "& .MuiMobileStepper-progress" : {
          width: "100%",
        },
    },
    only_next: {
        marginLeft: 0,
    },
    paginationButton: {
        float: "right",
        margin: theme.spacing(1),
        minWidth: "fit-content",
    },
    formStepperBufferBar: {
        backgroundColor: theme.palette.secondary.main,
    },
    formStepperBackgroundBar: {
        backgroundColor: theme.palette.primary.light,
        opacity: "0.5",
        animation: "none",
        backgroundImage: "none",
    },
    actionsMenu: {
        border: "1px solid " + theme.palette.divider,
        borderRadius: theme.spacing(3),
        display: "flex",
        marginBottom: theme.spacing(1),
    },
    actionsMenuItem: {
        padding: theme.spacing(0,1),
        "& .MuiButtonBase-root" : {
          fontWeight: "normal",
          justifyContent: "flex-start",
          textTransform: "none",
          width: "100%",
       }

    },
    childSubjectHeaderButton: {
        left: theme.spacing(1)
    },
    addNewSubjectButton: {
        width: "250px"
    },
    subjectFilterInput: {
        width: "100%"
    },
    deleteText: {
        fontSize: "15px",
        color: theme.palette.error.main,
    },
    createNewSubjectButton: {
        marginRight: 'auto',
    },
    newSubjectInput: {
        padding: theme.spacing(3, 3, 5),
    },
    subjectFilter: {
      marginTop: 0,
    },
    invalidSubjectText: {
        fontStyle: "italic"
    },
    NCRLoadingIndicator: {
        disable: "flex"
    },
    dialogContentWithTable: {
        padding: 0,
        "& .MuiPaper-root": {
          boxShadow: "0 none",
        },
        "& .MuiPaper-root > .MuiToolbar-root" : {
          paddingRight: theme.spacing(3),
        },
        "& .MuiTableCell-root" : {
          padding: theme.spacing(2, 3),
        },
        "& .MuiTableCell-footer" : {
          paddingRight: theme.spacing(1),
          paddingBottom: 0,
        },
    },
    fileInfo: {
        padding: theme.spacing(1),
        fontFamily: "'Roboto', 'Helvetica', 'Arial', sans-serif",
    },
    subjectChip: {
        marginBottom: theme.spacing(1),
        marginRight: theme.spacing(1),
    },
    INCOMPLETEChip: {
    },
    INVALIDChip: {
        borderColor: theme.palette.error.main,
        color: theme.palette.error.main,
    },
    DefaultChip: {
    },
    formPreview: {
        padding: theme.spacing(1),
        background: theme.palette.action.hover,
    },
    formPreviewQuestion: {
        display: "flex",
    },
    formPreviewSeparator: {
        margin: theme.spacing(0, 1.5),
    },
    formPreviewAnswer: {
        fontWeight: 200,
        "& .MuiChip-root" : {
            margin: "0 0.5em 0.5em 0",
            "& .MuiChip-iconSmall": {
                marginLeft: "6px",
            },
            "& a" : {
                color: "inherit",
                textDecoration: "none",
            },
        },
    },
    questionnaireDisabledListItem: {
        color: theme.palette.grey["500"]
    },
    questionnaireListItem: {
        color: theme.palette.grey["900"]
    },
    focusedQuestionnaireItem: {
      "&.MuiCard-root, > .MuiCard-root" : {
        outline: `2px solid ${theme.palette.primary.main}`,
      },
    },
    questionnaireItemWithError: {
      "&.MuiCard-root, > .MuiCard-root" : {
        outline: `1px solid ${theme.palette.error.light}`,
      },
      "& p[class*='-answerInstructions']" : {
        display: "block",
      }
    },
    fileResourceAnswerList: {
      listStyleType: 'none',
      paddingInlineStart: "0",
      marginTop: theme.spacing(3),
      "& > li" : {
        display: "flex",
        alignItems: "center",
      },
      "& > li > .MuiIconButton-root" : {
        marginLeft: theme.spacing(0.5),
      },
    },
    fileResourceAnswerInput: {
      marginTop: theme.spacing(-1.25),
      marginBottom: theme.spacing(3)
    },
    fileResourceDeleteButton: {
      margin: "0"
    },
    warningStatus: {
      color: theme.palette.warning.main
    },
    selectionChild: {
      cursor: "pointer",
      flexWrap: "wrap",
    },
    selectionDescription: {
      flexBasis: "100%",
      paddingLeft: theme.spacing(4)
    },
    questionMatrixView: {
      width: "auto",
      "& th, td" : {
        border: "none",
        padding: theme.spacing(1, 1, 1, 0),
        verticalAlign: "baseline",
      },
      "& td" : {
        fontSize: "1rem",
        fontWeight: "300 !important",
        padding: theme.spacing(1),
      },
    },
    questionMatrixControls: {
      "& .MuiTableHead-root th": {
        fontWeight: "bold",
        paddingTop: 0,
        verticalAlign: "top",
      },
      "& .MuiTableBody-root th": {
        paddingLeft: 0,
      },
      "& .MuiTableBody-root tr:last-child .MuiTableCell-root": {
        border: "0 none",
      },
    },
    questionMatrixHorizontal: {
      "& .MuiFormControlLabel-root" : {
        margin: "0 !important",
      },
      "& .MuiFormControlLabel-label, .MuiFormControlLabel-root + .MuiTypography-root": {
        display: "none",
      },
    },
    questionMatrixVertical: {
      display: "block",
      "& .MuiTableBody-root, .MuiTableRow-root, .MuiTableCell-root" : {
        display: "block",
        width: "100%",
      },
      "& td": {
        padding: theme.spacing(1, 0),
      },
    },
    questionMatrixStackedAnswer : {
      "&:not(:first-child) th" : {
        paddingTop: theme.spacing(3),
      },
      "& td" : {
        padding: 0,
      },
      "&:not(:last-child) td" : {
        paddingBottom: theme.spacing(3),
        borderBottom: "1px solid " + theme.palette.divider,
      }
    },
    questionMatrixFullEntry : {
      "& th" : {
        paggingBottom: theme.spacing(3),
      },
      "& th, td:not(:last-child)": {
        border: "0 none",
      },
      "&:not(:last-child) td:last-child" : {
        paddingBottom: theme.spacing(4),
      },
      "&:not(:first-child) th": {
        paddingTop: theme.spacing(4),
      },
    },
});

export default questionnaireStyle;
