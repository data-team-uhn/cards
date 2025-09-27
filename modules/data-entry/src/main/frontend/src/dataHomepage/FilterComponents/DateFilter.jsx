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

import React, { useState, forwardRef } from "react";
import { withStyles } from 'tss-react/mui';
import PropTypes from "prop-types";
import { checkPropTypes } from "../../propTypes";
import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS, VALUE_COMPARATORS } from "./FilterComparators.jsx";
import DateTimeUtilities from "../../components/DateTimeUtilities.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

import { AdapterLuxon } from "@mui/x-date-pickers/AdapterLuxon";
import { LocalizationProvider } from '@mui/x-date-pickers';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS).concat(VALUE_COMPARATORS);
const COMPARATORS_CREATED_DATE = DEFAULT_COMPARATORS.slice().concat(VALUE_COMPARATORS);

/**
 * Display a filter on a date answer of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {object} initial Object containing the initial value and label to place in the textfield
 * @param {func} onChangeInput Callback for when the value select has changed
 * @param {object} questionDefinition Object containing the definition of the question. May include a dateFormat
 *
 */
const DateFilter = forwardRef((props, ref) => {
  checkPropTypes(DateFilter, props);
  const { classes, initial, onChangeInput, questionDefinition } = props;

  const [ displayedDate, setDisplayedDate ] = useState(DateTimeUtilities.toPrecision(initial?.value));

  // Dates should have a dateFormat, or default to "yyyy/MM/dd"
  const dateFormat = questionDefinition["dateFormat"] || DateTimeUtilities.VIEW_DATE_FORMAT;
  const views = DateTimeUtilities.getPickerViews(dateFormat);
  const isMeridiem = DateTimeUtilities.formatIsMeridiem(dateFormat);

  return (
    <LocalizationProvider dateAdapter={AdapterLuxon}>
      <DateTimePicker
        ampm={isMeridiem}
        label="Any date"
        views={views}
        format={dateFormat}
        value={displayedDate}
        onChange={(value) => {
          setDisplayedDate(value);
          onChangeInput(value ? DateTimeUtilities.toPrecision(value, dateFormat).toISO() : null, value ? value.toFormat(dateFormat) : null);
        }}
        slotProps={{ textField: {
                       variant: 'standard',
                       className: classes.answerDateField,
                     },
                     field: {
                       clearable: true,
                       onClear: () => setDisplayedDate(""),
                     },
        }}
      />
    </LocalizationProvider>
  )
});

DateFilter.propTypes = {
  initial: PropTypes.shape({
    value: PropTypes.string,
    label: PropTypes.string,
  }),
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.shape({
    dateFormat: PropTypes.string
  })
}

const StyledDateFilter = withStyles(DateFilter, QuestionnaireStyle)

export default StyledDateFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType === "date") {
    return [COMPARATORS, StyledDateFilter, 50];
  } else if (questionDefinition.dataType === "datetime") {
    return [COMPARATORS_CREATED_DATE, StyledDateFilter, 50];
  }
});
