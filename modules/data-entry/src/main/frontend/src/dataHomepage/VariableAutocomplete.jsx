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

import React, { useState } from "react";
import PropTypes from 'prop-types';
import Autocomplete, { createFilterOptions } from "@mui/material/Autocomplete";
import { ListItemButton, ListItemText, Popper, TextField } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import FormattedText from "../components/FormattedText";

const variableAutocompleteStyle = theme => ({
  autocompleteRoot: {
    "& .MuiFormHelperText-root" : {
        wordBreak: "break-word",
    },
  },
  autocompletePopper: {
    zIndex: 1500,
    "& .MuiAutocomplete-option": {
      "& .MuiListItemText-secondary":{
        wordBreak: "break-word",
      },
    },
    "& .MuiAutocomplete-groupLabel": {
      color: theme.palette.primary.main,
      fontSize: "1em",
      lineHeight: 1.5,
      paddingTop: theme.spacing(1),
      paddingBottom: theme.spacing(1),
      fontWeight: "bold",
      top: theme.spacing(-1),
    },
  },
});


let VariableAutocomplete = (props) => {
  const { classes, options, getOptionValue, getOptionLabel, getOptionSecondaryLabel, groupBy, selectedValue, onValueChanged, textFieldProps, ...rest } = props;

  const filterOptions = createFilterOptions({
    stringify: (option) => `${getOptionLabel?.(option)} ${getOptionSecondaryLabel?.(option) || ''}`
  });

  return (
    <Autocomplete
      className={classes.autocompleteRoot}
      PopperComponent={ groupBy ?
        withStyles(variableAutocompleteStyle)((props) => <Popper {...props} className={classes.autocompletePopper} placement="bottom" />)
      : undefined }
      value={selectedValue && options.find(o => getOptionValue(o) == selectedValue) || null}
      options={options}
      filterOptions={filterOptions}
      getOptionLabel={getOptionLabel}
      groupBy={groupBy}
      onChange={(event, value) => onValueChanged(getOptionValue(value))}
      renderOption={(props, option) =>
        <ListItemButton
          value={getOptionValue(option)}
          key={getOptionValue(option)}
          dense
          {...props}
        >
          <ListItemText
            sx={groupBy ? {marginLeft: !!groupBy(option) ? 1 : -1} : undefined}
            primary={<FormattedText variant="inherit">{ getOptionLabel(option) }</FormattedText>}
            secondary={getOptionSecondaryLabel(option)}
          />
        </ListItemButton>
      }
      renderInput={(params) =>
        <TextField
          variant="standard"
          placeholder="Variable"
          helperText={selectedValue && getOptionSecondaryLabel(options.find(o => getOptionValue(o) == selectedValue)) || null}
          {...params}
          {...textFieldProps}
        />
      }
      {...rest}
    />
  )
}

VariableAutocomplete.propTypes = {
  options: PropTypes.array.isRequired,
  getOptionValue: PropTypes.func,
  getOptionLabel: PropTypes.func,
  getOptionSecondaryLabel: PropTypes.func,
  groupBy: PropTypes.func,
  value: PropTypes.string,
  onValueChanged: PropTypes.func,
  textFieldProps: PropTypes.object,
};

VariableAutocomplete.defaultProps = {
  options: [],
  getOptionValue: (option) => option?.uuid,
  getOptionLabel: (option) => option?.label,
  getOptionSecondaryLabel: () => {},
  onValueChanged: () => {},
  textFieldProps: {}
};
const StyledVariableAutocomplete = withStyles(variableAutocompleteStyle)(VariableAutocomplete);
export default StyledVariableAutocomplete;
