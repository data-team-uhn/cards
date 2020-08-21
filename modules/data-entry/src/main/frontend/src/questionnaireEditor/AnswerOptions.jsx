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

import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import uuid from 'uuid/v4';
import CloseIcon from '@material-ui/icons/Close';

let AnswerOptions = (props) => {
  let [ options, setOptions ] = useState(Object.values(props.data).filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption').slice());
  let [ newUuid, setNewUuid ] = useState([]);
  let [ newValue, setNewValue ] = useState([]);
  let [ tempValue, setTempValue ] = useState('');

  let deleteOption = (value) => {
    setOptions(oldOptions => {
      let newOptions = oldOptions.slice();
      newOptions.splice(newOptions.indexOf(value), 1);
      return newOptions;
    })
  }

  let updateInsertedOption = (index, event) => {
    setNewValue(oldValue => {
      var value = oldValue.slice();
      value[index] = event.target.value;
      return value;
    })
  }

  let deleteInsertedOption = (index, event) => {
    setNewValue(oldValue => {
      let value = oldValue.slice();
      value.splice(index, 1);
      return value;
    })
    setNewUuid(oldUuid => {
      let uuid = oldUuid.slice();
      uuid.splice(index, 1);
      return uuid;
    });
  }

  let handleInputOption = () => {
    if (!newValue.includes(tempValue)) {
      setNewUuid(oldUuid => {
        let tempUUID = oldUuid.slice();
        tempUUID.push(uuid());
        return tempUUID;
      });

      setNewValue(oldValue => {
        let value = oldValue.slice();
        value.push(tempValue);
        return value;
      });
    }

    setTempValue('');
  }

  return (
    <Grid container alignItems='flex-end' spacing={2}>
      <Grid item xs={6}>
        <Typography>Answer Options</Typography>
      </Grid>
      { options.map(value =>
        <Grid item xs={6} key={value['jcr:uuid']}>
          <input type='hidden' name={`${value['@path']}/jcr:primaryType`} value={'lfs:AnswerOption'} />
          <TextField
            name={`${value['@path']}/value`}
            defaultValue={value.value}
          />
          <IconButton onClick={() => { deleteOption(value) }}>
            <CloseIcon/>
          </IconButton>
        </Grid>
      )}
      { newUuid.map((value, index) =>
        <Grid item xs={6} key={index}>
          <input type='hidden' name={`${props.data['@path']}/${newValue[index]}/jcr:primaryType`} value={'lfs:AnswerOption'} />
          <TextField
            name={`${props.data['@path']}/${newValue[index]}/value`}
            value={newValue[index]}
            onChange={(event) => { updateInsertedOption(index, event); }}>
          </TextField>
          <IconButton onClick={(event) => { deleteInsertedOption(index, event) }}>
            <CloseIcon />
          </IconButton>
        </Grid> 
      )}
      <Grid item xs={6}>
        <TextField
          value={tempValue}
          helperText='Press ENTER to add a new line'
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
  )
}

AnswerOptions.propTypes = {
  data: PropTypes.object.isRequired
};

export default withStyles(QuestionnaireStyle)(AnswerOptions);
