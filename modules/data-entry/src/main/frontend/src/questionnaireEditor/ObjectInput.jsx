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
  Grid,
  MenuItem,
  Select,
  Typography,
  withStyles
} from "@material-ui/core";

import EditorInput from "./EditorInput";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle";
import QuestionComponentManager from "../questionnaireEditor/QuestionComponentManager";

// Object Input field used by Edit dialog component

let ObjectInput = (props) => {
  let { objectKey, value, data, isMatrix, onChange, ...rest } = props;
  const defaultValue = data[objectKey] || (Object.keys(value || {})[0] || '');
  let [ selectedValue, setSelectedValue ] = useState(defaultValue);

  return (
    <>
    <EditorInput name={objectKey}>
      <Select
        id={objectKey}
        name={objectKey}
        defaultValue={defaultValue}
        onChange={(event) => {
          setSelectedValue(event.target.value);
          onChange && onChange(event.target.value);
        }}>
        { typeof(value) === 'object' && Object.keys(value).map((name, val) =>
          <MenuItem key={val} name={name} id={name} value={name}>
            <Typography>{name}</Typography>
          </MenuItem>
        )}
      </Select>
    </EditorInput>
    { typeof(value) === 'object' && selectedValue != '' && typeof (value[selectedValue]) === 'object' ?
        <Fields data={data} JSON={value[selectedValue]} isMatrix={isMatrix || selectedValue === "matrix"} edit={true} {...rest} />
      :
      (selectedValue != '') && <Typography color="secondary" variant="subtitle2">Unsupported: {selectedValue}</Typography>
    }
    </>
  )
}

ObjectInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  value: PropTypes.object.isRequired,
  data: PropTypes.object.isRequired
};

const StyledObjectInput = withStyles(QuestionnaireStyle)(ObjectInput);
export default StyledObjectInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (typeof(definition) === 'object') {
    return [StyledObjectInput, 50];
  }
});
