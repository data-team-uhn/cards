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

import React, { useState } from 'react';
import PropTypes from 'prop-types';
import { TextField, Button, withStyles } from "@material-ui/core";

import EditorInput from "./EditorInput";
import MarkdownElement from "./MarkdownElement";
import QuestionnaireStyle from '../questionnaire/QuestionnaireStyle';
import QuestionComponentManager from "../questionnaireEditor/QuestionComponentManager";

// Markdown Text Input field used by Edit dialog component
let MarkdownTextField = (props) => {
  let { objectKey, data, classes } = props;

  const [value, setValue] = useState(data[objectKey] || '');
  const [preview, setPreview] = useState(false);

  const handleChange = (event) => {
    setValue(event.target.value);
  };

  let onPreview = () => {
    setPreview(!preview);
  }

  return (
      <EditorInput name={objectKey}>
        { !preview
          ?
          <TextField name={objectKey}
                   id={objectKey}
                   required={objectKey.includes('text')}
                   value={value}
                   onChange={handleChange}
                   variant="outlined"
                   rows={4}
                   multiline
                   fullWidth
          />
          :
          <MarkdownElement text={value} />
        }
        <Button size="small" target="_blank" href="https://www.markdownguide.org/basic-syntax/" className={classes.rightAlignedButton}> Markdown help </Button>
        <Button size="small" onClick={onPreview} disabled={!value} className={classes.rightAlignedButton} > { !preview ? "Preview" : "Write" } </Button>
      </EditorInput>
  )
}

MarkdownTextField.propTypes = {
  objectKey: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired
};

const StyledMarkdownTextField = withStyles(QuestionnaireStyle)(MarkdownTextField);
export default StyledMarkdownTextField;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (definition === 'markdown') {
    return [StyledMarkdownTextField, 50];
  }
});
