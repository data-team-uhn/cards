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

import { Dialog, DialogTitle, List, ListItem } from "@material-ui/core";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

export default function NewFormDialog(props) {
  const { presetForm } = props;
  const [ open, setOpen ] = useState(false);
  const [ questionnaires, setQuestionnaires ] = useState([]);

  let openForm = () => {
    // Make a POST request to the form
    // Then take the response and... something something something
    setOpen(true);


  }

  return (
    <React.Fragment>
      { /* Only create a dialog if we need to allow the user to choose from multiple questionnaires */
      presetForm || <Dialog open={open} onClose={() => { setOpen(false); }}>
        <DialogTitle id="new-form-title">
          Select questionnaire
        </DialogTitle>
        <List>
          {questionnaires.map((questionnaire) => {

          })}
        </List>
      </Dialog>}
      {presetForm ?
        <Button variant="outlined" color="primary" onClick={openForm}>
          New Form
        </Button>
      :
        <Link href={`/content.html/Forms?questionnaire=${presetForm}`}>
        </Link>
      }
    </React.Fragment>
  )
}

export default withStyles(QuestionnaireStyle)(NewFormDialog);