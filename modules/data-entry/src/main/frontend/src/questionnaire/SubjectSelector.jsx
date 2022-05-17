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

import React, { useRef, useEffect, useState, useContext } from "react";
import { useHistory } from 'react-router-dom';
import { v4 as uuidv4 } from 'uuid';

import {
  Avatar,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  ListItem,
  ListItemAvatar,
  TextField,
} from "@material-ui/core";
import withStyles from '@material-ui/styles/withStyles';
import AssignmentIndIcon from "@material-ui/icons/AssignmentInd";
import MaterialTable from "material-table";
import Alert from '@mui/material/Alert';

import { escapeJQL } from "../escape.jsx";
import { getHierarchy, getSubjectIdFromPath } from "./Subject.jsx";
import ResponsiveDialog from "../components/ResponsiveDialog"; // commons
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

/***
 * Create a URL that checks for the existence of a subject
 */
let createQueryURL = (query, type, order) => {
  let url = new URL("/query", window.location.origin);
  let fullquery = `SELECT * FROM [${type}] as n` + query;
  if (order) {
    fullquery += ` order by n.'${order}'`;
  }
  url.searchParams.set("query", fullquery);
  return url;
}

/**
 * Component that displays a dialog to create a new subject
 *
 * @param {array} allowedTypes A collection of cards:SubjectTypes that are allowed to be chosen.
 * @param {bool} continueDisabled If true, the continue button is disabled.
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
  const { allowedTypes, classes, continueDisabled, disabled, error, open, onClose, onChangeSubject, onChangeType, onSubmit, requiresParents, theme, value } = props;
  const [ newSubjectType, setNewSubjectType ] = useState();
  const [ pageSize, setPageSize ] = useState(5);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const COLUMNS = [
    { title: 'Subject type', field: 'label' },
  ];

  let changeType = (type) => {
    onChangeType(type);
    setNewSubjectType(type);
  }

  // Auto-select if there's only one valid SubjectType given in allowedTypes
  useEffect(() => {
    if (allowedTypes?.length === 1) {
      changeType(allowedTypes[0]);
    }
  }, [allowedTypes]);

  return(
    <React.Fragment>
      <ResponsiveDialog title="Create new subject" open={open} onClose={onClose}>
        <DialogContent dividers className={classes.dialogContentWithTable}>
          { error && <Alert severity="error">{error}</Alert>}
          <div className={classes.newSubjectInput}>
            <TextField
              label="Enter subject identifier"
              variant="outlined"
              fullWidth
              autoFocus
              disabled={disabled}
              value={value}
              onChange={onChangeSubject}
              />
          </div>
          <MaterialTable
            title="Select a type"
            columns={COLUMNS}
            data={allowedTypes?.length ? allowedTypes :
              query => {
                let url = createQueryURL(query.search ? ` WHERE CONTAINS(n.label, '*${escapeJQL(query.search)}*')` : "", "cards:SubjectType", "cards:defaultOrder");
                url.searchParams.set("limit", query.pageSize);
                url.searchParams.set("offset", query.page*query.pageSize);
                return fetchWithReLogin(globalLoginDisplay, url)
                  .then(response => response.json())
                  .then(result => {
                    // Auto-select if there's only one available SubjectType
                    if (result["rows"].length === 1) {
                      changeType(result["rows"][0]);
                    }
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
              header: false,
              addRowPosition: 'first',
              pageSize: pageSize,
              rowStyle: rowData => ({
                /* It doesn't seem possible to alter the className from here */
                backgroundColor: (newSubjectType?.["label"] === rowData["label"]) ? theme.palette.grey["200"] : theme.palette.background.default
              })
            }}
            onRowClick={(event, rowData) => {
              changeType(rowData);
            }}
            onChangeRowsPerPage={setPageSize}
          />
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {setNewSubjectType(""); onClose()}}
            variant="contained"
            disabled={disabled}
            >
            Cancel
          </Button>
          <Button
            onClick={() => {setNewSubjectType(""); onSubmit()}}
            variant="contained"
            color="primary"
            disabled={disabled || continueDisabled}
            >
            {requiresParents ? "Continue" : "Create"}
          </Button>
        </DialogActions>
      </ResponsiveDialog>
    </React.Fragment>
  )
}

