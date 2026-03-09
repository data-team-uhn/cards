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

import filterStyles from "./filterStyles.jsx";

const questionStyles = theme => ({
  ...filterStyles(theme),
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
  viewModeAnswers :{
    "& .MuiList-root": {
      padding: 0,
    },
    "& .MuiListItem-root" : {
      paddingTop: 0,
      paddingBottom: 0,
    }
  },
  ghostListItem: {
    paddingTop: 0,
    alignItems: "flex-start",
  },
  searchWrapper: {
    margin: 0,
    position: 'relative',
    display: 'inline-block',
    padding: theme.spacing(.5, 0, 0, 0),
  },
  nestedInput: {
    minWidth: "218px !important",
    marginLeft: theme.spacing(-2.5),
  },
  textField: {
    // Differing input types have differing widths, so setting width:100%
    // is insufficient in making sure all components are the same size
    width: "250px",
  },
  textBox: {
    // Outlined textboxes that are not part of a single select list should stretch full width
    width: "100%",
  },
  selectMultiValues: {
    whiteSpace: "normal",
    "& .MuiChip-root" : {
      margin: "1px",
    },
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
    alignItems: "baseline",
    flexWrap: "wrap",
    "& .numberRangeLimit": {
      minWidth: "110px !important",
      width: "110px",
    },
    "& .separator" : {
      padding: theme.spacing(1),
    }
  },
  hiddenQuestion: {
    display: "none"
  },
  answerInstructions: {
    margin: theme.spacing(-3,0,1),
    padding: theme.spacing(1, 0),
  },
  notesContainer: {
    whiteSpace: "pre-wrap",
    padding: theme.spacing(3, 0, 1),
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
    "& .MuiListItem-root > div:first-of-type > .MuiTextField-root" : {
      [theme.breakpoints.up('sm')]: {
        minWidth: "100px",
        marginTop: theme.spacing(-1.5),
      },
    },
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
    },
  },
  selectionChild: {
    cursor: "pointer",
    flexWrap: "wrap",
  },
  selectionDescription: {
    flexBasis: "100%",
    paddingLeft: theme.spacing(4)
  },
});

export default questionStyles;
