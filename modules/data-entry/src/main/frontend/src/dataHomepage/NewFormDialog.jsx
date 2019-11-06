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
import { withRouter } from "react-router-dom";
import uuid from "uuid/v4";

import { Button, Dialog, DialogTitle, Link, List, ListItem, withStyles } from "@material-ui/core";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

function NewFormDialog(props) {
  const { children, classes, presetPath } = props;
  const [ open, setOpen ] = useState(false);
  const [ questionnaires, setQuestionnaires ] = useState([]);

  let openForm = () => {
    // Make a POST request to the form
    // Then take the response and... something something something
    setOpen(true);
    
    const URL = "/Forms/" + uuid();
    /*const request_data = {
      'jcr:primaryType': 'lfs:Form',
      'questionnaire': presetPath,
      'questionnaire@TypeHint': 'Reference'
    }*/
    var request_data = new FormData();
    request_data.append('jcr:primaryType', 'lfs:Form');
    request_data.append('questionnaire', presetPath);
    request_data.append('questionnaire@TypeHint', 'Reference');
    var xhr = new XMLHttpRequest();
    xhr.open('POST', URL, true);
    xhr.onreadystatechange = function () {
      if(xhr.readyState === XMLHttpRequest.DONE && xhr.status === 201) {
        // Redirect the user to the new uuid
        console.log(URL);
        props.history.push(URL);
      }
    };
    xhr.send(request_data);
  }

  let openDialog = () => {
    // Determine what questionnaires are available
    setOpen(true);
    if (questionnaires.length === 0) {
      // Send a fetch request to determine the questionnaires available
      fetch('/query?query=' + encodeURIComponent('select * from [lfs:Questionnaire]'))
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {setQuestionnaires(json)});
    }
  }

  return (
    <React.Fragment>
      { /* Only create a dialog if we need to allow the user to choose from multiple questionnaires */
      presetPath ? "" : <Dialog open={open} onClose={() => { setOpen(false); }}>
        <DialogTitle id="new-form-title">
          Select questionnaire
        </DialogTitle>
        {questionnaires ? 
          <List>
            questionnaires.map((questionnaire) => {
              <ListItemText
                primary={questionnaire["title"]}
                >
              </ListItemText>
            })
          </List>
          :
          ""
        }
      </Dialog>}
      {presetPath ?
        <Button variant="contained" color="primary" onClick={openForm} className={classes.newFormButton}>
          { children }
        </Button>
        :
        <Button variant="contained" color="primary" onClick={openDialog} className={classes.newFormButton}>
          { children }
        </Button>
      }
    </React.Fragment>
  )
}

export default withStyles(QuestionnaireStyle)(withRouter(NewFormDialog));