const NewSubjectDialogChild = withStyles(QuestionnaireStyle, {withTheme: true})(UnstyledNewSubjectDialog)

/**
 * Component that displays a dialog to select parents for a new subject
 *
 * @param {string} childName The name of the new child, used to determine ineligible parents
 * @param {object} childType The object representing the cards:SubjectType of the child that is being created
 * @param {bool} continueDisabled If true, the continue button is disabled
 * @param {object} currentSubject The object representing the subject we must be a child of
 * @param {bool} disabled If true, all controls are disabled
 * @param {string} error Error message to display
 * @param {bool} isLast If true, the button to continue will read "Continue" instead of "Create"
 * @param {bool} open If true, this dialog is open
 * @param {func} onBack Callback fired when the user clicks the "Back" button
 * @param {func} onChangeParent Callback fired when the user changes the parent subject
 * @param {func} onCreateParent Callback fired when the user wants to create a new parent. If present, adds a "Create new subject" button.
 * @param {func} onClose Callback fired when the user tries to close this dialog
 * @param {func} onSubmit Callback fired when the user clicks the "Create" or "Continue" button
 * @param {object} parentType The object representing the cards:SubjectType of the parent that is being selected
 * @param {ref} tableRef Pass a reference to the MaterialTable object
 * @param {object} value The currently selected parent
 */
function UnstyledSelectParentDialog (props) {
  const { classes, childName, childType, continueDisabled, currentSubject, disabled, error, isLast, open, onBack, onChangeParent, onCreateParent, onClose, onSubmit, parentType, tableRef, theme, value } = props;

  const globalLoginDisplay = useContext(GlobalLoginContext);
  const [ pageSize, setPageSize ] = useState(5);

  const COLUMNS = [
    { title: 'Subject', field: 'hierarchy' },
  ];

  // Convert from a MaterialTable row to whether or not it has a child of the same name as
  // the one we're trying to avoid
  let hasChildWithId = (rowData, childId) => {
    return Object.values(rowData).find((rowValue) => (rowValue?.["identifier"] == childId));
  }

  let initialized = parentType && childType;

  return(
    <ResponsiveDialog open={open} onClose={onClose} keepMounted title={`Select parent ${parentType?.['label']} for new ${childType?.['label']}`}>
      <DialogContent dividers className={classes.dialogContentWithTable}>
        { error && <Alert severity="error">{error}</Alert>}
        {
          initialized &&
            <MaterialTable
              title=""
              columns={COLUMNS}
              data={query => {
                  let sql = ` WHERE n.type='${parentType?.["jcr:uuid"]}'`;
                  if (query.search) {
                    sql += ` AND CONTAINS(n.fullIdentifier, '*${escapeJQL(query.search)}*')`;
                  }
                  if (currentSubject) {
                    sql += ` AND ISDESCENDANTNODE(n, '${currentSubject["@path"]}')`;
                  }
                  let url = createQueryURL(sql, "cards:Subject", "fullIdentifier");
                  url.searchParams.set("limit", query.pageSize);
                  url.searchParams.set("offset", query.page*query.pageSize);
                  url.searchParams.set("serializeChildren", "1");
                  return fetchWithReLogin(globalLoginDisplay, url)
                    .then(response => response.json())
                    .then(result => {
                      return {
                        data: result["rows"].map((row) => ({
                          hierarchy: getHierarchy(row, React.Fragment, ()=>({})),
                          ...row })),
                        page: Math.trunc(result["offset"]/result["limit"]),
                        totalCount: result["totalrows"],
                      }}
                    )
                }
              }
              options={{
                search: true,
                header: false,
                addRowPosition: 'first',
                pageSize: pageSize,
                rowStyle: rowData => ({
                  /* It doesn't seem possible to alter the className from here */
                  backgroundColor: (value?.["jcr:uuid"] === rowData["jcr:uuid"]) ? theme.palette.grey["200"] : theme.palette.background.default,
                  // grey out subjects that already have something by this name
                  color: (hasChildWithId(rowData, childName) ? theme.palette.grey["500"] : theme.palette.grey["900"])
                })
              }}
              onRowClick={(event, rowData) => {
                if (!hasChildWithId(rowData, childName)) {
                  onChangeParent(rowData);
                }
              }}
              onChangeRowsPerPage={setPageSize}
              tableRef={tableRef}
            />
        }
      </DialogContent>
      <DialogActions>
        { onCreateParent &&
          <Button
            variant="contained"
            color="secondary"
            onClick={onCreateParent}
            className={classes.createNewSubjectButton}
            >
            New subject
          </Button>
        }
        <Button
          onClick={onClose}
          variant="contained"
          disabled={disabled}
          >
          Cancel
        </Button>
        <Button
          onClick={onBack}
          variant="contained"
          disabled={disabled}
          >
          Back
        </Button>
        <Button
          onClick={onSubmit}
          variant="contained"
          color="primary"
          disabled={disabled || continueDisabled}
          >
          { isLast ? "Create" : "Continue" }
        </Button>
      </DialogActions>
    </ResponsiveDialog>
  )
}

