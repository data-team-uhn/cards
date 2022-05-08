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
import PropTypes from "prop-types";
import {
  Grid,
  IconButton,
  TextField,
  Tooltip,
  makeStyles
} from "@material-ui/core";

import ToggleButton from '@material-ui/lab/ToggleButton';
import ToggleButtonGroup from '@material-ui/lab/ToggleButtonGroup';

import EditorInput from "./EditorInput";
import QuestionComponentManager from "./QuestionComponentManager";
import ValueComponentManager from "./ValueComponentManager";
import CloseIcon from '@material-ui/icons/Close';

const useStyles = makeStyles(theme => ({
  referenceToggle: {
    "& .MuiToggleButton-root" : {
      paddinTop: theme.spacing(.5),
      paddingBottom: theme.spacing(0.5),
      textTransform: "none",
    },
  },
  valueEntry: {
    border: "1px solid " + theme.palette.divider,
    borderRadius: theme.spacing(.5, 3, 3, .5),
    margin: theme.spacing(1, 0),
    "& > .MuiGrid-item" : {
      display: "flex",
      paddingLeft: theme.spacing(1.5),
      alignItems: "center",
    },
  },
  valueActions: {
    justifyContent: "flex-end",
  },
}));

let ConditionalValueInput = (props) => {
  let { objectKey, value, data, saveButtonRef } = props;

  let [ values, setValues ] = useState(data[objectKey]?.value || []);
  let [ isReference, setReference ] = useState(data[objectKey] ? data[objectKey].isReference : (objectKey == 'operandA'));
  let [ tempValue, setTempValue ] = useState(''); // Holds new, non-committed values
  let [ isDuplicate, setDuplicate ] = useState(false);

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

  return (
    <EditorInput name={objectKey}>

      {/* Is this a variable name or a value? */}
      <ToggleButtonGroup
        className={classes.referenceToggle}
        value={`${isReference}`}
        exclusive
        onChange={(event, newSetting) => setReference(JSON.parse(newSetting))}>
      >
        <ToggleButton value="true">Variable name</ToggleButton>
        <ToggleButton value="false">Value</ToggleButton>
      </ToggleButtonGroup>

      {/* List the entered values */}
      { values?.map((value, index) =>
        <Grid container
          direction="row"
          justify="space-between"
          alignItems="stretch"
          className={classes.valueEntry}
          key={value}
        >
          <Grid item xs={9}>{value}</Grid>
          <Grid item xs={3} className={classes.valueActions}>
            <Tooltip title="Delete entry">
              <IconButton onClick={() => deleteValue(index)}><CloseIcon/></IconButton>
            </Tooltip>
          </Grid>
        </Grid>
      )}

      {/* Input for adding a new value */}
      {/* To do: if "variable" is selected, render a dropdown or autocomplete with available variable names */}
      <TextField
        fullWidth
        value={tempValue}
        error={isDuplicate}
        label={isReference ? "Enter the name of a variable from this questionnaire" : "Enter a value"}
        helperText={isDuplicate ? 'Duplicated entry' : 'Press ENTER to add a new line'}
        onChange={(event) => { setTempValue(event.target.value); validateEntry(event.target.value); }}
        onBlur={(event) => { handleValue(event.target.value); }}
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
   return (
     data?.[objectKey]?.value?.length > 0 ? data[objectKey].value.map( v => <div>{v} {data?.[objectKey]?.isReference ? " (variable name)" : ""}</div>) : null
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
