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

import React from 'react';
import PropTypes from 'prop-types';
import { Chip, Input, MenuItem, Select, Typography, withStyles } from "@material-ui/core";

import EditorInput from "./EditorInput";
import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import QuestionComponentManager from "./QuestionComponentManager";

let VocabularyInput = (props) => {
  let { objectKey, data } = props;
  let [ value, setValue ] = React.useState(Array.isArray(data[objectKey]) ? data[objectKey] : data[objectKey] ? [data[objectKey]] : []);
  const [ options, setOptions ] = React.useState([]);
  const requiredSubjectTypes = React.useState(objectKey.includes('requiredSubjectTypes'));

  if (requiredSubjectTypes && options.length === 0) {
    fetch('/query?query=' + encodeURIComponent(`select * from [lfs:Vocabulary] as n WHERE n.'jcr:primaryType'='lfs:Vocabulary' order by n.'lfs:Vocabulary'`))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let vocabularies = Array.from(json["rows"]); setOptions(vocabularies);
        let updatedValues = [];
        for (let option in vocabularies) {
          for (let val in value) {
            if (value[val].name === vocabularies[option].name) {
              updatedValues.push(vocabularies[option]);
            }
          }
        }
        setValue(updatedValues);
      })
      .catch(handleError);
  }

  let handleError = () => {
    console.log('error');
  }

  const handleChange = (event) => {
    setValue(event.target.value);
  };

  return (
    <EditorInput name={objectKey}>
      <input type="hidden" name={objectKey + "@TypeHint"} value={"Reference"} />
      {
        // Maps each selected object to a reference type for submitting
        value.map((typeObject) => <input type="hidden" name={objectKey} value={typeObject['jcr:uuid']} key={typeObject['jcr:uuid']} />)
      }
      {
        // Delete the current values within this list if nothing is selected
        value.length == 0 && <input type="hidden" name={objectKey + "@Delete"} value="" />
      }
      <Select
        id={objectKey}
        multiple
        value={value}
        onChange={handleChange}
        input={requiredSubjectTypes ? <Input id={objectKey} /> : null}
        renderValue={(value) => (
          <div>
            {value.map((val) => (
              <Chip key={val['jcr:uuid']} label={val['name']}/>
            ))}
          </div>
        )}
      >
      {options.map((name) => (
        <MenuItem key={name['jcr:uuid']} value={name}>
          <Typography>{name['name']}</Typography>
        </MenuItem>
      ))}
    </Select>
  </EditorInput>
  )
}

VocabularyInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

var StyledVocabularyInput = withStyles(QuestionnaireStyle)(VocabularyInput);
export default StyledVocabularyInput;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (["vocabularylist"].includes(definition)) {
    return [StyledVocabularyInput, 50];
  }
});

