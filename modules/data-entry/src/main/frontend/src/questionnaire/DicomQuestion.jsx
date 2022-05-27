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
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  FormGroup,
  Switch,
  Typography,
  withStyles
} from "@material-ui/core";

import cornerstone from "cornerstone-core";

// Non dynamic loading version
import cornerstoneWADOImageLoader from "cornerstone-wado-image-loader";

// Dynamic loading version
//import cornerstoneWADOImageLoader from "cornerstone-wado-image-loader/dist/dynamic-import/cornerstoneWADOImageLoader.min.js";

import dicomParser from "dicom-parser";

cornerstoneWADOImageLoader.external.cornerstone = cornerstone;
cornerstoneWADOImageLoader.external.dicomParser = dicomParser;

import PropTypes from "prop-types";

import FileQuestion from "./FileQuestion";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

import DICOM_TAG_DICT from "./dicomDataDictionary";
import DICOM_REDACTED_TAGS from "./dicomRedactedTags";

// Component that renders a dicom upload question.
// Filepaths are placed in a series of <input type="hidden"> tags for
// submission.
//
function DicomQuestion(props) {
  const { existingAnswer, questionDefinition, ...rest } = props;

  let [ dicomMetadataNote, setDicomMetadataNote ] = useState();
  let [ dicomImagePreviewURL, setDicomImagePreviewURL ] = useState();
  let [ errorDialogText, setErrorDialogText ] = useState();
  let [ advancedErrorDialogText, setAdvancedErrorDialogText ] = useState();
  let [ showAdvancedErrorHelp, setShowAdvancedErrorHelp ] = useState(false);

  let fetchDicomFile = () => {
    let dicomFilePath = existingAnswer?.[1]
        .value?.split("/")
        .map(s => encodeURIComponent(s))
        .join("/");
    if (dicomFilePath) {
      // Don't cache DICOM images as they may change on the back-end
      cornerstoneWADOImageLoader.wadouri.dataSetCacheManager.purge();
      cornerstone.loadImage("wadouri:" + dicomFilePath)
        .then((dicomImage) => {
          setDicomImagePreviewURL(dicomImageToDataURL(dicomImage));
        })
    }
  }

  // Load the DICOM image preview, only once, upon initialization
  useEffect(() => fetchDicomFile(), []);

  let isRedactedTag = (tag) => {
    let group = tag.substring(1,5);
    let element = tag.substring(5,9);
    let tagIndex = ("(" + group + "," + element + ")").toUpperCase();
    return Boolean(DICOM_REDACTED_TAGS.indexOf(tagIndex) >= 0);
  }

  let getDicomTagInfo = (tag) => {
    let group = tag.substring(1,5);
    let element = tag.substring(5,9);
    let tagIndex = ("(" + group + "," + element + ")").toUpperCase();
    if (tagIndex in DICOM_TAG_DICT) {
      return DICOM_TAG_DICT[tagIndex];
    } else {
      return undefined;
    }
  }

  let getDicomTagName = (tag) => {
    return getDicomTagInfo(tag)?.name;
  }

  let getDicomTagDataFormat = (tag) => {
    return getDicomTagInfo(tag)?.vr;
  }

  let getDicomRawHexData = (dicomObj, tag) => {
    let dataLength = dicomObj.elements[tag].length;
    let dataOffset = dicomObj.elements[tag].dataOffset;
    let truncateData = Boolean(dicomObj.elements[tag].length > 8);
    if (truncateData) {
      dataLength = 8;
    }
    let hex = "0x" + Array.from(dicomObj.byteArray.slice(dataOffset, dataOffset + dataLength))
      .map(x => x.toString(16).padStart(2, '0'))
      .join('');
    if (truncateData) {
      hex += "...";
    }
    return hex;
  }

  let getDicomTagValue = (dicomObj, tag) => {
    if (getDicomTagDataFormat(tag) === "US") {
      return dicomObj.uint16(tag);
    } else if (getDicomTagDataFormat(tag) === "UL") {
      return dicomObj.uint32(tag);
    } else if (["CS", "UI", "DA", "TM", "LO", "PN", "SH", "DS", "IS", "AE"].indexOf(getDicomTagDataFormat(tag)) >= 0) {
      return dicomObj.string(tag);
    } else {
      return getDicomRawHexData(dicomObj, tag);
    }
  }

  let dicomImageToDataURL = (dicomImage) => {
    let dicomImagePixels = dicomImage.getPixelData();
    let dicomMinPix = dicomImage.minPixelValue;
    let dicomMaxPix = dicomImage.maxPixelValue;
    let dicomCanvas = document.createElement("canvas");
    dicomCanvas.width = dicomImage.width;
    dicomCanvas.height = dicomImage.height;
    let dicomCtx = dicomCanvas.getContext('2d');
    let canvasImageData = dicomCtx.getImageData(0, 0, dicomCanvas.width, dicomCanvas.height);

    if (dicomImage.color) {
      // If the DICOM image is colored, simply copy the RGBA pixel values
      for (let i = 0; i < canvasImageData.data.length; i++) {
        canvasImageData.data[i] = dicomImagePixels[i];
      }
    } else {
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
    }
    dicomCtx.putImageData(canvasImageData, 0, 0);
    return dicomCanvas.toDataURL();
  }

  let processDicomFile = (file) => {
    // Extract and display the image
    let dicomPointer = cornerstoneWADOImageLoader.wadouri.fileManager.add(file);
    cornerstone.loadImage(dicomPointer)
      .then((dicomImage) => {
        setDicomImagePreviewURL(dicomImageToDataURL(dicomImage));
      })
      .catch(() => {
        setErrorDialogText("Something went wrong when parsing the DICOM file.");
        setAdvancedErrorDialogText("You may be able to fix the DICOM file with: gdcmconv -C file.dcm fixed_file.dcm");
      })
    // Parse the metadata locally and populate the Notes
    file.arrayBuffer()
      .then((arrayBuf) => {
        let dicomU8 = new Uint8Array(arrayBuf);
        let dcmObject = dicomParser.parseDicom(dicomU8);
        // Build a list of DICOM metadata keys --> values
        let dicomMetadata = [];
        for (let dcmtag in dcmObject.elements) {
          let tagName = getDicomTagName(dcmtag);
          if (typeof(tagName) != "string") {
            continue;
          }
          let tagValue = isRedactedTag(dcmtag) ? "***REDACTED***" : getDicomTagValue(dcmObject, dcmtag);
          dicomMetadata.push(tagName + ": " + tagValue);
        }
        setDicomMetadataNote(dicomMetadata.join("\n"));
      });
  }

  let thumbnailStyle = {
    maxWidth: "400px",
    maxHeight: "400px",
  };

  let previewRenderer = () => (
    dicomImagePreviewURL ? <div><img style={thumbnailStyle} alt="DICOM Preview" src={dicomImagePreviewURL} /></div> : null
  )

  // Render a customized FileQuestion
  return (
    <React.Fragment>
      <Dialog
        open={Boolean(errorDialogText)}
        onClose={() => {
          setErrorDialogText();
          setAdvancedErrorDialogText();
          setShowAdvancedErrorHelp();
        }}
      >
        <DialogTitle disableTypography>
          <Typography variant="h6" color="error">
            Error
          </Typography>
        </DialogTitle>
        <DialogContent dividers>
          <Typography variant="h6">
            {errorDialogText}
          </Typography>
          { showAdvancedErrorHelp &&
            <Typography variant="body1">
              {advancedErrorDialogText}
            </Typography>
          }
        </DialogContent>
        <DialogActions>
          <FormGroup>
            <FormControlLabel
              control={
                <Switch
                  onChange={(evt) => setShowAdvancedErrorHelp(evt.target.checked)}
                 />
              }
              label="Advanced Help"
            />
          </FormGroup>
        </DialogActions>
      </Dialog>
      <FileQuestion
        questionDefinition={{...questionDefinition, maxAnswers: 1, enableNotes: true}}
        existingAnswer={existingAnswer}
        {...rest}
        onBeforeUpload={processDicomFile}
        onDelete={() => {
          setDicomMetadataNote("");
          setDicomImagePreviewURL(undefined);
        }}
        previewRenderer={previewRenderer}
        answerNodeType="cards:DicomAnswer"
        noteProps={{
          fullSize: true,
          placeholder: "Dicom metadata",
          value: dicomMetadataNote
        }}
      />
    </React.Fragment>
  );
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
