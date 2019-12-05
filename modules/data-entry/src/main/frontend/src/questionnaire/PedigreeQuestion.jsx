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

import { useState } from "react";

import { Dialog, DialogContent, Link, withStyles } from "@material-ui/core";

import PropTypes from "prop-types";

import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

import AnswerComponentManager from "./AnswerComponentManager";

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
  var image_div = "";
  var full_image_div = "";

  // If we have a valid image, render a small and a large version.
  if (existingAnswer && existingAnswer.length > 1 && existingAnswer[1].image) {
    // FIXME: Hardcoded height
    var new_image = existingAnswer[1].image.replace(/(<svg[^>]+)height="\d+"/, "$1height=\"250px\"");
    image_div = (<div className={classes.pedigreeSmall} dangerouslySetInnerHTML={{__html: new_image}}/>);
    full_image_div = (<div className={classes.pedigreeSmall} dangerouslySetInnerHTML={{__html: existingAnswer[1].image}}/>);
  }

  return (
    <Question
      {...rest}
      >
      {image_div && (
        <Link onClick={() => {setExpanded(true);}}>
          {image_div}
        </Link>
      )}
      <Dialog maxWidth={false} open={expanded} onClose={() => {setExpanded(false);}}>
        <DialogContent>
          {full_image_div}
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
