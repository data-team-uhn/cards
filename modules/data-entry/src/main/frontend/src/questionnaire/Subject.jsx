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
import { Link, useLocation, withRouter } from 'react-router-dom';
import PropTypes from "prop-types";
import moment from "moment";

import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import NewFormDialog from "../dataHomepage/NewFormDialog";
import { usePageNameWriterContext } from "../themePage/Page.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import MaterialTable, { MTablePagination } from 'material-table';

import {
  Avatar,
  CircularProgress,
  Chip,
  Grid,
  Typography,
  withStyles,
  Button,
} from "@material-ui/core";
import FileIcon from "@material-ui/icons/InsertDriveFile";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import EditButton from "../dataHomepage/EditButton.jsx";

const QUESTION_TYPES = ["lfs:Question"];
const SECTION_TYPES = ["lfs:Section"];
const ENTRY_TYPES = QUESTION_TYPES.concat(SECTION_TYPES);

/***
 * Create a URL that checks for the existence of a subject
 */
let createQueryURL = (query, type) => {
  let url = new URL("/query", window.location.origin);
  url.searchParams.set("query", `SELECT * FROM [${type}] as n` + query);
  return url;
}

let defaultCreator = (node) => {
  return {to: "/content.html" + node["@path"]}
}

// Extract the subject id from the subject path
// returns null if the parameter is not a valid subject path (expected format: Subjects/<id>)
export function getSubjectIdFromPath (path) {
  return /Subjects\/([^.]+)/.exec(path || '')?.[1];
}

// Recursive function to get a flat list of parents
export function getHierarchy (node, RenderComponent, propsCreator) {
  let HComponent = RenderComponent || Link;
  let hpropsCreator = propsCreator || defaultCreator;
  let props = hpropsCreator(node);
  let output = <React.Fragment>{node.type.label} <HComponent {...props}>{node.identifier}</HComponent></React.Fragment>;
  if (node["parents"]) {
    let ancestors = getHierarchy(node["parents"], HComponent, propsCreator);
    return <React.Fragment>{ancestors} / {output}</React.Fragment>
  } else {
    return output;
  }
}

