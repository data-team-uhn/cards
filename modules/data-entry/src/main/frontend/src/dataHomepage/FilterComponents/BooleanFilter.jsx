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
import { Select, MenuItem } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS } from "./FilterComparators.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS);

/**
 * Display a filter on a boolean answer of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {string} defaultValue The default value to place in the boolean filter
 * @param {func} onChangeInput Callback for when the value select has changed
 * @param {object} questionDefinition Object containing the definition of the question. Should include nodes whose jcr:primaryType is cards:AnswerOption
 * Other props are forwarded to the Select component
 *
 */
const BooleanFilter = forwardRef((props, ref) => {
  const { classes, defaultValue, onChangeInput, questionDefinition, ...rest } = props;
  // Manage our own state inside here as well
  const [ selection, setSelection ] = useState(defaultValue || "");

  const {yesLabel, noLabel, unknownLabel, enableUnknown} = { ...props.questionDefinition, ...props }
  // Define the defaults for yesLabel, etc. here because we want questionDefinition to be able to
  // override them, and the props to be able to override the questionDefinition
  let options = [[yesLabel || "Yes", "1"], [noLabel || "No", "0"]];
  if (enableUnknown) {
    options.push([unknownLabel || "Unknown", "-1"]);
  }


  return (
    <Select
      variant="standard"
      value={selection}
      onChange={(event, el) => {
        setSelection(event.target.value);
        onChangeInput(event.target.value, el.props["data-label"]);
      }}
      className={classes.answerField}
      ref={ref}
      {...rest}
      >
      { options.map( (answer) => {
          return(
            <MenuItem value={answer[1]} key={answer[1]} data-label={answer[0]}>{answer[0]}</MenuItem>
          );
        })
      }
    </Select>
  );
});

BooleanFilter.propTypes = {
  defaultValue: PropTypes.string,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.object
}

const StyledBooleanFilter = withStyles(QuestionnaireStyle)(BooleanFilter)

export default StyledBooleanFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType === "boolean") {
    return [COMPARATORS, StyledBooleanFilter, 50];
  }
});
