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

import React, { forwardRef, useState, useEffect, useContext } from "react";
import { TextField } from "@mui/material";
import Autocomplete, { createFilterOptions } from "@mui/material/Autocomplete";
import withStyles from '@mui/styles/withStyles';
import PropTypes from "prop-types";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS } from "./FilterComparators.jsx";
import QuestionnaireStyle from "../../questionnaire/QuestionnaireStyle.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../../login/loginDialogue.js";

const filterUserOptions =  createFilterOptions({
  stringify: (option) => `${option.name} ${option.principalName}`
});

/**
 * Display a filter on a user creator or editor of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {string} defaultValue The default value to place in the list
 * @param {func} onChangeInput Callback for when the value select has changed
 * @param {object} questionDefinition Object containing the definition of the question. Should include nodes whose jcr:primaryType is cards:AnswerOption
 * Other props are forwarded to the Select component
 *
 */
const UserFilter = forwardRef((props, ref) => {
  const { classes, defaultValue, onChangeInput, questionDefinition, ...rest } = props;
  // Manage our own state inside here as well
  const [ selection, setSelection ] = useState(defaultValue || "");
  const [ users, setUsers ] = useState();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  useEffect(() => {
    if (!users) {
      fetchWithReLogin(globalLoginDisplay, "/home/users.json")
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {
          setUsers(json.rows);
        });
    }
  }, []);

  return (
    <Autocomplete
        value={selection && users.find(item => item.name == selection) || null}
        filterOptions={filterUserOptions}
        onChange={(event, value) => {
          setSelection(value?.name);
          onChangeInput(value?.name);
        }}
        getOptionLabel={(option) => option?.name}
        options={users || []}
        renderInput={(params) =>
          <TextField
            ref={ref}
            variant="standard"
            placeholder="Select user"
            {...params}
          />
        }
      />
  );
});

UserFilter.propTypes = {
  defaultValue: PropTypes.string,
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.object
}

const StyledUserFilter = withStyles(QuestionnaireStyle)(UserFilter)

export default StyledUserFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType === "user") {
    return [DEFAULT_COMPARATORS, StyledUserFilter, 60];
  }
});
