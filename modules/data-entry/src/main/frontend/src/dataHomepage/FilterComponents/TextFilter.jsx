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

import { TextField } from "@mui/material";
import PropTypes from "prop-types";
import { withStyles } from 'tss-react/mui';

import { DEFAULT_COMPARATORS, UNARY_COMPARATORS, TEXT_COMPARATORS } from "./FilterComparators.jsx";
import FilterComponentManager from "./FilterComponentManager.jsx";
import { checkPropTypes } from "../../propTypes";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS).concat(TEXT_COMPARATORS);

const QuestionnaireStyleNotesContain = theme => ({ 
  ...QuestionnaireStyle,
  textField: {
    // The default min-width is 250 px, which is too wide when the comparator is "notes contain"
    minWidth: "155px !important",
  } });

/**
 * Display a filter on a numeric answer of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {object} initial Object containing the initial value and label to place in the text field
 * @param {func} onChangeInput Callback for when the value select has changed
 *
 */
const TextFilter = (props, ref) => {
  checkPropTypes(TextFilter, props);
  const { classes, initial, onChangeInput } = props;
  // Manage our own state inside here as well
  const [ input, setInput ] = useState(initial?.value || "");

  return (
    <TextField
      variant="standard"
      className={classes.answerField}
      slotProps={{
        input: {
          className: classes.answerField,
        },
        inputLabel: {
          shrink: true,
        },
      }}
      defaultValue={initial?.value}
      onChange={(event) => {
        setInput(event.target.value);
        onChangeInput(event.target.value)
      }}
      value={input}
      inputRef={ref}
      placeholder="empty"
    />
  );
};

TextFilter.propTypes = {
  initial: PropTypes.shape({
    value: PropTypes.string,
    label: PropTypes.string,
  }),
  onChangeInput: PropTypes.func
}

const StyledTextFilter = withStyles(TextFilter, QuestionnaireStyle)
const StyledNotesContainFilter = withStyles(TextFilter, QuestionnaireStyleNotesContain)
export default { StyledTextFilter, StyledNotesContainFilter }

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  return [COMPARATORS, StyledTextFilter, 10];
});

FilterComponentManager.registerTextFilterComponent((questionDefinition) => {
  return StyledNotesContainFilter;
});