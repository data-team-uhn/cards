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

import React, { useContext, useState } from "react";
import { Grid, IconButton, LinearProgress, Link, TextField, Typography, withStyles, List, ListItem } from "@material-ui/core";
import Delete from "@material-ui/icons/Delete";

import PropTypes from "prop-types";

import Answer from "./Answer";
import DragAndDrop from "../dragAndDrop";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
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
  const { classes, existingAnswer, isEdit, ...rest } = props;
  const { maxAnswers, minAnswers, namePattern } = { ...props.questionDefinition, ...props }
  let initialValues =
    // Check whether or not we have an initial value
    (!existingAnswer || existingAnswer[1].value === undefined) ? [] :
    // The value can either be a single value or an array of values; force it into a dictionary
    Array.of(existingAnswer[1].value).flat()
    // Finally, split it into a series of [value, label]s
    .map((element) => [/.+\/(.+?)$/.exec(element)?.[1], element]);
  let initialDict = {};
  initialValues.forEach((value) => {
    initialDict[value[0]] = value[1];
  });
  let [ uploadedFiles, setUploadedFiles ] = useState(initialDict);
  let [ error, setError ] = useState();
  let [ uploadInProgress, setUploadInProgress ] = useState(false);
  let [ answerPath, setAnswerPath ] = useState(existingAnswer);

  // If the form is in the view mode
  if (existingAnswer?.[1]["displayedValue"] && !isEdit) {
    let prettyPrintedAnswers = existingAnswer[1]["displayedValue"];
    // The value can either be a single value or an array of values; force it into an array
    prettyPrintedAnswers = Array.of(prettyPrintedAnswers).flat();
    let hrefs = Array.of(existingAnswer[1]["value"]).flat();

    return (
      <Question
        {...rest}
        >
        <List>
          { prettyPrintedAnswers.map((answerValue, idx) => {
            return(
              <ListItem key={idx}>
                <a key={answerValue} href={hrefs[idx]} target="_blank" rel="noopener" download>{answerValue}</a>
              </ListItem>
            )
          })}
        </List>
      </Question>
    );
  }

  // Default value of knownAnswers is the name of every field we can find in namePattern
  let initialParsedAnswers = {};
  if (namePattern) {
    var varNamesRegex = /@{(.+?)}/g;
    var varNames = [...namePattern.matchAll(varNamesRegex)].map((match) => match[1]);

    var clearedNamesRegex = namePattern.replaceAll(varNamesRegex, "(.+)");
    var nameRegex = new RegExp(clearedNamesRegex);

    // Match each of the values into our default initialParsedAnswers
    initialValues.forEach((filename) => {
      let results = filename[0].match(nameRegex)?.slice(1);

      // Determine which fields we have parsed out
      initialParsedAnswers[filename[0]] = results;
    })
  }

  let [ knownAnswers, setKnownAnswers ] = useState(initialParsedAnswers);

  // The answers to give to our <Answers /> object
  let [ answers, setAnswers ] = useState(initialValues);
  let writer = useFormUpdateWriterContext();
  let reader = useFormReaderContext();
  let saveForm = reader['/Save'];
  // Since adding new entries doesn't trigger the form's onChange, we need to force
  // the form to allow saving after we finish updating
  let allowResave = reader['/AllowResave'];
  let outURL = reader["/URL"] + "/" + answerPath;
  const globalLoginDisplay = useContext(GlobalLoginContext);

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
      var results = file['name'].match(nameRegex)?.slice(1);

      // At this point, results contains each match, which all correspond to their respective entry in varNames
      writer((oldCommands) => {
        let newCommands = {...oldCommands};
        for (let i = 0; i < varNames.length; i++) {
          if (results && results[i]) {
            if (varNames[i] in newCommands) {
              newCommands[varNames[i]].push(results[i]);
            } else {
              newCommands[varNames[i]] = [results[i]];
            }
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
    return fetchWithReLogin(globalLoginDisplay, outURL, {
      method: "POST",
      body: data
    }).then((response) =>
      response.ok ? response.text() : Promise.reject(response)
    ).then((response) => {
      // Determine what the output filename is, and save it
      let uploadFinder = /Content (?:created|modified) (.+)<\/title>/;
      let match = response.match(uploadFinder);
      let fileURL = match[1] + "/" + file["name"];
      if (maxAnswers != 1) {
        // Guard against duplicates
        if (!(file["name"] in uploadedFiles)) {
          setUploadedFiles((old) => {
            return {...old, [file["name"]]: fileURL};
          });
          setAnswers((old) => {
            let newAnswers = old.slice();
            newAnswers.push([file["name"], fileURL]);
            return newAnswers;
          });
        }
      } else {
        // Change the new values
        setUploadedFiles({[file["name"]]: fileURL});
        setAnswers([[file["name"], fileURL]]);
      }
      allowResave();
    }).catch((errorObj) => {
      // Is the user logged out? Or did something else happen?
      console.log(errorObj);
      setError(String(errorObj));
    });
  };

  // Delete an answer by its index
  let deletePath = (index) => {
    // Rather than waiting to delete, we'll just delete it immediately
    let data = new FormData();
    data.append(':operation', 'delete');
    fetchWithReLogin(globalLoginDisplay, uploadedFiles[answers[index][0]], {
      method: "POST",
      body: data
      });
    setUploadedFiles((old) => {
      let newUploadedFiles = {...old};
      delete newUploadedFiles[answers[index][0]];
      return newUploadedFiles;
    })
    setAnswers((old) => {
      let newAnswers = old.slice();
      newAnswers.splice(index, 1);
      return newAnswers;
    });
    allowResave();
  }

  // display error if minimum is not met, display 'at least' if there is no maximum or if max is greater than min
  const warning = (
    (Object.keys(uploadedFiles).length < minAnswers && minAnswers > 0) ? (
    <Typography color={error ? 'error' : 'textSecondary'} className={classes.warningTypography}>
      Please upload {maxAnswers !== minAnswers && "at least"} {minAnswers} file{minAnswers > 1 && "s"}.
    </Typography>
    ) : (Object.keys(uploadedFiles).length > maxAnswers && maxAnswers > 0 &&
    <Typography color="error" className={classes.warningTypography}>
      Please upload at most {maxAnswers} file{maxAnswers > 1 && "s"}.
    </Typography>));

  return (
    <Question
      {...rest}
      >
      { uploadInProgress && (
        <Grid item className={classes.root}>
          <LinearProgress color="primary" />
        </Grid>
      ) }
      {warning}
      <DragAndDrop
        classes={classes}
        handleDrop={addFiles}
        multifile={maxAnswers != 1}
        />

      { error && <Typography color="error">error</Typography>}

      { uploadedFiles && Object.values(uploadedFiles).length > 0 && <ul className={classes.answerField + " " + classes.fileResourceAnswerList}>
        {Object.keys(uploadedFiles).map((filepath, idx) =>
          <li key={idx}>
            <div>
              <span>File </span>
              <Link href={uploadedFiles[filepath]} target="_blank" rel="noopener" download>
                {filepath}
              </Link>:
              <IconButton
                onClick={() => {deletePath(idx)}}
                className={classes.deleteButton + " " + classes.fileResourceDeleteButton}
                color="secondary"
                title="Delete"
              >
                <Delete color="action" className={classes.deleteIcon}/>
              </IconButton>
            </div>
            { namePattern &&
              <span>
                {varNames.map((name, nameIdx) => (
                  <TextField
                    label={name}
                    value={knownAnswers?.[filepath]?.[nameIdx]}
                    className={classes.fileDetail + " " + classes.fileResourceAnswerInput}
                    key={nameIdx}
                    readOnly
                  />
                ))}
              </span>
            }
          </li>
        )}
      </ul>}
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
  namePattern: PropTypes.string,
  isEdit: PropTypes.bool,
};

const StyledFileResourceQuestion = withStyles(QuestionnaireStyle)(FileResourceQuestion)
export default StyledFileResourceQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "file") {
    return [StyledFileResourceQuestion, 50];
  }
});
