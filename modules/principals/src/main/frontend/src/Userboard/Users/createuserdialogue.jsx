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
import withStyles from '@mui/styles/withStyles';
import { Grid, Dialog, DialogTitle, DialogContent } from "@mui/material";
import userboardStyle from '../userboardStyle.jsx';

import SignUpForm from "../../login/signUpForm.js";

class CreateUserDialogue extends React.Component {
    constructor(props) {
        super(props);
    }

    handleCreateUser() {
        this.props.reload();
        this.props.handleClose();
    }

    handleError(error) {
        console.log(error);
    }

    render() {
        const { classes } = this.props;

        return (
            <Dialog
                open={this.props.isOpen}
                onClose={() => this.props.handleClose()}
            >
                <DialogTitle>Register a new user</DialogTitle>
                <DialogContent>
                  <Grid container>
                    <SignUpForm loginOnSuccess={false} handleSuccess={() => this.handleCreateUser()} handleExit={() => this.props.handleClose()}/>
                  </Grid>
                </DialogContent>
            </Dialog>
        );
    }
}

export default withStyles (userboardStyle)(CreateUserDialogue);
