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

import makeStyles from '@mui/styles/makeStyles';

import FormattedText from "../components/FormattedText";

const useStyles = makeStyles(theme => ({
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
    "& .MuiAutocomplete-groupLabel:empty": {
      display: "none",
    },
  },
}));

// Component that renders a single-select autocomplete where the options are expected to be resources
//
// Props:
// * className: a class name to be applied to the Autocomplete component
// * options: an array of objects; by default the object shape is expected to be:
//   { uuid: string, label: string, path: string, relativePath: string }, however other structures are supported,
//   together with defining the getOption* handlers accordingly. REQUIRED.
// * getOptionValue: a function that takes an option and retrieves its value; defaults to (option) => option.uuid
// * getOptionLabel: a function that takes an option and retrieves its label; defaults to (option) => option.label;
//   also passed down to the rendered Autocomplete component as the `getOptionLabel` handler
// * getOptionSecondaryLabel: a function that takes an option and retrieves its secondary label, i.e. the text;
//   displayed as in textSecondary color under the option label in the dropdown.
//   Filtering options in the autocomplete based on user input is set to take the secondary label into account when
//   matching options.
//   Defaults to () => {} (no secondary label)
// * groupBy: a function that takes an option and retrieves the criterion to group by; defaults to undefined (no grouping);
//   passed down to the rendered Autocomplete component as the `groupBy` handler
// * selectedValue: a string representing the value of the selected option (according to getOptionValue)
// * onValueChanged: handler for when selectedValue changes; passed to the Autocomplete component's `onChange` handler
// * getHelperText: a function that takes an option and retrieves the text to be displayed under the Autocomplete's
//   input when that option is selected; applied to the option that matches selectedValue; undefined by default.
//   Example: option => option?.path would show the selected variable's path under the input.
// * textFieldProps: an object allowing to specify properties that should be passed down to the TextField.
//   Example: {multiline: true, placeholder: "Add a filter" }
// Any other props are passed directly to the Autocomplete component.

let VariableAutocomplete = (props) => {
  const { className, options, getOptionValue, getOptionLabel, getOptionSecondaryLabel, groupBy, selectedValue, onValueChanged, getHelperText, textFieldProps, ...rest } = props;

  const filterOptions = createFilterOptions({
    stringify: (option) => `${getOptionLabel?.(option)} ${getOptionSecondaryLabel?.(option) || ''}`
  });

  const classes = useStyles();

  const classNames = [ classes.autocompleteRoot ];
  className && classNames.push(className);

  return (
    <Autocomplete
      className={classNames.join(' ')}
      PopperComponent={ groupBy ?
        (props) => <Popper {...props} className={classes.autocompletePopper} placement="bottom" />
      : undefined }
      value={selectedValue && options.find(o => getOptionValue(o) == selectedValue) || null}
      options={options}
      filterOptions={filterOptions}
      getOptionLabel={getOptionLabel}
      groupBy={groupBy}
      onChange={(event, value) => onValueChanged(getOptionValue(value))}
      renderOption={(props, option) =>
        <ListItemButton
          {...props}
          value={getOptionValue(option)}
          key={getOptionValue(option)}
          dense
        >
          <ListItemText
            sx={groupBy ? {marginLeft: !!groupBy(option) ? 1 : -1} : undefined}
            disableTypography
            primary={<FormattedText variant="body1">{ getOptionLabel(option) }</FormattedText>}
            secondary={<FormattedText variant="body2" color="textSecondary">{ getOptionSecondaryLabel(option) }</FormattedText>}
          />
        </ListItemButton>
      }
      renderInput={(params) =>
        <TextField
          variant="standard"
          placeholder="Variable"
          helperText={selectedValue && getHelperText?.(options.find(o => getOptionValue(o) == selectedValue)) || null}
          {...params}
          {...textFieldProps}
        />
      }
      {...rest}
    />
  )
}

VariableAutocomplete.propTypes = {
  className: PropTypes.string,
  options: PropTypes.array.isRequired,
  getOptionValue: PropTypes.func,
  getOptionLabel: PropTypes.func,
  getOptionSecondaryLabel: PropTypes.func,
  groupBy: PropTypes.func,
  value: PropTypes.string,
  onValueChanged: PropTypes.func,
  getHelperText: PropTypes.func,
  textFieldProps: PropTypes.object,
};

VariableAutocomplete.defaultProps = {
  getOptionValue: (option) => option?.uuid,
  getOptionLabel: (option) => option?.label,
  getOptionSecondaryLabel: () => {},
  onValueChanged: () => {},
  getHelperText: () => {},
  textFieldProps: {}
};

export default VariableAutocomplete;
