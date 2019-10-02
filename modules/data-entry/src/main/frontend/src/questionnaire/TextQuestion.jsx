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

import { withStyles } from "@material-ui/core";

import PropTypes from "prop-types";

import MultipleChoice from "./MultipleChoice";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

function TextQuestion(props) {
  let {defaults, max, min, name, userInput, regexp, ...rest} = props;
  return (
    <Question
      text={name}
      >
      <MultipleChoice
        max={max}
        min={min}
        defaults={defaults}
        input={userInput==="input"}
        textbox={userInput==="textbox"}
        />
    </Question>);
}

TextQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  name: PropTypes.string,
  min: PropTypes.number,
  max: PropTypes.number,
  defaults: PropTypes.array,
  userInput: PropTypes.oneOf([undefined, "input", "textbox"]),
};

export default withStyles(QuestionnaireStyle)(TextQuestion);
