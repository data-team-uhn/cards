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
import uuid from "uuid/v4";

import { Avatar, Button, Dialog, DialogActions, DialogContent, DialogTitle, Input, ListItem, ListItemAvatar, Typography, withStyles } from "@material-ui/core";
import AssignmentIndIcon from "@material-ui/icons/AssignmentInd";
import MaterialTable from "material-table";

import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

/***
 * Create a URL that checks for the existance of a subject
 */
let createQueryURL = (query, type) => {
  let url = new URL("/query", window.location.origin);
  url.searchParams.set("query", `SELECT * FROM [${type}] as n` + query);
  return url;
}

/**
 * Component that displays a dialog to create a new subject
 *
 * @param {array} allowedTypes A collection of lfs:SubjectTypes that are allowed to be chosen.
 * @param {bool} disabled If true, all controls are disabled
 * @param {string} error Error message to display
 * @param {bool} open If true, this dialog is open
 * @param {func} onClose Callback fired when the user tries to close this dialog
 * @param {func} onChangeSubject Callback fired when the user changes the name of the subject
 * @param {func} onChangeType Callback fired when the user selects a subject type
 * @param {func} onSubmit Callback fired when the user clicks the "Create" or "Continue" button
 * @param {bool} requiresParents If true, the button to continue will read "Continue" instead of "Create"
 * @param {string} value The current name of the subject
 */
