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
import { withStyles } from "@material-ui/core";
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS, VALUE_COMPARATORS } from "./FilterComparators.jsx";
import VocabularySelector from "../../vocabQuery/query.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS).concat(VALUE_COMPARATORS);

const VocabularyFilter = forwardRef((props, ref) => {
  const { classes, comparator, onChangeComparator, onChangeInput, questionDefinition, ...rest } = props;
  let vocabulary = questionDefinition["sourceVocabulary"];
  let vocabularyFilter = questionDefinition["vocabularyFilter"];

  return (
    <VocabularySelector
      onClick={(id, name) => {onChangeInput(id, name)}}
      clearOnClick={false}
      vocabularyFilter={vocabularyFilter}
      defaultValue={defaultValue}
      vocabulary={vocabulary}
      placeholder="empty"
      ref={ref}
      noMargin
      {...rest}
      />
  )
});

VocabularyFilter.propTypes = {
  onChangeComparator: PropTypes.func,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.shape({
    dateFormat: PropTypes.string
  })
}

const StyledVocabularyFilter = withStyles(QuestionnaireStyle)(VocabularyFilter)

export default StyledVocabularyFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType === "vocabulary") {
    return [COMPARATORS, StyledVocabularyFilter, 50];
  }
});
