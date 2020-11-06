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
import {
  Grid,
  Typography
} from "@material-ui/core";

let EditorInput = (props) => {
  let { children, name } = props;
  let formatString = (key) => {
    return key.charAt(0).toUpperCase() + key.slice(1).replace( /([A-Z])/g, " $1" ).toLowerCase();
  }
  return (
    <Grid container alignItems='flex-end' spacing={2}>
      <Grid item xs={4}>
        <Typography variant="subtitle2">
          {formatString(name?.concat(':')) || ''}
        </Typography>
      </Grid>
      <Grid item xs={8}>
        {children}
      </Grid>
    </Grid>
    );
}

EditorInput.propTypes = {
  children: PropTypes.node.isRequired,
  name: PropTypes.string.isRequired
};

export default EditorInput