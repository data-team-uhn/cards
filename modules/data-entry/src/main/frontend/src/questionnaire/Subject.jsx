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

import React, { useState, useContext, useEffect, useRef } from "react";
import { Link, useLocation, withRouter } from 'react-router-dom';
import PropTypes from "prop-types";
import { DateTime } from "luxon";

import FormattedText from "../components/FormattedText";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import NewFormDialog from "../dataHomepage/NewFormDialog";
import { QUESTION_TYPES, SECTION_TYPES, ENTRY_TYPES } from "./FormEntry.jsx";
import { usePageNameWriterContext } from "../themePage/Page.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import { getSubjectIdFromPath, getHierarchyAsList, getTextHierarchy, getHomepageLink } from "./SubjectIdentifier";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import EditButton from "../dataHomepage/EditButton.jsx";
import PrintButton from "../dataHomepage/PrintButton.jsx";
import ResourceHeader from "./ResourceHeader.jsx"
import SubjectTimeline from "./SubjectTimeline.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";
import SubjectActions from "./SubjectActions.jsx";

import MaterialReactTable from 'material-react-table';
import { Box } from '@mui/material';
import {
  Avatar,
  CircularProgress,
  Card,
  CardContent,
  Chip,
  Grid,
  IconButton,
  Tooltip,
  Tab,
  Tabs,
  Typography,
} from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import FileIcon from "@mui/icons-material/InsertDriveFile";
import CollapsedIcon from "@mui/icons-material/ChevronRight";
import ExpandedIcon from "@mui/icons-material/ExpandMore";
import FormIcon from "@mui/icons-material/Description";
import SubjectIcon from "@mui/icons-material/AssignmentInd";

/***
 * Create a URL that checks for the existence of a subject
 */
let createQueryURL = (query, type) => {
  let url = new URL("/query", window.location.origin);
  url.searchParams.set("query", `SELECT * FROM [${type}] as n` + query);
  url.searchParams.set("limit", 1000);
  return url;
}

/**
 * Component that displays a Subject.
 *
 * @example
 * <Subject id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a subject; this is the JCR node name
 * @param {int} maxDisplayed the maximum number of form question/answers to be displayed. defaults to 4
 */

function Subject(props) {
  let { id, classes, maxDisplayed, pageSize, history } = props;
  const [ currentSubject, setCurrentSubject ] = useState();
  const [ currentSubjectId, setCurrentSubjectId ] = useState(id);
  const [ activeTab, setActiveTab ] = useState(0);
  const fetchRelated = useRef();

  // TODO: These tabs should be extensible.
  // This will involve moving SubjectContainer to it's own file and moving
  // handleDisplay() to a utility file for SubjectContainer and SubjectTimeline.
  const tabs = ["Chart", "Timeline"]
  const location = useLocation();

  useEffect(() => {
    let newId = getSubjectIdFromPath(location.pathname);
    newId && setCurrentSubjectId(newId);
    if (location.hash.length > 0 && tabs.includes(location.hash.substring(1))) {
      setActiveTab(tabs.indexOf(location.hash.substring(1)));
    }
  }, [location]);

  let pageTitle = currentSubject && getTextHierarchy(currentSubject, true);

  // Change the title of the page whenever parentDetails changes
  let pageNameWriter = usePageNameWriterContext();
  useEffect(() => {
    pageTitle && pageNameWriter(pageTitle);
  }, [pageTitle]);

  // the subject data, fetched in the SubjectContainer component, will be stored in the `type` state
  function handleSubject(e) {
    setCurrentSubject(e);
  }

  function setTab(index) {
    history.replace(location.pathname+location.search+"#"+tabs[index], location.state)
    setActiveTab(index);
  }

  return (
    <React.Fragment>
      <NewFormDialog currentSubject={currentSubject}>
        { "New questionnaire for this " + (currentSubject?.type?.label || "Subject") }
      </NewFormDialog>
      <Grid container spacing={4} direction="column" className={classes.subjectContainer}>
        <SubjectHeader
          id={currentSubjectId}
          key={"SubjectHeader"}
          pageTitle={pageTitle}
          classes={classes}
          getSubject={handleSubject}
          reloadSubject={fetchRelated}
          history={history}
          contentOffset={props.contentOffset}/>
        <Grid item>
          <Tabs className={classes.subjectTabs} value={activeTab} onChange={(event, value) => {
            setTab(value);
          }}
          indicatorColor="primary" textColor="inherit" >
            {tabs.map((tab) => {
              return <Tab label={tab} key={tab}/>;
            })}
          </Tabs>
          <Card variant="outlined"><CardContent>
          <Grid container spacing={4} direction="column" wrap="nowrap">
          { activeTab === tabs.indexOf("Chart")
          ? <SubjectContainer
              id={currentSubjectId}
              key={currentSubjectId}
              classes={classes}
              maxDisplayed={maxDisplayed}
              pageSize={pageSize}
              subject={currentSubject}
              fetchSubjectData={fetchRelated.current}
            />
          : <Grid item>
            <SubjectTimeline
              id={currentSubjectId}
              classes={classes}
              pageSize={pageSize}
              subject={currentSubject}
            />
            </Grid> }
          </Grid>
          </CardContent></Card>
        </Grid>
      </Grid>
    </React.Fragment>
  );
}

