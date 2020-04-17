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

import React, { useCallback, useRef, useState } from "react";
import PropTypes from "prop-types";
import uuid from "uuid/v4";

import { Avatar, Button, Dialog, DialogActions, DialogContent, DialogTitle, ListItem, ListItemAvatar, withStyles } from "@material-ui/core";
import AssignmentIndIcon from "@material-ui/icons/AssignmentInd";
import MaterialTable from "material-table";

import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

const NUM_SUBJECTS_VISIBLE_DEFAULT = 10;

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
        onError={onError}
        onSelect={selectSubject}
        setSubjects={setSubjects}
        selectedSubject={selectedSubject}
        subjects={subjects}
        {...rest}
        />
    </DialogContent>
    <DialogActions>
      <Button
        onClick={handleSubmit}
        variant="contained"
        >
        Confirm
      </Button>
    </DialogActions>
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
    // Do not allow blank subjects
    if (subjectName == "") {
      continue;
    }

    let URL = "/Subjects/" + subjectName;

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
  avatarIcon: AssignmentIndIcon
}

/**
 * Component that displays the list of subjects.
 *
 * @example
 * <Form id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {disabled} bool whether selections should be disabled on this element
 * @param {onDelete} func Callback for the deletion of a subject. The only parameter is the subject deleted.
 * @param {onError} func Callback for an issue in the reading or editing of subjects. The only parameter is a response object.
 * @param {onSelect} func Callback for when the user selects a subject.
 * @param {selectedSubject} object The currently selected subject
 * @param {setSubjects} func A callback for setting the currently available subjects. The parameter is a list of subjects
 * @param {subjects} array A list of (potentially filtered) subjects that the user has available
 */
function SubjectSelectorList(props) {
  const { allowAddSubjects, allowDeleteSubjects, classes, disabled, onDelete, onEdit, onError, onSelect, selectedSubject,
      setSubjects, subjects, theme, ...rest } = props;
  const COLUMNS = [
    { title: 'Identifier', field: 'identifier' },
  ];

  let createQueryURL = (query) => {
    let url = new URL("/query", window.location.origin);
    url.searchParams.set("query", "SELECT * FROM [lfs:Subject] as n" + query);
    return url;
  }

  return(
    <React.Fragment>
      <MaterialTable
        title=""
        columns={COLUMNS}
        data={query => {
            let url = createQueryURL(query.search ? ` WHERE CONTAINS(n.identifier, '*${query.search}*')` : "");
            url.searchParams.set("limit", query.pageSize);
            url.searchParams.set("offset", query.page*query.pageSize);
            return fetch(url)
              .then(response => response.json())
              .then(result => {
                return {
                  data: result["rows"],
                  page: Math.trunc(result["offset"]/result["limit"]),
                  totalCount: result["totalrows"],
                }}
              )
          }
        }
        editable={{
          onRowAdd: (allowAddSubjects ? newData => {
            // Do not allow blank subjects
            if (!newData["identifier"]) {
              onError("You cannot create a blank subject");
              return Promise.resolve();
            }

            // Prevent the user from creating a user that already exists
            let check_already_exists_url = new URL("/Subjects/" + newData["identifier"], window.location.origin);

            // Add the new data
            let url = new URL("/Subjects/" + newData["identifier"], window.location.origin);

            // Make a POST request to create a new subject
            let request_data = new FormData();
            request_data.append('jcr:primaryType', 'lfs:Subject');
            request_data.append('identifier', newData["identifier"]);

            let check_url = createQueryURL(` WHERE n.'identifier'='${newData["identifier"]}'`);
            return fetch( check_already_exists_url )
              .then ( (response) => {
                if (response.ok) {
                  onError("Subject already exists");
                  return Promise.reject();
                }
              })
              .then( () => (
                fetch( url, { method: 'POST', body: request_data })
                  .then( () => (
                    // Continually attempt to query the newly inserted data until we are certain it is findable
                    new Promise((resolve, reject) => {
                      let checkForNew = () => {
                        fetch(check_url)
                        .then((response) => response.ok ? response.json() : Promise.reject(response))
                        .then((json) => {
                          if (json.returnedrows > 0) {
                            onError();
                            resolve();
                          } else {
                            Promise.reject(response);
                          }
                        })
                        .catch((error) => {console.log(error); setTimeout(checkForNew, 1000)});
                      }
                      setTimeout(checkForNew, 1000);
                    }))
                  )
                )
              );
          } : undefined),
          onRowDelete: (allowDeleteSubjects ? oldData => {
            // Get the URL of the old data
            let url = new URL(oldData["@path"], window.location.origin);

            // Make a POST request to delete the given subject
            let request_data = new FormData();
            request_data.append(':operation', 'delete');
            onDelete(oldData);
            return fetch( url, { method: 'POST', body: request_data })
            } : undefined),
        }}
        options={{
          search: true,
          actionsColumnIndex: -1,
          addRowPosition: 'first',
          rowStyle: rowData => ({
            /* It doesn't seem possible to alter the className from here */
            backgroundColor: (selectedSubject?.["identifier"] === rowData["identifier"]) ? theme.palette.grey["200"] : theme.palette.background.default
          })
        }}
        localization={{
          body: {
            addTooltip: "Add a new subject",
            editRow: {
              /* NB: We can't escape the h6 placed around the delete text, so we instead override the style */
              deleteText: <span className={classes.deleteText}>Are you sure you want to delete this row?</span>
            }
          }
        }}
        onRowClick={(event, rowData) => {onSelect(rowData)}}
      />
    </React.Fragment>
  )
};

const StyledSubjectSelectorList = withStyles(QuestionnaireStyle, {withTheme: true})(SubjectSelectorList)

export default StyledSubjectSelectorList;
