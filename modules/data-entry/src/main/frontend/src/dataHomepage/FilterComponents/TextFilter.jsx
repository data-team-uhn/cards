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

import React, { forwardRef, useState } from "react";
import { TextField } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS, TEXT_COMPARATORS } from "./FilterComparators.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS).concat(TEXT_COMPARATORS);

const QuestionnaireStyleNotesContain = theme => ({ 
  ...QuestionnaireStyle,
  textField: {
    // The default min-width is 250 px, which is too wide when the comparator is "notes contain"
    minWidth: "155px !important",
}});

/**
 * Display a filter on a numeric answer of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {string} defaultValue The default value to place in the text field
 * @param {func} onChangeInput Callback for when the value select has changed
 * @param {object} questionDefinition Object containing the definition of the question. Should include nodes whose jcr:primaryType is cards:AnswerOption
 * Other props are forwarded to the TextField component
 *
 */
const TextFilter = forwardRef((props, ref) => {
  const { classes, defaultValue, onChangeInput, questionDefinition, ...rest } = props;
  // Manage our own state inside here as well
  const [ input, setInput ] = useState(defaultValue || "");

  return (
    <TextField
      variant="standard"
      className={classes.answerField}
      InputProps={{
      className: classes.answerField
      }}
      defaultValue={defaultValue}
      onChange={(event) => {
        setInput(event.target.value);
        onChangeInput(event.target.value)
      }}
      value={input}
      inputRef={ref}
      placeholder="empty"
      {...rest}
      />
  );
});

TextFilter.propTypes = {
  onChangeInput: PropTypes.func
}

const StyledTextFilter = withStyles(QuestionnaireStyle)(TextFilter)
const StyledNotesContainFilter = withStyles(QuestionnaireStyleNotesContain)(TextFilter)
export default { StyledTextFilter, StyledNotesContainFilter }

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  return [COMPARATORS, StyledTextFilter, 10];
});

FilterComponentManager.registerTextFilterComponent((questionDefinition) => {
  return StyledNotesContainFilter;
});