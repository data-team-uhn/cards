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
import { Grid, LinearProgress, Link, TextField, Typography, withStyles } from "@material-ui/core";

import PropTypes from "prop-types";

import Answer from "./Answer";
import DragAndDrop from "../dragAndDrop";
import { useFormReaderContext } from "./FormContext";
import { useFormUpdateWriterContext } from "./FormUpdateContext";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

// Component that renders a file upload question.
// Filepaths are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional arguments:
//  namePattern (optional): a string specifying if the filename can be processed to extract the subject ID from it
//
// Sample usage:
// (TODO)
function FileResourceQuestion(props) {
  const { classes, existingAnswer, ...rest } = props;
  const { maxAnswers, namePattern } = { ...props.questionDefinition, ...props }
  let initialValues =
    // Check whether or not we have an initial value
    (!existingAnswer || existingAnswer[1].value === undefined) ? [] :
    // The value can either be a single value or an array of values; force it into a dictionary
    Array.of(existingAnswer[1].value).flat();
  let initialDict = {};
  initialValues.forEach((value) => {initialDict[value] = value;});
  let [ uploadedFiles, setUploadedFiles ] = useState(initialDict);
  let [ error, setError ] = useState();
  let [ uploadInProgress, setUploadInProgress ] = useState(false);
  let [ answerPath, setAnswerPath ] = useState(existingAnswer);

  // Default value of knownAnswers is the name of every field we can find in namePattern
  let initialParsedAnswers = {};
  if (namePattern) {
    var varNamesRegex = /@{(.+?)}/g;
    var varNames = [...namePattern.matchAll(varNamesRegex)].map((match) => match[1]);

    var clearedNamesRegex = namePattern.replaceAll(varNamesRegex, "(.+)");
    var nameRegex = new RegExp(clearedNamesRegex, "g");

    // Match each of the values into our default initialParsedAnswers
    initialValues.forEach((filename) => {
      let results = [...filename.matchAll(nameRegex)].map((match) => match[1]);

      // Determine which fields we have parsed out
      initialParsedAnswers[filename] = results;
    })
  }

  let [ knownAnswers, setKnownAnswers ] = useState(initialParsedAnswers);

  // The answers to give to our <Answers /> object
  let [ answers, setAnswers ] = useState([]);
  //let answers = Object.keys(uploadedFiles).map((filepath) => [uploadedFiles[filepath], filepath]);
  let writer = useFormUpdateWriterContext();
  let reader = useFormReaderContext();
  let saveForm = reader['/Save'];
  let outURL = reader["/URL"] + "/" + answerPath;

  // Add files to the pending state
  let addFiles = (files) => {
    upload(files);
  }

  // Event handler for selecting files
  let upload = (files) => {
    // TODO - handle possible logged out situation here - open a login popup
    setUploadInProgress(true);
    setError("");

    let savePromise = saveForm();
    if (savePromise) {
      savePromise.then(uploadAllFiles(files))
        .catch( (err) => {
          console.log(err);
          setError(err.ToString());
          setUploadInProgress(false);
        })
        .finally(() => {
          setUploadInProgress(false);
        });
    } else {
      setError("Could not save form to prepare for file upload");
    }
  };

  // Find the icon and load them
  let uploadAllFiles = (selectedFiles) => {
    const promises = [];
    for (let i = 0; i < selectedFiles.length; i++) {
      promises.push(uploadSingleFile(selectedFiles[i]));
    }

    return Promise.all(promises);
  };

  let uploadSingleFile = (file) => {
    // Determine whether or not the filename matches the namePattern (if given)
    if (namePattern) {
      // Regex out variable names from the namePattern
      var results = [...file['name'].matchAll(nameRegex)].map((match) => match[1]);

      // At this point, results contains each match, which all correspond to their respective entry in varNames
      writer((oldCommands) => {
        let newCommands = {...oldCommands};
        for (let i = 0; i < varNames.length; i++) {
          if (varNames[i] in newCommands) {
            newCommands[varNames[i]].push(results[i]);
          } else {
            newCommands[varNames[i]] = [results[i]];
          }
        }
        return newCommands;
      });

      // Determine which fields we have parsed out
      setKnownAnswers((old) => {
        let newAnswers = {...old};
        newAnswers[file['name']] = results;
        return newAnswers;
      })
    }

    // TODO: Handle duplicate filenames
    // NB: A lot of the info here is duplicated from Answer. Is a fix possible?
    // Also NB: Since we save before this, we're guaranteed to have the parent created
    let data = new FormData();
    data.append(file['name'], file);
    data.append('jcr:primaryType', 'lfs:FileResourceAnswer');
    data.append('question', props.questionDefinition['jcr:uuid']);
    data.append('question@TypeHint', "Reference");
    return fetch(reader["/URL"] + "/" + answerPath, {
      method: "POST",
      body: data
    }).then((response) =>
      response.ok ? response.text() : Promise.reject(response)
    ).then((response) => {
      // Determine what the output filename is, and save it
      let uploadFinder = /Content (?:created|modified) (.+)<\/title>/;
      let match = response.match(uploadFinder);
      let fileURL = match[1] + "/" + file["name"];
      uploadedFiles[file["name"]] = fileURL;
      setUploadedFiles(uploadedFiles);
      setAnswers((old) => {
        let newAnswers = old.slice();
        newAnswers.push([file["name"], fileURL]);
        return newAnswers;
      })
    }).catch((errorObj) => {
      // Is the user logged out? Or did something else happen?
      console.log(errorObj);
      setError(String(errorObj));
    });
  };

  return (
    <Question
      {...rest}
      >
      { uploadInProgress && (
        <Grid item className={classes.root}>
          <LinearProgress color="primary" />
        </Grid>
      ) }
      <DragAndDrop
        accept={"*.csv"}
        classes={classes}
        handleDrop={addFiles}
        multifile={maxAnswers != 1}
        />

      { error && <Typography variant="error">error</Typography>}

      { uploadedFiles && Object.values(uploadedFiles).length > 0 && <span>
        <Typography variant="h6" className={classes.fileInfo}>Selected files info</Typography>
        {Object.keys(uploadedFiles).map((filepath) =>
          <React.Fragment key={filepath}>
            <div>
              <span>File </span>
              <Link href={uploadedFiles[filepath]} target="_blank" rel="noopener">
                {filepath}
              </Link>:
            </div>
            <span>
              {
                namePattern && varNames.map((name, idx) => (
                  <TextField
                    label={name}
                    value={knownAnswers?.[filepath]?.[idx]}
                    className={classes.fileDetail}
                    key={name}
                    readOnly
                  />
                ))
              }
            </span>
            <br />
          </React.Fragment>
        )}
      </span>}
      <Answer
        answers={answers}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="lfs:FileResourceAnswer"
        onDecidedOutputPath={setAnswerPath}
        valueType="path"
        {...rest}
        />
    </Question>);
}

FileResourceQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
  }).isRequired,
  namePattern: PropTypes.string
};

const StyledFileResourceQuestion = withStyles(QuestionnaireStyle)(FileResourceQuestion)
export default StyledFileResourceQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "file") {
    return [StyledFileResourceQuestion, 50];
  }
});
