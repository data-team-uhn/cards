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

import React, { forwardRef } from "react";
import { TextField } from "@material-ui/core";
import withStyles from '@material-ui/styles/withStyles';
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS, VALUE_COMPARATORS } from "./FilterComparators.jsx";
import DateQuestionUtilities from "../../questionnaire/DateQuestionUtilities.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS).concat(VALUE_COMPARATORS);
const COMPARATORS_CREATED_DATE = DEFAULT_COMPARATORS.slice().concat(VALUE_COMPARATORS);

/**
 * Display a filter on a date answer of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {date} defaultValue Default value to place in the textfield
 * @param {func} onChangeInput Callback for when the value select has changed
 * @param {object} questionDefinition Object containing the definition of the question. May include a dateFormat
 * Other props are forwarded to the TextField component
 *
 */
const DateFilter = forwardRef((props, ref) => {
  // DefaultLabel intentionally unused, since it needs to not be passed to TextField
  const { classes, defaultLabel, defaultValue, onChangeInput, questionDefinition, ...rest } = props;

  // Dates should have a dateFormat, or default to "yyyy-MM-dd"
  let dateFormat = questionDefinition["dateFormat"] || "yyyy-MM-dd";
  let dateType = DateQuestionUtilities.getDateType(dateFormat);
  let isMonth = (dateType === DateQuestionUtilities.MONTH_DATE_TYPE);
  let isDate = (dateType === DateQuestionUtilities.FULL_DATE_TYPE);
  let textFieldType = isMonth ? "month" :
    isDate ? "date" :
    "datetime-local";

  return (
    <TextField
      id="date"
      type={textFieldType}
      className={classes.answerField}
      InputLabelProps={{
        shrink: true,
      }}
      InputProps={{
        className: classes.answerField
      }}
      defaultValue={defaultValue}
      onChange={(event) => {onChangeInput(event.target.value)}}
      inputRef={ref}
      {...rest}
      />
  )
});

DateFilter.propTypes = {
  defaultValue: PropTypes.string,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.shape({
    dateFormat: PropTypes.string
  })
}

const StyledDateFilter = withStyles(QuestionnaireStyle)(DateFilter)

export default StyledDateFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date") {
    return [COMPARATORS, StyledDateFilter, 50];
  } else if (questionDefinition.dataType === "createddate") {
    return [COMPARATORS_CREATED_DATE, StyledDateFilter, 50];
  }
});