export const SelectParentDialog = withStyles(QuestionnaireStyle, {withTheme: true})(UnstyledSelectParentDialog)

// The value of a subjectType's parents are either an array, or if it is length 1 it will just be an object
// We must cast each case into an array to handle it properly
export const parseToArray = (object) => {
  // Null or undefined is length 0
  if (!object) {
    return [];
  }

  // Convert a non-array to a length 1 array
  return !Array.isArray(object) ? [object] : object;
}

/**
 * Component that displays a dialog to create a new subject
 *
 * @param {array} allowedTypes A collection of cards:SubjectTypes that are allowed to be chosen.
 * @param {currentSubject} object The preselected subject (e.g. on the Subject page, the subject who's page it is is the 'currentSubject')
 * @param {bool} disabled If true, all controls are disabled
 * @param {func} onClose Callback fired when the user tries to close this dialog
 * @param {func} onSubmit Callback fired when the user clicks the "Create" or "Continue" button
 * @param {bool} openNewSubject whether to redirect to the newly created subject upon successful creation
 * @param {bool} open If true, this dialog is open
 */
export function NewSubjectDialog (props) {
  const { allowedTypes, currentSubject, disabled, onClose, onSubmit, open, openNewSubject } = props;
  const [ error, setError ] = useState("");
  const [ newSubjectName, setNewSubjectName ] = useState([""]);
  const [ newSubjectType, setNewSubjectType ] = useState([""]);
  const [ newSubjectTypeParent, setNewSubjectTypeParent ] = useState(false);
  const [ newSubjectParent, setNewSubjectParent ] = useState([]);
  const [ newSubjectIndex, setNewSubjectIndex ] = useState(0);
  const [ newSubjectAllowedTypes, setNewSubjectAllowedTypes ] = useState([]);
  const [ isPosting, setIsPosting ] = useState(false);

  const [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(true);
  const [ selectParentPopperOpen, setSelectParentPopperOpen ] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const tableRef = useRef();
  const history = useHistory();

  let curSubjectRequiresParents = newSubjectTypeParent?.["jcr:primaryType"] == "cards:SubjectType";
  let disabledControls = disabled || isPosting;

  // Called only by createNewSubject, a callback to create the next child on our list
  let createNewSubjectRecursive = (subject, index, parentList) => {
    if (index <= -1) {
      // End of recursion
      setIsPosting(false);
      onClose();
      // redirect to the new just created subject page
      let subjectId = getSubjectIdFromPath(subject);
      if (openNewSubject && subjectId) {
        history.push({
          pathname: "/content.html/Subjects/" + subjectId
        });
        return;
      } else {
        onSubmit(subject);
        return;
      }
    }

    // Grab the parent as an array if it exists, or the callback from the previously created parent, or use an empty array
    let parent = parentList[index]?.["@path"] || subject;
    createSubjects(
      globalLoginDisplay,
      [newSubjectName[index]],
      newSubjectType[index],
      parent,
      newSubjectName[index],
      (new_subject) => {createNewSubjectRecursive(new_subject, index-1, parentList)},
      handleError);
  }

  // Callback when an error occurs during createNewSubjectRecursive. Expects a Response object
  let handleError = (error) => {
    setIsPosting(false);
    if (error.status == 409) {
      // HTTP Conflict occurs when the given user does not have permission to create a subject
      setError("You do not have permissions to create this subject.");
    } else {
      setError("Error while creating subject: " + error.statusText);
    }

    // Since the error will always be during the creation of a subject, we'll revert back to the create subject page and remove all details
    clearDialog(false);
  }

  // Called when creating a new subject
  let createNewSubject = () => {
    if (newSubjectName[newSubjectIndex] == "") {
      setError("Please enter a name for this subject.");
    } else if (newSubjectType[newSubjectIndex] == "") {
      setError("Please select a subject type.");
    } else if (selectParentPopperOpen && newSubjectParent.length < newSubjectIndex) {
      // They haven't selected a parent for the current type yet
      setError("Please select a valid parent.");
    } else if (newSubjectPopperOpen && curSubjectRequiresParents) {
      // If we were given a subject whose parent we must be a child of, we can just look there instead
      // Find everything after the first /
      let newSubjectParentPath = /(.+)\/.+?/g.exec(newSubjectType[newSubjectIndex]?.["@path"])[1];
      if (currentSubject && newSubjectParentPath == currentSubject["type"]?.["@path"]) {
        // Set the last parent to the currentSubject
        let newParentList = newSubjectParent.slice();
        newParentList[newParentList.length-1] = currentSubject;
        setIsPosting(true);
        createNewSubjectRecursive(null, newSubjectIndex, newParentList);
      } else {
        // Display the parent type to select
        setError();
        setNewSubjectPopperOpen(false);
        tableRef.current && tableRef.current.onQueryChange(); // Force the table to re-query our server with the new subjectType
        setSelectParentPopperOpen(true);
      }
    } else {
      // Initiate the call
      setIsPosting(true);
      createNewSubjectRecursive(null, newSubjectIndex, newSubjectParent);
    }
  }

  let changeNewSubjectName = (name) => {
    setNewSubjectName((old) => {
      let newNames = old.slice();
      newNames[newSubjectIndex] = name;
      return newNames;
    })
  }

  let changeNewSubjectType = (type) => {
    setNewSubjectParent((old) => {
      let newParents = old.slice(0, newSubjectIndex);
      newParents.push("");
      return newParents;
    });
    // Unselect all parents after this one
    setNewSubjectType((old) => {
      let newTypes = old.slice();
      newTypes[newSubjectIndex] = type;
      return newTypes;
    });

    // Also begin a promise to find the parent of the new subject type
    // The parent is the node one above our current path, so we just grab the info about that parent
    let newAllowedTypeParent = type["@path"].split("/").slice(0, -1).join("/");
    let promise;
    if (newAllowedTypeParent) {
      promise = fetchWithReLogin(globalLoginDisplay, `${newAllowedTypeParent}.full.json`)
        .then((result) => result.ok ? result.json() : Promise.reject(result))
        .then((result) => result?.["jcr:primaryType"] == "cards:SubjectType" ? result : false);
    } else {
      promise = new Promise((resolve) => {resolve(false);});
    }

    promise.then((result) => {
      setNewSubjectTypeParent(result);
    });
  }

  let changeNewSubjectParent = (parent) => {
    setNewSubjectParent((old) => {
      let newParents = old.slice();
      if (old.length < newSubjectIndex) {
        newParents.push(parent);
      } else {
        newParents[newSubjectIndex] = parent;
      }
      return newParents;
    });
  }

  // Handle the case where the user wants to create a new subject to act as the parent
  let addNewParentSubject = () => {
    // The parent is the node one above our current path
    let newAllowedTypeParent = newSubjectType[newSubjectIndex]["@path"].split("/").slice(0, -1).join("/");
    let promise;
    if (newAllowedTypeParent) {
      promise = fetchWithReLogin(globalLoginDisplay, `${newAllowedTypeParent}.full.json`)
        .then((result) => result.ok ? result.json() : Promise.reject(result));
    } else {
      promise = new Promise((resolve) => {resolve(false);});
    }

    promise.then((json) => {
      let newAllowedTypes = parseToArray(json);
      if (currentSubject) {
        // If we have a subject we must be a child of, only allow subject types who are children of the current type
        let prefix = currentSubject["type"]["@path"];
        newAllowedTypes.filter((newType) => newType["@path"].startsWith(prefix) && newType["@path"] != prefix);
      }
      setNewSubjectAllowedTypes((old) => {
        let newTypes = old.slice();
        newTypes.push(newAllowedTypes);
        return newTypes;
      });
      setNewSubjectIndex((old) => old+1);
      setNewSubjectName((old) => {
        let newNames = old.slice();
        newNames.push("");
        return newNames;
      });
      setNewSubjectType((old) => {
        let newTypes = old.slice();
        newTypes.push("");
        return newTypes;
      });
      setNewSubjectPopperOpen(true);
      setSelectParentPopperOpen(false);
    });
  }

  let goBack = () => {
    // if there are no new subjects...
    if (newSubjectIndex == 0) {
      // Close the entire dialog
      closeDialog();
    } else {
      // Go back a stage, and reopen the select parent dialog
      setError();
      setNewSubjectIndex((old) => old-1);
      setNewSubjectPopperOpen(false);
      setSelectParentPopperOpen(true);
    }
  }

  let clearDialog = (clearError=true) => {
    if (clearError) {
      setError();
    }
    setNewSubjectIndex(0);
    setNewSubjectName([""]);
    setNewSubjectType([""]);
    setNewSubjectTypeParent([""]);
    setNewSubjectParent([]);
    setNewSubjectAllowedTypes([]);
    setNewSubjectPopperOpen(true);
    setSelectParentPopperOpen(false);
  }

  let closeDialog = () => {
    clearDialog();
    onClose();
  }

  return (
    <React.Fragment>
      <NewSubjectDialogChild
        allowedTypes={newSubjectIndex == 0 ? allowedTypes : newSubjectAllowedTypes[newSubjectIndex-1]}
        continueDisabled={!(newSubjectName[newSubjectIndex] && newSubjectType[newSubjectIndex])}
        disabled={disabledControls}
        error={error}
        onClose={goBack}
        onChangeSubject={(event) => {changeNewSubjectName(event.target.value)}}
        onChangeType = {changeNewSubjectType}
        onSubmit={createNewSubject}
        requiresParents={curSubjectRequiresParents}
        open={open && newSubjectPopperOpen}
        value={newSubjectName[newSubjectIndex]}
      />
      {open && selectParentPopperOpen && <SelectParentDialog
        childName={newSubjectName[newSubjectIndex]}
        childType={newSubjectType[newSubjectIndex]}
        continueDisabled={!newSubjectParent[newSubjectIndex]}
        currentSubject={currentSubject}
        disabled={disabledControls}
        error={error}
        onBack={() => {
          // Go back to the new subject popper
          setNewSubjectPopperOpen(true);
          setSelectParentPopperOpen(false);
          setError();
        }}
        onClose={closeDialog}
        onChangeParent={changeNewSubjectParent}
        onCreateParent={addNewParentSubject}
        onSubmit={createNewSubject}
        open={open && selectParentPopperOpen}
        parentType={newSubjectTypeParent}
        tableRef={tableRef}
        value={newSubjectParent[newSubjectIndex]}
        /> }
    </React.Fragment>)
}

/**
 * Component that displays the list of subjects in a dialog. Double clicking a subject selects it.
 *
 * @param {array} allowedTypes A collection of cards:SubjectTypes that are allowed to be chosen.
 * @param {currentSubject} object The preselected subject (e.g. on the Subject page, the subject who's page it is is the 'currentSubject')
 * @param {bool} open Whether or not this dialog is open
 * @param {func} onChange Callback for when the user changes their selection
 * @param {func} onClose Callback for when the user closes this dialog
 * @param {func} onError Callback for when an error occurs during subject selection
 * @param {string} title Title of the dialog, if any
 */
function UnstyledSelectorDialog (props) {
  const { allowedTypes, classes, currentSubject, disabled, open, onChange, onClose, onError, title, selectedQuestionnaire, ...rest } = props;
  const [ subjects, setSubjects ] = useState([]);
  const [ selectedSubject, setSelectedSubject ] = useState();
  const [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(false);
  const [ isPosting, setIsPosting ] = useState(false);
  const [ error, setError ] = useState("");
  const [ disableProgress, setDisableProgress ] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // Handle the user clicking on a subject, potentially submitting it
  let selectSubject = (subject) => {
    if (selectedSubject == subject) {
      handleSubmitExisting();
    }
    setSelectedSubject(subject);
  }

  // Obtain the full details on a new subject
  let grabNewSubject = (subjectPath) => {
    let url = new URL(subjectPath + ".deep.json", window.location.origin);

    setIsPosting(true);

    fetchWithReLogin(globalLoginDisplay, url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((data) => appendPath(data, subjectPath))
      .then(onChange)
      .then(() => setNewSubjectPopperOpen(false))
      .catch((err) => {console.log(err); onError(err);})
      .finally(() => {setIsPosting(false);});
  }

  // Append the @path attribute to an object
  let appendPath = (json, path) => {
    json["@path"] = path;
    return json;
  }

  // Handle the SubjectSelector clicking on a subject, selecting it
  let handleSubmitNew = (subject) => {
    if (disableProgress) {
      // don't allow user to progress if the maxPerSubject has been reached
      return;
    }
    setSelectedSubject(subject);
    grabNewSubject(subject);
    setNewSubjectPopperOpen(false);
  }

  // Handle the user selecting a subject that already exists
  let handleSubmitExisting = () => {
    onChange(selectedSubject);
    setNewSubjectPopperOpen(false);
  }

  let disabled_controls = isPosting || disabled;

  return (<React.Fragment>
    <NewSubjectDialog
      allowedTypes={allowedTypes}
      currentSubject={currentSubject}
      onClose={() => { setNewSubjectPopperOpen(false); }}
      onSubmit={handleSubmitNew}
      open={open && newSubjectPopperOpen}
      />
    <ResponsiveDialog title={title} open={open} onClose={onClose}>
      <DialogContent dividers className={classes.dialogContentWithTable}>
        {isPosting && <CircularProgress />}
        {error && (!newSubjectPopperOpen) && <Alert severity="error">{error}</Alert>}
        <StyledSubjectSelectorList
          allowedTypes={allowedTypes}
          disabled={disabled_controls}
          onError={setError}
          onSelect={(data) => {selectSubject(data);}}
          setSubjects={setSubjects}
          selectedSubject={selectedSubject}
          subjects={subjects}
          selectedQuestionnaire={selectedQuestionnaire}
          disableProgress={setDisableProgress}
          {...rest}
          />
      </DialogContent>
      <DialogActions>
        <Button
          variant="contained"
          color="secondary"
          disabled={disabled_controls}
          onClick={() => { setNewSubjectPopperOpen(true); }}
          className={classes.createNewSubjectButton}
          >
          New subject
        </Button>
        <Button
          onClick={onClose}
          variant="contained"
          disabled={disabled_controls}
          >
          Cancel
        </Button>
        <Button
          onClick={handleSubmitExisting}
          variant="contained"
          color="primary"
          disabled={disabled_controls}
          >
          Confirm
        </Button>
      </DialogActions>
    </ResponsiveDialog>
  </React.Fragment>);
}

export const SelectorDialog = withStyles(QuestionnaireStyle)(UnstyledSelectorDialog)

/**
 * Create new subjects from an array of identifiers.
 *
 * @param {array} newSubjects The new subjects to add to the repository, as an array of strings.
 * @param {array} subjectType The subjectType uuid to use for the new subjects.
 * @param {array} subjectParent Parent subject required by the subject type.
 * @param {object or string} subjectToTrack The selected subject to return the URL for
 * @param {func} returnCall The callback after all subjects have been created
 * @param {func} onError The callback if an error occurs during subject creation
 */
export function createSubjects(globalLoginDisplay, newSubjects, subjectType, subjectParent, subjectToTrack, returnCall, onError) {
  let selectedURL = subjectToTrack["@path"];
  let subjectTypeToUse = subjectType["jcr:uuid"] ? subjectType["jcr:uuid"] : subjectType;
  let lastPromise = null;
  let firstSubject = true;
  for (let subjectName of newSubjects) {
    // Do not allow blank subjects
    if (subjectName == "") {
      continue;
    }

    let url = (subjectParent || "/Subjects") + "/" + uuidv4();

    // If this is the subject the user has selected, make a note of the output URL
    if (subjectName == subjectToTrack) {
      selectedURL = url;
    }

    // Make a POST request to create a new subject
    let requestData = new FormData();
    requestData.append('jcr:primaryType', 'cards:Subject');
    requestData.append('identifier', subjectName);
    requestData.append('type', subjectTypeToUse);
    requestData.append('type@TypeHint', 'Reference');

    let parentCheckQueryString = "";
    if (firstSubject && subjectParent) {
      parentCheckQueryString = `ISCHILDNODE(n , '${subjectParent}')`;
    } else {
      parentCheckQueryString = "ISCHILDNODE(n , '/Subjects/')";
    }

    let checkAlreadyExistsURL = createQueryURL(` WHERE n.'identifier'='${escapeJQL(subjectName)}' AND ${parentCheckQueryString}`, "cards:Subject");
    let newPromise = fetchWithReLogin(globalLoginDisplay, checkAlreadyExistsURL)
      .then( (response) => response.ok ? response.json() : Promise.reject(response))
      .then( (json) => {
        if (json?.rows?.length > 0) {
          // Create an error message, adding the parents if they exist
          let error_msg = subjectType?.['label'] || "Subject";
          error_msg += ` ${subjectName} already exists`;
          let id = json["rows"][0]["parents"]?.["identifier"];
          if (id) {
            let parentType = json["rows"][0]["parents"]?.["type"]?.["label"] || "parent";
            error_msg += ` for ${parentType} ${id}.`;
          }

          return Promise.reject(error_msg);
        }
      });

    // Either chain the promise or create a new one
    if (lastPromise) {
      lastPromise
        .then(newPromise)
        .then(() => fetchWithReLogin(globalLoginDisplay, url, { method: 'POST', body: requestData }))
        .then((response) => response.ok || Promise.reject(response));
    } else {
      lastPromise = newPromise
        .then(() => fetchWithReLogin(globalLoginDisplay, url, { method: 'POST', body: requestData }))
        .then((response) => response.ok || Promise.reject(response));
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
 * @param {selectedQuestionnaire} object The currently selected questionnaire
 * @param {disableProgress} bool If true, will not allow user to progress in the dialog (e.g. with creating the form)
 * @param {currentSubject} object The preselected subject (e.g. on the Subject page, the subject who's page it is is the 'currentSubject')
 */
function SubjectSelectorList(props) {
  const { allowedTypes, allowAddSubjects, allowDeleteSubjects, classes, disabled, onDelete, onEdit, onError, onSelect, selectedSubject, selectedQuestionnaire, disableProgress,
    currentSubject, theme, ...rest } = props;
  const COLUMNS = [
    { title: 'Identifier', field: 'hierarchy' },
  ];
  const [ relatedSubjects, setRelatedSubjects ] = useState();
  const [ pageSize, setPageSize ] = useState(5);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // if the number of related forms of a certain questionnaire/subject is at the maxPerSubject, an error is set
  let handleSelection = (rowData) => {
    let atMax = (relatedSubjects?.length && selectedQuestionnaire && (relatedSubjects.filter((i) => (i["jcr:uuid"] == rowData["jcr:uuid"])).length >= (+(selectedQuestionnaire?.["maxPerSubject"]) || undefined)))
    if (atMax) {
      onError(`${rowData?.["type"]["@name"]} ${rowData?.["identifier"]} already has ${selectedQuestionnaire?.["maxPerSubject"]} ${selectedQuestionnaire?.["title"]} form(s) filled out.`);
      disableProgress(true);
    }
    else {
      onError("");
      disableProgress(false);
    }
  }

  return(
    <React.Fragment>
      <MaterialTable
        title=""
        columns={COLUMNS}
        data={query => {
            let conditions = [];
            if (allowedTypes?.length) {
              conditions.push("(" + allowedTypes.map((type) => `n.'type' = '${type["jcr:uuid"]}'`).join(" OR ") + ")");
            }
            if (query.search) {
              conditions.push(`CONTAINS(n.fullIdentifier, '*${escapeJQL(query.search)}*')`);
            }
            if (currentSubject) {
              let subjectID = currentSubject["jcr:uuid"];
              conditions.push(`(n.'ancestors'='${subjectID}' OR isdescendantnode(n,'${currentSubject["@path"]}') OR n.'jcr:uuid'='${subjectID}')`);
            }
            let condition = (conditions.length === 0) ? "" : ` WHERE ${conditions.join(" AND ")}`

            // fetch all subjects
            let url = createQueryURL( condition, "cards:Subject", "fullIdentifier");
            url.searchParams.set("limit", query.pageSize);
            url.searchParams.set("offset", query.page*query.pageSize);
            return fetchWithReLogin(globalLoginDisplay, url)
              .then(response => response.json())
              .then(result => {
                let filteredData = result["rows"];
                let querySubjectSubset = "";
                for (let i = 0; i < filteredData.length; i++) {
                  querySubjectSubset += "s.'jcr:uuid'='" + filteredData[i]['jcr:uuid'] + "'";
                  if ((i+1) != filteredData.length) {
                    querySubjectSubset += " or ";
                  }
                }
                let querySubjectSubsetClause = (querySubjectSubset.length > 0) ? (" and (" + querySubjectSubset + ") ") : " ";
                // fetch the Subjects of each form of this questionnaire type for all listed subjects
                return fetchWithReLogin(globalLoginDisplay, `/query?query=SELECT distinct s.* FROM [cards:Subject] AS s inner join [cards:Form] as f on f.'subject'=s.'jcr:uuid' where f.'questionnaire'='${selectedQuestionnaire?.['jcr:uuid']}'${querySubjectSubsetClause}order by s.'fullIdentifier'&limit=${query.pageSize}`)
                .then((relatedSubjectsResp) => relatedSubjectsResp.json())
                .then((relatedSubjectsResp) => {
                  setRelatedSubjects(relatedSubjectsResp.rows);
                  return relatedSubjectsResp.rows;
                })
                .then((latestRelatedSubjects) => {
                  // Auto-select if there is only one subject available which has not execeeded maximum Forms per Subject
                  let atMax = (latestRelatedSubjects?.length && selectedQuestionnaire && (latestRelatedSubjects.filter((i) => (i["jcr:uuid"] == filteredData[0]["jcr:uuid"])).length >= (+(selectedQuestionnaire?.["maxPerSubject"]) || undefined)))
                  if (filteredData.length === 1 && !atMax) {
                    onSelect(filteredData[0]);
                    handleSelection(filteredData[0]);
                  }
                  return {
                    data: filteredData.map((row) => ({
                            hierarchy: getHierarchy(row, React.Fragment, () => ({})),
                              ...row })),
                    page: Math.trunc(result["offset"]/result["limit"]),
                    totalCount: result["totalrows"],
                  }
                })
              })
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
            let url = new URL("/Subjects/" + uuidv4(), window.location.origin);

            // Make a POST request to create a new subject
            let request_data = new FormData();
            request_data.append('jcr:primaryType', 'cards:Subject');
            request_data.append('identifier', newData["identifier"]);

            let check_url = createQueryURL(` WHERE n.'identifier'='${escapeJQL(newData["identifier"])}'`, "cards:Subject");
            return fetchWithReLogin(globalLoginDisplay, check_url)
              .then( (response) => response.ok ? response.json() : Promise.reject(response))
              .then( (json) => {
                if (json?.rows?.length > 0) {
                  onError("Subject already exists");
                  return Promise.reject();
                }
              })
              .then( () => (
                fetchWithReLogin(globalLoginDisplay, url, { method: 'POST', body: request_data })
                  .then( () => (
                    // Continually attempt to query the newly inserted data until we are certain it is findable
                    new Promise((resolve, reject) => {
                      let checkForNew = () => {
                        fetchWithReLogin(globalLoginDisplay, check_url)
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
            return fetchWithReLogin(globalLoginDisplay, url, { method: 'POST', body: request_data })
            } : undefined),
        }}
        options={{
          search: true,
          header: false,
          actionsColumnIndex: -1,
          addRowPosition: 'first',
          pageSize: pageSize,
          rowStyle: rowData => ({
            /* It doesn't seem possible to alter the className from here */
            backgroundColor: (selectedSubject?.["jcr:uuid"] === rowData["jcr:uuid"]) ? theme.palette.grey["200"] : theme.palette.background.default,
            // grey out subjects that have already reached maxPerSubject
            color: ((relatedSubjects?.length && selectedQuestionnaire && (relatedSubjects.filter((i) => (i["jcr:uuid"] == rowData["jcr:uuid"])).length >= (+(selectedQuestionnaire?.["maxPerSubject"]) || undefined)))
            ? theme.palette.grey["500"]
            : theme.palette.grey["900"]
            )
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
        onRowClick={(event, rowData) => {onSelect(rowData); handleSelection(rowData)}}
        onChangeRowsPerPage={setPageSize}
      />
    </React.Fragment>
  )
}

const StyledSubjectSelectorList = withStyles(QuestionnaireStyle, {withTheme: true})(SubjectSelectorList)

export default StyledSubjectSelectorList;
