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

import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import withStyles from '@mui/styles/withStyles';

import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import MDEditor, { commands } from '@uiw/react-md-editor';

const infoButton = {
  name: "Markdown help button",
  keyCommand: "helpButton",
  buttonProps: { "aria-label": "Markdown help" },
  icon: ( <svg width="14" height="14" viewBox="0 0 24 22">
    <path
      fill="currentColor"
      d="M15.07,11.25L14.17,12.17C13.45,12.89 13,13.5 13,15H11V14.5C11,13.39 11.45,12.39 12.17,11.67L13.41,10.41C13.78,10.05 14,9.55 14,9C14,7.89 13.1,7 12,7A2,2 0 0,0 10,9H8A4,4 0 0,1 12,5A4,4 0 0,1 16,9C16,9.88 15.64,10.67 15.07,11.25M13,19H11V17H13M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12C22,6.47 17.5,2 12,2Z" />
    </svg> ),
  execute: () => {
    window.open("https://www.markdownguide.org/basic-syntax/");
  }
};

// Markdown Text Input field used by Edit dialog component
let MarkdownText = (props) => {
  let { onChange, height, preview, classes } = props;
  const [value, setValue] = useState(props.value || '');
  let cmd = commands.getExtraCommands();
  cmd.push(commands.divider);
  cmd.push(infoButton);

  useEffect(() => {
    setValue(props.value || '');
  }, [props.value]);

  return (
    <MDEditor className={classes.markdown} value={value} height={height} preview={preview} onChange={value => {setValue(value); onChange && onChange(value);}} extraCommands={cmd}/>
  )
}

MarkdownText.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func,
  height: PropTypes.number,
  preview: PropTypes.string
};

MarkdownText.defaultProps = {
  height: 200,
  preview: "live"
}

export default withStyles(QuestionnaireStyle)(MarkdownText);
