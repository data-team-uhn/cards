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

import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

import Autocomplete, { createFilterOptions } from "@mui/material/Autocomplete";
import {
  IconButton,
  Divider,
  FormControl,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
} from "@mui/material";

import { makeStyles } from '@mui/styles';

import CloseIcon from '@mui/icons-material/Close';

import EditorInput from "./EditorInput";
import QuestionComponentManager from "./QuestionComponentManager";
import ValueComponentManager from "./ValueComponentManager";
import { useQuestionnaireReaderContext } from "../questionnaire/QuestionnaireContext";

const useStyles = makeStyles(theme => ({
  referenceToggle: {
    "& .MuiToggleButton-root" : {
      paddinTop: theme.spacing(.5),
      paddingBottom: theme.spacing(0.5),
      textTransform: "none",
    },
  },
  withMultiSelect: {
    "& > .MuiListItem-root:first-child" : {
      marginTop: theme.spacing(1),
    },
    "& .MuiListItem-root": {
      paddingLeft: 0,
    },
    "& .MuiDivider-root": {
      marginLeft: 0,
    },
  }
}));

const filterOptions = createFilterOptions({
  stringify: (option) => `${option.name} ${option.text}`
});

let ConditionalValueInput = (props) => {
  let { objectKey, data, saveButtonRef, hint } = props;

  let [ values, setValues ] = useState(data[objectKey]?.value || []);
  let [ isReference, setReference ] = useState(data[objectKey] ? data[objectKey].isReference : (objectKey == 'operandA'));
  let [ tempValue, setTempValue ] = useState(''); // Holds new, non-committed values
  let [ isDuplicate, setDuplicate ] = useState(false);
  let [ variables, setVariables ] = useState();

  let questions = useQuestionnaireReaderContext();

  let path = (data?.['@path'] || props.path) + `/${objectKey}`;

  const classes = useStyles();

  let handleValue = (inputValue) => {
    if (inputValue && !isDuplicate) {
      let newValue = (inputValue || '').trim();
      setValues(oldValue => {
        var newValues = oldValue.slice();
        newValues.push(newValue);
        return newValues;
      });
    }
    tempValue && setTempValue('');
    setDuplicate(false);

    // Have to manually invoke submit with timeout to let re-rendering of adding new answer option complete
    // Cause: Calling onBlur and mutating state can cause onClick for form submit to not fire
    // Issue details: https://github.com/facebook/react/issues/4210
    if (event?.relatedTarget?.type == "submit") {
      const timer = setTimeout(() => {
        saveButtonRef?.current?.click();
      }, 500);
    }
  }

  let deleteValue = (index) => {
    setValues(oldValues => {
      let newValues = oldValues.slice();
      newValues.splice(index, 1);
      return newValues;
    });
  }

  let validateEntry = (inputValue) => {
    if (inputValue) {
      let newValue = (inputValue || '').trim();
      let isDuplicateValue = values?.some(v => v == newValue);
      setDuplicate(isDuplicateValue);
      return isDuplicateValue;
    }
    return false;
  }

  let handleChange = (event) => {
    setTempValue(event.target.value);
    validateEntry(event.target.value);
  }

  let handleValueSelected = (event) => {
    handleValue(event.target.value);
  }

  useEffect(() => {
    if (questions && !variables) {
      setVariables(questions);
    }
  }, [questions]);

  // Input for adding a new value
  let textField = (label, params) => (
    <TextField
        {...params}
        variant="standard"
        fullWidth
        value={tempValue}
        error={isDuplicate}
        label={label}
        helperText={isDuplicate ? 'Duplicated entry' : 'Press ENTER to add a new line'}
        onChange={handleChange}
        onBlur={handleValueSelected}
        inputProps={Object.assign({
          onKeyDown: (event) => {
            if (event.key == 'Enter') {
              // We need to stop the event so that it doesn't trigger a form submission
              event.preventDefault();
              event.stopPropagation();
              handleValue(event.target.value);
            }
          }
        })}
      />
  );

  return (
    <EditorInput name={objectKey} hint={hint}>

      {/* Is this a variable name or a value? */}
      <ToggleButtonGroup
        className={classes.referenceToggle}
        value={`${isReference}`}
        exclusive
        onChange={(event, newSetting) => setReference(JSON.parse(newSetting))}
      >
        <ToggleButton value="true">Question id</ToggleButton>
        <ToggleButton value="false">Value</ToggleButton>
      </ToggleButtonGroup>

      <List disablePadding dense className={classes.withMultiSelect}>
        {/* List the entered values */}
        { values?.map((value, index) => <>
          { !!index && <Divider key={`divider-${index}`} variant="inset" component="li" /> }
          <ListItem
	        key={`${value}-${index}`}
	        secondaryAction={
		      <Tooltip title="Delete entry">
	            <IconButton onClick={() => deleteValue(index)}><CloseIcon/></IconButton>
	          </Tooltip>
	        }
	      >
            <ListItemText primary={value} secondary={isReference && variables?.find(v => v.name == value)?.text} />
	      </ListItem>
        </>)}
      </List>
      {/* Display a dropdown for variable names or a simple input for values */}
      { isReference && variables ?
        <FormControl variant="standard" fullWidth>
          <Autocomplete
            multiple
            value={variables?.filter(v => values.includes(v.name)) || []}
            renderTags={() => null}
            filterOptions={filterOptions}
            onChange={(event, value) => {
              setValues(value?.map(item => item.name));
            }}
            getOptionLabel={(option) => option.text}
            options={variables}
            renderOption={(props, option) => { return !values.includes(option.name) &&
                <ListItemButton
                  value={option.name}
                  key={option.name}
                  {...props}
                >
                  <ListItemText primary={option.name} secondary={option.text} />
                </ListItemButton>
            }}
            renderInput={(params) =>
                <TextField
                  variant="standard"
                  label="Select the id of a question from this questionnaire"
                  {...params}
                />
            }
          />
        </FormControl>
        :
        textField("Enter a value")
      }

      {/* Metadata to sent to the server */}
      { values ?
        <>
          <input type='hidden' name={`${path}/jcr:primaryType`} value={'cards:ConditionalValue'} />
          { values.map(v => <input type='hidden' key={v} name={`${path}/value`} value={v} />)}
          <input type="hidden" name={`${path}/value@TypeHint`} value="String[]" />
          <input type="hidden" name={`${path}/isReference`} value={isReference || false} />
          <input type="hidden" name={`${path}/isReference@TypeHint`} value="Boolean" />
        </>
        :
        <input type='hidden' name={`${path}@Delete`} value="0" />
      }
    </EditorInput>
  )
}

ConditionalValueInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired,
  hint: PropTypes.string,
};

export default ConditionalValueInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (["conditionalValue"].includes(definition)) {
    return [ConditionalValueInput, 50];
  }
});

// View mode component
let ConditionalValue = (props) => {
  let { objectKey, data } = props;
  let [ variables, setVariables ] = useState();

  let values = data[objectKey]?.value || [];

  if (values.length == 0) {
    return null;
  }

  let isReference = data?.[objectKey]?.isReference;
  let questions = useQuestionnaireReaderContext();

  useEffect(() => {
    if (isReference && questions && !variables) {
      setVariables(questions);
    }
  }, [questions]);

  return (
    values.map(value => (
      <ListItemText
        style={{marginTop: 0}}
        key={value}
        primary={value}
        secondary={isReference && variables?.find(v => v.name == value)?.text}
      />
    ))
  );
}

ConditionalValue.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

ValueComponentManager.registerValueComponent((definition) => {
  if (["conditionalValue"].includes(definition)) {
    return [ConditionalValue, 50];
  }
});
