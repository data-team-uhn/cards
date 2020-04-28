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
import { Select, MenuItem,  withStyles } from "@material-ui/core";
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS } from "./FilterComparators.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS);

const BooleanFilter = forwardRef((props, ref) => {
  const { classes, comparator, defaultValue, onChange, onChangeComparator, onChangeInput, questionDefinition, ...rest } = props;
  // Manage our own state inside here as well
  const [ selection, setSelection ] = useState(defaultValue || "");

  const {yesLabel, noLabel, unknownLabel, enableUnknown} = { ...props.questionDefinition, ...props }
  // Define the defaults for yesLabel, etc. here because we want questionDefinition to be able to
  // override them, and the props to be able to override the questionDefinition
  let options = [[yesLabel || "Yes", "true"], [noLabel || "No", "false"]];
  if (enableUnknown) {
    options.push([unknownLabel || "Unknown", "undefined"]);
  }


  return (
    <Select
      value={selection}
      onChange={(event) => {
        setSelection(event.target.value);
        onChangeInput(event.target.value, event.currentTarget.dataset.label);
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
  onChangeComparator: PropTypes.func,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.shape({
    dateFormat: PropTypes.string
  })
}

const StyledBooleanFilter = withStyles(QuestionnaireStyle)(BooleanFilter)

export default StyledBooleanFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType === "boolean") {
    return [COMPARATORS, StyledBooleanFilter, 50];
  }
});
