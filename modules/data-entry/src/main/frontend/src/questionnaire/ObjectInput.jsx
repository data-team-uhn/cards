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

import React, { useState, useRef } from "react";
import { fields } from "./EditDialog"
import PropTypes from "prop-types";
import {
  MenuItem,
  Select,
  Typography,
  withStyles
} from "@material-ui/core";

import QuestionnaireStyle from "./QuestionnaireStyle";

let ObjectInput = (props) => {
  let { key, value, data } = props;
  let [ selectedValue, setSelectedValue] = useState(data[key] || '');

  return (
    <div>
      <Grid item xs={6}>
          <Select id={key} name={key} defaultValue={data[key] || ''} onChange={(event) => { setSelectedValue(event.target.value); }}>
          { typeof(value) === "object" && Object.keys(value).map((name, val) => 
            <MenuItem name={name} id={name} value={name}>
              <Typography>{name}</Typography>
            </MenuItem>
          )}
        </Select>
      </Grid>
      { typeof(value) === "object" && selectedValue != '' && fields(data, value[selectedValue]) }
    </div>
  )
}

ObjectInput.propTypes = {
  key: PropTypes.string.isRequired,
  value: PropTypes.object.isRequired,
  data: PropTypes.object.isRequired,
};
  
export default withStyles(QuestionnaireStyle)(ObjectInput);