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

import React, {useState} from "react";
import PropTypes from "prop-types";

import { Button, Collapse, TextField, Tooltip, withStyles } from "@material-ui/core";
import AddIcon from "@material-ui/icons/Add";
import UnfoldMore from "@material-ui/icons/UnfoldMore";
import UnfoldLess from "@material-ui/icons/UnfoldLess";

import QuestionnaireStyle from "./QuestionnaireStyle";

function Comment (props) {
  const { answerPath, existingAnswer, classes, onChangeComment } = {...props};
  let [ comment, setComment ] = useState((existingAnswer?.[1]?.comment));
  let [ visible, setVisible ] = useState(Boolean(comment));

  let changeComment = (event) => {
    onChangeComment && onChangeComment(event.target.value);
    setComment(event.target.value);
  }

  const commentIsEmpty = comment == null || comment == "";

  return (<React.Fragment>
    <div className = {classes.showCommentsContainer}>
      <Tooltip
        title = {visible ? "Hide notes" : (commentIsEmpty ? "Add notes" : "Show notes")}
        >
        <Button
          color = "default"
          className = {classes.showCommentsButton}
          onClick = {() => {
            setVisible(!visible);
          }}
          startIcon = {visible ?
            <UnfoldLess fontSize="small" />
            : (commentIsEmpty ? <AddIcon fontSize="small" /> : <UnfoldMore fontSize="small" />)
          }
          disableFocusRipple
          >
          Notes
        </Button>
      </Tooltip>
    </div>
    <Collapse
      in={visible}
      >
      <TextField
        value = {comment}
        onChange = {changeComment}
        variant = "outlined"
        multiline
        rows = "4"
        className = {classes.commentSection}
        InputProps = {{
          className: classes.textField
        }}
        placeholder = "Please place any additional comments here."
        />
    </Collapse>
    {commentIsEmpty ?
      <input type="hidden" name={`${answerPath}/comment@Delete`} value="0" />
      : <input type="hidden" name={`${answerPath}/comment`} value={comment} />}
  </React.Fragment>);
}

export default withStyles(QuestionnaireStyle)(Comment);