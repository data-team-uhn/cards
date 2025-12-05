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


import CloudUploadIcon from "@mui/icons-material/CloudUpload";
import DoneIcon from "@mui/icons-material/Done";
import EditIcon from '@mui/icons-material/Edit';
import MoreIcon from '@mui/icons-material/MoreVert';
import {
  Backdrop,
  Breadcrumbs,
  Button,
  Chip,
  CircularProgress,
  Grid,
  IconButton,
  List,
  ListItem,
  Popover,
  Tooltip,
  Typography,
} from "@mui/material";
import { alpha } from '@mui/material/styles';
import { DateTime } from "luxon";
import { Link, useNavigate } from "react-router";
import { withStyles } from 'tss-react/mui';

import { FormProvider } from "./FormContext";
import FormEntry, { ENTRY_TYPES } from "./FormEntry";
import FormPagination from "./FormPagination";
import { FormUpdateProvider } from "./FormUpdateContext";
import { getFirstIncompleteQuestionEl, hasWarningFlags } from "./FormUtilities.jsx";
import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";
import ResourceHeader from "./ResourceHeader.jsx";
import SessionExpiryWarningModal from "./SessionExpiryWarningModal.jsx";
import { getTextHierarchy, getHierarchyAsList } from "./SubjectIdentifier";
import { SelectorDialog, parseToArray } from "./SubjectSelector";
import ErrorDialog from "../components/ErrorDialog";
import FormattedText from "../components/FormattedText.jsx";
import MainActionButton from "../components/MainActionButton.jsx";
import DeleteButton from "../dataHomepage/DeleteButton";
import PrintButton from "../dataHomepage/PrintButton.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";
import { usePageNameWriterContext } from "../themePage/Page.jsx";

// TODO Once components from the login module can be imported, open the login Dialog in-page instead of opening a popup window

// TODO Try to move the save-failed code somewhere more generic instead of the Form component

/**
 * Component that displays an editable Form.
 *
 * @example
 * <Form />
 */
