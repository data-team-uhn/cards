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

import React, { useEffect, useState, useContext } from "react";
import { withRouter, useHistory } from "react-router-dom";
import PropTypes from "prop-types";

import {
  Breadcrumbs,
  Button,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  Grid,
  IconButton,
  List,
  ListItem,
  Popover,
  Tooltip,
  Typography,
  withStyles
} from "@material-ui/core";
import CloseIcon from "@material-ui/icons/Close";
import EditIcon from '@material-ui/icons/Edit';
import CloudUploadIcon from "@material-ui/icons/CloudUpload";
import DoneIcon from "@material-ui/icons/Done";
import WarningIcon from '@material-ui/icons/Warning';
import MoreIcon from '@material-ui/icons/MoreVert';

import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";
import FormEntry, { QUESTION_TYPES, ENTRY_TYPES } from "./FormEntry";
import moment from "moment";
import { getHierarchy, getTextHierarchy } from "./Subject";
import { SelectorDialog, parseToArray } from "./SubjectSelector";
import { FormProvider } from "./FormContext";
import { FormUpdateProvider } from "./FormUpdateContext";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import DeleteButton from "../dataHomepage/DeleteButton";
import MainActionButton from "../components/MainActionButton.jsx";
import FormPagination from "./FormPagination";
import { usePageNameWriterContext } from "../themePage/Page.jsx";
import MDEditor from '@uiw/react-md-editor';

class Page {
  constructor(visible) {
    this.visible = visible;
    this.canBeVisible = true;
  }
  conditionalVisible = [];

  addConditionalVisible(visible, index) {
    this.conditionalVisible[index] = visible;
    this.canBeVisible = this.conditionalVisible.length === 0 || this.conditionalVisible.includes(true);
  }
}

// TODO Once components from the login module can be imported, open the login Dialog in-page instead of opening a popup window

// TODO Try to move the save-failed code somewhere more generic instead of the Form component

/**
 * Component that displays an editable Form.
 *
 * @example
 * <Form id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a form; this is the JCR node name
 */
