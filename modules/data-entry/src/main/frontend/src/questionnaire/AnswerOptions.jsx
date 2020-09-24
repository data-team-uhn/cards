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
  Typography,
  withStyles
} from "@material-ui/core";

import QuestionnaireStyle from "./QuestionnaireStyle";
import uuid from "uuid/v4";
import CloseIcon from '@material-ui/icons/Close';

let AnswerOptions = (props) => {
  let [ options, setOptions ] = useState(Object.values(props.data).filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption').slice());
  let [ newUuid, setNewUuid ] = useState([]);
  let [ newValue, setNewValue ] = useState([]);
  let [ tempValue, setTempValue ] = useState('');
  let [ insertedOptions, setInsertedOptions ] = useState([]);

  let deleteOption = (value) => {
    let updatedAnswerChoices = options.slice();
    delete updatedAnswerChoices[value];
    setOptions(updatedAnswerChoices);
  }

  let updateInsertedOption = (index, event) => {
    let updatedNewValue = newValue.slice();
    updatedNewValue[index] = event.target.value;
    setNewValue(updatedNewValue);
  }

  let deleteInsertedOption = (index, event) => {
    let updatedNewValue = newValue.slice();
    updatedNewValue.splice(updatedNewValue.indexOf(event.target.value), 1);
    setNewValue(updatedNewValue);
  }
  let insertedOption = (index) => {
    return (<Grid item xs={6}>
      <input type="hidden" name={`${props.data['@path']}/${newValue[index]}/jcr:primaryType`} value={'lfs:AnswerOption'} />
      <input type="hidden" name={`${props.data['@path']}/${newValue[index]}/jcr:uuid`} value={newUuid[index]} />
      <TextField
        name={`${props.data['@path']}/${newValue[index]}/value`}
        value={newValue[index]}
        onChange={(event) => { updateInsertedOption(index, event); }}>
      </TextField>
      <IconButton onClick={ (event) => { deleteInsertedOption(index, event) }}>
        <CloseIcon/>
      </IconButton>
    </Grid>)
  }

  let handleInputOption = () => {
    console.log(newUuid, newValue);

    let Uuid = newUuid.slice();
    Uuid.push(uuid());
    let value = newValue.slice();
    value.push(tempValue);

    setNewUuid(Uuid);
    setNewValue(value);

    let index = newValue.indexOf(tempValue);

    let newInsertedOptions = insertedOptions.slice();
    newInsertedOptions.push(insertedOption(index));
    setInsertedOptions(newInsertedOptions);

    setTempValue('');
  }
  return (
    <div>
      <Grid container alignItems="flex-end" spacing={2}>
        { options.map(value => 
          <Grid item xs={6}>
            <input type="hidden" name={`${value['@path']}/jcr:primaryType`} value={'lfs:AnswerOption'} />
            <input type="hidden" name={`${value['@path']}/jcr:uuid`} value={value['jcr:uuid']} />
            <TextField
              name={`${value['@path']}/value`}
              defaultValue={value.value}
            />
            <IconButton onClick={ () => { deleteOption(value) }}>
              <CloseIcon/>
            </IconButton>
          </Grid>
        )}
        { insertedOptions }
        <Grid item xs={6}>
          <TextField
            value={tempValue}
            helperText="Press ENTER to add a new line"
            onChange={(event) => { setTempValue(event.target.value); }}
            inputProps={Object.assign({
              onKeyDown: (event) => {
                if (event.key == 'Enter') {
                  // We need to stop the event so that it doesn't trigger a form submission
                  event.preventDefault();
                  event.stopPropagation();
                  handleInputOption();
                }
              }
            })}>
          </TextField>
        </Grid>
      </Grid>
    </div>
  )
}

AnswerOptions.propTypes = {
  data: PropTypes.object.isRequired
};
  
export default withStyles(QuestionnaireStyle)(AnswerOptions);