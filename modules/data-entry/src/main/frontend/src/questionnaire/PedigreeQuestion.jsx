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

import { Button, Dialog, DialogContent, Grid, Link, Tooltip } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import PropTypes from "prop-types";

import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";
import DeleteButton from "../dataHomepage/DeleteButton";

import Answer from "./Answer";
import AnswerComponentManager from "./AnswerComponentManager";

import PedigreeEditor from "../pedigree/pedigree";

// Component that renders a pedigree, although answering these questions is not currently possible.
//
// Optional props:
//  existingAnswer: array of length 1, where the first entry corresponds to a pedigree object. The
//    pedigree object is assumed to contain an image property with an SVG as its value.
//  questionDescription: props forwarded to the Question element.
//
// Sample usage:
// <PedigreeQuestion
//    questionDefinition={{
//      text="Patient pedigree"
//      description="De-identified information only."
//      }}
//    />
function PedigreeQuestion(props) {
  const { existingAnswer, classes, pageActive, ...rest } = props;
  const [ expanded, setExpanded ] = useState(false);
  // default pedigreeData state variable to the pedigree saved in CARDS:
  const [ pedigreeData, setPedigree ] = useState(existingAnswer && existingAnswer.length > 1 && existingAnswer[1].value
                                        ? {"image": existingAnswer[1].image, "pedigreeJSON": existingAnswer[1].value}
                                        : {});

  // FIXME: hardcoded value
  const PEDIGREE_THUMBNAIL_WIDTH = 300;

  var resizeSVG = function(svgText, newWidthInPixels) {
    const newWidth = "$1width=\"" + newWidthInPixels + "px\"";
    var resizedSVG = svgText?.replace(/(<svg[^>]+)height="\d+"/, "$1");
    resizedSVG = resizedSVG.replace(/(<svg[^>]+)width="\d+"/, newWidth);
    return resizedSVG;
  };

  var pedigreeJSON = null;
  var pedigreeSVG  = null;
  var displayedImage = '';

  if (pedigreeData && pedigreeData.image && pedigreeData.pedigreeJSON) {
    // use pedigree stored in React component state:
    // default value for that state is the pedigree loaded from CARDS, but it gets overwritten each time pedigree is saved
    // from the pedigree editor, even if that data is not yet saved to CARDS
    pedigreeSVG  = pedigreeData.image;
    pedigreeJSON = pedigreeData.pedigreeJSON;
    displayedImage = resizeSVG(pedigreeSVG, PEDIGREE_THUMBNAIL_WIDTH);
  }

  let [ outputAnswers, setOutputAnswers ] = useState(pedigreeJSON ? [["value", pedigreeJSON]] : []);
  let answerMetadata = {image:  pedigreeSVG};

  useEffect(() => {
    setOutputAnswers(pedigreeJSON ? [["value", pedigreeJSON]] : []);
  }, [pedigreeJSON]);

  var image_div = <div className={classes.thumbnail} dangerouslySetInnerHTML={{__html: displayedImage}}/>;

  var closeDialog = function () {
    setExpanded(false);
  };

  var openPedigree = function () {
    window.pedigreeEditor = new PedigreeEditor({
      "pedigreeJSON": pedigreeJSON,
      "pedigreeDiv": "pedigreeEditor",  // the DIV to render entire pedigree in
      "onCloseCallback": closeDialog,
      "onPedigreeSaved": onUpdatedPedigree,
      "readOnlyMode": false });
  };

  var closePedigree = function () {
    window.pedigreeEditor.unload();
    typeof(props.onChange) == 'function' && props.onChange();
    delete window.pedigreeEditor;
  };

  var onUpdatedPedigree = function (pedigreeJSON, pedigreeSVG) {
    // state change will trigger re-render
    setPedigree({"image": pedigreeSVG, "pedigreeJSON": pedigreeJSON});
  };

  let defaultDisplayFormatter = function(label, idx) {
    return image_div || "";
  }

  return (
    <Question
      defaultDisplayFormatter={defaultDisplayFormatter}
      currentAnswers={outputAnswers.length}
      {...props}
      >
      {
        pageActive && <>
          <div className={classes.answerField}>
          { pedigreeData.image ?
            <Grid container direction="row" justifyContent="flex-start" alignItems="flex-start" spacing={0}>
              <Grid item>
                <Tooltip title="Edit Pedigree">
                  <Link className={classes.thumbnailLink} onClick={() => {setExpanded(true);}} underline="hover">
                    {image_div}
                  </Link>
                </Tooltip>
              </Grid>
              <Grid item>
                <DeleteButton
                  entryName={"pedigree"}
                  entryType={"Pedigree"}
                  onComplete={() => {setPedigree({});}}
                />
              </Grid>
            </Grid>
            :
            <Button variant="outlined" onClick={() => {setExpanded(true);}}>Draw</Button>
          }
          </div>
          <Dialog fullScreen open={expanded}
            onClose={() => { setExpanded(false); }}
            TransitionProps={{
              onEntering: () => { openPedigree(); },
              onExit: () => { closePedigree(); }
            }}>
            <DialogContent>
              <div id="pedigreeEditor"></div>
            </DialogContent>
          </Dialog>
        </>
      }
      <Answer
        answers={outputAnswers}
        answerMetadata={answerMetadata}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="cards:PedigreeAnswer"
        valueType="String"
        pageActive={pageActive}
        {...rest}
      />
    </Question>);
}

PedigreeQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    description: PropTypes.string
  }).isRequired,
  existingAnswer: PropTypes.array,
}

const StyledPedigreeQuestion = withStyles(QuestionnaireStyle)(PedigreeQuestion)
export default StyledPedigreeQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "pedigree") {
    return [StyledPedigreeQuestion, 50];
  }
});
