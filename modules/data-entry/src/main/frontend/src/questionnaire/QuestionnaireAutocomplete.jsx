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
import React, { useState, useEffect } from 'react';
import PropTypes from "prop-types";
import { makeStyles } from '@mui/styles';
import { deepPurple, orange } from '@mui/material/colors';

import Autocomplete, { createFilterOptions } from "@mui/material/Autocomplete";
import {
  Avatar,
  Divider,
  FormControl,
  FormControlLabel,
  Icon,
  IconButton,
  List,
  ListItem,
  ListItemAvatar,
  ListItemButton,
  ListItemText,
  TextField,
  Typography,
  Tooltip
} from "@mui/material";
import ClearIcon from '@mui/icons-material/Clear';

import FormattedText from "../components/FormattedText";

const useStyles = makeStyles(theme => ({
  selectionList: {
    "& .MuiListItem-root": {
      paddingLeft: 0,
    },
    "& .MuiDivider-root": {
      marginLeft: theme.spacing(7),
    },
  },
  avatar: {
    backgroundColor: theme.palette.action.hover,
    color: theme.palette.text.primary,
    fontWeight: "bold",
  },
  optionText: {
    "& .MuiListItemText-secondary": {
       wordBreak: "break-word",
       "& > *": {
         color: theme.palette.text.disabled,
       },
     },
  },
}));

let entitySpecs = {
  Question: {
    color: deepPurple[700]
  },
  Section: {
    icon: "view_stream",
    color: orange[800]
  }
}

// Component that renders a multi-select autocomplete where the options are expected to be questions or sections
// from a certain questionnaire
//
// Props:
// * entities: an array of objects describing questionnaire entries; the object shape is expected to be:
//   { uuid: string, name: string, text: string, path: string, relativePath: string }
// * selection: an array of strings representing the values of the selected option (according to getOptionValue)
// * onValueChanged: handler for when selection changes; passed to the Autocomplete component's `onChange` handler
// * getOptionValue: a function that takes an option and retrieves its value; defaults to (option) => option.path
// Any other props are passed directly to the Autocomplete component.

function QuestionnaireAutocomplete(props) {
  const { entities, selection, onSelectionChanged, getOptionValue, ...rest } = props;

  const filterOptions = createFilterOptions({
    stringify: (option) => `${option.relativePath} ${option.name} ${option.text}`
  });

  const classes = useStyles();

  let unselectEntity = (index) => {
    onSelectionChanged(oldValues => {
      let newValues = oldValues.slice();
      newValues.splice(index, 1);
      return newValues;
    });
  }

  let getAvatar = (type, selected) => {
    return (
      <ListItemAvatar>
        <Tooltip title={type}>
          <Avatar
            style={{color: entitySpecs[type].color, backgroundColor: selected ? "transparent" : undefined}}
            className={classes.avatar}
          >
            { selected ?
              <Icon>check_box</Icon>
              :
              entitySpecs[type].icon ? <Icon>{entitySpecs[type].icon}</Icon> : type?.charAt(0)
            }
          </Avatar>
        </Tooltip>
      </ListItemAvatar>
    );
  }

  let getQuestionnaireEntryText = (entry) => {
    return (
      <ListItemText
        className={classes.optionText}
        primary={
          <FormattedText variant="inherit">
            { entry.text }
          </FormattedText>
        }
        secondary={
          <>
            <span>{ entry.relativePath }</span>
            { entry.name }
          </>
        }
      />
    );
  }

  return (<>
    <FormControl variant="standard" fullWidth>
      <Autocomplete
        multiple
        disableCloseOnSelect
        disableClearable
        value={entities?.filter(v => selection.includes(getOptionValue(v))) || []}
        filterOptions={filterOptions}
        onChange={(event, value) => {
          onSelectionChanged(value?.map(item => getOptionValue(item)));
        }}
        renderTags={() => null}
        getOptionLabel={(option) => option?.name}
        options={entities || []}
        renderOption={(props, option) =>
          <ListItemButton
            value={getOptionValue(option)}
            key={option.path}
            dense
            {...props}
          >
            { getAvatar(option.type, selection.includes(getOptionValue(option))) }
            { getQuestionnaireEntryText(option) }
          </ListItemButton>
        }
        renderInput={(params) =>
          <TextField
            variant="standard"
            placeholder="Select questions/sections from this questionnaire"
            {...params}
          />
        }
        {...rest}
      />
    </FormControl>
    {/* List the entered values */}
    <List dense className={classes.selectionList}>
      { entities?.filter(v => selection.includes(getOptionValue(v))).map((value, index) =>
        <React.Fragment key={`selection-list-item-${index}`}>
          { !!index && <Divider key={`divider-${index}`} variant="inset" component="li" /> }
          <ListItem
            key={`${value.name}-${index}`}
            secondaryAction={
              <Tooltip title="Delete entry">
                <IconButton onClick={() => unselectEntity(index)}>
                  <ClearIcon/>
                </IconButton>
              </Tooltip>
            }
          >
            { getAvatar(value.type) }
            { getQuestionnaireEntryText(value) }
          </ListItem>
        </React.Fragment>
      )}
    </List>
  </>);
}

QuestionnaireAutocomplete.propTypes = {
  entities: PropTypes.array.isRequired,
  selection: PropTypes.array,
  onSelectionChanged: PropTypes.func,
  getOptionValue: PropTypes.func,
}
QuestionnaireAutocomplete.defaultProps = {
  selection: [],
  onSelectionChanged: () => {},
  getOptionValue: (option) => option?.path,
}

export default QuestionnaireAutocomplete;