function UnstyledNewSubjectDialog (props) {
  const { allowedTypes, classes, disabled, error, open, onClose, onChangeSubject, onChangeType, onSubmit, requiresParents, theme, value } = props;
  const [ selectedType, setSelectedType ] = useState();

  const COLUMNS = [
    { title: 'Subject type', field: 'label' },
  ];

  let changeType = (type) => {
    onChangeType(type);
    setSelectedType(type);
  }

  // Auto-select if there's only one valid SubjectType
  if (allowedTypes?.length === 1 && !selectedType) {
    changeType(allowedTypes[0]);
  }

  return(
    <Dialog open={open} onClose={onClose} className={classes.newSubjectPopper}>
      <DialogTitle id="new-form-title">
        Create new subject
      </DialogTitle>
      <DialogContent dividers className={classes.NewFormDialog}>
        { error && <Typography color="error">{error}</Typography>}
        <Input
          autoFocus
          disabled={disabled}
          value={value}
          onChange={onChangeSubject}
          className={classes.newSubjectInput}
          placeholder={"Enter subject identifier here"}
          />
        <MaterialTable
          title="Select a type"
          columns={COLUMNS}
          data={allowedTypes ? allowedTypes :
            query => {
              let url = createQueryURL(query.search ? ` WHERE CONTAINS(n.label, '*${query.search}*')` : "", "lfs:SubjectType");
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
          options={{
            search: true,
            addRowPosition: 'first',
            rowStyle: rowData => ({
              /* It doesn't seem possible to alter the className from here */
              backgroundColor: (selectedType?.["label"] === rowData["label"]) ? theme.palette.grey["200"] : theme.palette.background.default
            })
          }}
          onRowClick={(event, rowData) => {
            changeType(rowData);
          }}
        />
      </DialogContent>
      <DialogActions>
        <Button
          onClick={onClose}
          variant="contained"
          color="default"
          disabled={disabled}
          >
          Cancel
        </Button>
        <Button
          onClick={onSubmit}
          variant="contained"
          color="primary"
          disabled={disabled}
          >
          {requiresParents ? "Continue" : "Create"}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export const NewSubjectDialog = withStyles(QuestionnaireStyle, {withTheme: true})(UnstyledNewSubjectDialog)

/**
 * Component that displays a dialog to select parents for a new subject
 *
 * @param {object} childType The object representing the lfs:SubjectType of the child that is being created
 * @param {bool} disabled If true, all controls are disabled
 * @param {string} error Error message to display
 * @param {bool} isLast If true, the button to continue will read "Continue" instead of "Create"
 * @param {bool} open If true, this dialog is open
 * @param {func} onBack Callback fired when the user clicks the "Back" button
 * @param {func} onChangeParent Callback fired when the user changes the parent subject
 * @param {func} onClose Callback fired when the user tries to close this dialog
 * @param {func} onSubmit Callback fired when the user clicks the "Create" or "Continue" button
 * @param {object} parentType The object representing the lfs:SubjectType of the parent that is being selected
 * @param {ref} tableRef Pass a reference to the MaterialTable object
 * @param {object} value The currently selected parent
 */
function UnstyledSelectParentDialog (props) {
  const { classes, childType, disabled, error, isLast, open, onBack, onChangeParent, onClose, onSubmit, parentType, tableRef, theme, value } = props;

  const COLUMNS = [
    { title: 'Subject', field: 'identifier' },
  ];

  let initialized = parentType && childType;

  return(
    <Dialog open={open} onClose={onClose} className={classes.newSubjectPopper}>
      <DialogTitle id="new-form-title">
        Select parent {parentType?.['label']} for new {childType?.['label']}.
      </DialogTitle>
      <DialogContent dividers className={classes.NewFormDialog}>
        { error && <Typography color="error">{error}</Typography>}
        {
          initialized &&
            <MaterialTable
              title={"Select a " + parentType?.['label']}
              columns={COLUMNS}
              data={query => {
                  let url = createQueryURL(` WHERE n.type='${parentType?.["jcr:uuid"]}'` + (query.search ? ` AND CONTAINS(n.identifier, '*${query.search}*')` : ""), "lfs:Subject");
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
              options={{
                search: true,
                addRowPosition: 'first',
                rowStyle: rowData => ({
                  /* It doesn't seem possible to alter the className from here */
                  backgroundColor: (value?.["identifier"] === rowData["identifier"]) ? theme.palette.grey["200"] : theme.palette.background.default
                })
              }}
              onRowClick={(event, rowData) => {onChangeParent(rowData);}}
              tableRef={tableRef}
            />
        }
      </DialogContent>
      <DialogActions>
        <Button
          onClick={onClose}
          variant="contained"
          color="default"
          disabled={disabled}
          >
          Cancel
        </Button>
        <Button
          onClick={onBack}
          variant="contained"
          color="default"
          disabled={disabled}
          >
          Back
        </Button>
        <Button
          onClick={onSubmit}
          variant="contained"
          color="primary"
          disabled={disabled}
          >
          { isLast ? "Create" : "Continue" }
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export const SelectParentDialog = withStyles(QuestionnaireStyle, {withTheme: true})(UnstyledSelectParentDialog)

/**
 * Component that displays the list of subjects in a dialog. Double clicking a subject selects it.
 *
 * @param {open} bool Whether or not this dialog is open
 * @param {func} onChange Callback for when the user changes their selection
 * @param {func} onClose Callback for when the user closes this dialog
 * @param {func} onError Callback for when an error occurs during subject selection
 * @param {string} title Title of the dialog, if any
 * @param {bool} popperOpen Whether or not the 'Create a new subject' dialog is open - allows it to be open on its own
 * @param {func} onPopperClose Callback for when user closes the 'Create a new subject' dialog
 */
function UnstyledSelectorDialog (props) {
  const { classes, open, onChange, onClose, onError, title, popperOpen, onPopperClose, ...rest } = props;
  const [ subjects, setSubjects ] = useState([]);
  const [ selectedSubject, setSelectedSubject ] = useState();
  const [ isPosting, setIsPosting ] = useState();
  const [ newSubjectName, setNewSubjectName ] = useState("");
  const [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(false);
  const [ newSubjectError, setNewSubjectError ] = useState();

  // Handle the user clicking on a subject, potentially submitting it
  let selectSubject = (subject) => {
    setSelectedSubject(subject);
  }

  // Handle an error when creating a new subject
  let handleCreateSubjectsError = (message) => {
    setNewSubjectError(message);
    setIsPosting(false);
  }

  // Handle the SubjectSelector clicking on a subject, selecting it
  let handleSubmit = (useNewSubject) => {
    // Submit the new subjects
    if (useNewSubject) {
      setIsPosting(true);
      createSubjects([newSubjectName], selectedSubject, newSubjectName, grabNewSubject, handleCreateSubjectsError);
    } else {
      onChange(selectedSubject);
      setNewSubjectError();
      setNewSubjectPopperOpen(false);
    }
  }

  // Obtain the full details on a new subject
  let grabNewSubject = (subjectPath) => {
    let url = new URL(subjectPath + ".json", window.location.origin);

    fetch(url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((data) => appendPath(data, subjectPath))
      .then(onChange)
      .then(() => setNewSubjectPopperOpen(false))
      .then(() => onPopperClose())
      .then(() => setNewSubjectName(""))
      .catch((err) => {console.log(err); onError(err);})
      .then(() => setNewSubjectError())
      .finally(() => {setIsPosting(false);});
  }

  // Append the @path attribute to an object
  let appendPath = (json, path) => {
    json["@path"] = path;
    return json;
  }

  let closeNewSubjectPopper = () => {
    setNewSubjectError();
    setNewSubjectPopperOpen(false);
    onPopperClose();
    //clear the input field
    setNewSubjectName("");
  }

  return (<React.Fragment>
    <NewSubjectDialog
      disabled={isPosting}
      error={newSubjectError}
      onClose={closeNewSubjectPopper}
      onChange={(event) => {setNewSubjectName(event.target.value);}}
      onSubmit={() => {handleSubmit(true);}}
      open={newSubjectPopperOpen}
      value={newSubjectName}
      />
    <Dialog open={open} onClose={onClose}>
      {title && <DialogTitle>{title}</DialogTitle>}
      <DialogContent className={classes.NewFormDialog}>
        <StyledSubjectSelectorList
          disabled={isPosting}
          onError={onError}
          onSelect={(data) => {selectSubject(data);}}
          setSubjects={setSubjects}
          selectedSubject={selectedSubject}
          subjects={subjects}
          {...rest}
          />
      </DialogContent>
      <DialogActions>
        <Button
          variant="contained"
          color="secondary"
          onClick={() => { setNewSubjectPopperOpen(true); }}
          className={classes.createNewSubjectButton}
          >
          New subject
        </Button>
        <Button
          onClick={onClose}
          variant="contained"
          color="default"
          >
          Cancel
        </Button>
        <Button
          onClick={() => handleSubmit()}
          variant="contained"
          color="primary"
          >
          Confirm
        </Button>
      </DialogActions>
    </Dialog>
  </React.Fragment>);
}

export const SelectorDialog = withStyles(QuestionnaireStyle)(UnstyledSelectorDialog)

/**
 * Create new subjects from an array of identifiers.
 *
 * @param {array} newSubjects The new subjects to add to the repository, as an array of strings.
 * @param {array} subjectType The subjectType uuid to use for the new subjects.
 * @param {array} subjectParents Parent subjects required by the subject type.
 * @param {object or string} subjectToTrack The selected subject to return the URL for
 * @param {func} returnCall The callback after all subjects have been created
 * @param {func} onError The callback if an error occurs during subject creation
 */
export function createSubjects(newSubjects, subjectType, subjectParents, subjectToTrack, returnCall, onError) {
  let selectedURL = subjectToTrack["@path"];
  let subjectTypeToUse = subjectType["jcr:uuid"] ? subjectType["jcr:uuid"] : subjectType;
  let lastPromise = null;
  for (let subjectName of newSubjects) {
    // Do not allow blank subjects
    if (subjectName == "") {
      continue;
    }

    let checkAlreadyExistsURL = createQueryURL(` WHERE n.'identifier'='${subjectName}'`, "lfs:Subject");

    let url = "/Subjects/" + uuid();

    // If this is the subject the user has selected, make a note of the output URL
    if (subjectName == subjectToTrack) {
      selectedURL = url;
    }

    // Make a POST request to create a new subject
    let requestData = new FormData();
    requestData.append('jcr:primaryType', 'lfs:Subject');
    requestData.append('identifier', subjectName);
    requestData.append('type', subjectTypeToUse);
    requestData.append('type@TypeHint', 'Reference');
    subjectParents.forEach((parent) => {
      requestData.append('parents', parent);
    })
    requestData.append('parents@TypeHint', 'Reference');

    let newPromise = fetch( checkAlreadyExistsURL )
      .then( (response) => response.ok ? response.json() : Promise.reject(response))
      .then( (json) => {
        if (json?.rows?.length > 0) {
          return Promise.reject(`Subject ${subjectName} already exists`);
        }
      });

    // Either chain the promise or create a new one
    if (lastPromise) {
      lastPromise
        .then(newPromise)
        .then(() => {fetch( url, { method: 'POST', body: requestData })});
    } else {
      lastPromise = newPromise.then(() => fetch( url, { method: 'POST', body: requestData }));
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
 * <SubjectSelectorList
 *   disabled={false}
 *   onSelect={(subject) => {setSelectedSubject(subject)}}
 *   />
 *
 * @param {allowedTypes} array A list of allowed SubjectTypes
 * @param {allowAddSubjects} bool If true, enables an "add user" button on this list
 * @param {allowDeleteSubjects} bool If true, enables an "delete user" button on this list
 * @param {disabled} bool whether selections should be disabled on this element
 * @param {onDelete} func Callback for the deletion of a subject. The only parameter is the subject deleted.
 * @param {onError} func Callback for an issue in the reading or editing of subjects. The only parameter is a response object.
 * @param {onSelect} func Callback for when the user selects a subject.
 * @param {selectedSubject} object The currently selected subject.
 */
function SubjectSelectorList(props) {
  const { allowedTypes, allowAddSubjects, allowDeleteSubjects, classes, disabled, onDelete, onEdit, onError, onSelect, selectedSubject,
    currentSubject, theme, ...rest } = props;

  const COLUMNS = [
    { title: 'Identifier', field: 'identifier' },
  ];

  return(
    <React.Fragment>
      <MaterialTable
        title=""
        columns={COLUMNS}
        data={query => {
            //todo: would check here
            let condition = "";
            if (allowedTypes || query.search) {
              condition = " WHERE ";
            }
            if (allowedTypes) {
              condition += "(" + allowedTypes.map((type) => `n.'type' = '${type["jcr:uuid"]}'`).join(" OR ") + ")";
            }
            if (query.search) {
              condition += ` CONTAINS(n.identifier, '*${query.search}*')`;
            }
            let url = createQueryURL( condition, "lfs:Subject");
            url.searchParams.set("limit", query.pageSize);
            url.searchParams.set("offset", query.page*query.pageSize);

            console.log(currentSubject)

            return fetch(url)
              .then(response => response.json())
              .then(result => {
                //todo: rename
                let andAgain = (e) => {
                  console.log("DOES contain")
                  if (e['parents']?.['@path'] == currentSubject['@path']){
                    return e;
                  }
                  else if (e['parents']) {
                    andAgain(e['parents']); // get again with parents
                  }
                  else return;
                }

                let getAgain = (e) => {
                  if (currentSubject && (e['parents']?.['type']['@path'] == currentSubject.type['@path'])){
                    // check if this list is a descendant of the current type. if yes, will apply this
                    return true;
                  }
                  else if (e['parents']) {
                    getAgain(e['parents']); // get again with parents
                  }
                  else return;
                }

                let testing = result['rows'].map((row) => getAgain(row));

                console.log(testing);

                return {
                  data: ((currentSubject && (result['rows'].map((e) => getAgain(e))) == true) //todo: fix, filterworks if == true but parent forms dont..
                    ? result['rows'].filter((e) => andAgain(e)) 
                    : result['rows']
                  ),
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
            let url = new URL("/Subjects/" + uuid(), window.location.origin);

            // Make a POST request to create a new subject
            let request_data = new FormData();
            request_data.append('jcr:primaryType', 'lfs:Subject');
            request_data.append('identifier', newData["identifier"]);

            let check_url = createQueryURL(` WHERE n.'identifier'='${newData["identifier"]}'`, "lfs:Subject");
            return fetch( check_url )
              .then( (response) => response.ok ? response.json() : Promise.reject(response))
              .then( (json) => {
                if (json?.rows?.length > 0) {
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
                            return Promise.reject(json);
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
