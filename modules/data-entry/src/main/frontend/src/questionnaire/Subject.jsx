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

import React, { useEffect, useState } from "react";
import { Link, useLocation } from 'react-router-dom';

import PropTypes from "prop-types";
import moment from "moment";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import NewFormDialog from "../dataHomepage/NewFormDialog";
import { usePageNameWriterContext } from "../themePage/Page.jsx";

import {
  CircularProgress,
  Chip,
  Grid,
  Typography,
  Card,
  CardHeader,
  CardContent,
  withStyles,
  Button,
} from "@material-ui/core";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";

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
  return /Subjects\/(.+)/.exec(path || '')?.[1];
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
  let { id, classes, maxDisplayed } = props;
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

  let parentDetails = currentSubject && currentSubject['parents'] && getHierarchy(currentSubject['parents']);

  return (
    <React.Fragment>
      <div className={classes.subjectNewButton} style={{ top: props.contentOffset + 'px' }} >
          <NewFormDialog currentSubject={currentSubject}>
            New form for this Subject
          </NewFormDialog>
      </div>
      {parentDetails && <Typography variant="overline">{parentDetails}</Typography>}
      <SubjectContainer id={currentSubjectId} key={currentSubjectId}  classes={classes} maxDisplayed={maxDisplayed} getSubject={handleSubject}/>
    </React.Fragment>
  );
}

/**
 * Component that recursively gets and displays the selected subject and its related SubjectTypes
 */
