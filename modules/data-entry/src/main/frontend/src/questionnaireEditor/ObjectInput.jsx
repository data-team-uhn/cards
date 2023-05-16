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
import Fields from "./Fields"
import PropTypes from "prop-types";
import {
  MenuItem,
  Select,
  Typography
} from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import EditorInput from "./EditorInput";
import BooleanInput from "./BooleanInput";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle";
import QuestionComponentManager from "../questionnaireEditor/QuestionComponentManager";

// Object Input field used by Edit dialog component

let ObjectInput = (props) => {
  let { objectKey, value, data, hint, hints, onChange, ...rest } = props;

  let options = Object.keys(value || {});
  let isBoolean = (options.length == 2 && options.includes("true") && options.includes("false"));

  const defaultValue = data[objectKey] || (isBoolean ? 'false' : (options[0] || ''));
  let [ selectedValue, setSelectedValue ] = useState(defaultValue);

  return (
    <>
    { isBoolean ?
      <BooleanInput
        objectKey={objectKey}
        data={data}
        hint={hint}
        onChange={(value) => {
          setSelectedValue(`${value}`);
          onChange?.(`${value}`);
        }}
      />
      :
      <EditorInput name={objectKey} hint={hint}>
        <Select
          variant="standard"
          id={objectKey}
          name={objectKey}
          defaultValue={defaultValue}
          onChange={(event) => {
            setSelectedValue(event.target.value);
            onChange?.(event.target.value);
           }}>
          { typeof(value) === 'object' && Object.keys(value).map((name, val) =>
            <MenuItem key={val} name={name} id={name} value={name}>
              <Typography>{name}</Typography>
            </MenuItem>
          )}
        </Select>
      </EditorInput>
    }
    { typeof(value) === 'object' && selectedValue != '' && typeof (value[selectedValue]) === 'object' ?
        <Fields data={data} JSON={value[selectedValue]} edit={true} hints={hints} {...rest} />
      :
      (selectedValue != '') && <Typography color="secondary" variant="subtitle2">Unsupported: {selectedValue}</Typography>
    }
    </>
  )
}

ObjectInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  value: PropTypes.object.isRequired,
  data: PropTypes.object.isRequired,
  hint: PropTypes.string,
  hints: PropTypes.object,
};

const StyledObjectInput = withStyles(QuestionnaireStyle)(ObjectInput);
export default StyledObjectInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (typeof(definition) === 'object') {
    return [StyledObjectInput, 50];
  }
});
