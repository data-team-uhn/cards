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
import withStyles from '@mui/styles/withStyles';
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS } from "./FilterComparators.jsx";
import ResourceQuery from "../../resourceQuery/ResourceQuery.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS);

/**
 * Display a filter on a resource answer of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {string} defaultValue The default value to place in the filter
 * @param {func} onChangeInput Callback for when the value select has changed
 * @param {object} questionDefinition Object containing the definition of the question. Should include "primaryType", "labelProperty", and "propertiesToSearch" children.
 * Other props are forwarded to the VocabularyQuery component
 *
 */
const ResourceFilter = forwardRef((props, ref) => {
  const { classes, defaultValue, defaultLabel, onChangeInput, questionDefinition, ...rest } = props;
  const enableUserEntry = !!!questionDefinition?.displayMode || questionDefinition?.displayMode?.includes("input");

  return (
    <ResourceQuery
      onClick={(id, name) => {onChangeInput(id, name)}}
      onChange={(event) => {event.target.value == "" && onChangeInput("", "")}}
      clearOnClick={false}
      focusAfterSelecting={false}
      questionDefinition={questionDefinition}
      placeholder="empty"
      inputRef={ref}
      value={defaultLabel}
      enableUserEntry={enableUserEntry}
      {...rest}
      />
  )
});

ResourceFilter.propTypes = {
  defaultValue: PropTypes.string,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.shape({
    primaryType: PropTypes.string,
    labelProperty: PropTypes.string,
    propertiesToSearch: PropTypes.string,
  })
}

const StyledResourceFilter = withStyles(QuestionnaireStyle)(ResourceFilter)

export default StyledResourceFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType === "resource") {
    return [COMPARATORS, StyledResourceFilter, 70];
  }
});
