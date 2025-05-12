/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/

import React from "react";
import PropTypes from "prop-types";
import { Grid, Dialog, DialogTitle, DialogContent } from "@mui/material";
import RegistrationForm from "../../login/RegistrationForm.js";
import { checkPropTypes } from "../../propTypes";

function CreateUserDialogue(props) {
  checkPropTypes(CreateUserDialogue, props);
  const { reload, isOpen, handleClose } = props;

  let handleCreateUser = () => {
    reload();
    handleClose();
  }

  return (
    <Dialog
      open={isOpen}
      onClose={() => handleClose()}
    >
      <DialogTitle>Register a new user</DialogTitle>
      <DialogContent>
        <Grid container>
          <RegistrationForm
            loginOnSuccess={false}
            handleSuccess={() => handleCreateUser()}
            handleExit={() => handleClose()}
            closeButtonText="Cancel"
            submitButtonText="Create account"
          />
        </Grid>
      </DialogContent>
    </Dialog>
  );
}

CreateUserDialogue.propTypes = {
  isOpen: PropTypes.bool,
  handleClose: PropTypes.func.isRequired,
  reload: PropTypes.func.isRequired
}

export default CreateUserDialogue;
