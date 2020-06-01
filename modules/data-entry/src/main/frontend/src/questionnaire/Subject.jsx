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
  CardActions,
  withStyles,
  Button
} from "@material-ui/core";

const QUESTION_TYPES = ["lfs:Question"];
const SECTION_TYPES = ["lfs:Section"];
const ENTRY_TYPES = QUESTION_TYPES.concat(SECTION_TYPES);

// TODO: new recursive subject component
// function SubjectWrapper(props) {
//   let { id, classes, level } = props;
//   // hold related subjects
//   let [ relatedSubjects, setRelatedSubjects ] = useState();

//   //TODO: fetch related subjects --> setRelatedSubjects. get the id's of each related subject ? need to somehow loop through

//   const currentLevel = level || 0;

//   //currentLevel will also decide styling (header style, etc)

//   return (
//     <React.Fragment>
//       {/* title goes in this component */}
//       <Grid item>
//         {
//           data && data.identifier ?
//             <Typography variant="h2">SubjectType {data.identifier}</Typography>
//           : <Typography variant="h2">SubjectType {id}</Typography>
//         }
//         {
//           data && data['jcr:createdBy'] && data['jcr:created'] ?
//           <Typography variant="overline">Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}</Typography>
//           : ""
//         }
//       </Grid>
//       {/* place the following in its own grid ? */}
//       {/* return the current subject AND it's related subjects. then calls this component again, each previously related subject is returned with THEIR related subjects.  */}
//       {id.map((subject, i) => {
//         return (
//           <Grid item key={`level-${currentLevel}-${i}`}>
//             <Subject id={id}/> 
//             {/* set props for above subject (render as usual) */}
//             {relatedSubjects && <SubjectWrapper id={id.relatedSubjects} level={currentLevel+1}/>}
//           </Grid>
//         )
//       })}
      
//     </React.Fragment>
//   );
// }

/**
 * Component that displays a Subject.
 *
 * @example
 * <Subject id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a subject; this is the JCR node name
 */
function Subject (props) {
  let { id, classes } = props;
  // This holds the full subject JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // table data: related forms to the subject
  let [tableData, setTableData] = useState();

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

  const customUrl='/Forms.paginate?fieldname=subject&fieldvalue='
        + encodeURIComponent(data['jcr:uuid']);
  
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
            Error obtaining subject data: {error.status} {error.statusText}
          </Typography>
        </Grid>
      </Grid>
    );
  }  

  return (
    <React.Fragment>
      <Grid item className={classes.subjectHeader}>
        {
          data && data.identifier ?
            <Typography variant="h2">SubjectType {data.identifier}</Typography>
          : <Typography variant="h2">SubjectType {id}</Typography>
        }
        {
          data && data['jcr:createdBy'] && data['jcr:created'] ?
          <Typography variant="overline">Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}</Typography>
          : ""
        }
      </Grid>
      {tableData ?
          (<Grid container spacing={3}>
            {tableData.map( (entry) => { // map each result from the subject fetch (each form)
              return(
                <Grid item lg={12} xl={6} key={entry.questionnaire["jcr:uuid"]}>
                  <Card className={classes.subjectCard}>
                    <CardHeader
                      title={
                        <Button size="large" className={classes.subjectFormHeaderButton}>
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
    </React.Fragment>
  );
};

let displayQuestion = (questionDefinition, existingAnswer, key) => {

  if (SECTION_TYPES.includes(questionDefinition["jcr:primaryType"])) {
    console.log(existingAnswer);
    // Object.entries(existingAnswer.questionnaire)
    // .filter(([key, value]) => SECTION_TYPES.includes(value['jcr:primaryType']))
    // .filter(([key, value]) => QUESTION_TYPES.includes(value['jcr:primaryType']))
    // .map(([key, questionDefinition]) => displayQuestion(questionDefinition, existingAnswer, key))
    // return displaySection(entryDefinition, path, depth, existingAnswers, keyProp);
  }

  // TODO: section (get questions --> displayQuestion)

  const existingQuestionAnswer = existingAnswer && Object.entries(existingAnswer)
    .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
      && value["question"]["jcr:uuid"] === questionDefinition["jcr:uuid"]);

  // question title, to be used when 'previewing' the form
  const questionTitle = questionDefinition["text"];

  if (existingQuestionAnswer && existingQuestionAnswer[1]["value"]) {
    let content = `${questionTitle}: ${existingQuestionAnswer[1]["value"]}`
    return (
      <Typography variant="body2" component="p" key="key">{content}</Typography>
    );
  }

  else return null;
};

// let getQuestions = (questionDefinition, existingAnswer, key) => {
//   console.log(questionDefinition);
//   console.log(existingAnswer)

//   const existingQuestionAnswer = questionDefinition && Object.entries(questionDefinition)
//   .filter(([key, value]) => QUESTION_TYPES.includes(value['jcr:primaryType']));

//   if (existingQuestionAnswer) {
//     console.log(existingQuestionAnswer);
//     return (existingQuestionAnswer);
//   }

//   else return;
// }

// Component that displays a preview of the saved form answers
function FormData(props) {
  let { classes, formID, maxDisplayed } = props; // todo: set maxDisplayed default to 2
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

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

  if (data && data.questionnaire) {
    return (
      <React.Fragment>
        {/* sections --> questions */}
        {/* {console.log(data.questionnaire)}
        {
          Object.entries(data.questionnaire)
          .filter(([key, value]) => SECTION_TYPES.includes(value['jcr:primaryType']))
          .filter(([key, value]) => QUESTION_TYPES.includes(value['jcr:primaryType']))
        }
        {console.log(data.questionnaire)} */}
        {/* display questions */}
        {
          Object.entries(data.questionnaire)
          .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
          .slice(0, maxDisplayed) // should it be sliced here or just display first filled values
          .map(([key, entryDefinition]) => displayQuestion(entryDefinition, data, key))
        }
      </React.Fragment>
    );

  }
  //TODO fix: return this if nothing was returned (not working)
  else return (
    <Typography variant="body2" component="p">No form data yet</Typography> 
  );
}


Subject.propTypes = {
  id: PropTypes.string
}

export default withStyles(QuestionnaireStyle)(Subject);
