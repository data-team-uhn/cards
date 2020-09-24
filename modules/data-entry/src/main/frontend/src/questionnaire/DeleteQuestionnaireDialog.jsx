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

import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography, withStyles } from "@material-ui/core";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

let DeleteQuestionnaireDialog = (props) => {
    let [ forms, setForms] = useState(0);
    let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);
    let [ saveInProgress, setSaveInProgress ] = useState();
    
    let deleteQuestionnaireWarningMessage = () => {
      let formsExist = forms && forms > 0;
      if (!formsExist) {
        // Find all forms with that questionnaire uuid
        fetch('/query?query=' + encodeURIComponent(`select * from [lfs:Form] as n WHERE n.'questionnaire'='${props.data['jcr:uuid']}'`))
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then((json) => { parseResult(json); });
      }
      // Count the number of returned forms
      let parseResult = (forms) => {
        let filteredForms = Object.values(forms['rows']).length;
        setForms(filteredForms);
        formsExist = forms && forms > 0;
     }
      return formsExist
        ? `There are ${forms} forms filled out for this questionnaire. Are you sure you wish to proceed?`
        : "Are you sure you wish to proceed?"
    }
  
    let deleteQuestionnaire = () => {
      event.preventDefault();
  
      // If the previous save attempt failed, instead of trying to save again, open a login popup
      if (lastSaveStatus === false) {
        loginToSave();
        return;
      }
  
      setSaveInProgress(true);
  
      fetch(props.data["@path"], {
        method: "DELETE",
      }).then((response) => response.ok ? true : Promise.reject(response))
        .then(() => setLastSaveStatus(true))
        // FIXME Use setError?
        .catch(() => {
          // If the user is not logged in, offer to log in
          const sessionInfo = window.Sling.getSessionInfo();
          if (sessionInfo === null || sessionInfo.userID === 'anonymous') {
            // On first attempt to save while logged out, set status to false to make button text inform user
            setLastSaveStatus(false);
          }
        })
      .finally(() => setSaveInProgress(false));
      props.onClose();
    }
  
    let loginToSave = () => {
      const width = 600;
      const height = 800;
      const top = window.top.outerHeight / 2 + window.top.screenY - ( height / 2);
      const left = window.top.outerWidth / 2 + window.top.screenX - ( width / 2);
      // After a successful log in, the login dialog code will "open" the specified resource, which results in executing the specified javascript code
      window.open("/login.html?resource=javascript%3Awindow.close()", "loginPopup", `width=${width}, height=${height}, top=${top}, left=${left}`);
      // Display 'save' on button
      setLastSaveStatus(undefined);
    }
  
  return (
    <React.Fragment>
      <Dialog id="deleteDialog" open={props.open} onClose={props.onClose}>
        <form action={props.data["@path"]} onSubmit={deleteQuestionnaire} method="DELETE" key={props.id}>
          <DialogTitle>
            Confirm questionnaire deletion
          </DialogTitle>
          <DialogContent>
            <Typography>{ deleteQuestionnaireWarningMessage() }</Typography>
          </DialogContent>
          <DialogActions>
            <Button
              type="submit"
              variant="contained"
              color="secondary"
              >
              {'Yes, delete'}
            </Button>
            <Button
              variant="contained"
              color="default"
              onClick={props.onClose}
              >
              {'Cancel'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </React.Fragment>
  );
};


export default withStyles(QuestionnaireStyle)(withRouter(DeleteQuestionnaireDialog));
