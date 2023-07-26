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
import withStyles from '@mui/styles/withStyles';
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS } from "./FilterComparators.jsx";
import VocabularyQuery from "../../vocabQuery/VocabularyQuery.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS);

/**
 * Display a filter on a vocabulary answer of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {string} defaultValue The default value to place in the vocabulary filter
 * @param {func} onChangeInput Callback for when the value select has changed
 * @param {object} questionDefinition Object containing the definition of the question. Should include "sourceVocabularies" and "vocabularyFilters" children.
 * Other props are forwarded to the VocabularyQuery component
 *
 */
const VocabularyFilter = forwardRef((props, ref) => {
  const { classes, defaultValue, defaultLabel, onChangeInput, questionDefinition, ...rest } = props;

  return (
    <VocabularyQuery
      onClick={(id, name) => {onChangeInput(id, name)}}
      onChange={(event) => {event.target.value == "" && onChangeInput("", "")}}
      clearOnClick={false}
      focusAfterSelecting={false}
      questionDefinition={questionDefinition}
      placeholder="empty"
      inputRef={ref}
      value={defaultLabel}
      {...rest}
      />
  )
});

VocabularyFilter.propTypes = {
  defaultValue: PropTypes.string,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.shape({
    sourceVocabularies: PropTypes.array,
  })
}

const StyledVocabularyFilter = withStyles(QuestionnaireStyle)(VocabularyFilter)

export default StyledVocabularyFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType === "vocabulary") {
    return [COMPARATORS, StyledVocabularyFilter, 50];
  }
});
