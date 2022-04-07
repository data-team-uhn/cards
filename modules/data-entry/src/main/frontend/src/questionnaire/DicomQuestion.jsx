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
import { withStyles } from "@material-ui/core";

import cornerstone from "cornerstone-core";
import cornerstoneWADOImageLoader from "cornerstone-wado-image-loader";
import dicomParser from "dicom-parser";

cornerstoneWADOImageLoader.external.cornerstone = cornerstone;
cornerstoneWADOImageLoader.external.dicomParser = dicomParser;

import PropTypes from "prop-types";

import FileQuestion from "./FileQuestion";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

import DICOM_TAG_DICT from "./dicomDataDictionary";

// Component that renders a dicom upload question.
// Filepaths are placed in a series of <input type="hidden"> tags for
// submission.
//
function DicomQuestion(props) {
  const { existingAnswer, questionDefinition, ...rest } = props;

  let [ dicomMetadataNote, setDicomMetadataNote ] = useState();
  // TODO: We will no longer store the image in the `image` property of the answer node
  // Instead, load it from the file directly
  // `existingAnswer?.[1].value` holds the URL of the file (which might need to be escaped
  // (see the fixFileURL method in FileQuestion)
  // If there's a file, fetch it from the backend and generate dicomImagePreviewURL from it
  let [ dicomImagePreviewURL, setDicomImagePreviewURL ] = useState(existingAnswer?.[1].image);

  let fetchDicomFile = () => {
    let dicomFilePath = existingAnswer?.[1].value;
    console.log("Fetching the DICOM file from: " + dicomFilePath);
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
  useEffect(() => {
    fetchDicomFile();
  }, []);

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

  let dicomImageToDataURL = (dicomImage) => {
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
    return dicomCanvas.toDataURL();
  }

  let processDicomFile = (file) => {
    // Extract and display the image
    let dicomPointer = cornerstoneWADOImageLoader.wadouri.fileManager.add(file);
    cornerstone.loadImage(dicomPointer)
      .then((dicomImage) => {
        setDicomImagePreviewURL(dicomImageToDataURL(dicomImage));
      })
    // Parse the metadata locally and populate the Notes
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
