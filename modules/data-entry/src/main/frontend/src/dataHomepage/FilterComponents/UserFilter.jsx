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
import PropTypes from "prop-types";
import { checkPropTypes } from "../../propTypes";

import FilterComponentManager from "./FilterComponentManager.jsx";
import { DEFAULT_COMPARATORS } from "./FilterComparators.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../../login/ReLoginDialog.js";

const filterUserOptions =  createFilterOptions({
  stringify: (option) => `${option.name} ${option.principalName}`
});

/**
 * Display a filter on a user creator or editor of a form. This is not meant to be instantiated directly, but is returned from FilterComponentManager's
 * getFilterComparatorsAndComponent method.
 *
 * @param {object} initial Object containing the initial value and label to place in the list
 * @param {func} onChangeInput Callback for when the value select has changed
 * Other props are forwarded to the TextField component
 *
 */
const UserFilter = forwardRef((props, ref) => {
  checkPropTypes(UserFilter, props);
  const { initial, onChangeInput } = props;
  // Manage our own state inside here as well
  const [ selection, setSelection ] = useState(initial?.value || "");
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
        value={selection && users?.find(item => item.name == selection) || null}
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
  initial: PropTypes.shape({
    value: PropTypes.string,
    label: PropTypes.string,
  }),
  onChangeInput: PropTypes.func,
  questionDefinition: PropTypes.object
}

export default UserFilter;

FilterComponentManager.registerFilterComponent((questionDefinition) => {
  if (questionDefinition.dataType === "user") {
    return [DEFAULT_COMPARATORS, UserFilter, 60];
  }
});
