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
import React, { useState, useEffect, useContext, useRef } from "react";
import { withRouter } from "react-router-dom";
import { v4 as uuidv4 } from 'uuid';

import {
  Button,
  DialogActions,
  DialogContent,
  Typography
} from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import MaterialReactTable from "material-react-table";
import Alert from '@mui/material/Alert';

import SubjectSelectorList, { NewSubjectDialog, parseToArray } from "../questionnaire/SubjectSelector.jsx";
import NewItemButton from "../components/NewItemButton.jsx";
import ResponsiveDialog from "../components/ResponsiveDialog"; // commons
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import FormattedText from "../components/FormattedText.jsx";

const PROGRESS_SELECT_QUESTIONNAIRE = 0;
const PROGRESS_SELECT_SUBJECT = 1;
export const MODE_ACTION = 0;
export const MODE_DIALOG = 1;

/**
 * A component that renders a FAB to open a dialog to create a new form.
 *
 * @param {presetPath} string The questionnaire to use automatically, if any.
 */
function NewFormDialog(props) {
  const { children, classes, presetPath, currentSubject, theme, mode, open, onClose } = { mode:MODE_ACTION, open: false, ...props };
  const [ dialogOpen, setDialogOpen ] = useState(false);
  const [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(false);
  const [ initialized, setInitialized ] = useState(false);
  const [ selectedQuestionnaire, setSelectedQuestionnaire ] = useState();
  const [ selectedSubject, setSelectedSubject ] = useState();
  const [ progress, setProgress ] = useState();
  const [ numFetchRequests, setNumFetchRequests ] = useState(0);
  const [ error, setError ] = useState("");
  const [ relatedForms, setRelatedForms ] = useState();
  const [ disableProgress, setDisableProgress ] = useState(false);
  const [ wasOpen, setWasOpen ] = useState(false);
  const [ data, setData ] = useState([]);
  const [ isLoading, setIsLoading ] = useState(false);
  const [ isRefetching, setIsRefetching ] = useState(false);
  const [ rowCount, setRowCount ] = useState(0);

  //table state
  const [globalFilter, setGlobalFilter] = useState('');
  const [pagination, setPagination] = useState({
    pageIndex: 0,
    pageSize: 5,
  });
  const tableRef = useRef();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  let resetDialogState = () => {
    setSelectedQuestionnaire(null);
    setSelectedSubject(null);
    setDisableProgress(false);
    setError();
  }

  let closeAllDialogs = () => {
    setDialogOpen(false);
    setNewSubjectPopperOpen(false);
  }

  let createForm = (subject) => {
    setError("");

    // Get the subject identifier, if necessary
    if (!subject) {
      subject = selectedSubject["@path"];
    }

    // Make a POST request to create a new form, with a randomly generated UUID
    const URL = "/Forms/" + uuidv4();
    var request_data = new FormData();
    request_data.append('jcr:primaryType', 'cards:Form');
    request_data.append('questionnaire', selectedQuestionnaire["@path"]);
    request_data.append('questionnaire@TypeHint', 'Reference');
    request_data.append('subject', subject);
    request_data.append('subject@TypeHint', 'Reference');
    fetchWithReLogin(globalLoginDisplay, URL, { method: 'POST', body: request_data })
      .then( (response) => {
        setNumFetchRequests((num) => (num-1));
        if (response.ok) {
          // Redirect the user to the new uuid
          // FIXME: Would be better to somehow obtain the router prefix from props
          // but that is not currently possible
          props.history.push("/content.html" + URL + '.edit');
        } else {
          return(Promise.reject(response));
        }
      })
      .catch(parseErrorResponse);
    setNumFetchRequests((num) => (num+1));
  }

  let openDialog = () => {
    if (!initialized) {
      if (typeof presetPath === 'string') {
        fetchQuestionnaire(presetPath.substring(presetPath.lastIndexOf("/") + 1));
      }
      setInitialized(true);
    }
    setDialogOpen(true);
    setError("");
    setProgress(presetPath ? PROGRESS_SELECT_SUBJECT : PROGRESS_SELECT_QUESTIONNAIRE);
  }

  let fetchQuestionnaire = (questionnaireName) => {
    // Send a fetch request to determine the subjects available for the specified questionnaire
    let query = `select * from [cards:Questionnaire] as n where name()='${questionnaireName}'`;
    fetchWithReLogin(globalLoginDisplay, '/query?query=' + encodeURIComponent(query))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        // If a matching questionnaire was found, turn it into the actual questionnaire object
        if (json["returnedrows"] === 1) {
          let foundQuestionnaire = json["rows"][0]
          setSelectedQuestionnaire(foundQuestionnaire);
        }
      })
      .catch(parseErrorResponse)
      .finally(() => {setNumFetchRequests((num) => (num-1))});
  }

  // Parse an errored response object
  let parseErrorResponse = (response) => {
    setError(`New form request failed with error code ${response.status}: ${response.statusText}`);
  }

  // Offer the ability to select a subject or create the form, depending on what step the user is on
  let progressThroughDialog = () => {
    if (disableProgress) {
      // don't allow user to progress if the maxPerSubject has been reached
      return;
    } else if (progress === PROGRESS_SELECT_QUESTIONNAIRE) {
      if (!selectedQuestionnaire) {
        setError("Please select a questionnaire.");
        return;
      }
      if (selectedSubject) {
        createForm();
      }
      else {
        setProgress(PROGRESS_SELECT_SUBJECT);
      }
    } else {
      if (!selectedSubject) {
        setError("Please select a subject.");
        return;
      } else {
        createForm();
      }
    }
  }

  // Set the currently selected subject and remove all errors.
  let selectSubject = (subject) => {
    setSelectedSubject(subject);
    setError(false);
  }

  let goBack = () => {
    setError(false);
    tableRef.current.resetGlobalFilter();
    // Exit the dialog if we're at the first page or if there is a preset path
    if (progress === PROGRESS_SELECT_QUESTIONNAIRE || presetPath) {
      setDialogOpen(false);
      if (onClose) {
        onClose();
      }
    } else {
      setProgress(PROGRESS_SELECT_QUESTIONNAIRE);
      setSelectedSubject(null);
      if (disableProgress) setDisableProgress(false);
    }
  }

  // Unselect the given subject if it is currently selected
  let unselectSubject = (subject) => {
    if (selectedSubject["@path"] == subject["@path"]) {
      setSelectedSubject(null);
    }
  }

  // if the number of related forms of a certain questionnaire type is at the maxPerSubject, an error is set
  useEffect(() => {
    // only considers currentSubject, since setting this error would only be necessary on the 'Subject' page, which would set a currentSubject
    if (currentSubject && relatedForms && selectedQuestionnaire) {
      let atMax = (relatedForms.length && (relatedForms.filter((i) => (i["q.jcr:uuid"] == selectedQuestionnaire["jcr:uuid"])).length >= (+(selectedQuestionnaire?.["maxPerSubject"]) || undefined)));
      if (atMax) {
        setError(`${currentSubject?.["type"]["@name"]} ${currentSubject?.["identifier"]} already has ${selectedQuestionnaire?.["maxPerSubject"]} ${selectedQuestionnaire?.["title"]} form(s) filled out.`);
        setDisableProgress(true);
      }
      else {
        setError("");
        setDisableProgress(false);
      }
    }
  }, [selectedQuestionnaire]);

  // get all the forms related to the selectedSubject, saved in the `relatedForms` state
  let filterQuestionnaire = () => {
    fetchWithReLogin(globalLoginDisplay, `/query?rawResults=true&query=SELECT q.[jcr:uuid] FROM [cards:Questionnaire] AS q inner join [cards:Form] as f on f.'questionnaire'=q.'jcr:uuid' where f.'subject'='${(currentSubject || selectedSubject)?.['jcr:uuid']}'&limit=1000`)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((response) => {
      setRelatedForms(response.rows);
    })
  }

  useEffect(() => {
    if (progress === PROGRESS_SELECT_QUESTIONNAIRE && (selectedSubject || currentSubject)) {filterQuestionnaire();}
    else setRelatedForms([]);
  }, [progress]);

  const isFetching = numFetchRequests > 0;
  if (wasOpen !== open) {
    setWasOpen(open);
    if (MODE_DIALOG) {
      openDialog();
    }
  }

  // if the current subject is the only required type for the questionnaire or if the
  // only acceptable subjects are the subject we're looking at or its descendents (e.g.
  // tumors and tumor regions for a tumor), filter the acceptable subject types that can be created.
  let allowedSubjectTypes = selectedQuestionnaire && parseToArray(selectedQuestionnaire["requiredSubjectTypes"]);
  let filteredAllowedSubjectTypes = allowedSubjectTypes;
  if (selectedQuestionnaire && currentSubject) {
    // We only allow creation of children of the current subject, and not the same type of the subject itself
    let prefix = currentSubject["type"]?.["@path"];
    filteredAllowedSubjectTypes = allowedSubjectTypes.filter(
      (allowedType) => allowedType["@path"].startsWith(prefix) && allowedType["@path"] != prefix);
  }

  useEffect(() => {
    if (currentSubject && selectedQuestionnaire) {
      if ( !(filteredAllowedSubjectTypes?.length) ||
        (filteredAllowedSubjectTypes.length == 1 &&
        (currentSubject.type["@path"] == filteredAllowedSubjectTypes[0]["@path"]))) {
        setSelectedSubject(currentSubject); // now that selectedsubject is set, will create form with current subject as subject
      }
      else {
        setSelectedSubject(null); // remove selectedsubject so next dialog can open (should not create form right away)
      }
    }
  }, [selectedQuestionnaire, dialogOpen]);

  useEffect(() => {
    const fetchData = async () => {
      if (!data.length) {
        setIsLoading(true);
      } else {
        setIsRefetching(true);
      }

      let url = new URL("/query", window.location.origin);
      let sql = `select * from [cards:Questionnaire] as n `;
      let conditions = [];
      if (globalFilter ) {
        conditions.push(`(CONTAINS(n.'title', '*${globalFilter }*') or CONTAINS(n.'description', '*${globalFilter }*'))`);
      }
      // If we're on the patient chart, only allow the current subjects whose type is:
      // a) the type of the current subject
      // b) the type of any progeny of the current subject
      if (currentSubject) {
        // Join the UUIDs of the progeny types, and convert them into conditions
        // These conditions will be n.'requiredSubjectTypes' (IS NULL or ='<uuid>')
        let acceptableSubjectTypes = [" IS NULL", `='${currentSubject["type"]["jcr:uuid"]}'`];

        let findProgeny = (subjectType) => {
          // Recursively find child subject types
          // Make sure to exclude the `parents` field, which will match ["jcr:primaryType"] == "cards:SubjectType"
          // but should obviously not be counted as progeny
          let progeny = Object.entries(subjectType || {})
            .filter(([key, val]) => (val?.["jcr:primaryType"] == "cards:SubjectType" && key != 'parents'))
            .map(([_, type]) => type);
          let retVal = [...progeny, ...(progeny.map(findProgeny))].flat();
          return retVal;
        }

        let progenyTypes = findProgeny(currentSubject["type"]);
        acceptableSubjectTypes = acceptableSubjectTypes.concat(progenyTypes.map((type) => `='${type["jcr:uuid"]}'`));

        // Join the conditions together with an OR, and wrap it in brackets
        conditions.push(
          "(" + acceptableSubjectTypes.map((condition) => `n.'requiredSubjectTypes'${condition}`).join(" OR ") + ")"
        );
      }

      if (conditions.length > 0) {
        sql += "WHERE " + conditions.join(" AND ");
      }

      sql += " order by n.'title'";

      url.searchParams.set("query", sql);
      url.searchParams.set("limit", pagination.pageSize);
      url.searchParams.set("offset", pagination.pageIndex*pagination.pageSize);
      const response = await fetchWithReLogin(globalLoginDisplay, url);
      const json = await response.json();
      setData(json["rows"]);
      setRowCount(json.totalrows);

      setIsLoading(false);
      setIsRefetching(false);
    };
    fetchData();
  }, [
    globalFilter,
    pagination.pageIndex,
    pagination.pageSize
  ]);

  return (
    <React.Fragment>
      <ResponsiveDialog
        title={progress === PROGRESS_SELECT_QUESTIONNAIRE ? "Select a questionnaire" : "Select a subject"}
        open={mode === MODE_ACTION ? dialogOpen : open}
        onClose={() => {
          resetDialogState();
          closeAllDialogs();
          if (onClose) {
            onClose();
          }
        }}
      >
        <DialogContent dividers className={classes.dialogContentWithTable}>
          {error && (!newSubjectPopperOpen) && <Alert severity="error">{error}</Alert>}
          {progress === PROGRESS_SELECT_QUESTIONNAIRE ?
          <React.Fragment>
            {relatedForms &&
              <MaterialReactTable
                tableInstanceRef={tableRef}
                enableColumnActions={false}
                enableColumnFilters={false}
                enableSorting={false}
                enableToolbarInternalActions={false}
                manualPagination
                onGlobalFilterChange={setGlobalFilter}
                onPaginationChange={setPagination}
                rowCount={rowCount}
                state={{
                  globalFilter,
                  isLoading,
                  pagination,
                  showProgressBars: isRefetching
                }}
                initialState={{ showGlobalFilter: true, columnVisibility: { description: false } }}
                columns={[{
                  accessorKey: 'title',
                  Cell: ({ renderedCellValue, row }) => (<>
                                  <Typography component="div">{row.original.title}</Typography>
                                  <FormattedText variant="caption" color="textSecondary">
                                    {row.original.description}
                                  </FormattedText>
                                </>)
                }, {
                  accessorKey: 'description',
                }]}
                data={data}
                muiTableBodyRowProps={({ row }) => ({
                  onClick: () => { !isFetching && setSelectedQuestionnaire(row.original) },
                  sx: (theme) => ({
                    // /* It doesn't seem possible to alter the className from here */
                    backgroundColor: (selectedQuestionnaire?.["jcr:uuid"] === row.original["jcr:uuid"]) ? theme.palette.grey["200"] : theme.palette.background.default,
                    // // grey out subjects that have already reached maxPerSubject
                    color: ((relatedForms?.length && (selectedSubject || currentSubject) && (relatedForms.filter((i) => (i["q.jcr:uuid"] == row.original["jcr:uuid"])).length >= (+(row.original?.["maxPerSubject"]) || undefined)))
                    ? theme.palette.grey["500"]
                    : theme.palette.grey["900"]
                    )
                  }),
                })}
              />
            }
          </React.Fragment>
          :
          <React.Fragment>
            { /* We need selectedQuestionnaire to be filled out before this renders, or it will try grabbing the wrong subjects */
            selectedQuestionnaire && <SubjectSelectorList
              allowedTypes={parseToArray(selectedQuestionnaire?.["requiredSubjectTypes"])}
              disabled={isFetching}
              onDelete={unselectSubject}
              onError={setError}
              onSelect={selectSubject}
              selectedSubject={selectedSubject}
              currentSubject={currentSubject}
              selectedQuestionnaire={selectedQuestionnaire}
              disableProgress={setDisableProgress}
              />}
          </React.Fragment>}
        </DialogContent>
        <DialogActions>
          {progress === PROGRESS_SELECT_SUBJECT &&
            <Button
              variant="contained"
              color="success"
              onClick={() => { setNewSubjectPopperOpen(true); setError(); }}
              className={classes.createNewSubjectButton}
              >
              New subject
            </Button>
          }
          <Button
            variant="outlined"
            onClick={goBack}
            >
            { (progress == PROGRESS_SELECT_QUESTIONNAIRE || presetPath) ?
              "Cancel"
            :
              "Back"
            }
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={progressThroughDialog}
            >
            { progress == PROGRESS_SELECT_QUESTIONNAIRE ?
              "Continue"
            :
              "Create Form"
            }
          </Button>
        </DialogActions>
      </ResponsiveDialog>
      <NewSubjectDialog
        allowedTypes={filteredAllowedSubjectTypes}
        disabled={isFetching}
        onClose={() => {
          resetDialogState();
          closeAllDialogs();
          if (onClose) {
            onClose();
          }
        }}
        onChangeSubject={(event) => {setNewSubjectName(event.target.value);}}
        currentSubject={currentSubject}
        onSubmit={createForm}
        open={newSubjectPopperOpen}
        />
      {
        mode === MODE_ACTION &&
          <NewItemButton
            title={children}
            onClick={openDialog}
            inProgress={!dialogOpen && isFetching}
          />
      }
    </React.Fragment>
  )
}

export default withStyles(QuestionnaireStyle, {withTheme: true})(withRouter(NewFormDialog));
