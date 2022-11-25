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
import { withRouter } from "react-router-dom";

import {
  Breadcrumbs,
  Button,
  Chip,
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
} from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import CloseIcon from "@mui/icons-material/Close";
import EditIcon from '@mui/icons-material/Edit';
import CloudUploadIcon from "@mui/icons-material/CloudUpload";
import DoneIcon from "@mui/icons-material/Done";
import MoreIcon from '@mui/icons-material/MoreVert';

import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";
import FormEntry, { ENTRY_TYPES } from "./FormEntry";
import { DateTime } from "luxon";
import { getTextHierarchy, getHierarchyAsList } from "./Subject";
import { SelectorDialog, parseToArray } from "./SubjectSelector";
import { FormProvider } from "./FormContext";
import { FormUpdateProvider } from "./FormUpdateContext";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import DeleteButton from "../dataHomepage/DeleteButton";
import PrintButton from "../dataHomepage/PrintButton.jsx";
import MainActionButton from "../components/MainActionButton.jsx";
import FormPagination from "./FormPagination";
import { usePageNameWriterContext } from "../themePage/Page.jsx";
import FormattedText from "../components/FormattedText.jsx";
import ResourceHeader from "./ResourceHeader.jsx";
import { hasWarningFlags } from "./AnswerInstructions";

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
  let { classes, id, contentOffset } = props;
  let { mode, className, disableHeader, disableButton, doneButtonStyle, doneIcon, doneLabel, onDone, questionnaireAddons } = props;
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
  let [ statusFlags, setStatusFlags ] = useState([]);
  let [ selectorDialogOpen, setSelectorDialogOpen ] = useState(false);
  let [ selectorDialogError, setSelectorDialogError ] = useState("");
  let [ changedSubject, setChangedSubject ] = useState();
  let [ errorCode, setErrorCode ] = useState();
  let [ errorMessage, setErrorMessage ] = useState("");
  let [ errorDialogDisplayed, setErrorDialogDisplayed ] = useState(false);
  let [ pages, setPages ] = useState(null);
  // Avoid rendering everything at once before we get all of the questionnaire details
  let [ paginationEnabled, setPaginationEnabled ] = useState(true);
  let [ removeWindowHandlers, setRemoveWindowHandlers ] = useState();
  let [ actionsMenu, setActionsMenu ] = useState(null);
  let [ formContentOffsetTop, setFormContentOffsetTop ] = useState(contentOffset);
  let [ formContentOffsetBottom, setFormContentOffsetBottom ] = useState(0);

  // Whether we reached the of the form (as opposed to a page that is not the last on a paginated form)
  let [ endReached, setEndReached ] = useState();

  // End is always reached on non-paginated forms
  // On paginated forms, the `endReached` starts out as `false`, and the `FormPagination` component
  // will notify the `Form` component when the final page was displayed by setting `endReached` to `true`
  useEffect(() => {
    setEndReached(!paginationEnabled);
  }, [paginationEnabled]);

  // When end was reached and save was successful, call `onDone` if applicable
  useEffect(() => {
    lastSaveStatus && endReached && onDone && onDone();
  }, [lastSaveStatus, endReached]);

  let formNode = React.useRef();
  let pageNameWriter = usePageNameWriterContext();
  const formURL = `/Forms/${id}`;
  const urlBase = "/content.html";
  const isEdit = window.location.pathname.endsWith(".edit") || mode == "edit";
  const isSummary = window.location.pathname.endsWith(".summary") || mode == "summary";
  let globalLoginDisplay = useContext(GlobalLoginContext);

  useEffect(() => {
    setFormContentOffsetTop(contentOffset + (document?.getElementById('cards-resource-header')?.clientHeight || 0));
  }, [data]);
  useEffect(() => {
    paginationEnabled && setFormContentOffsetBottom(document?.getElementById('cards-resource-footer')?.clientHeight || 0);
  }, [pages])

  useEffect(() => {
    if (isEdit) {
      function removeBeforeUnloadHandlers() {
        window.removeEventListener("beforeunload", saveDataWithCheckin);
      }
      setRemoveWindowHandlers(() => removeBeforeUnloadHandlers);
      window.addEventListener("beforeunload", saveDataWithCheckin, true);
      // When component unmounts:
      return (() => {
        // cleanup event handler
        window.removeEventListener("beforeunload", saveDataWithCheckin, true);
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
    if (questionnaireAddons != null && json?.['questionnaire']) {
      questionnaireAddons.forEach(element => {
        if (element['@name']) {
          json.questionnaire[element['@name']] = element;
        }
      });
    }
    setData(json);
    setStatusFlags(json.statusFlags);
    setPaginationEnabled(!!json?.['questionnaire']?.['paginate'] && isEdit);
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
  let saveData = (event, performCheckin, onSuccess) => {
    // This stops the normal browser form submission
    event && event.preventDefault();
    if (!formNode.current) {
      return;
    }

    let data = new FormData(formNode.current);
    setSaveInProgress(true);
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
        if (!disableHeader) {
          fetchWithReLogin(globalLoginDisplay, `${formURL}/statusFlags.json`)
            .then((response) => response.ok ? response.json() : Promise.reject(response))
            .then(json => setStatusFlags(json.statusFlags))
            .catch(err => console.log("The form status flags could not be updated after saving"));
        }
        onSuccess?.();
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

  let saveDataWithCheckin = (event, onSuccess) => {
      return saveData(event, true, onSuccess);
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
    saveData(event);
  }

  let onEdit = (event) => {
    // Redirect the user to the edit form mode
    props.history.push(urlBase + formURL + '.edit' + window.location.hash);
  }

  let onClose = (event) => {
    // Redirect the user to the view form mode
    // ...but only after the Form has been saved and checked-in
    saveDataWithCheckin(undefined, () => {
        removeWindowHandlers && removeWindowHandlers();
        props.history.push(urlBase + formURL);
    });
  }

  let onDelete = () => {
    removeWindowHandlers && removeWindowHandlers();
    props.history.push(urlBase + (data?.subject?.['@path'] || ''));
  }

  let title = data?.questionnaire?.title || id || "";
  let subjectName = data?.subject && getTextHierarchy(data?.subject);
  useEffect(() => {
    typeof(pageNameWriter) == "function" && pageNameWriter((subjectName ? subjectName + ": " : "") + title);
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
      <Grid container justifyContent="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  // If an error was returned, do not display a form at all, but report the error
  if (error) {
    return (
      <Grid container justifyContent="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining form data: {error.status} {error.statusText}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  let dropdownList = (
                  <List>
                    { isEdit ?
                    <ListItem className={classes.actionsMenuItem}>
                      <Button onClick={() => {setSelectorDialogOpen(true); setActionsMenu(null)}}>
                        Change subject
                      </Button>
                    </ListItem>
                    : <>
                    <ListItem className={classes.actionsMenuItem}>
                      <PrintButton
                         variant="text"
                         size="medium"
                         resourcePath={formURL}
                         resourceData={data}
                         breadcrumb={getTextHierarchy(data?.subject, true)}
                         date={DateTime.fromISO(data['jcr:created']).toLocaleString(DateTime.DATE_MED)}
                         onClose={() => { setActionsMenu(null); }}
                       />
                    </ListItem>
                    <ListItem className={classes.actionsMenuItem}>
                      <Button
                         size="medium"
                         onClick={() => {
                         window.open(formURL + ".txt");
                         setActionsMenu(null);
                        }}>
                        Export as text
                      </Button>
                    </ListItem>
                    </> }
                    <ListItem className={classes.actionsMenuItem}>
                      <DeleteButton
                          entryPath={data ? data["@path"] : formURL}
                          entryName={(data?.subject?.fullIdentifier ? (data.subject.fullIdentifier + ": ") : '') + (title)}
                          entryType="Form"
                          onComplete={onDelete}
                          variant="text"
                          size="medium"
                        />
                    </ListItem>
                  </List>
  )

  let formMenu = (
            <div className={classes.actionsMenu}>
                {isEdit ?
                  <Tooltip title="Save and view" onClick={onClose}>
                    <IconButton color="primary" size="large">
                      <DoneIcon />
                    </IconButton>
                  </Tooltip>
                  :
                  <Tooltip title="Edit">
                    <IconButton color="primary" onClick={onEdit} size="large">
                      <EditIcon />
                    </IconButton>
                  </Tooltip>
                }
                <Tooltip title="More actions" onClick={(event) => {setActionsMenu(event.currentTarget)}}>
                  <IconButton size="large">
                    <MoreIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                { !actionsMenu && <div style={{display: "none"}}>{ dropdownList }</div> }
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
                  { dropdownList }
                </Popover>
            </div>
  )

  return (
    <form action={data?.["@path"]} method="POST" onSubmit={handleSubmit} onChange={()=>setLastSaveStatus(undefined)} key={id} ref={formNode} className={className || null}>
      <Grid container {...FORM_ENTRY_CONTAINER_PROPS} >
        { !disableHeader &&
        <ResourceHeader
          title={title}
          breadcrumbs={[<Breadcrumbs separator="/">{getHierarchyAsList(data?.subject).map(a => <Typography variant="overline" key={a}>{a}</Typography>)}</Breadcrumbs>]}
          tags={ statusFlags?.map( item => (
            <Chip
              label={item[0].toUpperCase() + item.slice(1).toLowerCase()}
              variant="outlined"
              className={`${classes[item + "Chip"] || classes.DefaultChip}`}
              size="small"
            />
          ))}
          separator=":"
          action={formMenu}
          contentOffset={contentOffset}

        >
          <FormattedText variant="subtitle1" color="textSecondary">
            {data?.questionnaire?.description}
          </FormattedText>
          <Breadcrumbs separator="·">
          {
            data && data['jcr:createdBy'] && data['jcr:created'] ?
            <Typography variant="overline">Entered by {data['jcr:createdBy']} on {DateTime.fromISO(data['jcr:created']).toLocaleString(DateTime.DATE_MED_WITH_WEEKDAY)}</Typography>
            : ""
          }
          {
            data && data['jcr:isCheckedOut'] ?
            <Typography variant="overline" className={classes.warningStatus}>Another user is editing</Typography>
            : ""
          }
          {
            lastSaveTimestamp ?
            <Typography variant="overline">{saveInProgress ? "Saving ... " : "Saved " + DateTime.fromISO(lastSaveTimestamp.toISOString()).toRelativeCalendar()}</Typography>
            :
            data && data['jcr:lastModified'] ?
            <Typography variant="overline">{"Last modified " + DateTime.fromISO(data['jcr:lastModified']).toRelativeCalendar()}</Typography>
            : ""
          }
          </Breadcrumbs>
        </ResourceHeader>
        }
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
            {pages &&
              Object.entries(data.questionnaire)
                .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
                .map(([key, entryDefinition]) => {
                  let pageResult = pages[key];
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
                    isSummary={isSummary}
                    contentOffset={{top: formContentOffsetTop, bottom: formContentOffsetBottom}}
                  />
                })
            }
          </FormUpdateProvider>
        </FormProvider>
        {/* FormPagination must be called regardless of whether paginationEnabled is true or false,
            because it is what populates the contents of the form.
            However, it should only be displayed to the user in edit mode when paginationEnabled is true. */}
        <Grid item xs={12} className={paginationEnabled ? classes.formFooter : classes.hiddenFooter} id="cards-resource-footer">
          <FormPagination
              saveInProgress={saveInProgress}
              lastSaveStatus={lastSaveStatus}
              paginationEnabled={paginationEnabled}
              questionnaireData={data.questionnaire}
              setPagesCallback={setPages}
              onDone={() => { setEndReached(true) }}
              doneLabel={doneLabel}
          />
        </Grid>
        { !paginationEnabled && !disableButton &&
        <Grid item xs={false} className={classes.formBottom}>
          <div className={classes.mainPageAction}>
            { isEdit &&
              <MainActionButton
                style={doneButtonStyle}
                inProgress={saveInProgress}
                onClick={handleSubmit}
                icon={saveInProgress ? <CloudUploadIcon /> : doneIcon || <DoneIcon />}
                label={saveInProgress ? "Saving..." : lastSaveStatus ? "Saved" : doneLabel || "Save"}
              />
            }
          </div>
        </Grid>
        }
      </Grid>
      <Dialog open={errorDialogDisplayed} onClose={closeErrorDialog}>
        <DialogTitle>
          <Typography variant="h6" color="error" className={classes.dialogTitle}>Failed to save</Typography>
          <IconButton onClick={closeErrorDialog} className={classes.closeButton} size="large">
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent>
            <Typography variant="h6">Your changes were not saved.</Typography>
            <Typography variant="body1" paragraph>Server responded with response code {errorCode}: {errorMessage}</Typography>
            {lastSaveTimestamp &&
            <Typography variant="body1" paragraph>Time of the last successful save: {DateTime.fromISO(lastSaveTimestamp.toISOString()).toRelativeCalendar()}</Typography>
            }
        </DialogContent>
      </Dialog>
    </form>
  );
};

export default withStyles(QuestionnaireStyle)(withRouter(Form));
