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
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS, VALUE_COMPARATORS } from "./FilterComparators.jsx";
import { NumberFormatCustom } from "../../questionnaire/NumberQuestion";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS).concat(VALUE_COMPARATORS);

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
const NumericFilter = forwardRef((props, ref) => {
  const { classes, defaultValue, onChangeInput, questionDefinition, ...rest } = props;
  return (
    <TextField
      variant="standard"
      className={classes.answerField}
      inputProps={{
        decimalScale: questionDefinition["dataType"] === "long" ? 0 : undefined
      }}
      InputProps={{
        inputComponent: NumberFormatCustom, // Used to override a TextField's type
        className: classes.answerField
      }}
      defaultValue={defaultValue}
      onChange={(event) => {onChangeInput(event.target.value)}}
      placeholder="empty"
      inputRef={ref}
      {...rest}
      />
  )
});

NumericFilter.propTypes = {
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.shape({
    dataType: PropTypes.string
  })
}

const StyledNumericFilter = withStyles(QuestionnaireStyle)(NumericFilter)

export default StyledNumericFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType == 'decimal' || questionDefinition.dataType == 'double' || questionDefinition.dataType == 'long') {
    return [COMPARATORS, StyledNumericFilter, 50];
  }
});
