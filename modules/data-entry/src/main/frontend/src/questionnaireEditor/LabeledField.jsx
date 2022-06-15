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
} from "@mui/material";

export function camelCaseToWords(str) {
  return str.charAt(0).toUpperCase() + str.slice(1).replace( /([A-Z])/g, " $1" ).toLowerCase();
}

let LabeledField = (props) => {
  let {name, condensed, children } = props;

  return (
    <Grid container alignItems='flex-start' spacing={2} direction="row">
      <Grid item xs={condensed ? "auto" : 4}>
        <Typography variant="subtitle2">{camelCaseToWords(name)}:</Typography>
      </Grid>
      <Grid item xs={condensed ? "auto" : 8}>{children}</Grid>
    </Grid>
  );
}

LabeledField.propTypes = {
  name: PropTypes.string.isRequired,
  condensed: PropTypes.bool,
};

export default LabeledField;
