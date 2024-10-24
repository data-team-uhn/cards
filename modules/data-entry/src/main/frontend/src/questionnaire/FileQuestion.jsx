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

import { Grid, LinearProgress, Link, TextField } from "@mui/material";
import withStyles from '@mui/styles/withStyles';

import PropTypes from "prop-types";

import Answer from "./Answer";
import DragAndDrop from "../components/dragAndDrop";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import { useFormReaderContext } from "./FormContext";
import { useFormUpdateWriterContext } from "./FormUpdateContext";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import DeleteButton from "../dataHomepage/DeleteButton";

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
function FileQuestion(props) {
  const { classes, existingAnswer, pageActive, ...rest } = props;
  const { maxAnswers, namePattern } = { ...props.questionDefinition, ...props }
  const { onBeforeUpload, onAfterUpload, onDelete, previewRenderer, answerNodeType } = props;
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
  let disableUploads = !!reader['/DisableUploads'];
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
    // Don't do anything if the context provider says uploads are disabled
    if (disableUploads) {
      return;
    }
    // TODO - handle possible logged out situation here - open a login popup
    setUploadInProgress(true);
    setError("");

    let savePromise = saveForm(new Event("autosave"));
    if (savePromise) {
      // When this function returns, the "files selected" event is cleared, along with the files list. Make a copy to preserve the data.
      let filesCopy = [];
      for (let i = 0 ; i < files.length; ++i) {
        filesCopy.push(files.item(i));
      }
      savePromise.then(() => uploadAllFiles(filesCopy))
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
    let uploadedFileNames = Object.keys(uploadedFiles || {});
    let indicesToUpload = [];
    let fileNamesDiscarded = [];

    if (maxAnswers > 0) {
      // Prioritize re-uploading what has already been uploaded
      for (let i = 0; i < selectedFiles.length && indicesToUpload.length < maxAnswers; ++i) {
        if (uploadedFileNames.includes(selectedFiles[i]['name'])) {
          indicesToUpload.push(i);
        }
      }
      let reUploadCount = indicesToUpload.length;
      let maxNewUploads = maxAnswers - uploadedFileNames.length;
      // Remove existing selection if only one file is permitted
      if (maxAnswers == 1) {
        uploadedFileNames.forEach((filename, idx) => idx != indicesToUpload[0] && deletePath(idx));
        maxNewUploads = maxAnswers;
      }

      // See if there's any room for uploading any new files
      for (let i = 0; i < selectedFiles.length; ++i) {
        if (!indicesToUpload.includes(i)) {
          if ((indicesToUpload.length - reUploadCount) < maxNewUploads) {
            indicesToUpload.push(i);
          } else {
            fileNamesDiscarded.push(selectedFiles[i]['name']);
          }
        }
      }

      // If there is a limit to how many files can be uploaded and a larger number of files was selected,
      // inform the user which files did not get uploaded
      if (indicesToUpload.length < selectedFiles.length) {
        let errorText = "At most " + maxAnswers + " file" + (maxAnswers > 1 ? "s" : "") + " can be uploaded to this question. ";
        errorText += "Not uploaded from your selection: " + fileNamesDiscarded.join(", ");
        setError(errorText);
      }
    }

    const promises = [];
    for (let i = 0; i < selectedFiles.length; i++) {
      if (maxAnswers == 0 || indicesToUpload.includes(i)) {
        promises.push(uploadSingleFile(selectedFiles[i]));
      }
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

    onBeforeUpload && onBeforeUpload(file);
    // TODO: Handle duplicate filenames
    // NB: A lot of the info here is duplicated from Answer. Is a fix possible?
    // Also NB: Since we save before this, we're guaranteed to have the parent created
    let data = new FormData();
    data.append(file['name'], file);
    data.append('jcr:primaryType', 'cards:FileAnswer');
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
      onAfterUpload && onAfterUpload(file);
    }).catch((errorObj) => {
      // Backend did not allow this file to be uploaded
      console.log(errorObj);
      setError("You are not allowed to upload this file");
    });
  };

  // Delete an answer by its index
  let deletePath = (index) => {
    onDelete && onDelete(index);
    setError("");
    // Rather than waiting to delete, we'll just delete it immediately
    let data = new FormData();
    data.append(':operation', 'delete');
    fetchWithReLogin(globalLoginDisplay, fixFileURL(uploadedFiles[answers[index][0]], answers[index][0]), {
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

  let fixFileURL = (path, name) => {
    return path.slice(0, path.lastIndexOf(name)) + encodeURIComponent(name);
  }

  let hrefs = Array.of(existingAnswer?.[1]["value"]).flat();
  let defaultDisplayFormatter = function(label, idx) {
    return (
      <div>
        <Link href={fixFileURL(hrefs[idx], label)} target="_blank" rel="noopener" download underline="hover">{label}</Link>
        { previewRenderer && previewRenderer(fixFileURL(hrefs[idx], label), label, idx) }
      </div>
    );
  }

  return (
    <Question
      answerLabel="file"
      currentAnswers={Object.keys(uploadedFiles || {}).length}
      defaultDisplayFormatter={defaultDisplayFormatter}
      {...props}
      >
      {
        pageActive && <>
          { uploadInProgress && (
            <Grid item className={classes.root}>
              <LinearProgress color="primary" />
            </Grid>
          ) }
          <DragAndDrop
            handleDrop={addFiles}
            multifile={maxAnswers != 1}
            error={error}
            disabled={disableUploads}
            />
          { uploadedFiles && Object.values(uploadedFiles).length > 0 && <ul className={classes.answerField + " " + classes.fileResourceAnswerList}>
            {Object.keys(uploadedFiles).map((filepath, idx) =>
              <li key={idx}>
                <Link href={fixFileURL(uploadedFiles[filepath], filepath)} target="_blank" rel="noopener" download underline="hover">{filepath}</Link>
                <DeleteButton
                  size="small"
                  entryName={filepath}
                  entryType={"file"}
                  onComplete={() => deletePath(idx)}
                />
                { previewRenderer && previewRenderer(fixFileURL(uploadedFiles[filepath], filepath), filepath, idx) }
                { namePattern &&
                  <span>
                    {varNames.map((name, nameIdx) => (
                      <TextField
                        variant="standard"
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
        </>
      }
      <Answer
        answers={answers}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType={ answerNodeType || "cards:FileAnswer" }
        onDecidedOutputPath={setAnswerPath}
        valueType="path"
        isMultivalued={maxAnswers != 1}
        pageActive={pageActive}
        {...rest}
        />
    </Question>);
}

FileQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
  }).isRequired,
  namePattern: PropTypes.string
};

const StyledFileQuestion = withStyles(QuestionnaireStyle)(FileQuestion)
export default StyledFileQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "file") {
    return [StyledFileQuestion, 50];
  }
});
