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
  const { existingAnswer, classes, ...rest } = props;
  const [ expanded, setExpanded ] = useState(false);
  const [ pedigreeData, setPedigree ] = useState({});

  var pedigreeJSON = null;
  var displayedImage = "";

  if (existingAnswer && existingAnswer.length > 0 && existingAnswer[0].image) {
    displayedImage = existingAnswer[0].image;
    pedigreeJSON = existingAnswer[0].pedigreeJSON;
  } else if (pedigreeData && pedigreeData.image) {
    // FIXME: temporary hack until pedigree is saved in LFS
    displayedImage = pedigreeData.image;
    pedigreeJSON = pedigreeData.pedigreeJSON;
  } else {
    // FIXME: default placeholder, should be replaced by a "no pedigree" or something like that
    displayedImage = '<svg height="100" width="100"><circle cx="50" cy="50" r="40" stroke="black" stroke-width="3" fill="grey" /></svg> ';
  }
  
  // FIXME: Hardcoded height
  var new_image = displayedImage.replace(/(<svg[^>]+)height="\d+"/, "$1height=\"250px\"");
  var image_div = (<div className={classes.pedigreeSmall} dangerouslySetInnerHTML={{__html: new_image}}/>);

  var closeDialog = function () {
    setExpanded(false);
  };

  var openPedigree = function () {
    window.pedigreeEditor = new PedigreeEditor({
      "pedigreeJSON": pedigreeJSON,
      "onCloseCallback": closeDialog,
      "onPedigreeSaved": onNewPedigree });
  };

  var closePedigree = function () {
    window.pedigreeEditor.unload();
    delete window.pedigreeEditor;
  };

  var onNewPedigree = function (pedigreeJSON, pedigreeSVG) {
    // FIXME: save in LFS
    // state change should trigger re-render
    setPedigree({"image": pedigreeSVG, "pedigreeJSON": pedigreeJSON});
  };

  return (
    <Question
      {...rest}
      >
      {image_div && (
        <Link onClick={() => {setExpanded(true);}}>
          {image_div}
        </Link>
      )}
      <Dialog fullScreen open={expanded}
        onEntering={() => { openPedigree(); }}
        onExit={() => { closePedigree(); }}
        onClose={() => { setExpanded(false); }}>
        <DialogContent>
          <div id="pedigreeEditor"></div>
        </DialogContent>
      </Dialog>
    </Question>);
}

PedigreeQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    description: PropTypes.string
  }).isRequired,
  existingAnswer: PropTypes.array
}

const StyledPedigreeQuestion = withStyles(QuestionnaireStyle)(PedigreeQuestion)
export default StyledPedigreeQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "pedigree") {
    return [StyledPedigreeQuestion, 50];
  }
});
