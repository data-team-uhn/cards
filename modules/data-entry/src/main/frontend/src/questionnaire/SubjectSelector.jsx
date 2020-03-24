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

import React, { useState } from "react";
import PropTypes from "prop-types";
import uuid from "uuid/v4";

import { Avatar, CircularProgress, Dialog, DialogContent, DialogTitle, List, ListItem, ListItemAvatar, ListItemText } from "@material-ui/core";
import { Input, InputAdornment, withStyles } from "@material-ui/core";
import AssignmentIcon from "@material-ui/icons/Assignment";
import AddIcon from "@material-ui/icons/Add";
import SearchIcon from "@material-ui/icons/Search";

import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

/**
 * Component that displays the list of subjects in a dialog. Double clicking a subject selects it.
 *
 * @param {open} bool Whether or not this dialog is open
 * @param {func} onChange Callback for when the user changes their selection
 * @param {func} onClose Callback for when the user closes this dialog
 * @param {func} onError Callback for when an error occurs during subject selection
 * @param {string} title Title of the dialog, if any
 */
export function SelectorDialog (props) {
  const { classes, open, onChange, onClose, onError, title, ...rest } = props;
  const [ subjects, setSubjects ] = useState([]);
  const [ newSubjects, setNewSubjects ] = useState([]);
  const [ selectedSubject, setSelectedSubject ] = useState();
  const [ isPosting, setIsPosting ] = useState();

  // Add a new subject to us to track
  let addNewSubject = () => {
    setNewSubjects((old) => {
      let updated = old.slice();
      updated.push("");
      return(updated);
    })
  }

  // Handle the user clicking on a subject, potentially submitting it
  let selectSubject = (subject) => {
    if (selectedSubject == subject) {
      // We should submit this select
      handleSubmit();
    } else {
      setSelectedSubject(subject);
    }
  }

  // Handle the SubjectSelector clicking on a subject, selecting it
  let handleSubmit = () => {
    // Submit the new subjects
    setIsPosting(true);
    createSubjects(newSubjects, selectedSubject, grabNewSubject, console.log);
  }

  // Obtain the full details on a new subject
  let grabNewSubject = (subjectPath) => {
    let url = new URL(subjectPath + ".json", window.location.origin);

    fetch(url)
      .then(response => response.ok ? response.json() : Promise.reject(response))
      .then(json => appendPath(json, subjectPath))
      .then(onChange)
      .catch(onError);
  }

  // Append the @path attribute to an object
  let appendPath = (json, path) => {
    json["@path"] = path;
    return json;
  }

  return (<Dialog open={open} onClose={onClose}>
    {title && <DialogTitle>{title}</DialogTitle>}
    <DialogContent>
      <StyledSubjectSelectorList
        disabled={isPosting}
        onAddSubject={addNewSubject}
        onChangeNewSubjects={setNewSubjects}
        onError={onError}
        onSelect={selectSubject}
        newSubjects={newSubjects}
        setSubjects={setSubjects}
        selectedSubject={selectedSubject}
        subjects={subjects}
        {...rest}
        />
    </DialogContent>
  </Dialog>);
}

/**
 * Create new subjects from an array of identifiers.
 *
 * @param {array} newSubjects The new subjects to add to the repository, as an array of strings.
 * @param {object or string} subjectToTrack The selected subject to return the URL for
 * @param {func} returnCall The callback after all subjects have been created
 * @param {func} onError The callback if an error occurs during subject creation
 */
export function createSubjects(newSubjects, subjectToTrack, returnCall, onError) {
  let selectedURL = subjectToTrack["@path"];
  let lastPromise = null;
  for (let subjectName of newSubjects) {
    let URL = "/Subjects/" + uuid();

    // If this is the subject the user has selected, make a note of the output URL
    if (subjectName == subjectToTrack) {
      selectedURL = URL;
    }

    // Make a POST request to create a new subject
    let request_data = new FormData();
    request_data.append('jcr:primaryType', 'lfs:Subject');
    request_data.append('identifier', subjectName);

    // Either chain the promise or create a new one
    if (lastPromise) {
      lastPromise.then(() => {fetch( URL, { method: 'POST', body: request_data })});
    } else {
      lastPromise = fetch( URL, { method: 'POST', body: request_data });
    }
  }
  // If we're finished creating subjects, create the rest of the form
  if (lastPromise) {
    lastPromise
      .then((response) => {returnCall(selectedURL)})
      .catch(onError);
  } else {
    returnCall(selectedURL);
  }
}

