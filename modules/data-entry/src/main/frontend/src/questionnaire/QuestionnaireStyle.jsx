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
        display: 'inline-block',
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
        right: theme.spacing(5)
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
    paddedSection: {
        marginTop: theme.spacing(2),
        // Select only questions that occur immediately after padded sections,
        // and add a large margin before them
        "& +.questionContainer": {
            marginTop: theme.spacing(4),
        }
    },
    sectionHeader: {
        paddingBottom: "0 !important",
        marginBottom: theme.spacing(-1)
    }
});

export default questionnaireStyle;