function Form (props) {
  let { classes, contentOffset, extensionURL } = props;
  let { mode, className, actionSwitches, disableHeader, disableButton, doneButtonStyle, doneIcon, doneLabel, onDone, questionnaireAddons, paginationProps } = props;
  // Record if the form was already checked out before opening it, which may indicate that another user is editing, or it is being edited in a different tab
  let [ wasCheckedOut, setWasCheckedOut ] = useState(false);
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // This holds the base version of the form
  let [ baseVersion, setBaseVersion ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // Marks that a save operation is in progress
  let [ saveInProgress, setSaveInProgress ] = useState();
  let [ fetchInProgress, setFetchInProgress ] = useState();
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);
  let [ lastSaveTimestamp, setLastSaveTimestamp ] = useState(null);
  let [ statusFlags, setStatusFlags ] = useState([]);
  let [ autosaveOptions, setAutosaveOptions ] = useState();
  let [ selectorDialogOpen, setSelectorDialogOpen ] = useState(false);
  let [ selectorDialogError, setSelectorDialogError ] = useState("");
  let [ changedSubject, setChangedSubject ] = useState();
  let [ errorCode, setErrorCode ] = useState();
  let [ errorMessage, setErrorMessage ] = useState("");
  let [ errorDialogDisplayed, setErrorDialogDisplayed ] = useState(false);
  let [ pages, setPages ] = useState(null);
  // Avoid rendering everything at once before we get all of the questionnaire details
  let [ paginationEnabled, setPaginationEnabled ] = useState(false);
  let [ paginationVariant, setPaginationVariant ] = useState(paginationProps?.variant);
  let [ paginationNavMode, setPaginationNavMode ] = useState(paginationProps?.navMode);
  let [ removeWindowHandlers, setRemoveWindowHandlers ] = useState();
  let [ actionsMenu, setActionsMenu ] = useState(null);
  let [ formContentOffsetTop, setFormContentOffsetTop ] = useState(contentOffset);
  let [ formContentOffsetBottom, setFormContentOffsetBottom ] = useState(0);
  let [ classNames, setClassNames ] = useState(className ? [className] : []);

  let id = props.id || /Forms\/([^.\/]+)/.exec(location.pathname)[1];
  let isEdit = window.location.pathname.endsWith(".edit") || mode == "edit";
  let isSummary = window.location.pathname.endsWith(".summary") || mode == "summary";

  // Whether we reached the of the form (as opposed to a page that is not the last on a paginated form)
  let [ endReached, setEndReached ] = useState();
  // Check if the form is required to be complete before progressing
  // The requirement can either be passed to the Form component as a prop,
  // or come via Questionnaire properties. The custom prop should have
  // priority over the questionnaire configuration.
  let [ requireCompletion, setRequireCompletion ] = useState(props.requireCompletion);
  // The first incomplete question, to be brought to the user's attention
  let [ incompleteQuestionEl, setIncompleteQuestionEl ] = useState(null);
  let [ disableProgress, setDisableProgress ] = useState();

  let navigate = useNavigate();

  useEffect(() => {
    setPaginationEnabled(isEdit && !!data?.['questionnaire']?.['paginate']);
  }, [isEdit]);

  // End is always reached on non-paginated forms
  // On paginated forms, the `endReached` starts out as `false`, and the `FormPagination` component
  // will notify the `Form` component when the final page was displayed by setting `endReached` to `true`
  useEffect(() => {
    setEndReached(!paginationEnabled);
  }, [paginationEnabled]);

  // Handle autosave:
  // When autosave options are defined, trigger a background save
  useEffect(() => {
    if (typeof(autosaveOptions) == "object") {
      let { performCheckin, onSuccess } = autosaveOptions;
      saveData(new Event("autosave"), performCheckin, onSuccess);
    }
  }, [autosaveOptions]);

  // When the save is completed (successfully or not), clear the autosave options
  useEffect(() => {
    if (saveInProgress === false) setAutosaveOptions(undefined);
  }, [saveInProgress]);

  // When end was reached and save was successful, call `onDone` if applicable
  useEffect(() => {
    // If there's at least a question that is incomplete while we require completion,
    // focus on that element and do not call `onDone`
    if (incompleteQuestionEl) {
      // focus and hightlight the first unfinished mandatory question box
      incompleteQuestionEl.classList.add(classes.questionnaireItemWithError);
      incompleteQuestionEl.scrollIntoView({block: "center"});
    } else {
      !saveInProgress && lastSaveStatus && endReached && onDone?.();
    }
  }, [lastSaveStatus, endReached, incompleteQuestionEl, saveInProgress]);

  let formNode = React.useRef();
  let pageNameWriter = usePageNameWriterContext();
  const formURL = `/Forms/${id}`;
  const baseURL = "/content.html" + (extensionURL ? "/" + extensionURL : "");
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
        window.removeEventListener("beforeunload", saveDataWithCheckin, true);
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

  useEffect(() => {
    setFetchInProgress(true);
  }, [isEdit]);

  useEffect(() => {
    // If `requireCompletion` is set, stop any advancing progress until check that all required
    // questions are completed
    requireCompletion && paginationEnabled && setDisableProgress(true);
  }, [requireCompletion, paginationEnabled]);

  let checkoutIfNeededAndFetchData = (callback) => {
    // Check if it was already checked out
    fetchWithReLogin(globalLoginDisplay, formURL + "/jcr:isCheckedOut")
      .then(response => response.text())
      .then(text => {
        setWasCheckedOut(text === "true");
        if (window.location.pathname.endsWith(".edit") || mode == "edit") {
          // Perform a JCR check-out of the Form
          let checkoutForm = new FormData();
          checkoutForm.set(":operation", "checkout");
          fetchWithReLogin(globalLoginDisplay, formURL, {
            method: "POST",
            body: checkoutForm
          }).then(fetchData);
        } else {
          fetchData();
        }
      })
      .finally(callback);
  };

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
      .catch(handleFetchError)
      .finally(() => setFetchInProgress(false));
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
    setBaseVersion(json["jcr:baseVersion"]);
    setStatusFlags(json.statusFlags);

    if (window.location.pathname.endsWith(".edit") || mode == "edit") {
      setPaginationEnabled(!!json?.['questionnaire']?.['paginate']);
      typeof(paginationVariant) == "undefined" && setPaginationVariant(json?.questionnaire?.paginationVariant);
      typeof(paginationNavMode) == "undefined" && setPaginationNavMode(json?.questionnaire?.paginationMode);
      // If the completion requirement has not already been set via Form prop,
      // grab it from the questionnaire definition
      typeof(requireCompletion) == "undefined" && setRequireCompletion(json?.questionnaire?.requireCompletion);
      setIncompleteQuestionEl(null);
      // Take into account the option to hide answer instructions as specified in the questionnaire definition
      let hideInstructions = json?.['questionnaire']?.['hideAnswerInstructions'];
      !!hideInstructions && setClassNames(names => ([...names, classes.hideAnswerInstructions]));
    }
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleFetchError = (response) => {
    setError(response);
    setData([]);  // Prevent an infinite loop if data was not set
    if (isEdit) {
      paginationEnabled && setDisableProgress(requireCompletion);
      setIncompleteQuestionEl(null);
    }
  };

  // The form's onChange event handler
  let handleFormDataChange = () => {
    incompleteQuestionEl?.classList.remove(classes.questionnaireItemWithError);
    setIncompleteQuestionEl(null);
    setDisableProgress(paginationEnabled && requireCompletion);
    setLastSaveStatus(undefined);
  }

  // Event handler for the form submission event, replacing the normal browser form submission with a background fetch request.
  let saveData = (event, performCheckin, onSuccess) => {
    // This stops the normal browser form submission
    event && event.preventDefault();
    if (!formNode.current) {
      return;
    }

    let data = new FormData(formNode.current);
    setSaveInProgress(true);
    setIncompleteQuestionEl(null);
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
        if (!disableHeader) {
          fetchWithReLogin(globalLoginDisplay, `${formURL}/statusFlags.json`)
            .then((response) => response.ok ? response.json() : Promise.reject(response))
            .then(json => setStatusFlags(json.statusFlags))
            .catch(err => console.log("The form status flags could not be updated after saving"));
        }
        onSuccess?.();
        // If the form is required to be complete or if we need to display the page completion status
        // in nagivable pagination, re-fetch it after save to check the updated status flags
        // However, skip any completion checks if this is an autosave
        if ((requireCompletion || paginationVariant == 'navigable') && !(event?.type == "autosave")) {
            // Disable progress until we figure out if it's ok to proceed
            requireCompletion && setDisableProgress(true);
            fetchWithReLogin(globalLoginDisplay, formURL + '.deep.json')
              .then((response) => response.ok ? response.json() : Promise.reject(response))
              .then(json => {
                  setData(json);
                  if (!requireCompletion) return;
                  let incompleteEl = getFirstIncompleteQuestionEl(json);
                  if (!!incompleteEl) {
                    setIncompleteQuestionEl(incompleteEl);
                  } else {
                    setDisableProgress(false);
                  }
              })
              .catch(handleFetchError)
              .finally(() => {
                setLastSaveStatus(true);
                setLastSaveTimestamp(new Date());
              });
        } else {
          setLastSaveStatus(true);
          setLastSaveTimestamp(new Date());
        }
      } else if (response.status === 409) {
        response.json().then((json) => {
            setErrorCode(response.status);
            setErrorMessage(json["status.message"]);
            // We remove the "are you sure you want to leave" and autosave handlers
            // since we know the data is stale and won't be able to be saved
            removeWindowHandlers?.();
            openErrorDialog();
        })
        setLastSaveStatus(undefined);
      } else if (response.status >= 400 || response.status < 100) {
        response.json().then((json) => {
            setErrorCode(response.status);
            setErrorMessage(json["status.message"]);
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
    navigate(baseURL + formURL + '.edit' + window.location.hash);
  }

  let onClose = (event) => {
    // Redirect the user to the view form mode
    // ...but only after the Form has been saved and checked-in
    saveDataWithCheckin(undefined, () => {
        removeWindowHandlers?.();
        navigate(baseURL + formURL);
    });
  }

  let onDelete = () => {
    removeWindowHandlers?.();
    navigate(baseURL + (data?.subject?.['@path'] || ''));
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
    checkoutIfNeededAndFetchData();
  }, [mode]);

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    return (
      <Grid container justifyContent="center"><Grid><CircularProgress/></Grid></Grid>
    );
  }

  // If an error was returned, do not display a form at all, but report the error
  if (error) {
    return (
      <Grid container justifyContent="center">
        <Grid>
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
                          entryName={getEntityIdentifier(data)}
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

  let getTimestampString  = (timestamp) => {
    let time = DateTime.fromISO(timestamp);
    return time.hasSame(DateTime.local(),"day") ? "at " + time.toFormat("hh:mma") : time.toRelativeCalendar();
  }

  let validLinks = data?.["cards:links"]?.filter(link => link["to"]?.startsWith("/"));
  let links = validLinks?.length > 0 ?
      (
        <Typography variant="overline">
          {"Related: "}
          {validLinks.length == 1 ?
              validLinks.map(link => <Link key={link["@name"]} to={".." + baseURL + link["to"]}>{link["resourceLabel"]}</Link>)
              :
              <List dense disablePadding>
              {validLinks.map(link => <ListItem key={link["@name"]}><Link to={".." + baseURL + link["to"]}>{link["resourceLabel"]}</Link></ListItem>)}
              </List>
          }
        </Typography>
      )
      : <></>

  return (
    <form action={data?.["@path"]}
          method="POST"
          onSubmit={handleSubmit}
          onChange={handleFormDataChange}
          key={id}
          ref={formNode}
          className={classNames?.join(' ')}
      >
      <input type="hidden" name=":baseVersion" value={baseVersion} />
      <Grid container {...FORM_ENTRY_CONTAINER_PROPS} >
        { !disableHeader &&
        <ResourceHeader
          title={title}
          breadcrumbs={[<Breadcrumbs separator="/">{getHierarchyAsList(data?.subject, undefined, extensionURL).map(a => <Typography variant="overline" key={a}>{a}</Typography>)}</Breadcrumbs>]}
          tags={ statusFlags?.map( item => (
            <Chip
              label={item[0].toUpperCase() + item.slice(1).toLowerCase()}
              variant="outlined"
              className={`${classes[item + "Flag"] || classes.DefaultFlag}`}
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
            <Typography variant="overline">
              {"Entered by " + data['jcr:createdBy'] + " on "}
              <Tooltip title={data['jcr:created']}>
                <span>{DateTime.fromISO(data['jcr:created']).toLocaleString(DateTime.DATE_MED_WITH_WEEKDAY)}</span>
              </Tooltip>
            </Typography>
            : ""
          }
          {
            wasCheckedOut ?
            <Typography variant="overline" className={classes.warningStatus}>Another user is editing</Typography>
            : ""
          }
          {
            lastSaveTimestamp ?
            <Typography variant="overline">
              {saveInProgress ?
              "Saving ... "
              :
              <span>
                {"Saved "}
                <Tooltip title={lastSaveTimestamp.toISOString()}>
                  <span>{getTimestampString(lastSaveTimestamp.toISOString())}</span>
                </Tooltip>
              </span>
              }
            </Typography>
            :
            data && data['jcr:lastModified'] ?
            <Typography variant="overline">
                {"Last modified "}
                <Tooltip title={data['jcr:lastModified']}>
                  <span>{getTimestampString(data['jcr:lastModified'])}</span>
                </Tooltip>
            </Typography>
            : ""
          }
          </Breadcrumbs>
          {links}
        </ResourceHeader>
        }
        { /* We also expose the URL of the output form and the save function to any children. This shouldn't interfere
          with any other values placed inside the context since no variable name should be able to have a '/' in it */}
        <FormProvider additionalFormData={{
          ['/Save']: saveData,
          ['/URL']: formURL,
          ['/OnFormDataChanged']: handleFormDataChange
          }}>
          <FormUpdateProvider>
            {!disableHeader &&
              <SelectorDialog
                allowedTypes={parseToArray(data?.['questionnaire']?.['requiredSubjectTypes'])}
                error={selectorDialogError}
                open={selectorDialogOpen}
                onChange={changeSubject}
                onClose={() => {setSelectorDialogOpen(false)}}
                onError={setSelectorDialogError}
                title="Set subject"
                selectedQuestionnaire={data?.questionnaire}
                disableRedirect
              />
            }
            {fetchInProgress &&
              <Backdrop
               open={fetchInProgress}
               sx={(theme) => ({
                 backgroundColor: alpha(theme.palette.background.paper, .5),
                 marginLeft: {md : "260px"},
                 zIndex: theme.zIndex.drawer + 1
               })}
             >
               <CircularProgress />
             </Backdrop>
            }
            {changedSubject &&
              <React.Fragment>
                <input type="hidden" name={`${data["@path"]}/subject`} value={changedSubject["@path"]}></input>
                <input type="hidden" name={`${data["@path"]}/subject@TypeHint`} value="Reference"></input>
              </React.Fragment>
            }
            {pages && !fetchInProgress &&
              Object.entries(data.questionnaire)
                .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
                .map(([key, entryDefinition]) => {
                  let pageResult = pages[key];
                  return <FormEntry
                    key={key}
                    entryDefinition={entryDefinition}
                    path="."
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
        <Grid size={12} className={paginationEnabled ? classes.formFooter : classes.hiddenFooter} id="cards-resource-footer">
          {data && <FormPagination
              saveInProgress={saveInProgress}
              disableProgress={disableProgress}
              lastSaveStatus={lastSaveStatus}
              enabled={paginationEnabled}
              variant={paginationVariant}
              navMode={paginationNavMode}
              questionnaireData={data.questionnaire}
              setPagesCallback={setPages}
              isPageCompleted={keys => (
               Object.values(data)
                 .filter(e => Object.values(e).find(val => ENTRY_TYPES.includes(val["jcr:primaryType"])))
                 .filter(e => keys.includes((e.section || e.question)?.["@name"] || ""))
                 .every(p => !hasWarningFlags(p))
              )}
              onDone={() => { setEndReached(true); }}
              onPageChange={() => { setDisableProgress(requireCompletion); setIncompleteQuestionEl(null); }}
              doneLabel={doneLabel}
              doneIcon={doneIcon}
          />}
        </Grid>
        { !paginationEnabled && !disableButton &&
        <Grid size="auto" className={classes.formBottom}>
          <div className={classes.mainPageAction}>
            { isEdit &&
              <MainActionButton
                style={doneButtonStyle}
                disabled={disableProgress}
                inProgress={saveInProgress}
                onClick={handleSubmit}
                icon={saveInProgress ? <CloudUploadIcon /> : doneIcon || <DoneIcon />}
                label={saveInProgress ? "Saving..." : doneLabel || (lastSaveStatus ? "Saved" : "Save")}
              />
            }
          </div>
        </Grid>
        }
      </Grid>
      {errorDialogDisplayed && <ErrorDialog title="Failed to save" open={errorDialogDisplayed} onClose={closeErrorDialog}>
        <Typography variant="h6">Your changes were not saved.</Typography>
        <Typography component="p">Server responded with error code {errorCode}: {errorMessage}</Typography>
        {lastSaveTimestamp &&
          <Typography component="p">
            {"The last successful save was "}
            <Tooltip title={lastSaveTimestamp.toISOString()}>
              <span>{getTimestampString(lastSaveTimestamp.toISOString())}.</span>
            </Tooltip>
          </Typography>
        }
      </ErrorDialog>}
      { isEdit &&
        <SessionExpiryWarningModal
          lastActivityTimestamp={lastSaveTimestamp}
          onStay={() => setAutosaveOptions({})}
          onExit={() => props.history.push("/")}
          onExpired={() => { removeWindowHandlers(); setAutosaveOptions({performCheckin: true}); } }
        />
      }
    </form>
  );
};

export default withStyles(Form, QuestionnaireStyle);
