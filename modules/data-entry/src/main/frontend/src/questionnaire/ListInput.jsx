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
import { Chip, MenuItem, Select, Typography, withStyles } from "@material-ui/core";

import QuestionnaireStyle from './QuestionnaireStyle';

let ListInput = (props) => {
  let { objectKey, data } = props;
  const [ value, setValue ] = React.useState((data[objectKey] && data[objectKey].split(',')) || []);
  const [ options, setOptions ] = React.useState(['Any','x', 'y', 'z'])
  
  const handleChange = (event) => {
    if (objectKey.includes('subjectTypes') && event.target.value.includes('Any') && event.target.value.length > 1) {
      setValue(event.target.value.splice(event.target.value.indexOf('Any', 1)));
    } else {
      setValue(event.target.value);
    }
  };

  if (objectKey.includes('subjectTypes') && value.length === 0) {
    setValue(['Any']);
  }

  return (
    <Select
      id={objectKey}
      name={objectKey}
      multiple
      value={value}
      defaultValue={data[objectKey] && data[objectKey].split(',')}
      onChange={handleChange}
      renderValue={(selected) => (
        <div>
          {selected.map((value) => (
            <Chip key={value} label={value}/>
          ))}
        </div>
      )}
    >
    {options.map((name) => (
      <MenuItem key={name} value={name}>
        <Typography>{name}</Typography>
      </MenuItem>
    ))}
  </Select>
  )
}

ListInput.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

export default withStyles(QuestionnaireStyle)(ListInput);
  