// Helper function to simplify the many kinds of subject list items
// This is outside of NewFormDialog to prevent rerenders from losing focus on the children
export function SubjectListItem(props) {
  let { avatarIcon, children, ...rest } = props;
  let AvatarIcon = avatarIcon;  // Rename to let JSX know this is a prop
  return (<ListItem
    button
    {...rest}
    >
    <ListItemAvatar>
      <Avatar><AvatarIcon /></Avatar>
    </ListItemAvatar>
    {children}
  </ListItem>);
}

SubjectListItem.defaultProps = {
  avatarIcon: AssignmentIcon
}

/**
 * Component that displays the list of subjects.
 *
 * @example
 * <Form id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {disabled} bool whether selections should be disabled on this element
 * @param {onAddSubject} func Callback for when the user wishes to add a new subject. There are no parameters.
 * @param {onChangeNewSubjects} func Callback for when the user wishes to change the list of subjects they wish to add. The parameter is the list of new subjects.
 * @param {onError} func Callback for an issue in the reading or editing of subjects. The only parameter is a response object.
 * @param {onSelect} func Callback for when the user selects a subject.
 * @param {newSubjects} array A list of subjects with editable names that the user has added this session
 * @param {selectedSubject} object The currently selected subject
 * @param {setSubjects} func A callback for setting the currently available subjects. The parameter is a list of subjects
 * @param {subjects} array A list of (potentially filtered) subjects that the user has available
 */
function SubjectSelectorList(props) {
  const { classes, disabled, onAddSubject, onChangeNewSubjects, onError, onSelect, newSubjects, selectedSubject, setSubjects, subjects, ...rest } = props;
  const [ search, setSearch ] = useState("");
  const [ delayFilterTimer, setDelayFilterTimer ] = useState();
  const [ initialized, setInitialized ] = useState();
  const [ busy, setBusy ] = useState(false);

  // Send a fetch request to get all of the subjects available to the user
  let fetchSubjects = (newSearch) => {
    let url = new URL("/query", window.location.origin);
    let term = newSearch || search; // The search term is either overridden or given in the state
    let query = "SELECT * FROM [lfs:Subject]" + (term ? ` WHERE CONTAINS(*, '*${term}*')` : "");
    url.searchParams.set("query", query);

    fetch(url)
      .then(response => response.ok ? response.json() : Promise.reject(response))
      .then(parseSubjects)
      .catch(onError)
      .finally(() => {setBusy(false);})
  }

  // Parse out only the subjects
  let parseSubjects = (data) => {
    setSubjects(data["rows"]);
  }

  // Filter the search results after a short delay
  let delayFilter = (newQuery) => {
    if (delayFilterTimer !== null) {
      clearTimeout(delayFilterTimer);
    }

    setBusy(true);
    setDelayFilterTimer(setTimeout(fetchSubjects, 500, newQuery));
  }

  if (!initialized) {
    fetchSubjects();
    setInitialized(true);
  }

  return(
    <React.Fragment>
      <Input
        value={search}
        onChange={(event) => {
          setSearch(event.target.value);
          delayFilter(event.target.value);
        }}
        endAdornment={(
          <InputAdornment position="end" onClick={() => {setBusy(true); fetchSubjects();}}>
            {busy ? <CircularProgress size={24} /> : <SearchIcon /> }
          </InputAdornment>
        )}
        className={classes.subjectFilterInput}
        />
      <List {...rest} >
        {subjects.map((subject, idx) => (
          <SubjectListItem
            key={subject["jcr:uuid"]}
            onClick={() => {onSelect(subject)}}
            disabled={disabled}
            selected={subject["jcr:uuid"] === selectedSubject?.["jcr:uuid"]}
            >
            <ListItemText primary={subject["identifier"]} />
          </SubjectListItem>
        ))}
        {newSubjects.map((subject, idx) => (
          <SubjectListItem
            key={idx}
            onClick={() => {onSelect(subject)}}
            disabled={disabled}
            selected={subject === selectedSubject}
            >
            <Input
              value={subject}
              onChange={(event) => {
                let updated = newSubjects.slice();
                updated[idx] = event.target.value;
                onSelect(event.target.value);
                onChangeNewSubjects(updated);
              }}
              />
          </SubjectListItem>
        ))}
        <SubjectListItem
          avatarIcon={AddIcon}
          onClick={onAddSubject}
          disabled={disabled}
          >
          <ListItemText primary="Add new subject" className={classes.addNewSubjectButton} />
        </SubjectListItem>
      </List>
    </React.Fragment>
  )
};

const StyledSubjectSelectorList = withStyles(QuestionnaireStyle)(SubjectSelectorList)

export default StyledSubjectSelectorList;
