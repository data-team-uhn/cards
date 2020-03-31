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
import { TextField,  withStyles } from "@material-ui/core";
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS } from "./FilterComparators.jsx";
import { DATE_FORMATS } from "../../questionnaire/DateQuestion.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS);

const TextFilter = forwardRef((props, ref) => {
  const { classes, defaultValue, onChange, onChangeInput, questionDefinition, ...rest } = props;
  // Manage our own state inside here as well
  const [ input, setInput ] = useState(defaultValue || "");

  return (
    <TextField
      className={classes.textField}
      InputLabelProps={{
      shrink: true,
      }}
      InputProps={{
      className: classes.textField
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
  onChangeComparator: PropTypes.func,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.shape({
    dateFormat: PropTypes.string
  })
}

const StyledTextFilter = withStyles(QuestionnaireStyle)(TextFilter)

export default StyledTextFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  return [COMPARATORS, StyledTextFilter, 10];
});
