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

import React, { forwardRef, useState, useCallback } from "react";
import { Select, MenuItem } from "@material-ui/core";
import withStyles from '@material-ui/styles/withStyles';
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS } from "./FilterComparators.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS);

/**
 * Display a filter on a list answer of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {string} defaultValue The default value to place in the list
 * @param {func} onChangeInput Callback for when the value select has changed
 * @param {object} questionDefinition Object containing the definition of the question. Should include nodes whose jcr:primaryType is cards:AnswerOption
 * Other props are forwarded to the Select component
 *
 */
const ListFilter = forwardRef((props, ref) => {
  const { classes, defaultValue, onChangeInput, questionDefinition, ...rest } = props;
  // Manage our own state inside here as well
  const [ selection, setSelection ] = useState(defaultValue || "");

  // Populate our our map of options and labels if questionDefinition changes
  let valueToLabel = {};
  let options = Object.entries(questionDefinition)
    // answers are nodes with "jcr:primaryType" = "cards:AnswerOption"
    .filter( (answer) => {
      return answer[1]['jcr:primaryType'] && answer[1]['jcr:primaryType'] === 'cards:AnswerOption'
    })
    // turn these answers into options and populate our valueToLabel
    .map( (answer) => {
      let value = answer[1]['value'];
      let label = answer[1]['label'] || value;
      valueToLabel[value] = label;
      return value;
    });

  return (
    <Select
      value={selection}
      onChange={(event) => {
        let value = event.target.value;
        setSelection(value);
        onChangeInput(value, valueToLabel[value]);
      }}
      className={classes.answerField}
      ref={ref}
      {...rest}
      >
      {options.map((value) => (
        <MenuItem value={value} key={value}>{valueToLabel[value]}</MenuItem>
      ))
      }
    </Select>
  );
});

ListFilter.propTypes = {
  defaultValue: PropTypes.string,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.object
}

const StyledListFilter = withStyles(QuestionnaireStyle)(ListFilter)

export default StyledListFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.displayMode === "list") {
    return [COMPARATORS, StyledListFilter, 60];
  }
});
