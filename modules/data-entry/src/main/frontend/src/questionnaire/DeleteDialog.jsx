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
import PropTypes from "prop-types";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Typography,
  withStyles
} from "@material-ui/core";

import QuestionnaireStyle from "./QuestionnaireStyle";

let DeleteDialog = (props) => {
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);
  
  let deleteData = () => {
    event.preventDefault();

    // If the previous save attempt failed, instead of trying to save again, open a login popup
    if (lastSaveStatus === false) {
      loginToSave();
      return;
    }

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
    .finally(() => props.onClose());
  }

  let loginToSave = () => {
    const width = 600;
    const height = 800;
    const top = window.top.outerHeight / 2 + window.top.screenY - ( height / 2);
    const left = window.top.outerWidth / 2 + window.top.screenX - (width / 2);
    // After a successful log in, the login dialog code will "open" the specified resource, which results in executing the specified javascript code
    window.open("/login.html?resource=javascript%3Awindow.close()", "loginPopup", `width=${width}, height=${height}, top=${top}, left=${left}`);
    // Display 'save' on button
    setLastSaveStatus(undefined);
  }

  return (
    <React.Fragment>
      <Dialog id="deleteDialog" open={props.open} onClose={props.onClose}>
        <form action={props.data && props.data["@path"]} onSubmit={deleteData} method="DELETE" key={props.id}>
          <DialogTitle>
            <Typography>{props.type.includes("Question") ? "Confirm question deletion" : "Confirm Section Deletion"}</Typography>
          </DialogTitle>
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

DeleteDialog.propTypes = {
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired
};

export default withStyles(QuestionnaireStyle)(DeleteDialog);
