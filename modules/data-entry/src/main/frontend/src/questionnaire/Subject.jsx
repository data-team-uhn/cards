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

import React, { useState } from "react";
import PropTypes from "prop-types";
import moment from "moment";
import { Link } from 'react-router-dom';
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

import {
  CircularProgress,
  Grid,
  Typography,
  Card,
  CardHeader,
  CardContent,
  withStyles,
  Button
} from "@material-ui/core";

const QUESTION_TYPES = ["lfs:Question"];
const SECTION_TYPES = ["lfs:Section"];
const ENTRY_TYPES = QUESTION_TYPES.concat(SECTION_TYPES);

/***
 * Create a URL that checks for the existance of a subject
 */
let createQueryURL = (query, type) => {
  let url = new URL("/query", window.location.origin);
  url.searchParams.set("query", `SELECT * FROM [${type}] as n` + query);
  return url;
}

/**
 * Component that displays a Subject. Component recursively displays 'SubjectMember' components.
 *
 * @example
 * <Subject id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a subject; this is the JCR node name
 * @param {int} level the 'level' of the subject component, used for styling based on nesting level
 */

function Subject(props) {
  let { id, classes, level } = props;
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
  // let check_url = createQueryURL(` WHERE CONTAINS (n.'parents', '${data['jcr:uuid']}')`, "lfs:Subject");
  let fetchRelated = () => { 
    fetch(check_url)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((json) => {setRelatedSubjects(json.rows);})
  } //TODO: handle error

  if (!relatedSubjects) {
    fetchRelated();
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
    <Grid container spacing={3}>
      <SubjectMember classes={classes} id={id} level={currentLevel} data={data}/> 
      {relatedSubjects ?
        (<Grid item xs={12}>
          {relatedSubjects.map( (subject, i) => { // map each result from the subject fetch (each form)
            return(
                <Subject key={i} classes={classes} id={subject["@name"]} level={currentLevel+1}/>
            )
          })}
        </Grid>
        ) : <Grid item><Typography variant="body2" component="p">Loading...</Typography></Grid> // TODO: fix, doesnt work
      }
    </Grid>
  );
}

/**
 * Component that displays all forms related to a Subject.
 */
function SubjectMember (props) {
  let { id, classes, level, data } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // table data: related forms to the subject
  let [tableData, setTableData] = useState();

  const customUrl='/Forms.paginate?fieldname=subject&fieldvalue='
        + encodeURIComponent(data['jcr:uuid']);
  
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
  };

  let handleTableError = (response) => {
    setError(response);
    setTableData([]);
  };

  // If the data has not yet been fetched, return an in-progress symbol
  if (!tableData) {
    fetchTableData();
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
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

  let buttonSize = "large";
  let headerStyle = "h2";
  if (level == 1) {buttonSize = "medium"; headerStyle="h4"};
  if (level > 1) {buttonSize = "small"; headerStyle="h5"};

  //TODO: fix loading symbol, too many circles

  return (
    <Grid item xs={12}>
      <Grid item className={classes.subjectHeader}>
        {
          data && data.identifier ?
            <Typography variant={headerStyle}>{data.type.label} {data.identifier}</Typography>
          : <Typography variant={headerStyle}>{data.type.label} {id}</Typography>
        }
        {
          data && data['jcr:createdBy'] && data['jcr:created'] ?
          <Typography variant="overline">Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}</Typography>
          : ""
        }
      </Grid>
      <Grid item>
      {tableData ?
          (<Grid container spacing={3}>
            {tableData.map( (entry, i) => {
              return(
                <Grid item lg={12} xl={6} key={`${entry.questionnaire["jcr:uuid"]}-${i}`}>
                  <Card className={classes.subjectCard}>
                    <CardHeader
                      title={
                        <Button size={buttonSize} className={classes.subjectFormHeaderButton}>
                          {/* TODO: size will be dependent on subject 'level' */}
                          {entry.questionnaire["@name"]}
                        </Button> 
                      }
                    className={classes.subjectFormHeader}
                    />
                    <CardContent>
                      <FormData formID={entry["@name"]} maxDisplayed={4}/>
                      <Link to={"/content.html" + entry["@path"]}>
                        <Typography variant="body2" component="p">See More...</Typography>
                      </Link>
                    </CardContent>
                  </Card>
                </Grid>
              )
            })}
          </Grid>
          ) : <Typography variant="body2" component="p">Loading...</Typography>
        }
      </Grid>
    </Grid>
  );
};

// Component that displays a preview of the saved form answers
function FormData(props) {
  let { formID, maxDisplayed } = props; // todo: set maxDisplayed default to 2
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // Hold number of form items displayed
  // let [displayed, setDisplayed] = useState(0);

  let displayed = 0;

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

  let handleDisplay = (entryDefinition, data, key) => {
    if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
      return displayQuestion(entryDefinition, data, key);
    } else if (SECTION_TYPES.includes(entryDefinition["jcr:primaryType"])) {
      // get questions inside the section
      let currentSection = (
        Object.entries(data.questionnaire).filter(([key, value]) => SECTION_TYPES.includes(value['jcr:primaryType']))[0][1]
      );
      let currentAnswers = (
        (Object.entries(data).filter(([key, value]) => value["sling:resourceType"] == "lfs/AnswerSection")[0])
        ? (Object.entries(data).filter(([key, value]) => value["sling:resourceType"] == "lfs/AnswerSection")[0][1]) : ""
      )
      return (
        Object.entries(currentSection)
        .filter(([key, value]) => QUESTION_TYPES.includes(value['jcr:primaryType']))
        .map(([key, entryDefinition]) => displayQuestion(entryDefinition, currentAnswers, key))
      )
    }
  }
  
  let displayQuestion = (entryDefinition, data, key) => {
    const existingQuestionAnswer = data && Object.entries(data)
      .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
        && value["question"]["jcr:uuid"] === entryDefinition["jcr:uuid"]);
  
    // question title, to be used when 'previewing' the form
    const questionTitle = entryDefinition["text"];
  
    if (existingQuestionAnswer && existingQuestionAnswer[1]["value"] && (displayed < maxDisplayed)) { // and if count of displayed <= max
      let content = `${questionTitle}: ${existingQuestionAnswer[1]["value"]}`;
      // increase count of displayed
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
  //TODO fix: return this if nothing was returned
  // display if count of displayed = 0
  if (displayed = 0) {
    return (<Typography variant="body2" component="p">No form data yet</Typography>);
  }

  else return;
}

Subject.propTypes = {
  id: PropTypes.string
}

export default withStyles(QuestionnaireStyle)(Subject);