// Recursive function to get a flat list of parents with no links and subject labels
export function getTextHierarchy (node, withType = false) {
  let type = withType ? (node?.["type"]?.["@name"] + " "): "";
  let output = node.identifier;
  if (node["parents"]) {
    let ancestors = getTextHierarchy(node["parents"], withType);
    return `${ancestors} / ${type}${output}`;
  } else {
    return type + output;
  }
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
  let { id, classes, maxDisplayed, pageSize } = props;
  const [currentSubject, setCurrentSubject] = useState();
  const [currentSubjectId, setCurrentSubjectId] = useState(id);

  const location = useLocation();

  useEffect(() => {
    let newId = getSubjectIdFromPath(location.pathname);
    newId && setCurrentSubjectId(newId);
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

  return (
    <React.Fragment>
      <div className={classes.mainPageAction}>
        <NewFormDialog currentSubject={currentSubject}>
          New form for this Subject
        </NewFormDialog>
      </div>
      <SubjectContainer path={`/Subjects/${currentSubjectId}`} key={currentSubjectId} classes={classes} maxDisplayed={maxDisplayed} getSubject={handleSubject} pageSize={pageSize}/>
    </React.Fragment>
  );
}

/**
 * Component that recursively gets and displays the selected subject and its related SubjectTypes
 */
function SubjectContainer(props) {
  let { path, classes, level, maxDisplayed, pageSize, getSubject } = props;
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // hold related subjects
  let [relatedSubjects, setRelatedSubjects] = useState();
  // whether the subject has been deleted
  let [ deleted, setDeleted ] = useState();

  let globalLoginDisplay = useContext(GlobalLoginContext);

  // 'level' of subject component
  const currentLevel = level || 0;

  // Fetch the subject's data as JSON from the server.
  // The data will contain the subject metadata,
  // such as authorship and versioning information.
  // Once the data arrives from the server, it will be stored in the `data` state variable.
  let fetchData = () => {
    let strippedPath = path.endsWith('/') ? path.slice(0, -1) : path;
    fetchWithReLogin(globalLoginDisplay, `${strippedPath}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleResponse)
      .catch(handleError);
  };

  // Callback method for the `fetchData` method, invoked when the data successfully arrived from the server.
  let handleResponse = (json) => {
    if (currentLevel == 0) {
      // sends the data to the parent component
      getSubject(json);
    }
    setData(json);
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
    setData([]);  // Prevent an infinite loop if data was not set
  };

  // Fetch this Subject's data
  useEffect(() => {
    fetchData();
  }, []);

  if (deleted) {
    return null;
  }

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  let check_url = createQueryURL(` WHERE n.'parents'='${data?.['jcr:uuid']}' order by n.'jcr:created'`, "lfs:Subject");
  let fetchRelated = () => {
    fetchWithReLogin(globalLoginDisplay, check_url)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((json) => {setRelatedSubjects(json.rows);})
    .catch(handleError);
  }

  if (!relatedSubjects) {
    fetchRelated();
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
    data && <Grid container spacing={4} direction="column" className={classes.subjectContainer}>
      <SubjectMember classes={classes} id={path} level={currentLevel} data={data} maxDisplayed={maxDisplayed} pageSize={pageSize} onDelete={() => {setDeleted(true)}}/>
      {relatedSubjects && relatedSubjects.length > 0 ?
        (<Grid item xs={12} className={classes.subjectNestedContainer}>
          {relatedSubjects.map( (subject, i) => {
            // Render component again for each related subject
            return(
              <SubjectContainer key={i} classes={classes} path={subject["@path"]} level={currentLevel+1} maxDisplayed={maxDisplayed} pageSize={pageSize}/>
            )
          })}
        </Grid>
        ) : ""
      }
    </Grid>
  );
}

/**
 * Component that displays the subject chart header, with subject id, parents, and actions
 */
function SubjectHeader (props) {
  let {data, classes, title, action} = props;
  let parentDetails = data && data['parents'] && getHierarchy(data['parents']);

  return (
    <Grid item className={classes.subjectHeader}>
      {parentDetails && <Typography variant="overline">{parentDetails}</Typography>}
      <Typography variant="h2">{title}{action}</Typography>
      {
        data && data['jcr:createdBy'] && data['jcr:created'] ?
        <Typography
          variant="overline"
          className={classes.subjectSubHeader}>
            Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}
        </Typography>
        : ""
      }
    </Grid>
  )
}

/**
 * Component that displays all forms related to a Subject. Do not use directly, use SubjectMember instead.
 */
function SubjectMemberInternal (props) {
  let { classes, data, history, id, level, maxDisplayed, onDelete, pageSize } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // table data: related forms to the subject
  let [tableData, setTableData] = useState();
  let [subjectGroups, setSubjectGroups] = useState();

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
    setTableData(json.rows);
    let groups = {};
    json.rows.map( (entry, i) => {
      let title = entry.questionnaire.title || entry.questionnaire["@name"];
      groups[title]?.push(entry) || (groups[title] = [entry]);
    })
    setSubjectGroups(groups);
  };

  let handleTableError = (response) => {
    setError(response);
    setTableData([]);
    setSubjectGroups({});
  };

  let wordToTitleCase = (word) => {
    return word[0].toUpperCase() + word.slice(1).toLowerCase();
  }

  // When the main subject is deleted, if they're our top-level patient we'll redirect to Dashboard
  let handleDeletion = () => {
    if (level === 0) {
      history.push("/");
    }
    onDelete();
  }

  // Fetch table data for all forms related to a Subject
  useEffect(() => {
    fetchTableData();
  }, []);

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

  // change styling based on 'level'
  let headerStyle = (level == 0 ? "h2" : (level == 1 ? "h4" : "h5"));

  let identifier = data && data.identifier ? data.identifier : id;
  let label = data?.type?.label || "Subject";
  let title = `${label} ${identifier}`;
  let path = data ? data["@path"] : "/Subjects/" + id;
  let avatar = level > 0 && <Avatar className={classes.subjectAvatar}>{label.split(' ').map(s => s?.charAt(0)).join('').toUpperCase()}</Avatar>;
  let action = <DeleteButton
                 entryPath={path}
                 entryName={title}
                 entryType={label}
                 onComplete={handleDeletion}
                 buttonClass={level === 0 ? classes.subjectHeaderButton : classes.childSubjectHeaderButton}
                 size={level === 0 ? "large" : null}
               />

  return ( data &&
    <>
    {
      level == 0 ?
        <SubjectHeader data={data} classes={classes} title={title} action={action} />
      :
        <Grid item className={classes.subjectTitleWithAvatar}>
          <Grid container direction="row" spacing={1} justify="flex-start">
            <Grid item xs={false}>{avatar}</Grid>
            <Grid item>
              <Typography variant={headerStyle}>
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
              <Typography variant="subtitle1" color="textSecondary">{subjectGroups[questionnaireTitle]?.[0]?.questionnaire?.description}</Typography>
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
  let handleDisplay = (entryDefinition, data, key) => {
    if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
      return displayQuestion(entryDefinition, data, key);
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
        .filter(([key, value]) => value["sling:resourceType"] == "lfs/AnswerSection"
                               && value["section"]["@name"] == entryDefinition["@name"])[0];
      currentAnswers = currentAnswers ? currentAnswers[1] : "";
      return (
        Object.entries(currentSection)
        .filter(([key, value]) => QUESTION_TYPES.includes(value['jcr:primaryType']) || SECTION_TYPES.includes(value['jcr:primaryType']))
        .map(([key, entryDefinition]) => handleDisplay(entryDefinition, currentAnswers, key))
      )
    }
  }
  // Display the questions/question found within sections
  let displayQuestion = (entryDefinition, data, key) => {
    const existingQuestionAnswer = data && Object.entries(data)
      .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
        && value["question"]["jcr:uuid"] === entryDefinition["jcr:uuid"]);

    // question title, to be used when 'previewing' the form
    const questionTitle = entryDefinition["text"];

    if (typeof(existingQuestionAnswer?.[1]?.value) != "undefined" && (displayed < maxDisplayed)) {
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
              return <Chip
                       key={answerValue}
                       icon={<FileIcon />}
                       label={<a href={path} target="_blank" rel="noopener" download={answerValue}>{answerValue}</a>}
                       color="primary"
                       variant="outlined"
                       size="small"
                     />
            })}
            </>
          break;
        case "pedigree":
          if (!prettyPrintedAnswers) {
            // Display absolutely nothing if the value does not exist
            return <></>;
          } else {
            // Display Pedigree: yes if the value does exist
            content = "Yes";
          }
          break;
        default:
          content = <>{ prettyPrintedAnswers.join(", ") }</>
          break;
      }
      // If count of displayed <= max, increase count of displayed
      displayed++;
      return (
        <Typography variant="body2" component="p" key={key} className={classes.formPreviewQuestion}>{questionTitle}: {content}</Typography>
      );
    }
    else return;
  };

  if (data && data.questionnaire) {
    return (
      <React.Fragment>
        {
          Object.entries(data.questionnaire)
          .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
          .map(([key, entryDefinition]) => handleDisplay(entryDefinition, data, key))
        }
      </React.Fragment>
    );
  }
  else return;
}

Subject.propTypes = {
  id: PropTypes.string
}

Subject.defaultProps = {
  maxDisplayed: 4,
  pageSize: 10,
}

export default withStyles(QuestionnaireStyle)(Subject);