function Form (props) {
  let { classes, id } = props;
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // Marks that a save operation is in progress
  let [ saveInProgress, setSaveInProgress ] = useState();
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);
  let [ lastSaveTimestamp, setLastSaveTimestamp ] = useState(null);
  let [ selectorDialogOpen, setSelectorDialogOpen ] = useState(false);
  let [ selectorDialogError, setSelectorDialogError ] = useState("");
  let [ changedSubject, setChangedSubject ] = useState();
  let [ saveDataPending, setSaveDataPending ] = useState(false);
  let [ errorCode, setErrorCode ] = useState();
  let [ errorMessage, setErrorMessage ] = useState("");
  let [ errorDialogDisplayed, setErrorDialogDisplayed ] = useState(false);
  let [ activePage, setActivePage ] = useState(0);
  let [ pages, setPages ] = useState([]);
  let [ paginationEnabled, setPaginationEnabled ] = useState(false);
  let [ removeWindowHandlers, setRemoveWindowHandlers ] = useState();
  let [ actionsMenu, setActionsMenu ] = useState(null);

  let formNode = React.useRef();
  let pageNameWriter = usePageNameWriterContext();
  const history = useHistory();
  const formURL = `/Forms/${id}`;
  const urlBase = "/content.html";
  const isEdit = window.location.pathname.endsWith(".edit");
  let globalLoginDisplay = useContext(GlobalLoginContext);

  useEffect(() => {
    if (isEdit) {
      function removeBeforeUnloadHandlers() {
        window.removeEventListener("beforeunload", saveDataWithCheckin);
      }
      setRemoveWindowHandlers(() => removeBeforeUnloadHandlers);
      window.addEventListener("beforeunload", saveDataWithCheckin);
      // When component unmounts:
      return (() => {
        // cleanup event handler
        window.removeEventListener("beforeunload", saveDataWithCheckin);
      });
    }
  }, [isEdit]);

  // Fetch the form's data as JSON from the server.
  // The data will contain the form metadata,
  // such as authorship and versioning information, the associated subject,
  // the questionnaire definition,
  // and all the existing answers.
  // Once the data arrives from the server, it will be stored in the `data` state variable.
  let fetchData = () => {
    fetchWithReLogin(globalLoginDisplay, formURL + '.deep.json')
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleResponse)
      .catch(handleFetchError);
  };

  // Callback method for the `fetchData` method, invoked when the data successfully arrived from the server.
  let handleResponse = (json) => {
    setData(json);
    setPaginationEnabled(!!json?.['questionnaire']?.['paginate']);
    setPages([]);
    if (isEdit) {
      //Perform a JCR check-out of the Form
      let checkoutForm = new FormData();
      checkoutForm.set(":operation", "checkout");
      fetchWithReLogin(globalLoginDisplay, formURL, {
        method: "POST",
        body: checkoutForm
      });
    }
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleFetchError = (response) => {
    setError(response);
    setData([]);  // Prevent an infinite loop if data was not set
  };

  // Event handler for the form submission event, replacing the normal browser form submission with a background fetch request.
  let saveData = (event, performCheckin) => {
    // This stops the normal browser form submission
    event && event.preventDefault();
    if (!formNode.current) {
      return;
    }

    setSaveInProgress(true);
    let data = new FormData(formNode.current);
    if (performCheckin) {
        data.append(":checkin", "true");
    }
    return fetchWithReLogin(globalLoginDisplay, formURL, {
      method: "POST",
      body: data,
      headers: {
        Accept: "application/json"
      }
    }).then((response) => {
       if (!(formNode?.current)) {
        // component no longer mounted
        // nothing to do
        return;
      }
      if (response.ok) {
        setLastSaveStatus(true);
        setLastSaveTimestamp(new Date());
      } else if (response.status === 500) {
        response.json().then((json) => {
            setErrorCode(json["status.code"]);
            setErrorMessage(json.error.message);
            openErrorDialog();
        })
        setLastSaveStatus(undefined);
      }
    }).catch((err) => {
        setErrorCode(0);
        setErrorMessage(err?.message);
        openErrorDialog();
        setLastSaveStatus(undefined);
    })
    .finally(() => {formNode?.current && setSaveInProgress(false)});
  }

  let saveDataWithCheckin = (event) => {
      return saveData(event, true);
  }

  // Handle when the subject of the form changes
  let changeSubject = (subject) => {
    setData( (old) => {
      let updated = {...old}
      updated.subject = subject;
      return(updated);
    })
    setChangedSubject(subject);
    setSelectorDialogOpen(false);
  }

  let openErrorDialog = () => {
    if (!errorDialogDisplayed) {
      setErrorDialogDisplayed(true);
    }
  }

  let closeErrorDialog = () => {
    if (errorDialogDisplayed) {
      setErrorDialogDisplayed(false);
    }
  }

  let handleSubmit = (event) => {
    // Do not save when login in progress
    // Prevents issue where submitting login dialog would try to save twice,
    // once before login complete and once after
    if (saveDataPending === true) {
      return;
    }
    saveData(event);
  }

  let onEdit = (event) => {
    // Redirect the user to the edit form mode
    props.history.push(urlBase + formURL + '.edit' + window.location.hash);
  }

  let onClose = (event) => {
    // Redirect the user to the view form mode
    props.history.push(urlBase + formURL + window.location.hash);
  }

  let onDelete = () => {
    removeWindowHandlers && removeWindowHandlers();
    props.history.push(urlBase + (data?.subject?.['@path'] || ''));
  }

  let parentDetails = data?.subject && getHierarchy(data.subject);
  let title = data?.questionnaire?.title || id || "";
  let subjectName = data?.subject && getTextHierarchy(data?.subject);
  useEffect(() => {
    pageNameWriter((subjectName ? subjectName + ": " : "") + title);
  }, [subjectName, title])

  useEffect(() => {
    if (!isEdit) {
      saveDataWithCheckin();
    }
  }, [changedSubject])

  // Load the Form, only once, upon initialization
  useEffect(() => {
    fetchData();
  }, []);

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  // Pagination logic

  let lastValidPage = () => {
    let result = pages.length - 1;
    while (result > 0 && !pages[result].canBeVisible) {
      result--;
    }
    return result;
  }

  let handlePageChange = (direction) => {
    if (paginationEnabled) {
      let change = (direction === "next" ? 1 : -1);
      let lastPage = lastValidPage();
      let nextPage = activePage;
      while ((change === 1 || nextPage > 0) && (change === -1 || nextPage < lastPage)) {
        nextPage += change;
        if (pages[nextPage].canBeVisible) break;
      }
      if (nextPage !== activePage) {
        window.scrollTo(0, 0);
      }
      setActivePage(nextPage);
    }
  }

  let previousEntryType;
  let questionIndex = 0;

  let addPage = (entryDefinition) => {
    if (paginationEnabled) {
      let page;
      if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"]) && previousEntryType && QUESTION_TYPES.includes(previousEntryType)) {
        page = pages[pages.length - 1];
        questionIndex++;
      } else {
        page = new Page(!paginationEnabled || activePage == pages.length);
        pages.push(page);
        questionIndex = 0;
      }
      previousEntryType = entryDefinition["jcr:primaryType"];

      return {
        page: page,
        callback: (visible) => {page.addConditionalVisible(visible, questionIndex);}
      }
    } else {
      if (pages.length === 0) {
        pages.push(new Page(true));
      }
      return {page: pages[0], callback: ()=>{}}
    }
  }

  // If an error was returned, do not display a form at all, but report the error
  if (error) {
    return (
      <Grid container justify="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining form data: {error.status} {error.statusText}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  pages.length = 0;

  return (
    <form action={data?.["@path"]} method="POST" onSubmit={handleSubmit} onChange={()=>setLastSaveStatus(undefined)} key={id} ref={formNode}>
      <Grid container {...FORM_ENTRY_CONTAINER_PROPS} >
        <Grid item className={classes.formHeader} xs={12}>
          { parentDetails && <Typography variant="overline">
            {parentDetails}
          </Typography> }
          <Typography variant="h2">
            {title}
            <div className={classes.actionsMenu}>
                {isEdit ?
                  <Tooltip title="Save and close" onClick={onClose}>
                    <IconButton color="primary">
                      <DoneIcon />
                    </IconButton>
                  </Tooltip>
                  :
                  <Tooltip title="Edit">
                    <IconButton color="primary" onClick={onEdit}>
                      <EditIcon />
                    </IconButton>
                  </Tooltip>
                }
                <Tooltip title="More actions" onClick={(event) => {setActionsMenu(event.currentTarget)}}>
                  <IconButton>
                    <MoreIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Popover
                    open={Boolean(actionsMenu)}
                    anchorEl={actionsMenu}
                    onClose={() => {setActionsMenu(null)}}
                    anchorOrigin={{
                        vertical: 'bottom',
                        horizontal: 'right',
                    }}
                    transformOrigin={{
                        vertical: 'top',
                        horizontal: 'right',
                    }}
                >
                  <List>
                    <ListItem className={classes.actionsMenuItem}>
                      <Button onClick={() => {setSelectorDialogOpen(true); setActionsMenu(null)}}>
                        Change subject
                      </Button>
                    </ListItem>
                    <ListItem className={classes.actionsMenuItem}>
                      <DeleteButton
                          entryPath={data ? data["@path"] : formURL}
                          entryName={(data?.subject?.fullIdentifier ? (data.subject.fullIdentifier + ": ") : '') + (title)}
                          entryType="Form"
                          onComplete={onDelete}
                          variant="text"
                        />
                    </ListItem>
                  </List>
                </Popover>
            </div>
          </Typography>
          <Typography variant="subtitle1" color="textSecondary">
            <MDEditor.Markdown className={classes.markdown} source={data?.questionnaire?.description} />
          </Typography>
          <Breadcrumbs separator="·">
          {
            data && data['jcr:createdBy'] && data['jcr:created'] ?
            <Typography variant="overline">Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}</Typography>
            : ""
          }
          {
            data && data['jcr:isCheckedOut'] ?
            <Typography variant="overline" className={classes.warningStatus}>Another user is editing</Typography>
            : ""
          }
          {
            lastSaveTimestamp ?
            <Typography variant="overline">{saveInProgress ? "Saving ... " : "Saved " + moment(lastSaveTimestamp.toISOString()).calendar()}</Typography>
            :
            data && data['jcr:lastModified'] ?
            <Typography variant="overline">{"Last modified " + moment(data['jcr:lastModified']).calendar()}</Typography>
            : ""
          }
          </Breadcrumbs>
        </Grid>
        { /* We also expose the URL of the output form and the save function to any children. This shouldn't interfere
          with any other values placed inside the context since no variable name should be able to have a '/' in it */}
        <FormProvider additionalFormData={{
          ['/Save']: saveData,
          ['/URL']: formURL,
          ['/AllowResave']: ()=>setLastSaveStatus(undefined)
          }}>
          <FormUpdateProvider>
            <SelectorDialog
              allowedTypes={parseToArray(data?.['questionnaire']?.['requiredSubjectTypes'])}
              error={selectorDialogError}
              open={selectorDialogOpen}
              onChange={changeSubject}
              onClose={() => {setSelectorDialogOpen(false)}}
              onError={setSelectorDialogError}
              title="Set subject"
              selectedQuestionnaire={data?.questionnaire}
              />
            {changedSubject &&
              <React.Fragment>
                <input type="hidden" name={`${data["@path"]}/subject`} value={changedSubject["@path"]}></input>
                <input type="hidden" name={`${data["@path"]}/subject@TypeHint`} value="Reference"></input>
              </React.Fragment>
            }
            {
              Object.entries(data.questionnaire)
                .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
                .map(([key, entryDefinition]) => {
                  let pageResult = addPage(entryDefinition);
                  return <FormEntry
                    key={key}
                    entryDefinition={entryDefinition}
                    path={"."}
                    depth={0}
                    existingAnswers={data}
                    keyProp={key}
                    classes={classes}
                    onChange={()=>setLastSaveStatus(undefined)}
                    visibleCallback={pageResult.callback}
                    pageActive={pageResult.page.visible}
                    isEdit={isEdit}
                  />
                })
            }
          </FormUpdateProvider>
        </FormProvider>
        {paginationEnabled ?
        <Grid item xs={12} className={classes.formFooter}>
          <FormPagination
            lastPage={lastValidPage}
            activePage={activePage}
            saveInProgress={saveInProgress}
            lastSaveStatus={lastSaveStatus}
            handlePageChange={handlePageChange}
            />
        </Grid>
        :
        <Grid item xs={false} className={classes.formBottom}>
          <div className={classes.mainPageAction}>
            { isEdit &&
              <MainActionButton
                inProgress={saveInProgress}
                onClick={handleSubmit}
                icon={saveInProgress ? <CloudUploadIcon /> : <DoneIcon />}
                label={saveInProgress ? "Saving..." : lastSaveStatus ? "Saved" : "Save"}
              />
            }
          </div>
        </Grid>
        }
      </Grid>
      <Dialog open={errorDialogDisplayed} onClose={closeErrorDialog}>
        <DialogTitle disableTypography>
          <Typography variant="h6" color="error" className={classes.dialogTitle}>Failed to save</Typography>
          <IconButton onClick={closeErrorDialog} className={classes.closeButton}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent>
            <Typography variant="h6">Your changes were not saved.</Typography>
            <Typography variant="body1" paragraph>Server responded with response code {errorCode}: {errorMessage}</Typography>
            {lastSaveTimestamp &&
            <Typography variant="body1" paragraph>Time of the last successful save: {moment(lastSaveTimestamp.toISOString()).calendar()}</Typography>
            }
        </DialogContent>
      </Dialog>
    </form>
  );
};

export default withStyles(QuestionnaireStyle)(withRouter(Form));
