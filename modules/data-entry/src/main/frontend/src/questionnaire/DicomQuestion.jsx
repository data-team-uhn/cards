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
  Accordion,
  AccordionDetails,
  AccordionSummary,
  DialogContent,
  Typography,
} from "@mui/material";
import { makeStyles, withStyles } from '@mui/styles';

import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

import cornerstone from "cornerstone-core";

// Non dynamic loading version
import cornerstoneWADOImageLoader from "cornerstone-wado-image-loader";

import dicomParser from "dicom-parser";

cornerstoneWADOImageLoader.external.cornerstone = cornerstone;
cornerstoneWADOImageLoader.external.dicomParser = dicomParser;

import PropTypes from "prop-types";

import FileQuestion from "./FileQuestion";
import QuestionnaireStyle from "./QuestionnaireStyle";
import ResponsiveDialog from "../components/ResponsiveDialog";
import FormattedText from "../components/FormattedText";

import AnswerComponentManager from "./AnswerComponentManager";

import DICOM_TAG_DICT from "../dicom/dicomDataDictionary";

const useStyles = makeStyles(theme => ({
  advancedHelp : {
    background: theme.palette.action.hover,
    "&.Mui-expanded" : {
      margin: "0 !important",
    },
    "& .MuiAccordionSummary-root" : {
      flexDirection: "row-reverse",
    },
    "& .MuiAccordionDetails-root" : {
      background: "transparent",
      padding: theme.spacing(0, 3, 2),
    },
  }
}));

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

  let validateDicomFileTransferSyntax = (file) => {
    let FORBIDDEN_TRANSFER_SYNTAX_UID = ["1.2.840.10008.1.2.4.70"];
    return file.arrayBuffer()
    .then((arrayBuf) => {
      let dicomU8 = new Uint8Array(arrayBuf);
      let dcmObject = dicomParser.parseDicom(dicomU8);
      for (let dcmtag in dcmObject.elements) {
        let tagName = getDicomTagName(dcmtag);
        if (typeof(tagName) != "string") {
          continue;
        }
        let tagValue = getDicomTagValue(dcmObject, dcmtag);
        if (tagName === "TransferSyntaxUID") {
          if (FORBIDDEN_TRANSFER_SYNTAX_UID.indexOf(tagValue) >= 0) {
            return Promise.reject("Invalid TransferSyntaxUID");
          }
        }
      }
      return Promise.resolve(file);
    })
    .catch((err) => {
      return Promise.reject(err);
    });
  }

  let previewDicomFile = (file) => {
    return validateDicomFileTransferSyntax(file)
    .then((file) => {
      cornerstoneWADOImageLoader.wadouri.dataSetCacheManager.purge();
      cornerstoneWADOImageLoader.wadouri.fileManager.purge();
      let dicomPointer = cornerstoneWADOImageLoader.wadouri.fileManager.add(file);
      return Promise.resolve(dicomPointer);
    })
    .then((dicomPointer) => cornerstone.loadImage(dicomPointer))
    .then((dicomImage) => {
      setDicomImagePreviewURL(dicomImageToDataURL(dicomImage));
      return Promise.resolve();
    })
    .catch((err) => {
      setDicomImagePreviewURL("error:" + err)
      return Promise.reject(err);
    });
  }

  let fetchDicomFile = () => {
    let dicomFilePath = existingAnswer?.[1]
        .value?.split("/")
        .map(s => encodeURIComponent(s))
        .join("/");
    if (dicomFilePath) {
      fetch(dicomFilePath)
      .then((resp) => resp.blob())
      .then((blob) => Promise.resolve(new File([blob], "file.dcm")))
      .then((file) => previewDicomFile(file))
      .catch((err) => console.error(err));
    }
  }

  // Load the DICOM image preview, only once, upon initialization
  useEffect(() => fetchDicomFile(), []);

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

  let populateNotesFromDicomFile = (file) => {
    return file.arrayBuffer()
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
        let tagValue = getDicomTagValue(dcmObject, dcmtag);
        dicomMetadata.push(tagName + ": " + tagValue);
      }
      setDicomMetadataNote(dicomMetadata.join("\n"));
      return Promise.resolve();
    })
  }

  let processDicomFile = (file) => {
    // Parse the metadata locally, populate the Notes, then display an image preview
    populateNotesFromDicomFile(file)
    .then(() => previewDicomFile(file))
    .catch((err) => {
      if (err != "Invalid TransferSyntaxUID") {
        setErrorDialogText("Something went wrong when parsing the DICOM file");
        setAdvancedErrorDialogText("You may be able to fix the DICOM file with: `gdcmconv -C file.dcm fixed_file.dcm`");
      }
    });
  }

  let thumbnailStyle = {
    maxWidth: "400px",
    maxHeight: "400px",
  };

  let previewRenderer = () => {
    if (dicomImagePreviewURL === undefined) {
      return null;
    } else if (dicomImagePreviewURL.startsWith("data:")) {
      return (
        <div>
          <img
            style={thumbnailStyle}
            alt="DICOM Preview"
            src={dicomImagePreviewURL}
          />
        </div>
      );
    } else if (dicomImagePreviewURL.startsWith("error:")) {
      return (
        <div>
          <p>
            {"Failed to render preview due to " + dicomImagePreviewURL.split(":").slice(1).join(":") + "."}
          </p>
        </div>
      );
    }
  }

  const styles = useStyles();

  // Render a customized FileQuestion
  return (
    <React.Fragment>
      <ResponsiveDialog
        size="xs"
        open={Boolean(errorDialogText)}
        onClose={() => {
          setErrorDialogText();
          setAdvancedErrorDialogText();
        }}
        withCloseButton
        title="Error"
      >
        <DialogContent dividers>
          <Typography>
            {errorDialogText}
          </Typography>
        </DialogContent>
        <Accordion className={styles.advancedHelp}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography variant="subtitle2">Advanced help</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <FormattedText variant="body2">{advancedErrorDialogText}</FormattedText>
          </AccordionDetails>
        </Accordion>
      </ResponsiveDialog>
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
    text: PropTypes.string,
  }).isRequired,
};

const StyledDicomQuestion = withStyles(QuestionnaireStyle)(DicomQuestion)
export default StyledDicomQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "dicom") {
    return [StyledDicomQuestion, 50];
  }
});
