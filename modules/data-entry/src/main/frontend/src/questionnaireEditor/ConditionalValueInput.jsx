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

import {
  Autocomplete,
  FormControlLabel,
  ListItemText,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
} from "@mui/material";

import { makeStyles } from '@mui/styles';

import EditorInput from "./EditorInput";
import QuestionComponentManager from "./QuestionComponentManager";
import ValueComponentManager from "./ValueComponentManager";
import QuestionnaireAutocomplete from "../questionnaire/QuestionnaireAutocomplete";
import { useQuestionnaireReaderContext } from "../questionnaire/QuestionnaireContext";

const useStyles = makeStyles(theme => ({
  referenceToggle: {
    marginBottom: theme.spacing(2),
    "& .MuiToggleButton-root" : {
      paddinTop: theme.spacing(.5),
      paddingBottom: theme.spacing(0.5),
      textTransform: "none",
    },
  },
}));

let ConditionalValueInput = (props) => {
  let { objectKey, data, saveButtonRef, hint } = props;

  let [ values, setValues ] = useState(data[objectKey]?.value || []);
  let [ isReference, setReference ] = useState(data[objectKey] ? data[objectKey].isReference : (objectKey == 'operandA'));
  let [ requireAll, setRequireAll ] = useState(data[objectKey]?.requireAll);
  let [ valueExists, setValueExists ] = useState();

  let variables = useQuestionnaireReaderContext();

  let path = (data?.['@path'] || props.path) + `/${objectKey}`;

  const classes = useStyles();

  const checkValueExists = (inputValue) => {
    let isDuplicate = !!(values?.some(v => v == inputValue?.trim()));
    setValueExists(isDuplicate);
    return isDuplicate;
  }

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

      { isReference && variables ?
        <QuestionnaireAutocomplete
          entities={variables}
          selection={values}
          onSelectionChanged={setValues}
          getOptionValue={option => option.name}
        />
        :
        <Autocomplete
          multiple
          freeSolo
          autoSelect
          options={[]}
          value={values}
          onChange={(event, value) => setValues(value)}
          onInputChange={(event, value) => checkValueExists(value)}
          renderInput={(params) => (
            <TextField
              {...params}
              variant="standard"
              placeholder="Enter a value"
              error={valueExists}
              helperText={valueExists ? "This value has already been added" : "Press ENTER to add the value"}
              inputProps={{
                ...params.inputProps,
                onKeyDown: (event) => {
                  if (event.key == 'Enter') {
                    if (checkValueExists(event.target.value)) {
                      event.preventDefault();
                      event.stopPropagation();
                    }
                  }
                },
                onBlur: (event) => {
                  if (checkValueExists(event.target.value)) {
                    event.preventDefault();
                    event.stopPropagation();
                  }
                  params.inputProps?.onBlur?.(event);
                }
              }}
            />
          )}
        />
      }

      <FormControlLabel control={
        <Switch
          color="secondary"
          onChange={event => setRequireAll(event.target.checked)}
          checked={requireAll}
        />}
        label="Require all"
      />
      <input type="hidden" name={`${path}/requireAll`} value={requireAll || false} />
      <input type="hidden" name={`${path}/requireAll@TypeHint`} value="Boolean" />

      {/* Metadata to sent to the server */}
      { !!(values?.length) ?
        <>
          <input type='hidden' name={`${path}/jcr:primaryType`} value={'cards:ConditionalValue'} />
          { values.map(v => <input type='hidden' key={v} name={`${path}/value`} value={v} />) }
          <input type="hidden" name={`${path}/value@TypeHint`} value="String[]" />
          <input type="hidden" name={`${path}/isReference`} value={isReference || false} />
          <input type="hidden" name={`${path}/isReference@TypeHint`} value="Boolean" />
        </>
        :
        <input type='hidden' name={`${path}/value@Delete`} value="0" />
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

  let values = data[objectKey]?.value || [];

  if (values.length == 0) {
    return null;
  }

  let isReference = data?.[objectKey]?.isReference;
  let variables = useQuestionnaireReaderContext();

  return (
    values.map(value => (
      <ListItemText
        style={{marginTop: 0}}
        key={value}
        primary={isReference ? variables?.find(v => v.name == value)?.text : value}
        secondary={isReference && value}
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
