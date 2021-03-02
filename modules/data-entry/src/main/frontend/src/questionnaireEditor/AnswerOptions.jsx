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

import React, { useEffect, useState } from "react";
import PropTypes from "prop-types";
import {
  Grid,
  IconButton,
  TextField,
  Typography,
  withStyles
} from "@material-ui/core";

import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import EditorInput from "./EditorInput";
import QuestionComponentManager from "./QuestionComponentManager";
import { v4 as uuidv4 } from 'uuid';
import CloseIcon from '@material-ui/icons/Close';

let AnswerOptions = (props) => {
  const { objectKey, data, path, saveButtonRef, classes } = props;
  let [ options, setOptions ] = useState(Object.values(data).filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption').slice());
  let [ newUUID, setNewUUID ] = useState([]);
  let [ newValue, setNewValue ] = useState([]);
  let [ labels, setLabels ] = useState([]);
  let [ deletedOptions, setDeletedOptions ] = useState([]);
  let [ tempValue, setTempValue ] = useState(''); // Holds new, non-committed answer options

  // Clear local state when data changes
  useEffect(() => {
    setOptions(Object.values(data).filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption').slice());
    setNewUUID([]);
    setNewValue([]);
    setLabels([]);
    setDeletedOptions([]);
    setTempValue('');
  }, [data])

  let deleteOption = (value) => {
    setOptions(oldOptions => {
      let newOptions = oldOptions.slice();
      newOptions.splice(newOptions.indexOf(value), 1);
      return newOptions;
    })

    setDeletedOptions(old => {
      let newDeletedOptions = old.slice();
      newDeletedOptions.push(value);
      return newDeletedOptions;
    })
  }

  let updateInsertedOption = (index, event) => {
    let inputs = event.target.value.split("=");
    setNewValue(oldValue => {
      var value = oldValue.slice();
      value[index] = inputs[0].trim();
      return value;
    });
    setLabels(oldValue => {
      let value = oldValue.slice();
      value[index] = inputs[1] ? inputs[1].trim() : "";
      return value;
    });
  }

  let deleteInsertedOption = (index, event) => {
    setNewValue(oldValue => {
      let value = oldValue.slice();
      value.splice(index, 1);
      return value;
    });
    setLabels(oldValue => {
      let value = oldValue.slice();
      value.splice(index, 1);
      return value;
    });
    setNewUUID(oldUuid => {
      let uuid = oldUuid.slice();
      uuid.splice(index, 1);
      return uuid;
    });
  }

  let handleInputOption = (event) => {
    if (tempValue && !newValue.includes(tempValue)) {
      setNewUUID(oldUuid => {
        let tempUUID = oldUuid.slice();
        tempUUID.push(uuidv4());
        return tempUUID;
      });

      // The text entered on each line should be split
      // by the first occurrence of the separator = if the separator exists
      // e.g. F=Female as <value> = <label>
      let inputs = tempValue.split("=");

      setNewValue(oldValue => {
        let value = oldValue.slice();
        value.push(inputs[0].trim());
        return value;
      });
      setLabels(oldValue => {
        let value = oldValue.slice();
        value.push(inputs[1] ? inputs[1].trim() : "");
        return value;
      });
    }

    tempValue && setTempValue('');

    // Have to manually invoke submit with timeout to let re-rendering of adding new answer option complete
    // Cause: Calling onBlur and mutating state can cause onClick for form submit to not fire
    // Issue details: https://github.com/facebook/react/issues/4210
    if (event?.relatedTarget?.type == "submit") {
      const timer = setTimeout(() => {
        saveButtonRef?.current?.click();
      }, 500);
    }
  }

  return (
    <EditorInput name={objectKey}>
      { options.map((value, index) =>
        <React.Fragment key={value['@path']}>
          <input type='hidden' name={`${value['@path']}/jcr:primaryType`} value={'lfs:AnswerOption'} />
          <TextField
            className={classes.answerOptionInput}
            name={`${value['@path']}/value`}
            defaultValue={value.value + " = " + value.label}
            multiline
            />
          <IconButton onClick={() => { deleteOption(value) }} className={classes.answerOptionDeleteButton}>
            <CloseIcon/>
          </IconButton>
        </React.Fragment>
      )}
      { newUUID.map((value, index) =>
        <React.Fragment key={value}>
          <input type='hidden' name={`${path}/${newValue[index]}/jcr:primaryType`} value={'lfs:AnswerOption'} />
          <input type='hidden' name={`${path}/${newValue[index]}/label`} value={labels[index]} />
          <input type='hidden' name={`${path}/${newValue[index]}/value`} value={newValue[index]} />
          <TextField
            className={classes.answerOptionInput}
            value={newValue[index] + " = " + labels[index]}
            onChange={(event) => { updateInsertedOption(index, event); }}
            multiline
            />
          <IconButton onClick={(event) => { deleteInsertedOption(index, event) }} className={classes.answerOptionDeleteButton}>
            <CloseIcon />
          </IconButton>
        </React.Fragment>
      )}
      { deletedOptions.map((value, index) =>
        <input type='hidden' name={`${value['@path']}@Delete`} value="0" key={value['@path']} />
      )}
      <TextField
        fullWidth
        value={tempValue}
        helperText='value OR value=label (e.g. F=Female)'
        onChange={(event) => { setTempValue(event.target.value); }}
        onBlur={(event) => { handleInputOption(event); }}
        inputProps={Object.assign({
          onKeyDown: (event) => {
            if (event.key == 'Enter') {
              // We need to stop the event so that it doesn't trigger a form submission
              event.preventDefault();
              event.stopPropagation();
              handleInputOption();
            }
          }
        })}
        multiline
        />
    </EditorInput>
  )
}

AnswerOptions.propTypes = {
  data: PropTypes.object.isRequired
};

var StyledAnswerOptions = withStyles(QuestionnaireStyle)(AnswerOptions);
export default StyledAnswerOptions;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (definition === "options") {
    return [StyledAnswerOptions, 50];
  }
});