function SubjectContainer(props) {
  let { id, classes, level, maxDisplayed, getSubject } = props;
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // hold related subjects
  let [relatedSubjects, setRelatedSubjects] = useState();
  // 'level' of subject component
  const currentLevel = level || 0;

  // Fetch the subject's data as JSON from the server.
  // The data will contain the subject metadata,
  // such as authorship and versioning information.
  // Once the data arrives from the server, it will be stored in the `data` state variable.
  let fetchData = () => {
    fetch(`/Subjects/${id}.deep.json`)
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

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    fetchData();
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  // get related SubjectTypes
  let check_url = createQueryURL(` WHERE n.'parents'='${data['jcr:uuid']}'`, "lfs:Subject");
  let fetchRelated = () => {
    fetch(check_url)
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
    <Grid container spacing={3}>
      <SubjectMember classes={classes} id={id} level={currentLevel} data={data} maxDisplayed={maxDisplayed}/>
      {relatedSubjects ?
        (<Grid item xs={12} className={classes.subjectContainer}>
          {relatedSubjects.map( (subject, i) => {
            // Render component again for each related subject
            return(
              <SubjectContainer key={i} classes={classes} id={subject["@name"]} level={currentLevel+1} maxDisplayed={maxDisplayed}/>
            )
          })}
        </Grid>
        ) : ""
      }
    </Grid>
  );
}

/**
 * Component that displays all forms related to a Subject.
 */
function SubjectMember (props) {
  let { id, classes, level, data, maxDisplayed } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // table data: related forms to the subject
  let [tableData, setTableData] = useState();
  let [subjectGroups, setSubjectGroups] = useState();

  const customUrl=`/Forms.paginate?fieldname=subject&fieldvalue=${encodeURIComponent(data['jcr:uuid'])}&includeallstatus=true`;

  // Fetch the forms associated with the subject as JSON from the server
  // It will be stored in the `tableData` state variable
  let fetchTableData = () => {
    fetch(customUrl)
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

  // If the data has not yet been fetched, fetch
  if (!tableData) {
    fetchTableData();
  }

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
  let buttonSize = "large";
  let headerStyle = "h2";
  if (level == 1) {buttonSize = "medium"; headerStyle="h4"};
  if (level > 1) {buttonSize = "small"; headerStyle="h5"};

  let identifier = data && data.identifier ? data.identifier : id;

  return (
    <Grid item xs={12}>
      <Grid item>
        <span className={classes.subjectHeader}>
          <Typography variant={headerStyle}>
            {data?.type?.label || "Subject"} {identifier}
          </Typography>
        </span>
        <DeleteButton
          entryPath={data ? data["@path"] : "/Subjects/" + id}
          entryName={(data?.type?.label || "Subject") + " " + (identifier)}
          entryType={data?.type?.label || "Subject"}
          warning={data ? data["@referenced"] : false}
          shouldGoBack={level === 0}
          buttonClass={level === 0 ? classes.subjectHeaderButton : classes.childSubjectHeaderButton}
          size={level === 0 ? "large" : null}
        />
        {
          data && data['jcr:createdBy'] && (level == 0) && data['jcr:created'] ?
            <Typography
              variant="overline"
              className={classes.subjectSubHeader}>
                Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}
            </Typography>
          : ""
        }
      </Grid>
      <Grid item>
        { subjectGroups &&
          Object.keys(subjectGroups).map( (questionnaireName, j) => {
            return(<React.Fragment key={questionnaireName}>
	          <Typography variant="h4" className={classes.subjectQuestionnaireSubheader}>
		        {questionnaireName}
		      </Typography>
		      <Grid container spacing={3}>
		        { subjectGroups[questionnaireName].map( (entry, i) => {
	              return(
	                <Grid item lg={12} xl={6} key={`${entry.questionnaire["jcr:uuid"]}-${i}`}>
	                  <Card className={classes.subjectCard}>
	                    <CardHeader
	                      title={
	                        <React.Fragment>
	                          <Button size={buttonSize} className={classes.subjectFormHeaderButton}>
	                            {moment(entry["jcr:created"]).format("yyyy-MM-DD")}
	                          </Button>
	                          {entry["statusFlags"].map((status) => {
	                            return <Chip
	                              key={status}
	                              label={wordToTitleCase(status)}
	                              className={`${classes.subjectChip} ${classes[status + "Chip"] || classes.DefaultChip}`}
	                              size="small"
	                            />
	                          })}
	                        </React.Fragment>
	                      }
	                      className={classes.subjectFormHeader}
	                      action={
	                        <DeleteButton
	                          entryPath={entry["@path"]}
	                          entryName={`${identifier}: ${entry.questionnaire["@name"]}`}
	                          entryType="Form"
	                          warning={entry ? entry["@referenced"] : false}
	                        />
	                      }
	                    />
	                    <CardContent>
	                      <FormData formID={entry["@name"]} maxDisplayed={maxDisplayed}/>
	                      <Link to={"/content.html" + entry["@path"]}>
	                        <Typography variant="body2" component="p">See More...</Typography>
	                      </Link>
	                    </CardContent>
	                  </Card>
	                </Grid>
	              )
	            })}
	          </Grid>
	        </React.Fragment>)
          })
        }
      </Grid>
    </Grid>
  );
};

// Component that displays a preview of the saved form answers
function FormData(props) {
  let { formID, maxDisplayed } = props;
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // number of form question/answers listed, will increase
  let displayed = 0;

  // Fetch the form's data from the server
  // It will be stored in the `data` state variable
  let getFormData = (formID) => {
    fetch(`/Forms/${formID}.deep.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => setData(json))
        .catch(handleFormError)
  }

  let handleFormError = (response) => {
    setError(response);
    setData([]);  // Prevent an infinite loop if data was not set
  };

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    getFormData(formID);
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
      let currentSection = Object.entries(data.questionnaire)
        .filter(([key, value]) => SECTION_TYPES.includes(value['jcr:primaryType']) && value["@name"] == entryDefinition["@name"])[0]
      currentSection = currentSection ? currentSection[1] : "";
      let currentAnswers = Object.entries(data)
        .filter(([key, value]) => value["sling:resourceType"] == "lfs/AnswerSection" && value["section"]["@name"] == entryDefinition["@name"])[0];
      currentAnswers = currentAnswers ? currentAnswers[1] : "";
      return (
        Object.entries(currentSection)
        .filter(([key, value]) => QUESTION_TYPES.includes(value['jcr:primaryType']))
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

    if (existingQuestionAnswer && existingQuestionAnswer[1]["value"] && (displayed < maxDisplayed)) {
      // Check for a label and use the label instead of the value, if present
      let existingQuestionAnswerValue = existingQuestionAnswer[1]["value"];
      let answerValue = entryDefinition[existingQuestionAnswerValue]?.["label"]
        ? entryDefinition[existingQuestionAnswerValue]["label"]
        : existingQuestionAnswerValue;
      let content = `${questionTitle}: ${answerValue}`;
      // If count of displayed <= max, increase count of displayed
      displayed++;
      return (
        <Typography variant="body2" component="p" key={key}>{content}</Typography>
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
  maxDisplayed: 4
}

export default withStyles(QuestionnaireStyle)(Subject);
