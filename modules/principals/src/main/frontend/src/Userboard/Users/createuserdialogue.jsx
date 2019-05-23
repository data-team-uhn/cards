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
import { Button, Grid, Dialog, DialogTitle, DialogActions, DialogContent, TextField } from "@material-ui/core";

class CreateUserDialogue extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            newName: "",
            newPwd: "",
            newPwdConfirm: ""
        };
    }

    handleCreateUser() {
        let formData = new FormData();
        formData.append(':name', this.state.newName);
        formData.append('pwd', this.state.newPwd);
        formData.append('pwdConfirm', this.state.newPwdConfirm);

        console.log(formData);

        let url = "/system/userManager/user.create.html";

        fetch(url, {
            method: 'POST',
            credentials: 'include',
            body: formData
        })
            .then(() => {
                this.props.reload();
                this.props.handleClose();
            })
            .catch((error) => {
                console.log(error);
            });
    }

    render() {
        return (
            <Dialog
                open={this.props.isOpen}
                onClose={() => this.props.handleClose()}
            >
                <DialogTitle>Create New User</DialogTitle>
                <DialogContent>
                    
                        <Grid container>
                            <Grid item xs={12} sm={12} md={12}>
                                <TextField
                                    id="name"
                                    name="name"
                                    label="Name"
                                    onChange={(event) => { this.setState({ newName: event.target.value }); }}
                                    autoFocus
                                />
                            </Grid>
                            <Grid item xs={12} sm={12} md={12}>
                                <TextField
                                    id="firstName"
                                    name="firstName"
                                    label="First Name"
                                    onChange={(event) => { this.setState({ newFirstName: event.target.value }); }}
                                />
                            </Grid>
                            <Grid item xs={12} sm={12} md={12}>
                                <TextField
                                    id="lastName"
                                    name="lastName"
                                    label="Last Name"
                                    onChange={(event) => { this.setState({ newLastName: event.target.value }); }}
                                />
                            </Grid>
                            <Grid item xs={12} sm={12} md={12}>
                                <TextField
                                    id="password"
                                    name="password"
                                    label="Password"
                                    onChange={(event) => { this.setState({ newPwd: event.target.value }); }}
                                />
                            </Grid>
                            <Grid item xs={12} sm={12} md={12}>
                                <TextField
                                    id="passwordconfirm"
                                    name="passwordconfirm"
                                    label="Confirm Password"
                                    onChange={(event) => { this.setState({ newPwdConfirm: event.target.value }); }}
                                />
                            </Grid>
                        </Grid>
                </DialogContent>
                <DialogActions>
                    <Button
                        variant="contained"
                        color="primary"
                        size="small"
                        onClick={(event) => { event.preventDefault(); this.handleCreateUser(); }}
                    >
                        Create User
                    </Button>
                    <Button variant="contained" size="small" onClick={() => this.props.handleClose()}>Close</Button>
                </DialogActions>
            </Dialog>
        );
    }
}

export default CreateUserDialogue;