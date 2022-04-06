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

import React, { useState, useRef } from "react";
import PropTypes from "prop-types";

import { Button, Collapse, Grid, TextField, Tooltip } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import AddIcon from "@mui/icons-material/Add";
import UnfoldMore from "@mui/icons-material/UnfoldMore";
import UnfoldLess from "@mui/icons-material/UnfoldLess";

import QuestionnaireStyle from "./QuestionnaireStyle";

function Note (props) {
  const { answerPath, children, existingAnswer, classes, onAddSuggestion, onChangeNote, pageActive, externalNote, ...rest } = {...props};
  let [ note, setNote ] = useState((existingAnswer?.[1]?.note));
  let [ visible, setVisible ] = useState(Boolean(note));
  let inputRef = useRef();

  let changeNote = (event) => {
    onChangeNote && onChangeNote(event.target.value);
    setNote(event.target.value);
  }

  let focusInput = () => {
    inputRef.current && inputRef.current.focus();
  }

  const noteIsEmpty = (note == null || note == "") && (!externalNote);

  // Render nothing but keep state if this page is inactive
  if (!pageActive) {
    return <></>;
  }

  return (<React.Fragment>
    <div className = {classes.notesContainer}>
      <Tooltip
        title = {visible ? "Hide notes" : (noteIsEmpty ? "Add notes" : "Show notes")}
        >
        <Button
          variant = "text"
          className = {classes.toggleNotesButton}
          onClick = {() => {
            setVisible(!visible);
          }}
          startIcon = {visible ?
            <UnfoldLess fontSize="small" />
            : (noteIsEmpty ? <AddIcon fontSize="small" /> : <UnfoldMore fontSize="small" />)
          }
          >
          Notes
        </Button>
      </Tooltip>
    </div>
    <Collapse
      in = {visible}
      onEntered = {focusInput}
      >
      <Grid container spacing={2}>
        <Grid item xs={6}>
          <TextField
            value = {note || externalNote}
            onChange = {changeNote}
            variant = "outlined"
            multiline
            rows = "4"
            className = {classes.noteSection}
            InputProps = {{
              className: classes.noteTextField
            }}
            placeholder = "Please place any additional notes here."
            inputRef = {inputRef}
            {...rest}
            />
          </Grid>
          <Grid item xs={6}>
            {children}
          </Grid>
        </Grid>
    </Collapse>
    {noteIsEmpty ?
      <input type="hidden" name={`${answerPath}/note@Delete`} value="0" />
      : <input type="hidden" name={`${answerPath}/note`} value={note || externalNote} />}
  </React.Fragment>);
}

export default withStyles(QuestionnaireStyle)(Note);
