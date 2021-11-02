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

import React, { useState, useContext, useEffect } from "react";
import { Link, withRouter } from 'react-router-dom';
import PropTypes from "prop-types";
import moment from "moment";

import {
  Avatar,
  CircularProgress,
  Chip,
  Grid,
  Typography,
  withStyles,
} from "@material-ui/core";

import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import EditButton from "../dataHomepage/EditButton.jsx";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

import { ENTRY_TYPES } from "./FormEntry.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import MaterialTable, { MTablePagination } from 'material-table';
import { displayQuestion, handleDisplay } from "./SubjectUtilities.jsx";

/***
 * Create a URL that checks for the existence of a subject
 */
let createQueryURL = (query, type) => {
  let url = new URL("/query", window.location.origin);
  url.searchParams.set("query", `SELECT * FROM [${type}] as n` + query);
  return url;
}


/**
 * Component that recursively gets and displays the selected subject and its related SubjectTypes
 */
function SubjectContainer(props) {
  let { id, classes, level, maxDisplayed, pageSize, subject } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // hold related subjects
  let [relatedSubjects, setRelatedSubjects] = useState();
  // whether the subject has been deleted
  let [ deleted, setDeleted ] = useState();

  let globalLoginDisplay = useContext(GlobalLoginContext);

  // 'level' of subject component
  const currentLevel = level || 0;

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
  };

  let check_url = createQueryURL(` WHERE n.'parents'='${subject?.['jcr:uuid']}' order by n.'jcr:created'`, "cards:Subject");
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
    } else {
      setRelatedSubjects(null);
    }
  }, [subject]);

  if (deleted) {
    return null;
  }

  // If the data has not yet been fetched, return an in-progress symbol
  if (!subject) {
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  if (error) {
    return (
      <Grid container justify="center">
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
      <SubjectMember classes={classes} id={id} level={currentLevel} data={subject} maxDisplayed={maxDisplayed} pageSize={pageSize} onDelete={() => {setDeleted(true)}} hasChildren={!!(relatedSubjects?.length)}/>
      {relatedSubjects && relatedSubjects.length > 0 ?
        (<Grid item xs={12} className={classes.subjectNestedContainer}>
          {relatedSubjects.map( (subject, i) => {
            // Render component again for each related subject
            return(
              <SubjectContainer key={i} classes={classes} path={subject["@path"]} level={currentLevel+1} maxDisplayed={maxDisplayed} pageSize={pageSize} subject={subject}/>
            )
          })}
        </Grid>
        ) : ""
      }
    </React.Fragment>
  );
}

/**
 * Component that displays all forms related to a Subject. Do not use directly, use SubjectMember instead.
 */
function SubjectMemberInternal (props) {
  let { classes, data, history, id, level, maxDisplayed, onDelete, pageSize, hasChildren } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  let [ subjectGroups, setSubjectGroups ] = useState();

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

  // If an error was returned, do not display a subject at all, but report the error
  if (error) {
    return (
      <Grid container justify="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining subject data: {error.status} {error.statusText ? error.statusText : error.toString()}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  let identifier = data && data.identifier ? data.identifier : id;
  let label = data?.type?.label || "Subject";
  let title = `${label} ${identifier}`;
  let path = data ? data["@path"] : "/Subjects/" + id;
  let avatar = <Avatar className={classes.subjectAvatar}>{label.split(' ').map(s => s?.charAt(0)).join('').toUpperCase()}</Avatar>;
  let action = <DeleteButton
                 entryPath={path}
                 entryName={title}
                 entryType={label}
                 onComplete={onDelete}
                 buttonClass={classes.childSubjectHeaderButton}
               />

  // If this is the top-level subject and we have no data to display for it, inform the user:
  if (data && level == 0 && !(Object.keys(subjectGroups || {}).length) && !hasChildren) {
    return (
      <Grid item>
        <Typography color="textSecondary" variant="caption">{`No data associated with this ${label.toLowerCase()} was found.`}</Typography>
      </Grid>
    )
  }

  return ( data &&
    <>
    {
      level > 0 &&
        <Grid item className={classes.subjectTitleWithAvatar}>
          <Grid container direction="row" spacing={1} justify="flex-start">
            <Grid item xs={false}>{avatar}</Grid>
            <Grid item>
              <Typography variant="h5">
                 <Link to={"/content.html" + path}>{title}</Link>
                 {action}
              </Typography>
            </Grid>
          </Grid>
        </Grid>
      }
      { subjectGroups && Object.keys(subjectGroups).length > 0 && <>
        {
          Object.keys(subjectGroups).map( (questionnaireTitle, j) => {
            return(<Grid item key={questionnaireTitle}>
              <Typography variant="h6">{questionnaireTitle}</Typography>
              <MaterialTable
                data={subjectGroups[questionnaireTitle]}
                style={{ boxShadow : 'none' }}
                options={{
                  actionsColumnIndex: -1,
                  emptyRowsWhenPaging: false,
                  toolbar: false,
                  pageSize: pageSize,
                  header: false,
                  rowStyle: {
                    verticalAlign: 'top',
                  }
                }}
                columns={[
                  { title: 'Created',
                    cellStyle: {
                      paddingLeft: 0,
                      fontWeight: "bold",
                      width: '1%',
                      whiteSpace: 'nowrap',
                    },
                    render: rowData => <Link to={"/content.html" + rowData["@path"]}>
                                         {moment(rowData['jcr:created']).format("YYYY-MM-DD")}
                                       </Link> },
                  { title: 'Status',
                    cellStyle: {
                      width: '1%',
                      whiteSpace: 'pre-wrap',
                      paddingBottom: "8px",
                    },
                    render: rowData => <React.Fragment>
                                         {rowData["statusFlags"].map((status) => {
                                           return <Chip
                                             key={status}
                                             label={wordToTitleCase(status)}
                                             variant="outlined"
                                             className={`${classes.subjectChip} ${classes[status + "Chip"] || classes.DefaultChip}`}
                                             size="small"
                                           />
                                         })}
                                       </React.Fragment> },
                  { title: 'Summary',
                    render: rowData => <FormData className={classes.formData} formID={rowData["@name"]} maxDisplayed={maxDisplayed} classes={classes}/> },
                  { title: 'Actions',
                    cellStyle: {
                      padding: '0',
                      width: '20px',
                      textAlign: 'end'
                    },
                    render: rowData => <React.Fragment>
                                         <EditButton
                                           entryPath={rowData["@path"]}
                                           entryType="Form"
                                         />
                                         <DeleteButton
                                           entryPath={rowData["@path"]}
                                           entryName={`${identifier}: ${rowData.questionnaire["@name"]}`}
                                           entryType="Form"
                                           warning={rowData ? rowData["@referenced"] : false}
                                           onComplete={fetchTableData}
                                         />
                                       </React.Fragment> },
                ]}
                components={{
                    Pagination: props => { const { classes, ...other } = props;
                                           return (subjectGroups[questionnaireTitle].length > pageSize && <MTablePagination {...other} />)}
                }}
               />
            </Grid>)
          })
        }
        </>
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
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
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
      <React.Fragment>
        {
          Object.entries(data.questionnaire)
          .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
          .map(([key, entryDefinition]) => handleDisplay(entryDefinition, data, key, handleDisplayQuestion))
        }
      </React.Fragment>
    );
  }
  else return;
}

export default withStyles(QuestionnaireStyle)(SubjectContainer);
