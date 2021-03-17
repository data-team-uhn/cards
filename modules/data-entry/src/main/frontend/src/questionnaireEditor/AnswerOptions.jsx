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
import CloseIcon from '@material-ui/icons/Close';
import { stringToHash } from "../escape.jsx";

let AnswerOptions = (props) => {
  const { objectKey, data, path, saveButtonRef, classes } = props;
  let [ options, setOptions ] = useState(Object.values(data).filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption').slice());
  let [ deletedOptions, setDeletedOptions ] = useState([]);
  let [ tempValue, setTempValue ] = useState(''); // Holds new, non-committed answer options
  let [ isDuplicate, setIsDuplicate ] = useState(false);

  // Clear local state when data changes
  useEffect(() => {
    setOptions(Object.values(data).filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption').slice());
    setDeletedOptions([]);
    setTempValue('');
    setIsDuplicate(false);
  }, [data])

  let deleteOption = (index) => {
    setDeletedOptions(old => {
      let newDeletedOptions = old.slice();
      newDeletedOptions.push(options[index]);
      return newDeletedOptions;
    });

    setOptions(oldOptions => {
      let newOptions = oldOptions.slice();
      newOptions.splice(index, 1);
      return newOptions;
    });
  }

  let validateOption = (optionInput) => {
    if (optionInput) {
      setIsDuplicate(false);
      let inputs = (optionInput + ' ').split(/=(.+)/);
      let duplicateOption = options.find( option => option.value.trim() === inputs[0].trim() || inputs[1] && (option.label?.trim() === inputs[1].trim()));
      duplicateOption && setIsDuplicate(true);
    }
  }

  let handleInputOption = (optionInput) => {
    if (optionInput && !isDuplicate) {
      // The text entered on each line should be split
      // by the first occurrence of the separator = if the separator exists
      // e.g. F=Female as <value> = <label>
      let inputs = optionInput.split(/=(.+)/);
      let newOption = {};
      newOption.value = inputs[0].trim();
      newOption["@path"] = path + "/" + stringToHash(newOption.value);
      newOption.label = inputs[1] ? inputs[1].trim() : "";

      setOptions(oldValue => {
        var value = oldValue.slice();
        value.push(newOption);
        return value;
      });
    }

    tempValue && setTempValue('');
    setIsDuplicate(false);

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
      { deletedOptions.map((value, index) =>
        <input type='hidden' name={`${value['@path']}@Delete`} value="0" key={value['@path']} />
      )}
      { options.map((value, index) =>
        <React.Fragment key={value.value}>
          <input type='hidden' name={`${value['@path']}/jcr:primaryType`} value={'lfs:AnswerOption'} />
          <input type='hidden' name={`${value['@path']}/label`} value={value.label} />
          <input type='hidden' name={`${value['@path']}/value`} value={value.value} />
          <TextField
            InputProps={{
              readOnly: true,
            }}
            variant="filled"
            className={classes.answerOptionInput}
            defaultValue={value.label? value.value + " = " + value.label : value.value}
            multiline
            />
          <IconButton onClick={() => { deleteOption(index); }} className={classes.answerOptionDeleteButton}>
            <CloseIcon/>
          </IconButton>
        </React.Fragment>
      )}
      <TextField
        fullWidth
        value={tempValue}
        error={isDuplicate}
        helperText={isDuplicate ? 'duplicated value or label' : [<span key="helper-value">value OR value=label (e.g. F=Female)</span>,<br key="br"/>,<span key="helper-newline">Press ENTER to add a new line</span>]}
        onChange={(event) => { setTempValue(event.target.value); validateOption(event.target.value); }}
        onBlur={(event) => { handleInputOption(event.target.value); }}
        inputProps={Object.assign({
          onKeyDown: (event) => {
            if (event.key == 'Enter') {
              // We need to stop the event so that it doesn't trigger a form submission
              event.preventDefault();
              event.stopPropagation();
              handleInputOption(event.target.value);
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
