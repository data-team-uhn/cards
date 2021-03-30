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

import React, { useState, useContext } from "react";
import PropTypes from "prop-types";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
  withStyles
} from "@material-ui/core";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

let DeleteDialog = (props) => {
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  const { data, onClose, onCancel, isOpen, id } = props;
  let [ open, setOpen ] = useState(isOpen);
  let [ error, setError ] = useState("");
  const globalLoginDisplay = useContext(GlobalLoginContext);
  
  let deleteData = (event) => {
    event.preventDefault();

    // Delete this node and every node that refers to this question
    let url = new URL(data["@path"], window.location.origin);
    url.searchParams.set("recursive", true);
    fetchWithReLogin(globalLoginDisplay, url, { method: "DELETE" })
      .then((response) => response.ok ? true : Promise.reject(response))
      .then(() => { setOpen(false); onClose && onClose(); })
      .catch((errorObj) => {
          setError(String(errorObj.statusText));
      });
  }

  return (
    <React.Fragment>
      <Dialog id="deleteDialog" open={open} onClose={() => { setOpen(false); onClose && onClose(); }}>
        <form action={data && data["@path"]} onSubmit={deleteData} method="DELETE" key={id}>
          <DialogTitle>
            <Typography>{`Confirm ${(props.type || '').toLowerCase()} deletion`}</Typography>
          </DialogTitle>
          {error &&
            <DialogContent>
              <Typography color="error">{error}</Typography>
            </DialogContent>
          }
          <DialogActions>
            <Button
              type="submit"
              variant="contained"
              color="secondary"
              >
              {'Delete'}
            </Button>
            <Button
              variant="contained"
              color="default"
              onClick={() => { setOpen(false); onCancel && onCancel();}}
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
  type: PropTypes.string.isRequired,
  isOpen: PropTypes.bool.isRequired
};

export default withStyles(QuestionnaireStyle)(DeleteDialog);
