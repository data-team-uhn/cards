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
import { Button, Grid, LinearProgress, Typography, withStyles } from "@material-ui/core";
import BackupIcon from "@material-ui/icons/Backup";

import PropTypes from "prop-types";

import Answer from "./Answer";
import DragAndDrop from "../dragAndDrop";
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
  let [ uploadedFiles, setUploadedFiles ] = useState({});
  let [ error, setError ] = useState();
  let [ uploadInProgress, setUploadInProgress ] = useState(false);
  let [ uploadProgress, setUploadProgress ] = useState({});
  let [ answerPath, setAnswerPath ] = useState("");

  let answers = Object.values(uploadedFiles).map((filepath) => [filepath, filepath]);

  // Add files to the pending state
  let addFiles = (files) => {
    upload(files);
  }

  // Event handler for selecting files
  let upload = (files) => {
    // TODO - handle possible logged out situation here - open a login popup
    setUploadInProgress(true);
    setUploadProgress({});
    setError("");
    uploadAllFiles(files)
      .then(() => {
        setUploadInProgress(false);
      })
      .catch( (error) => {
        console.log(error);
        //handleError(error);
        setUploadInProgress(false);
    });
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
      var varNamesRegex = /@{(.+?)}/g;
      var varNames = [...namePattern.matchAll(varNamesRegex)];
      var clearedNamesRegex = namePattern.replaceAll(varNamesRegex, "(.+)");
      var nameRegex = new RegExp(clearedNamesRegex, "g");
      var results = file['name'].matchAll(nameRegex);

      // At this point, results contains each match, which all correspond to their respective entry in varNames
      // But how do I overwrite each of the entries and save them?
    }
    // TODO: Prevent duplicate filenames
    let data = new FormData();
    data.append(file['name'], file);
    data.append('jcr:primaryType', 'nt:folder');
    return fetch("/Uploads/", {
      method: "POST",
      body: data
    }).then((response) =>
      response.ok ? response.text() : Promise.reject(response)
    ).then((response) => {
      // Determine what the output filename is, and save it
      let uploadFinder = /Content created (.+)<\/title>/;
      let match = response.match(uploadFinder);
      uploadedFiles[file["name"]] = match[1];
      setUploadedFiles(uploadedFiles);
    }).catch((errorObj) => {
      // Is the user logged out? Or did something else happen?
      setError(String(errorObj));
    });

    /*return new Promise((resolve, reject) => {

      var reader = new FileReader();
      reader.readAsText(file);

      //When the file finishes loading
      console.log("C");
      reader.onload = function(event) {

        // get the file data
        var csv = event.target.result;

        let data = new FormData();
        data.append(':contentType', 'json');
        data.append(':operation', 'import');
        data.append(':content', JSON.stringify(json));

        var xhr = new XMLHttpRequest();
        // TODO: Figure out where to store this file
        xhr.open('POST', '/');

        xhr.onload = function() {
          console.log("Upload complete");
          if (xhr.status != 201) {
            uploadProgress[file.name] = { state: "error", percentage: 0 };
            console.log("Error", xhr.statusText)
          } else {
            // state: "done" change should turn all subject inputs into the link text
            uploadProgress[file.name] = { state: "done", percentage: 100 };

            file.formPath = "/" + Object.keys(json).find(str => str.startsWith("Forms/"));
            uploadedFiles[uploadedFiles.findIndex(el => el.name === file.name)] = file;
            setUploadedFiles(uploadedFiles);
          }

          setUploadProgress(uploadProgress);
          resolve(xhr.response);
        }

        xhr.onerror = function() {
          uploadProgress[file.name] = { state: "error", percentage: 0 };
          setUploadProgress(uploadProgress);
          resolve(xhr.response);
        }

        xhr.upload.onprogress = function (event) {

          if (event.lengthComputable) {
            let done = event.position || event.loaded;
            let total = event.totalSize || event.total;
            let percent = Math.round((done / total) * 100);
            const copy = { ...uploadProgress };
            copy[file.name] = { state: "pending", percentage: percent };
            setUploadProgress(copy);
          }
        }
        xhr.send(data);
      }
    });*/
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

      <input type="hidden" name="*@TypeHint" value="nt:file" />
      <label htmlFor="contained-button-file">
        <Button onClick={upload} variant="contained" color="primary" disabled={uploadInProgress || !!error && uploadedFiles.length == 0} className={classes.uploadButton}>
          <span><BackupIcon className={classes.buttonIcon}/>
            {uploadInProgress ? 'Uploading' :
                // TODO - judge upload status button message over all upload statuses of all files ??
                // uploadProgress[file.name].state =="done" ? 'Uploaded' :
                // uploadProgress[file.name].state =="error" ? 'Upload failed, try again?' :
                'Upload'}
          </span>
        </Button>
      </label>
      { uploadedFiles && uploadedFiles.length > 0 && <span>
      <Typography variant="h6" className={classes.fileInfo}>Selected files info</Typography>

      { uploadedFiles.map( (file, i) => {

          const upprogress = uploadProgress ? uploadProgress[file.name] : null;

          return (
            <div key={file.name} className={classes.fileInfo}>
              <div>
                <span>File <span className={classes.fileName}>{file.name}:</span></span>
                { upprogress && upprogress.state != "error" &&
                  <span>
                    <div className={classes.progressBar}>
                      <div className={classes.progress} style={{ width: upprogress.percentage + "%" }} />
                    </div>
                    { upprogress.percentage + "%" }
                  </span>
                }
                { upprogress && upprogress.state == "error" && <Typography color='error'>Error uploading file</Typography> }
              </div>
              { uploadProgress && uploadProgress[file.name] && uploadProgress[file.name].state === "done" ? 
                <Typography className={classes.fileDetail}>
                  { file.formPath && <span>Form: <Link href={file.formPath.replace("/Forms", "Forms")} target="_blank"> {file.formPath.replace("/Forms/", "")} </Link></span> }
                </Typography>
              : <span>
                <TextField
                  label="File:"
                  value={""}
                  onChange={(event) => setSubject(event.target.value, file.name)}
                  className={classes.fileDetail}
                  required
                />
                </span>
              }
              <Typography variant="body1" component="div" className={classes.fileInfo}>
                  {(!file.sameFiles || file.sameFiles.length == 0)
                      ?
                     <p>There are no versions of this file.</p>
                      :
                     <span>
                         <p>Other versions of this file :</p>
                         <ul>
                           {file.sameFiles && file.sameFiles.map( (samefile, index) => {
                            return (
                             <li key={index}>
                               Uploaded at {moment(samefile["jcr:created"]).format("dddd, MMMM Do YYYY")} by {samefile["jcr:createdBy"]}
                               <IconButton size="small" color="primary">
                                 <a href={samefile["@path"]} download><GetApp /></a>
                               </IconButton>
                             </li>
                           )})}
                         </ul>
                     </span>
                   }
              </Typography>
            </div>
        ) } ) }
                  </span>}
      <Answer
        answers={answers}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="lfs:FileResourceAnswer"
        valueType="path"
        onDecidedOutputPath={setAnswerPath}
        {...rest}
        />
    </Question>);
}

FileResourceQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
  }).isRequired,
  text: PropTypes.string,
  enableUnknown: PropTypes.bool,
  yesLabel: PropTypes.string,
  noLabel: PropTypes.string,
  unknownLabel: PropTypes.string
};

const StyledFileResourceQuestion = withStyles(QuestionnaireStyle)(FileResourceQuestion)
export default StyledFileResourceQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "file") {
    return [StyledFileResourceQuestion, 50];
  }
});
