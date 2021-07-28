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

import { green, grey } from '@material-ui/core/colors';

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
    questionCard : {
      "& .MuiCardHeader-root" : {
        padding: theme.spacing(1, 3, 0, 3),
      },
      "& .MuiCardContent-root" : {
        paddingLeft: theme.spacing(3),
        paddingRight: theme.spacing(3),
      },
      "& .MuiList-root": {
        marginLeft: theme.spacing(-2),
      },
    },
    editModeAnswers: {
      "& .MuiListItem-root:hover" : {
        background: theme.palette.action.hover,
        borderRadius: theme.spacing(0.5),
      }
    },
    viewModeAnswers :{
      paddingTop: theme.spacing(0),
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
    mdash: {
        padding: theme.spacing(0, 1),
    },
    cardHeaderButton: {
        // No styles here yet
    },
    hiddenQuestion: {
        display: "none"
    },
    answerInstructions: {
        margin: theme.spacing(-3,0,1),
        padding: theme.spacing(1, 0),
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
    dashboardEntry: {
        "& > *": {
            height: "100%",
            marginTop: theme.spacing(4),
        },
        "& .MuiTab-root": {
            width: "auto",
            minWidth: theme.spacing(10),
            paddingLeft: theme.spacing(2),
            paddingRight: theme.spacing(2),
            textTransform: "none",
         },
         "& .MuiCardContent-root": {
            padding: theme.spacing(3, 0),
         },
         "& .MuiTableCell-body": {
            padding: theme.spacing(0, 2),
         },
    },
    subjectView : {
        "& .MuiTabs-indicator": {
            background: "orange",
        },
    },
    subjectViewAvatar: {
        background: "orange",
    },
    formView: {
        "& .MuiTabs-indicator": {
            background: theme.palette.info.main,
        },
    },
    formViewAvatar: {
        background: theme.palette.info.main,
    },
    newFormTypePlaceholder: {
        position: 'relative',
        textAlign: 'center'
    },
    labeledSection: {
        marginTop: theme.spacing(GRID_SPACE_UNIT)
    },
    sectionHeader: {
        paddingBottom: "0 !important",
    },
    subjectCard: {
        minHeight: "200px",
    },
    subjectHeader: {
        position: "sticky",
        top: 0,
        paddingTop: theme.spacing(4) + 'px !important',
        backgroundColor: theme.palette.background.paper,
        zIndex: "1010",
    },
    subjectFormHeader: {
        paddingBottom: "0 !important",
    },
    subjectFormHeaderButton: {
        padding: "0 !important"
    },
    subjectAvatar : {
        backgroundColor: green[500],
    },
    subjectTitleWithAvatar: {
        marginLeft: theme.spacing(-7),
        "& a": {
          color: theme.palette.text.primary,
          textDecoration: "none",
        }
    },
    subjectContainer: {
        flexWrap: "nowrap" ,
        marginBottom: theme.spacing(4),
    },
    subjectNestedContainer: {
        marginLeft: theme.spacing(7),
        marginTop: theme.spacing(4),
        "& .MuiGrid-container:last-child" : {
          marginBottom: "0 !important",
        }
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
    notesContainer: {
        padding: theme.spacing(3, 0, 1)
    },
    toggleNotesButton: {
        textTransform: "none"
    },
    noteSection: {
        display: "block",
        marginLeft: theme.spacing(0)
    },
    formHeader: {
        position: "sticky",
        width: "100%",
        top: 0,
        paddingTop: theme.spacing(4) + 'px !important',
        backgroundColor: theme.palette.background.paper,
        opacity: 1,
        zIndex: "1010",
        margin: theme.spacing(2),
    },
    formFooter: {
        position: "sticky",
        bottom: theme.spacing(0),
        zIndex: 1000,
        maxHeight: "68px",
    },
    formStepper: {
        position: "relative",
    },
    formStepperTop: {
        bottom: "16px",
    },
    formStepperBottom: {
        background: "transparent",
        bottom: "84px",
    },
    formStepperBottomBackground: {
        background: "transparent",
    },
    formStepperTopBar: {
        backgroundColor: theme.palette.secondary.main,
    },
    paginationButton: {
        float: "right",
        margin: theme.spacing(1),
    },
    actionsMenu: {
        border: "1px solid " + theme.palette.divider,
        borderRadius: theme.spacing(3),
        display: "flex",
        float: "right",
        marginRight : theme.spacing(2),
        marginTop: theme.spacing(1)
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
    subjectSubHeader: {
        display: "block"
    },
    subjectHeaderButton: {
        float: "right",
        marginTop: theme.spacing(1),
        marginRight: theme.spacing(-0.5)
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
    invalidSubjectText: {
        fontStyle: "italic"
    },
    NCRTooltip: {
        color: theme.palette.primary.main
    },
    NCRLoadingIndicator: {
        disable: "flex"
    },
    closeButton: {
        position: 'absolute',
        right: theme.spacing(1),
        top: theme.spacing(1),
        color: theme.palette.grey[500]
    },
    dialogTitle: {
        marginRight: theme.spacing(5)
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
        color:'white',
        marginBottom: theme.spacing(1),
        marginRight: theme.spacing(1),
    },
    INCOMPLETEChip: {
        backgroundColor: theme.palette.warning.main
    },
    INVALIDChip: {
        backgroundColor: theme.palette.error.main
    },
    DefaultChip: {
        backgroundColor: theme.palette.warning.main
    },
    formPreviewQuestion: {
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
    questionnaireItemContent: {
        "&.avatarCardContent": {
           paddingLeft: theme.spacing(9),
        },
        "& table th": {
            border: "0 none",
            fontWeight: "bold",
            verticalAlign: "top",
            width: "1%",
            whiteSpace: "nowrap",
            paddingLeft: 0,
            paddingTop: 0,
        },
        "& table td": {
            border: "0 none",
            paddingLeft: 0,
            paddingTop: 0,
        }
    },
    focusedQuestionnaireItem: {
      borderColor: theme.palette.warning.light,
      borderWidth: '2px',
      borderStyle: 'solid',
    },
    fileResourceAnswerList: {
      listStyleType: 'none',
      paddingInlineStart: "0",
      marginTop: theme.spacing(3)
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
    answerOption: {
      backgroundColor: grey[200],
      borderRadius: theme.spacing(.5, 3, 3, .5),
      margin: theme.spacing(1, 0),
      "& .MuiFormControl-root" : {
        paddingTop: theme.spacing(1),
      },
      "& .MuiInput-underline:before" : {
        borderBottom: "0 none !important",
      },
      "& .MuiInput-underline:after" : {
        borderBottom: "0 none !important",
      }
    },
    answerOptionInput: {
      width: "100%",
      backgroundColor: grey[200],
      "& .MuiInputBase-input" : {
        paddingRight: theme.spacing(1),
        paddingLeft: theme.spacing(1),
      },
    },
    answerOptionButton: {
      float: "right",
    },
    specialOptionButton: {
      float: "right",
      paddingTop: theme.spacing(0.5),
      paddingRight: theme.spacing(0.5)
    },
    newOptionInput: {
      marginBottom: theme.spacing(2),
    },
    specialOptionSwitch: {
      margin: "0",
      float: "right",
    },
    optionsDragIndicator: {
      float: "left",
      padding: theme.spacing(1.5, 0.5),
      borderRadius: theme.spacing(0.5),
    },
    descriptionPopover: {
      "& .MuiPopover-paper" : {
        padding: theme.spacing(3),
        width: theme.spacing(87),
        height: theme.spacing(40),
      }
    },
    descriptionPopoverButton: {
      float: "right",
      marginTop: theme.spacing(3),
      marginLeft: theme.spacing(1),
    },
    descriptionPopoverTitle: {
      marginBottom: theme.spacing(3),
    },
    selectionChild: {
      flexWrap: "wrap",
    },
    selectionDescription: {
      flexBasis: "100%",
      paddingLeft: theme.spacing(4)
    },
    footerSection : {
      position: "sticky",
      bottom: theme.spacing(1.5),
      "& .MuiCard-root" : {
        background: theme.palette.info.light,
        marginTop: theme.spacing(-1),
        marginBottom: theme.spacing(-4),
      },
    }
});

export default questionnaireStyle;
