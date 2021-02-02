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

import { Dialog, DialogContent, Link, withStyles } from "@material-ui/core";

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
  const { existingAnswer, isEdit, onSave, classes, ...rest } = props;
  const [ expanded, setExpanded ] = useState(false);
  // default pedigreeData state variable to the pedigree saved in LFS:
  const [ pedigreeData, setPedigree ] = useState(existingAnswer && existingAnswer.length > 1 && existingAnswer[1].value
                                        ? {"image": existingAnswer[1].value[1], "pedigreeJSON": existingAnswer[1].value[0]}
                                        : {});

  // TODO: use another placeholder image? load from resources?
  const PLACEHOLDER_SVG = '<svg width="300" height="100"><rect fill="#e5e5e5" height="102" width="302" y="-1" x="-1"/>'+
                         '<text xml:space="preserve" text-anchor="start" font-family="Helvetica, Arial, sans-serif"' +
                         'font-size="24" y="55" x="86" stroke="#000" fill="#000000" stroke-width="0">no pedigree</text></svg>';
  // FIXME: hardcoded value
  const PEDIGREE_THUMBNAIL_WIDTH = 300;

  var resizeSVG = function(svgText, newWidthInPixels) {
    const newWidth = "$1width=\"" + newWidthInPixels + "px\"";
    var resizedSVG = svgText.replace(/(<svg[^>]+)height="\d+"/, "$1");
    resizedSVG = resizedSVG.replace(/(<svg[^>]+)width="\d+"/, newWidth);
    return resizedSVG;
  };

  var pedigreeJSON = null;
  var pedigreeSVG  = null;
  var displayedImage = PLACEHOLDER_SVG;

  if (pedigreeData && pedigreeData.image && pedigreeData.pedigreeJSON) {
    // use pedigree stored in React component state:
    // default value for that state is the pedogree loaded from LFS, but it gets overwritten each time pedigree is saved
    // from the pedogre editor, even if that data isnot yet saved to LFS
    pedigreeSVG  = pedigreeData.image;
    pedigreeJSON = pedigreeData.pedigreeJSON;
    displayedImage = resizeSVG(pedigreeSVG, PEDIGREE_THUMBNAIL_WIDTH);
  }

  var image_div = (
    <div className={classes.pedigreeThumbnail}>
        <div className={classes.pedigreeSmallSVG} dangerouslySetInnerHTML={{__html: displayedImage}}/>
    </div>);

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
    // TODO: verify if pedigree will be saved
    // state change should trigger re-render
    setPedigree({"image": pedigreeSVG, "pedigreeJSON": pedigreeJSON});
    onSave && onSave();
  };

  let outputAnswers = pedigreeJSON ? [["value", pedigreeJSON], ["image", pedigreeSVG]] : [];

  return (
    <Question
      preventDefaultView={true}
      {...rest}
      >
      {image_div && (
        isEdit ?
        <>
          <Link onClick={() => {setExpanded(true);}}>
            {image_div}
          </Link>
          { pedigreeData.image && existingAnswer && <DeleteButton
              entryPath={existingAnswer[1]["@path"]}
              entryName={"pedigree"}
              entryType={"Pedigree"}
              shouldGoBack={false}
              buttonClass={classes.pedigreeDeleteButton}
              onComplete={() => {setPedigree({});}}
            /> }
        </>
        : <span> {image_div} </span>
      )}
      <Dialog fullScreen open={expanded}
        onEntering={() => { openPedigree(); }}
        onExit={() => { closePedigree(); }}
        onClose={() => { setExpanded(false); }}>
        <DialogContent>
          <div id="pedigreeEditor"></div>
        </DialogContent>
      </Dialog>
      <Answer
        answers={outputAnswers}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="lfs:PedigreeAnswer"
        valueType="String"
        {...rest}
      />
    </Question>);
}

PedigreeQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    description: PropTypes.string
  }).isRequired,
  existingAnswer: PropTypes.array,
  isEdit: PropTypes.bool,
}

const StyledPedigreeQuestion = withStyles(QuestionnaireStyle)(PedigreeQuestion)
export default StyledPedigreeQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "pedigree") {
    return [StyledPedigreeQuestion, 50];
  }
});
