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
import { Select, MenuItem, Card, CardHeader, CardContent, withStyles } from "@material-ui/core";
import ErrorIcon from "@material-ui/icons/Error";
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS, UNARY_COMPARATORS } from "./FilterComparators.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";

const COMPARATORS = DEFAULT_COMPARATORS.slice().concat(UNARY_COMPARATORS);

/**
 * Display a filter on the associated questionnaire of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {string} defaultValue The default value to place in the questionnaire filter
 * @param {func} onChangeInput Function to call when this filter has chosen a new questionnaire
 * @param {func} questionDefinition Unused, here to stop a warning when it is passed to the SearchBar component
 * Other props will be forwarded to the SearchBar component
 */
const QuestionnaireFilter = forwardRef((props, ref) => {
  const { classes, defaultValue, onChangeInput, questionDefinition, ...rest } = props;
  const [ error, setError ] = useState();
  // Store information about each questionnaire and whether or not we have
  // initialized
  let [ questionnaires, setQuestionnaires ] = useState([]);
  let [ initialized, setInitialized ] = useState(false);
  // Store selected questionnaire uuid
  let [ selection, setSelection ] = useState(defaultValue);
  let [ uuidToTitle, setUuidToTitle ] = useState({});

  // Obtain information about the questionnaires available to the user
  let initialize = () => {
    setInitialized(true);

    // Fetch the questionnaires
    fetch("/query?limit=100&query=select * from [cards:Questionnaire]")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((response) => {
        if (response.totalrows == 0) {
          setError("Access to data is pending the approval of your account");
        }
        setQuestionnaires(response["rows"].map(questionnaire => questionnaire["jcr:uuid"]));
        // turn these questionnaires into options and populate our uuidToTitle
        setUuidToTitle(response["rows"].reduce( (result, questionnaire) => {
            result[questionnaire["jcr:uuid"]] = questionnaire["title"] || questionnaire["@name"];
            return result;
          }
          , {}
        ));
      })
      .catch(handleError);
  }

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response.statusText ? response.statusText : response.toString());
    setQuestionnaires([]);  // Prevent an infinite loop if data was not set
  };

  // If no forms can be obtained, we do not want to keep on re-obtaining questionnaires
  if (!initialized) {
    initialize();
  }
  
  // If an error was returned, report the error
  if (error) {
    return (
      <Card>
        <CardHeader title="Error"/>
        <CardContent>
          <Typography><ErrorIcon />{error}</Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Select
      value={selection}
      onChange={(event) => {
        let uuid = event.target.value;
        setSelection(uuid);
        onChangeInput(uuid, uuidToTitle[uuid]);
      }}
      className={classes.answerField}
      ref={ref}
      {...rest}
      >
      {questionnaires.map((uuid) => (
        <MenuItem value={uuid} key={uuid} selected={selection && selection == uuid}>{uuidToTitle[uuid]}</MenuItem>
      ))
      }
    </Select>
  )
});

QuestionnaireFilter.propTypes = {
  defaultValue: PropTypes.string,
  onChangeInput: PropTypes.func
}

const StyledQuestionnaireFilter = withStyles(QuestionnaireStyle)(QuestionnaireFilter)

export default StyledQuestionnaireFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType == 'questionnaire') {
    return [COMPARATORS, StyledQuestionnaireFilter, 50];
  }
});
