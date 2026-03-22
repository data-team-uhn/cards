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

import { useState, useRef, useEffect } from "react";

import AddIcon from "@mui/icons-material/Add";
import UnfoldLess from "@mui/icons-material/UnfoldLess";
import UnfoldMore from "@mui/icons-material/UnfoldMore";
import {
  Button,
  Collapse,
  Grid,
  TextField,
  Tooltip,
  Typography
} from "@mui/material";
import PropTypes from "prop-types";
import { makeStyles } from 'tss-react/mui';

import { checkPropTypes } from "../propTypes";

const useStyles = makeStyles()(theme => ({
  notesContainer: {
    whiteSpace: "pre-wrap",
    padding: theme.spacing(3, 0, 1),
  },
  toggleNotesButton: {
    textTransform: "none",
  },
  noteSection: {
    "& .MuiTextField-root" :{
      width: "100%",
    },
  },
}));

function Note (props) {
  checkPropTypes(Note, props);
  const {
    answerPath,
    children,
    existingAnswer,
    onChangeNote,
    pageActive,
    fullSize,
    value,
    readonly,
    // eslint-disable-next-line no-unused-vars
    onAddSuggestion,
    placeholder = "Please place any additional notes here.",
    ...rest
  } = props;

  let [ note, setNote ] = useState((existingAnswer?.[1]?.note));
  let [ visible, setVisible ] = useState(Boolean(note));
  let inputRef = useRef();
  let classes = useStyles();

  // This allows setting the note contents programatically via the `value` prop
  useEffect(() => {
    if (typeof(value) != "undefined") {
      setNote(value);
      setVisible(!!value);
    }
  }, [value]);

  useEffect(() => onChangeNote?.(note), [note]);

  const noteIsEmpty = (note == null || note == "");

  // Render nothing but keep state if this page is inactive
  if (!pageActive) {
    return <></>;
  }

  if (readonly) {
    return (note &&
      <div className={classes.notesContainer}>
        <Typography variant="subtitle1">Notes</Typography>
        { note }
      </div>
    );
  }

  return (<>
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
      onEntered = {() => inputRef?.current?.focus()}
    >
      <Grid container spacing={2}>
        <Grid size={fullSize ? 12 : 6} className = {classes.noteSection}>
          <TextField
            value = {note}
            onChange = {(event) => setNote(event?.target?.value)}
            variant = "outlined"
            multiline
            rows = {fullSize ? 16 : 4}
            placeholder = {placeholder}
            inputRef = {inputRef}
            {...rest}
          />
        </Grid>
        <Grid size={fullSize ? 12 : 6}>
          {children}
        </Grid>
      </Grid>
    </Collapse>
    {noteIsEmpty ?
      <input type="hidden" name={`${answerPath}/note@Delete`} value="0" />
      : <input type="hidden" name={`${answerPath}/note`} value={note} />}
  </>);
}

Note.propTypes = {
  answerPath: PropTypes.string,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  existingAnswer: PropTypes.array,
  onChangeNote: PropTypes.func,
  pageActive: PropTypes.bool,
  fullSize: PropTypes.bool,
  placeholder: PropTypes.string,
  value: PropTypes.string,
};

export default Note;