/**
 * Component that recursively gets and displays the selected subject and its related SubjectTypes
 */
function SubjectContainer(props) {
  let { id, classes, level, maxDisplayed, pageSize, subject, fetchSubjectData } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // hold related subjects
  let [relatedSubjects, setRelatedSubjects] = useState(null);
  // whether the subject has been deleted
  let [ deleted, setDeleted ] = useState();

  let globalLoginDisplay = useContext(GlobalLoginContext);

  // 'level' of subject component
  const currentLevel = level || 0;

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
    setRelatedSubjects([]);
  };

  let check_url = createQueryURL(` WHERE n.'parents'='${subject?.['jcr:uuid']}' order by n.'jcr:created' OPTION (index tag property)`, "cards:Subject");
  let fetchRelated = () => {
    fetchWithReLogin(globalLoginDisplay, check_url)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((json) => {setRelatedSubjects(json.rows);})
    .catch(handleError);
  }

  // Fetch this Subject's data
  useEffect(() => {
    if (subject) {
      fetchRelated();
    }
  }, [subject]);

  if (deleted) {
    return null;
  }

  // If the data has not yet been fetched, return an in-progress symbol
  if (!relatedSubjects) {
    return (
      <Grid container justifyContent="center" className={classes.circularProgressContainer}><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  if (error) {
    return (
      <Grid container justifyContent="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining subject data: {error.status} {error.statusText ? error.statusText : error.toString()}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  return (
    subject && <React.Fragment>
      <SubjectMember
        classes={classes}
        id={id} level={currentLevel}
        data={subject}
        maxDisplayed={maxDisplayed}
        pageSize={pageSize}
        onDelete={() => {setDeleted(true)}}
        childSubjects={relatedSubjects}
        fetchSubjectData={fetchSubjectData}/>
    </React.Fragment>
  );
}

/**
 * Component that displays the header for the selected subject and its SubjectType
 */
function SubjectHeader(props) {
  let { id, classes, getSubject, history, pageTitle, reloadSubject } = props;
  // This holds the full form JSON, once it is received from the server
  let [ subject, setSubject ] = useState(null);
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  let [ statusFlags, setStatusFlags ] = useState([]);
  let [ initialized, setInitialized ] = useState(false);

  let globalLoginDisplay = useContext(GlobalLoginContext);

  useEffect(() => {
    if (!initialized) {
      reloadSubject.current = fetchSubjectData;
      setInitialized(true);
    }
  }, [initialized]);

  // Fetch the subject's data as JSON from the server.
  // The data will contain the subject metadata,
  // such as authorship and versioning information.
  // Once the data arrives from the server, it will be stored in the `data` state variable.
  let fetchSubjectData = () => {
    fetchWithReLogin(globalLoginDisplay, `/Subjects/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleSubjectResponse)
      .catch(handleError);
  };

  // Callback method for the `fetchData` method, invoked when the data successfully arrived from the server.
  let handleSubjectResponse = (json) => {
    getSubject(json);
    setSubject({data: json});
    setStatusFlags(json.statusFlags);
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
    setSubject({});  // Prevent an infinite loop if data was not set
  };

  // When the top-level subject is deleted, redirect to its parent if it has one, otherwise to the Subjects page
  let handleDeletion = () => {
    let nodeName = id.substring(id.lastIndexOf("/") + 1);
    let parentNodePath = location.pathname.substring(0, location.pathname.lastIndexOf("/" + nodeName));
    let hasParentSubject = (id.indexOf("/") > 0);
    history.push(parentNodePath + (hasParentSubject ? (location.search + location.hash) : ""));
  }

  // Fetch this Subject's data
  useEffect(() => {
    fetchSubjectData();
  }, [id]);

  if (!subject) {
    return (
      <Grid item><CircularProgress/></Grid>
    );
  }

  if (error) {
    return (
      <Grid item>
        <Typography variant="h2" color="error">
          Error obtaining subject data: {error.status} {error.statusText ? error.statusText : error.toString()}
        </Typography>
      </Grid>
    );
  }

  let identifier = subject?.data?.identifier || id;
  let label = subject?.data?.type?.label;
  let title = `${label || "Subject"} ${identifier}`;
  let path = subject?.data?.["@path"] || "/Subjects/" + id;
  let subjectMenu = (
            <div className={classes.actionsMenu}>
              <SubjectActions
                entryPath={path}
                entryName={identifier}
                entryType={label}
                statusFlags={statusFlags}
                onComplete={fetchSubjectData}
              />
              <PrintButton
                resourcePath={path}
                resourceData={subject?.data}
                breadcrumb={pageTitle}
                date={DateTime.fromISO(subject?.data['jcr:created']).toLocaleString(DateTime.DATE_MED)}
              />
              <DeleteButton
                entryPath={path}
                entryName={getEntityIdentifier(subject?.data)}
                entryType="Subject"
                entryLabel={label}
                onComplete={handleDeletion}
                size="large"
              />
            </div>
  );
  let parentDetails = (subject?.data?.['parents'] && getHierarchyAsList(subject.data['parents'], true) || [getHomepageLink(subject?.data)]);;

  return (
    subject?.data &&
      <ResourceHeader
        title={title}
        breadcrumbs={parentDetails}
        action={subjectMenu}
        contentOffset={props.contentOffset}
        tags={ statusFlags?.map( item => (
          <Chip
            label={item[0].toUpperCase() + item.slice(1).toLowerCase()}
            variant="outlined"
            className={`${classes[item + "Chip"] || classes.DefaultChip}`}
            size="small"
          />
        ))}
        >
      {
        subject?.data?.['jcr:created'] ?
        <Typography
          variant="overline"
          color="textSecondary" >
            Entered by {subject.data['jcr:createdBy']} on {DateTime.fromISO(subject.data['jcr:created']).toLocaleString(DateTime.DATE_MED_WITH_WEEKDAY)}
        </Typography>
        : ""
      }
      </ResourceHeader>
  );
}

/**
 * Component that displays all forms related to a Subject. Do not use directly, use SubjectMember instead.
 */
function SubjectMemberInternal (props) {
  let { classes, data, id, level, maxDisplayed, onDelete, pageSize, childSubjects, fetchSubjectData } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // Whether a subject is expanded and displaying its forms
  // The root subject is always expanded. Child subjects are collapsed by default.
  let [ expanded, setExpanded ] = useState(level == 0);
  let [ subjectGroups, setSubjectGroups ] = useState(null);

  let globalLoginDisplay = useContext(GlobalLoginContext);

  const customUrl=`/Forms.paginate?fieldname=subject&fieldvalue=${encodeURIComponent(data['jcr:uuid'])}&includeallstatus=true&limit=1000`;

  // Fetch the forms associated with the subject as JSON from the server
  // It will be stored in the `tableData` state variable
  let fetchTableData = () => {
    fetchWithReLogin(globalLoginDisplay, customUrl)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then(handleTableResponse)
    .catch(handleTableError);
  };

  let handleTableResponse = (json) => {
    let groups = {};
    json.rows.map( (entry, i) => {
      let title = entry.questionnaire.title || entry.questionnaire["@name"];
      groups[title]?.push(entry) || (groups[title] = [entry]);
    })
    setSubjectGroups(groups);
  };

  let handleTableError = (response) => {
    setError(response);
    setSubjectGroups({});
  };

  let wordToTitleCase = (word) => {
    return word[0].toUpperCase() + word.slice(1).toLowerCase();
  }


  // Fetch table data for all forms related to a Subject
  useEffect(() => {
    fetchTableData();
  }, [data['jcr:uuid']]);

  // If the subjectGroups data has not yet been fetched, return an in-progress symbol
  if (!subjectGroups) {
    return (
      <Grid container justifyContent="center" className={classes.circularProgressContainer}><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  // If an error was returned, do not display a subject at all, but report the error
  if (error) {
    return (
      <Grid container justifyContent="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining subject data: {error.status} {error.statusText ? error.statusText : error.toString()}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  let identifier = data && data.identifier ? data.identifier : id;
  let label = data?.type?.label;
  let statusFlags = data?.statusFlags;
  let title = `${label || "Subject"} ${identifier}`;
  let path = data ? data["@path"] : "/Subjects/" + id;
  let avatar = <Avatar className={classes.subjectAvatar}><SubjectIcon/></Avatar>;
  let expandAction = (
    <Tooltip title={`Data for ${title}`}>
      <IconButton onClick={()=> setExpanded(!expanded)}>
      { expanded ? <ExpandedIcon/> : <CollapsedIcon /> }
      </IconButton>
    </Tooltip>
  )
  let action = <>

                <SubjectActions
                  entryPath={path}
                  entryType={label}
                  entryName={identifier}
                  statusFlags={statusFlags}
                  onComplete={fetchSubjectData}
                  className={classes.childSubjectHeaderButton}
                />
                <PrintButton
                  resourcePath={path}
                  resourceData={data}
                  breadcrumb={getTextHierarchy(data, true)}
                  date={DateTime.fromISO(data['jcr:created']).toLocaleString(DateTime.DATE_MED)}
                  className={classes.childSubjectHeaderButton}
                  disableShortcut
                />
                <DeleteButton
                  entryPath={path}
                  entryName={getEntityIdentifier(data)}
                  entryType="Subject"
                  entryLabel={label}
                  onComplete={onDelete}
                  className={classes.childSubjectHeaderButton}
                />
              </>

  let tags = statusFlags?.map( item => (
      <Chip
        label={item[0].toUpperCase() + item.slice(1).toLowerCase()}
        variant="outlined"
        className={`${[classes[item + "Chip"] || classes.DefaultChip, classes.subjectChartChip].join(" ")}`}
        size="small"
      />
    ))

  return ( data &&
    <>
    {
      level > 0 &&
        <Grid item className={classes.childSubjectHeader}>
          <Grid container direction="row" spacing={1} justifyContent="flex-start">
            <Grid item xs={false}>{expandAction}</Grid>
            <Grid item xs={false}>{avatar}</Grid>
            <Grid item>
              <Typography variant="overline">
                 {label} <Link to={"/content.html" + path} underline="hover" className={classes.subjectChartLink}>{identifier}</Link>
                 {tags}
                 {action}
              </Typography>
            </Grid>
          </Grid>
        </Grid>
      }
      { /* If we finished all fetching and have no data or child subjects to display for this subject, inform the user */ }
      { expanded && childSubjects && childSubjects.length == 0 && subjectGroups && Object.keys(subjectGroups).length == 0 &&
        <Grid item>
          <Typography color="textSecondary" variant="caption">{`No data associated with this ${label.toLowerCase()} was found.`}</Typography>
        </Grid>
      }
      { expanded && subjectGroups && <>
        {
          Object.keys(subjectGroups).map( (questionnaireTitle, j) => (
            <Grid item key={questionnaireTitle}>
              <MaterialReactTable
                data={subjectGroups[questionnaireTitle]}
                enableTopToolbar={false}
                enableTableHead={false}
                enableTableFooter={false}
                enableBottomToolbar={!!(subjectGroups[questionnaireTitle]?.length > pageSize)}
                enablePagination={!!(subjectGroups[questionnaireTitle]?.length > pageSize)}
                initialState={{ pagination: { pageSize: pageSize, pageIndex: 0 } }}
                muiTablePaperProps={{
                  elevation: 0,
                }}
                muiTableBodyRowProps={{
                  sx: {
                    verticalAlign: 'top',
                  },
                }}
                layoutMode="grid"
                muiTableBodyCellProps={{
                  sx: {
                    flex: '0 0 auto',
                  }
                }}
                muiTableDetailPanelProps={{
                  sx: (theme) => ({
                    marginLeft: theme.spacing(9),
                    width: '100%'
                  })
                }}
                renderDetailPanel={({ row }) => <FormData formID={row.original["@name"]} maxDisplayed={maxDisplayed} classes={classes}/> }
                defaultColumn={{
                  minSize: 20,
                  maxSize: 9001
                }}
                displayColumnDefOptions={{
                  'mrt-row-actions': {
                    id: 'Actions',
                    size: 80,
                    muiTableBodyCellProps: {
                      sx: (theme) => ({
                        paddingRight: theme.spacing(2),
                        flex: '0 0 auto',
                      }),
                    },
                  },
                  'mrt-row-expand': {
                    size: 40,
                    minSize: 40,
                    maxSize: 40,
                    muiTableBodyCellProps: {
                      sx: {
                        paddingRight: '2px',
                        paddingLeft: '0',
                        flex: '0 0 auto',
                        alignItems: 'start'
                      },
                    },
                  },
                }}
                columns={[
                  { id: 'Questionnaire',
                    size: 400,
                    muiTableBodyCellProps: {
                      sx: {
                        paddingLeft: 0,
                        fontWeight: "bold",
                        paddingTop: "10px",
                        whiteSpace: 'nowrap',
                      },
                    },
                    Cell: ({ row }) => (
                                   <Grid container direction="row" spacing={1} justifyContent="flex-start" wrap="nowrap">
                                     <Grid item xs={false}>
                                       <Avatar className={classes.subjectFormAvatar}><FormIcon/></Avatar>
                                     </Grid>
                                     <Grid item xs={false}>
                                       <Link to={"/content.html" + row.original["@path"]} underline="hover">
                                         {questionnaireTitle}
                                       </Link>
                                       <Typography variant="caption" component="div" color="textSecondary">
                                         Created {DateTime.fromISO(row.original['jcr:created']).toFormat("yyyy-MM-dd HH:mm")}
                                       </Typography>
                                       <Typography variant="caption" component="div" color="textSecondary">
                                         Last modified {DateTime.fromISO(row.original['jcr:lastModified']).toFormat("yyyy-MM-dd HH:mm")}
                                       </Typography>
                                     </Grid>
                                   </Grid>
                                 ) },
                  { id: 'Status',
                    muiTableBodyCellProps: {
                      sx: (theme) => ({
                        whiteSpace: 'nowrap',
                        paddingTop: "10px",
                        paddingBottom: theme.spacing(1),
                      }),
                    },
                    Cell: ({ row }) => (<>
                                         { row.original["statusFlags"].map((status) => {
                                           return <Chip
                                             key={status}
                                             label={wordToTitleCase(status)}
                                             variant="outlined"
                                             className={`${classes.subjectChip} ${classes[status + "Chip"] || classes.DefaultChip}`}
                                             size="small"
                                           />
                                         })}
                                       </>) },
                ]}
                enableRowActions
                positionActionsColumn="last"
                renderRowActions={({ row }) => (
                    <Box sx={{ display: 'flex', flexWrap: 'nowrap'}}>
                      <EditButton
                        entryPath={row.original["@path"]}
                        entryType="Form"
                      />
                      <DeleteButton
                        entryPath={row.original["@path"]}
                        entryName={getEntityIdentifier(row.original)}
                        entryType="Form"
                        onComplete={fetchTableData}
                      />
                    </Box>
                )}
              />
            </Grid>
          ))
        }
        </>
      }
      { /* Render child subjects at the bottom when the current subject is expanded */ }
      { expanded && childSubjects?.length ?
        (<Grid item xs={12} className={classes.subjectNestedContainer}>
          {childSubjects.map( (subject, i) => {
            // Render the container again for each child subject
            return(
              <SubjectContainer
                key={i}
                classes={classes}
                path={subject["@path"]}
                level={level+1}
                maxDisplayed={maxDisplayed}
                pageSize={pageSize}
                subject={subject}
                fetchSubjectData={fetchSubjectData}/>
            )
          })}
        </Grid>
        )
        : ""
      }
    </>
  );
};

let SubjectMember = withRouter(SubjectMemberInternal);

// Component that displays a preview of the saved form answers
function FormData(props) {
  let { formID, maxDisplayed, classes } = props;
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // number of form question/answers listed, will increase
  let displayed = 0;

  let globalLoginDisplay = useContext(GlobalLoginContext);

  // Fetch the form's data from the server
  // It will be stored in the `data` state variable
  let getFormData = (formID) => {
    fetchWithReLogin(globalLoginDisplay, `/Forms/${formID}.deep.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => setData(json))
        .catch(handleFormError)
  }

  let handleFormError = (response) => {
    setError(response);
    setData([]);  // Prevent an infinite loop if data was not set
  };

  // Fetch this Form's data
  useEffect(() => {
    getFormData(formID);
  }, [formID]);

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    return (
      <Grid container justifyContent="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  if (error) {
    return (
      <Typography variant="h2" color="error">
        Error obtaining form data: {error.status} {error.statusText}
      </Typography>
    );
  }
  // Handle questions and sections differently
  let handleDisplayQuestion = (entryDefinition, data, key) => {
    let result = displayQuestion(entryDefinition, data, key, classes);
    if (result && displayed < maxDisplayed) {
      displayed++;
    } else {
      result = null;
    }
    return result;
  }

  if (data && data.questionnaire) {
    return (
      <div className={classes.formPreview}>
        {
          Object.entries(data.questionnaire)
          .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
          .map(([key, entryDefinition]) => handleDisplay(entryDefinition, data, key, handleDisplayQuestion))
        }
        { !displayed && <Typography variant="caption" color="textSecondary">There is no data in this form</Typography> }
      </div>
    );
  }
  else return;
}

// Display the questions/question found within sections
export function displayQuestion(entryDefinition, data, key, classes) {
  const existingQuestionAnswer = data && Object.entries(data)
    .find(([key, value]) => value["sling:resourceSuperType"] == "cards/Answer"
      && value["question"]["jcr:uuid"] === entryDefinition["jcr:uuid"]);

  // question title, to be used when 'previewing' the form
  const questionTitle = entryDefinition["text"];
  // check the display mode and don't display if "hidden"
  const isHidden = (entryDefinition.displayMode == "hidden");

  if (typeof(existingQuestionAnswer?.[1]?.value) != "undefined") {
    let prettyPrintedAnswers = existingQuestionAnswer[1]["displayedValue"];
    // The value can either be a single value or an array of values; force it into an array
    prettyPrintedAnswers = Array.of(prettyPrintedAnswers).flat();

    let content = "";
    switch(entryDefinition["dataType"]) {
      case "file":
        // The value can either be a single value or an array of values; force it into an array
        let paths = Array.of(existingQuestionAnswer[1]["value"]).flat();
        content = <>
          {prettyPrintedAnswers.map((answerValue, idx) => {
            // Encode the filename to ensure special charactars don't result in a broken link
            let path = paths[idx].slice(0, paths[idx].lastIndexOf(answerValue)) + encodeURIComponent(answerValue);
            return (
                <Tooltip key={answerValue} title={"Download " + answerValue}>
                  <Chip
                    icon={<FileIcon />}
                    label={<a href={path} target="_blank" rel="noopener" download={answerValue}>{answerValue}</a>}
                    color="primary"
                    variant="outlined"
                    size="small"
                  />
                </Tooltip>
            );
          })}
          </>
        break;
      case "pedigree":
        if (!prettyPrintedAnswers) {
          // Display absolutely nothing if the value does not exist
          return null;
        } else {
          // Display Pedigree: yes if the value does exist
          content = "Yes";
        }
        break;
      case "computed":
        content = prettyPrintedAnswers.join(", ");
        // check the display mode; if formatted, display accordingly
        if (entryDefinition.displayMode == "formatted") {
          content = <FormattedText variant="body2">{content}</FormattedText>;
        } else {
          content = <>{content}</>;
        }
        break;
      default:
        if (entryDefinition.isRange) {
          let limits = prettyPrintedAnswers.slice(0, 2);
          // In case of invalid data (only one limit of the range is available)
          if (limits.length == 1) limits.push("");
          content = <>{ limits.join(" - ") }</>
        } else {
          content = <>{ prettyPrintedAnswers.join(", ") }</>
        }
        break;
    }
    return (
      isHidden ? null :
      <Typography variant="body2" component="div" className={classes.formPreviewQuestion} key={key}>
        {questionTitle}
        <span className={classes.formPreviewSeparator}>–</span>
        <div className={classes.formPreviewAnswer}>{content}</div>
      </Typography>
    );
  }
  else return null;
};

// Handle questions and sections differently
export function handleDisplay(entryDefinition, data, key, handleDisplayQuestion) {
    if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
      return handleDisplayQuestion(entryDefinition, data, key);
    } else if (SECTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
      // If a section is found, filter questions inside the section
      let currentSection = entryDefinition;
      if (data.questionnaire) {
        currentSection = Object.entries(data.questionnaire)
          .filter(([key, value]) => SECTION_TYPES.includes(value['jcr:primaryType'])
                               && value["@name"] == entryDefinition["@name"])[0]
        currentSection = currentSection ? currentSection[1] : "";
      }

      let currentAnswers = Object.entries(data)
        .filter(([key, value]) => value["sling:resourceType"] == "cards/AnswerSection"
                               && value["section"]["@name"] == entryDefinition["@name"])[0];
      currentAnswers = currentAnswers ? currentAnswers[1] : "";
      return Object.entries(currentSection)
        .filter(([key, value]) => QUESTION_TYPES.includes(value['jcr:primaryType']) || SECTION_TYPES.includes(value['jcr:primaryType']))
        .map(([key, entryDefinition]) => handleDisplay(entryDefinition, currentAnswers, key, handleDisplayQuestion))
  }
}

Subject.propTypes = {
  id: PropTypes.string
}

Subject.defaultProps = {
  maxDisplayed: 4,
  pageSize: 10,
}

export default withStyles(QuestionnaireStyle)(withRouter(Subject));
