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
  Grid,
  IconButton,
  FormControl,
  InputLabel,
  ListItemText,
  MenuItem,
  Select,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from "@mui/material";

import { makeStyles } from '@mui/styles';

import CloseIcon from '@mui/icons-material/Close';

import EditorInput from "./EditorInput";
import QuestionComponentManager from "./QuestionComponentManager";
import ValueComponentManager from "./ValueComponentManager";

const useStyles = makeStyles(theme => ({
  referenceToggle: {
    "& .MuiToggleButton-root" : {
      paddinTop: theme.spacing(.5),
      paddingBottom: theme.spacing(0.5),
      textTransform: "none",
    },
  },
  variableDropdown: {
    "& > .MuiInputLabel-root" : {
      maxWidth: `calc(100% - ${theme.spacing(3)})`,
	},
  },
  variableOption: {
    whiteSpace: "normal",
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
  let { objectKey, value, data, saveButtonRef, hints, onHelpClick } = props;

  let [ values, setValues ] = useState(data[objectKey]?.value || []);
  let [ isReference, setReference ] = useState(data[objectKey] ? data[objectKey].isReference : (objectKey == 'operandA'));
  let [ tempValue, setTempValue ] = useState(''); // Holds new, non-committed values
  let [ isDuplicate, setDuplicate ] = useState(false);
  let [ variables, setVariables ] = useState();
  let [ error, setError ] = useState();

  let path = (data?.['@path'] || props.path) + `/${objectKey}`;
  let parentQuestionnaire = /(\/Questionnaires\/([^.\/]+))(.)*/.exec(path)[1];

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
    if (isReference && !variables) {
      fetch(`${parentQuestionnaire}.deep.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then(loadVariableNames)
        .catch(() => {
           setError("Cannot load question identifiers");
           setVariables([]);
        })
    }
  }, [isReference]);

  let loadVariableNames = (json) => {
    let vars = [];
    findQuestions(json, vars);
    setVariables(vars);
  }

  let findQuestions = (json, result) =>  {
    Object.entries(json || {}).forEach(([k,e]) => {
      if (e?.['jcr:primaryType'] == "cards:Question") {
        result.push({name: e['@name'], text: e['text']});
      } else if (typeof(e) == 'object') {
        findQuestions(e, result);
      }
    })
  }

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
    <EditorInput name={objectKey} hasHint={Boolean(hints?.[objectKey])} onHelpClick={onHelpClick}>

      {/* Is this a variable name or a value? */}
      <ToggleButtonGroup
        className={classes.referenceToggle}
        value={`${isReference}`}
        exclusive
        onChange={(event, newSetting) => setReference(JSON.parse(newSetting))}>
      >
        <ToggleButton value="true">Question id</ToggleButton>
        <ToggleButton value="false">Value</ToggleButton>
      </ToggleButtonGroup>

      {/* List the entered values */}
      { values?.map((value, index) =>
        <Grid container
          key={`${value}-${index}`}
          direction="row"
          justifyContent="space-between"
          alignItems="stretch"
          className={classes.valueEntry}
          key={value}
        >
          <Grid item xs={9}>
            <ListItemText primary={value} secondary={isReference && variables?.find(v => v.name == value)?.text} />
          </Grid>
          <Grid item xs={3} className={classes.valueActions}>
            <Tooltip title="Delete entry">
              <IconButton onClick={() => deleteValue(index)}><CloseIcon/></IconButton>
            </Tooltip>
          </Grid>
        </Grid>
      )}

      {/* Display a dropdown for variable names or a simple input for values */}
      { isReference && !error ?
        <FormControl variant="standard" fullWidth className={classes.variableDropdown}>
          <InputLabel id={`label-${path}`}>Select the id of a question from this questionnaire</InputLabel>
          <Select
            variant="standard"
            labelId={`label-${path}`}
            id={path}
            value={tempValue}
            label="Select the id of a question from this questionnaire"
            onChange={handleValueSelected}
          >
            { variables?.filter(v => !values.includes(v.name))
                .map(v => <MenuItem value={v.name} key={`option-${v.name}`} className={classes.variableOption}>
                <ListItemText primary={v.name} secondary={v.text} />
              </MenuItem>)
            }
          </Select>
        </FormControl>
        :
        ( isReference && error ?
          <>
            <Typography color="error">{error}</Typography>
            { textField("Enter the id of a question from this questionnaire") }
          </>
          :
          textField("Enter a value")
        )
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
  hints: PropTypes.object,
  onHelpClick: PropTypes.func
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

  let parentQuestionnaire = /(\/Questionnaires\/([^.\/]+))(.)*/.exec(data['@path'])[1];

  useEffect(() => {
    if (data?.[objectKey]?.isReference && !variables) {
      fetch(`${parentQuestionnaire}.deep.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then(loadVariableNames)
        .catch(() => {
           setVariables([]);
        })
    }
  }, []);

  let loadVariableNames = (json) => {
    let vars = [];
    findQuestions(json, vars);
    setVariables(vars);
  }

  let findQuestions = (json, result) =>  {
    Object.entries(json || {}).forEach(([k,e]) => {
      if (e?.['jcr:primaryType'] == "cards:Question") {
        result.push({name: e['@name'], text: e['text']});
      } else if (typeof(e) == 'object') {
        findQuestions(e, result);
      }
    })
  }

  return (
    data?.[objectKey]?.value?.length > 0
    ? data[objectKey].value.map(value => (
      <ListItemText
        style={{marginTop: 0}}
        key={value}
        primary={value}
        secondary={data?.[objectKey]?.isReference && variables?.find(v => v.name == value)?.text}
      />
    ))
    : null
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
