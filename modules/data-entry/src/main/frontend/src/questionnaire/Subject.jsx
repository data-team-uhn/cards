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

import FormattedText from "../components/FormattedText";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import NewFormDialog from "../dataHomepage/NewFormDialog";
import { QUESTION_TYPES, SECTION_TYPES, MATRIX_TYPES, ENTRY_TYPES } from "./FormEntry.jsx";
import { usePageNameWriterContext } from "../themePage/Page.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import MaterialTable, { MTablePagination } from 'material-table';

import {
  Avatar,
  CircularProgress,
  Card,
  CardContent,
  Chip,
  Grid,
  Tooltip,
  Tab,
  Tabs,
  Typography,
  withStyles,
} from "@material-ui/core";
import FileIcon from "@material-ui/icons/InsertDriveFile";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import EditButton from "../dataHomepage/EditButton.jsx";
import PrintButton from "../dataHomepage/PrintButton.jsx";
import ResourceHeader from "./ResourceHeader.jsx"
import SubjectTimeline from "./SubjectTimeline.jsx";

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
  if (node["parents"] && node["parents"].type) {
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

// Recursive function to get the list of ancestors as an array
export function getHierarchyAsList (node, includeHomepage) {
  let props = defaultCreator(node);
  let parent = <>{node.type.label} <Link {...props}>{node.identifier}</Link></>;
  if (node["parents"]) {
    let ancestors = getHierarchyAsList(node["parents"]);
    ancestors.push(parent);
    return ancestors;
  } else {
    let result = [parent];
    includeHomepage && result.unshift(getHomepageLink(node));
    return result;
  }
}

export function getHomepageLink (subjectNode) {
  let props = defaultCreator({"@path": `/Subjects#subjects:activeTab=${subjectNode?.type?.["@name"]}`});
  return (<Link {...props}>{subjectNode?.type?.subjectListLabel || "Subjects"}</Link>);
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
        <SubjectHeader id={currentSubjectId} key={"SubjectHeader"} pageTitle={pageTitle} classes={classes} getSubject={handleSubject} history={history} contentOffset={props.contentOffset}/>
        <Grid item>
          <Tabs className={classes.subjectTabs} value={activeTab} onChange={(event, value) => {
            setTab(value);
          }}>
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
 * Component that displays the header for the selected subject and its SubjectType
 */
function SubjectHeader(props) {
  let { id, classes, getSubject, history, pageTitle } = props;
  // This holds the full form JSON, once it is received from the server
  let [ subject, setSubject ] = useState(null);
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  let globalLoginDisplay = useContext(GlobalLoginContext);

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

  if (!subject?.data) {
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
  let label = subject?.data?.type?.label || "Subject";
  let title = `${label} ${identifier}`;
  let path = subject?.data?.["@path"] || "/Subjects/" + id;
  let subjectMenu = (
            <div className={classes.actionsMenu}>
               <PrintButton
                 resourcePath={path}
                 breadcrumb={pageTitle}
                 date={moment(subject?.data['jcr:created']).format("MMM Do YYYY")}
               />
               <DeleteButton
                 entryPath={path}
                 entryName={title}
                 entryType={label}
                 onComplete={handleDeletion}
                 size="medium"
               />
            </div>
  );
  let parentDetails = (subject?.data?.['parents'] && getHierarchyAsList(subject.data['parents'], true) || [getHomepageLink(subject?.data)]);

  return (
    subject?.data &&
      <ResourceHeader
        title={title}
        breadcrumbs={parentDetails}
        action={subjectMenu}
        contentOffset={props.contentOffset}
        >
      {
        subject?.data?.['jcr:created'] ?
        <Typography
          variant="overline"
          color="textSecondary" >
            Entered by {subject.data['jcr:createdBy']} on {moment(subject.data['jcr:created']).format("dddd, MMMM Do YYYY")}
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
  let action = <>
                 <PrintButton
                   resourcePath={path}
                   breadcrumb={getTextHierarchy(data, true)}
                   date={moment(data['jcr:created']).format("MMM Do YYYY")}
                   buttonClass={classes.childSubjectHeaderButton}
                   disableShortcut
                 />
                 <DeleteButton
                   entryPath={path}
                   entryName={title}
                   entryType={label}
                   onComplete={onDelete}
                   buttonClass={classes.childSubjectHeaderButton}
                 />
               </>
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

// Display the question matrix found within sections
export function displayQuestionMatrix(entryDefinition, data, key, classes) {

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
  if (isHidden) {
    return null;
  }

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
            // Encode the filename to ensure special characters don't result in a broken link
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
    } else if (SECTION_TYPES.includes(entryDefinition["jcr:primaryType"]) || MATRIX_TYPES.includes(entryDefinition["jcr:primaryType"])) {
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
