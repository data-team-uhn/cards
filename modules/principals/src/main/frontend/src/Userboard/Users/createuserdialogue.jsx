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
import { Dialog, DialogTitle, DialogActions, DialogContent, TextField } from "@material-ui/core";
import { Button, GridItem, GridContainer } from "MaterialDashboardReact";

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

        let url = "http://localhost:8080/system/userManager/user.create.html";

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
                    <form
                        onSubmit={(event) => { event.preventDefault(); this.handleCreateUser(); }}
                    >
                        <GridContainer>
                            <GridItem xs={12} sm={12} md={12}>
                                <TextField
                                    id="name"
                                    name="name"
                                    label="Name"
                                    onChange={(event) => { this.setState({ newName: event.target.value }); }}
                                    autoFocus
                                />
                            </GridItem>
                            <GridItem xs={12} sm={12} md={12}>
                                <TextField
                                    id="password"
                                    name="password"
                                    label="Password"
                                    onChange={(event) => { this.setState({ newPwd: event.target.value }); }}
                                />
                            </GridItem>
                            <GridItem xs={12} sm={12} md={12}>
                                <TextField
                                    id="passwordconfirm"
                                    name="passwordconfirm"
                                    label="Confirm Password"
                                    onChange={(event) => { this.setState({ newPwdConfirm: event.target.value }); }}
                                />
                            </GridItem>
                        </GridContainer>
                        <Button
                            variant="contained"
                            color="sucess"
                            type="submit"
                        >
                            Create User
                        </Button>
                    </form>
                </DialogContent>
                <DialogActions>
                    <Button variant="contained" onClick={() => this.props.handleClose()}>Close</Button>
                </DialogActions>
            </Dialog>
        );
    }
}

export default CreateUserDialogue;