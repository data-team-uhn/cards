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
import makeStyles from '@mui/styles/makeStyles';
import PropTypes from 'prop-types';
import {
  Grid,
  Typography
} from "@mui/material";

export function formatIdentifier(key) {
  return key.charAt(0).toUpperCase() + key.slice(1).replace( /([A-Z])/g, " $1" ).toLowerCase();
}

let EditorInput = (props) => {
  let { children, name } = props;

  const classes = makeStyles((theme) => ({
    labelContainer: {
      /* Match the input padding so the text of the label would appear aligned with the text of the input */
      /* To do: switch to a vertical layout in the future to avoid most alignment issues  */
      paddingTop: theme.spacing(1.75) + " !important",
    },
  }))();

  return (
  <Grid item>
    <Grid container alignItems="flex-start" spacing={2}>
      <Grid item xs={4} className={classes.labelContainer}>
        <Typography variant="subtitle2">
          {formatIdentifier(name?.concat(':')) || ''}
        </Typography>
      </Grid>
      <Grid item xs={8}>
        {children}
      </Grid>
    </Grid>
  </Grid>
  );
}

EditorInput.propTypes = {
  children: PropTypes.node.isRequired,
  name: PropTypes.string.isRequired
};

export default EditorInput