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
import { DATE_FORMATS } from "../../questionnaire/DateQuestion.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS);

const ListFilter = forwardRef((props, ref) => {
  const { classes, comparator, defaultValue, onChange, onChangeComparator, onChangeInput, questionDefinition, ...rest } = props;
  // Manage our own state inside here as well
  const [ selection, setSelection ] = useState(defaultValue || "");
  const isUnary = comparator && UNARY_COMPARATORS.includes(comparator);

  return (
    <Select
      value={selection}
      onChange={(event) => {
        setSelection(event.target.value);
        onChange(event);
      }}
      defaultValue={defaultValue}
      onChange={(event) => {onChangeInput(event.target.value)}}
      className={classes.answerField}
      ref={ref}
      {...rest}
      >
      {Object.entries(questionDefinition)
        // answers are nodes with "jcr:primaryType" = "lfs:AnswerOption"
        .filter( (answer) => {
          return answer[1]['jcr:primaryType'] && answer[1]['jcr:primaryType'] === 'lfs:AnswerOption'
        })
        // turn these answers into menuItems
        .map( (answer) => {
          return(
            <MenuItem value={answer[1]['value']} key={answer[1]['value']}>{answer[1]['value']}</MenuItem>
          );
        })
      }
    </Select>
  );
});

ListFilter.propTypes = {
  onChangeComparator: PropTypes.func,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.shape({
    dateFormat: PropTypes.string
  })
}

const StyledListFilter = withStyles(QuestionnaireStyle)(ListFilter)

export default StyledListFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType === "list") {
    return [COMPARATORS, StyledListFilter, 50];
  }
});
