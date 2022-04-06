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
import { Grid, LinearProgress, Link, TextField, Typography, withStyles } from "@material-ui/core";

import cornerstone from "cornerstone-core";
import cornerstoneWADOImageLoader from "cornerstone-wado-image-loader";
import dicomParser from "dicom-parser";

cornerstoneWADOImageLoader.external.cornerstone = cornerstone;
cornerstoneWADOImageLoader.external.dicomParser = dicomParser;

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

import DICOM_TAG_DICT from "./dicomDataDictionary";

// Component that renders a dicom upload question.
// Filepaths are placed in a series of <input type="hidden"> tags for
// submission.
//
//
// Sample usage:
// (TODO)
function DicomQuestion(props) {
  const { classes, existingAnswer, pageActive, ...rest } = props;
  // Enforce maxAnswers 1 for the time being
  const maxAnswers = 1;

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
  let [ dicomMetadataNote, setDicomMetadataNote ] = useState();
  let [ dicomImagePreviewURL, setDicomImagePreviewURL ] = useState(existingAnswer?.[1].image);

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

  let getDicomTagName = (tag) => {
    let group = tag.substring(1,5);
    let element = tag.substring(5,9);
    let tagIndex = ("(" + group + "," + element + ")").toUpperCase();
    if (tagIndex in DICOM_TAG_DICT) {
      return DICOM_TAG_DICT[tagIndex].name;
    } else {
      return undefined;
    }
  }

  let getDicomTagDataFormat = (tag) => {
    let group = tag.substring(1,5);
    let element = tag.substring(5,9);
    let tagIndex = ("(" + group + "," + element + ")").toUpperCase();
    if (tagIndex in DICOM_TAG_DICT) {
      return DICOM_TAG_DICT[tagIndex].vr;
    } else {
      return undefined;
    }
  }

  // TODO: Support more VR types
  let getDicomTagValue = (dicomObj, tag) => {
    //let tagObj = dicomObj.elements[tag];
    //console.log("Working with this tagObj...");
    //console.log(tagObj);
    if (getDicomTagDataFormat(tag) === "US") {
      return dicomObj.uint16(tag);
    } else {
      return dicomObj.string(tag);
    }
  }

  let isASCII = (str) => {
    return /^[\x00-\x7F]*$/.test(str);
  }

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

    let savePromise = saveForm();
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
      setError("Could not save form to prepare for DICOM upload");
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
    // First, display the image
    let dicomPointer = cornerstoneWADOImageLoader.wadouri.fileManager.add(file);
    cornerstone.loadImage(dicomPointer)
      .then((dicomImage) => {
        //console.log("Loaded the DICOM image with cornerstoneWADOImageLoader...OKAY!");
        //console.log(dicomImage);
        let dicomImagePixels = dicomImage.getPixelData();
        let dicomMinPix = dicomImage.minPixelValue;
        let dicomMaxPix = dicomImage.maxPixelValue;
        let dicomCanvas = document.createElement("canvas");
        dicomCanvas.width = dicomImage.width;
        dicomCanvas.height = dicomImage.height;
        let dicomCtx = dicomCanvas.getContext('2d');
        let canvasImageData = dicomCtx.getImageData(0, 0, dicomCanvas.width, dicomCanvas.height);

        // Normalize the data so that all pixels are between 0 and 255
        for (let i = 0; i < canvasImageData.data.length; i+=4) {
          let thisPixVal = 0;
          if ((dicomMaxPix - dicomMinPix) !== 0) {
            thisPixVal = Math.floor(255 * ((dicomImagePixels[i/4] - dicomMinPix) / (dicomMaxPix - dicomMinPix)));
          }
          canvasImageData.data[i] = thisPixVal;
          canvasImageData.data[i+1] = thisPixVal;
          canvasImageData.data[i+2] = thisPixVal;
          canvasImageData.data[i+3] = 255;
        }
        dicomCtx.putImageData(canvasImageData, 0, 0);
        //console.log(dicomCanvas.toDataURL());
        setDicomImagePreviewURL(dicomCanvas.toDataURL());
        //console.log(dicomCanvas);
      })
    // ... Parse the metadata locally
    file.arrayBuffer()
      .then((arrayBuf) => {
        let dicomU8 = new Uint8Array(arrayBuf);
        let dcmObject = dicomParser.parseDicom(dicomU8);
        //console.log(dcmObject);
        // Build a DICOM metadata
        let dicomMetadata = [];
        for (let dcmtag in dcmObject.elements) {
          let tagName = getDicomTagName(dcmtag);
          if (typeof(tagName) != "string") {
            continue;
          }
          let tagValue = getDicomTagValue(dcmObject, dcmtag);
          /*
          let tagValue = dcmObject.string(dcmtag);
          if (typeof(tagValue) != "string") {
            continue;
          }
          if (!isASCII(tagValue)) {
            continue;
          }
          */
          dicomMetadata.push(tagName + ": " + tagValue);
        }
        setDicomMetadataNote(dicomMetadata.join("\n"));
      });

    // Then upload the file

    // TODO: Handle duplicate filenames
    // NB: A lot of the info here is duplicated from Answer. Is a fix possible?
    // Also NB: Since we save before this, we're guaranteed to have the parent created
    let data = new FormData();
    data.append(file['name'], file);
    data.append('jcr:primaryType', 'cards:DicomAnswer');
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
    setError("");
    // Clear the note
    setDicomMetadataNote("");
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

  let thumbnailStyle = {
    maxWidth: "400px",
    maxHeight: "400px",
  };

  let fixFileURL = (path, name) => {
    return path.slice(0, path.lastIndexOf(name)) + encodeURIComponent(name);
  }

  let hrefs = Array.of(existingAnswer?.[1]["value"]).flat();

  let defaultDisplayFormatter = function(label, idx) {
    return ( uploadedFiles && Object.values(uploadedFiles).length > 0 && <ul className={classes.answerField + " " + classes.fileResourceAnswerList}>
      {Object.keys(uploadedFiles).map((filepath, idx) =>
        <li key={idx}>
          <Link href={fixFileURL(hrefs[idx], filepath)} target="_blank" rel="noopener" download>{filepath}</Link>
          <div>
            { /* FIXME: Temporary placeholder image for testing the layout*/ }
            {/* <img style={thumbnailStyle} alt="DICOM Preview" src="https://upload.wikimedia.org/wikipedia/en/thumb/c/ce/Pacs1.jpg/200px-Pacs1.jpg" /> */}
            {dicomImagePreviewURL && <img style={thumbnailStyle} alt="DICOM Preview" src={dicomImagePreviewURL} />}
            { /*{fixFileURL(uploadedFiles[filepath], filepath).split('/').slice(0, -1).join('/') + '/image'}/> */}
          </div>
        </li>
      )}
    </ul>)
  }

  return (
    <Question
      answerLabel="dicom"
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
                <Link href={fixFileURL(uploadedFiles[filepath], filepath)} target="_blank" rel="noopener" download>{filepath}</Link>
                <DeleteButton
                  entryName={filepath}
                  entryType={"Dicom file"}
                  shouldGoBack={false}
                  onComplete={() => deletePath(idx)}
                />
                <div>
                  { /* FIXME: Temporary placeholder image for testing the layout*/ }
                  {dicomImagePreviewURL && <img style={thumbnailStyle} alt="DICOM Preview" src={dicomImagePreviewURL} />}
                  { /* {fixFileURL(uploadedFiles[filepath], filepath).split('/').slice(0, -1).join('/') + '/image'}/> */ }
                </div>
              </li>
            )}
          </ul>}
        </>
      }
      {/* Enforce enableNotes: true in the questionDefinition passed to the answer component
          to ensure the notes are rendered in all modes */}
      <Answer
        answers={answers}
        answerMetadata={{image : dicomImagePreviewURL}}
        questionDefinition={{...props.questionDefinition, enableNotes: true}}
        existingAnswer={existingAnswer}
        answerNodeType="cards:DicomAnswer"
        onDecidedOutputPath={setAnswerPath}
        valueType="path"
        isMultivalued={maxAnswers != 1}
        pageActive={pageActive}
        noteProps={{
          fullSize: true,
          placeholder: "Dicom metadata",
          value: dicomMetadataNote
        }}
        {...rest}
        />
    </Question>);
}

DicomQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
  }).isRequired,
};

const StyledDicomQuestion = withStyles(QuestionnaireStyle)(DicomQuestion)
export default StyledDicomQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "dicom") {
    return [StyledDicomQuestion, 50];
  }
});
