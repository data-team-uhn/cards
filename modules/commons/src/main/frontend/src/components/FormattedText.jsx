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
import { Typography } from "@mui/material";
import makeStyles from '@mui/styles/makeStyles';
import MDEditor from '@uiw/react-md-editor';

const useStyles = makeStyles(theme => ({
  markdown: {
    "&.wmde-markdown" : {
      background: "transparent",
      color: "inherit",
      fontSize: "inherit",
      fontFamily: "inherit",
      "& .anchor" : {
        display: "none",
      },
    },
  }
}));

let FormattedText = (props) => {
  let { children, ...typographyProps } = props;
  const mdClasses = useStyles();

  return (
    <Typography component="div" {...typographyProps} >
      <MDEditor.Markdown classes={mdClasses} className={mdClasses.markdown} source={children} />
    </Typography>
  );
}

FormattedText.propTypes = {
  children: PropTypes.string
};

export default FormattedText;
