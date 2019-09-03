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
import {Dialog, DialogTitle, DialogActions, DialogContent, TextField} from "@material-ui/core";
import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";
import GridContainer from "material-dashboard-react/dist/components/Grid/GridContainer.js";
import Button from "material-dashboard-react/dist/components/CustomButtons/Button.js";

class ChangeUserPasswordDialogue extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            oldPwd: "",
            newPwd: "",
            newPwdConfirm: ""
        }
    }

    handlePasswordChange(name) {
        let formData = new FormData();
        formData.append('oldPwd', this.state.oldPwd);
        formData.append('newPwd', this.state.newPwd);
        formData.append('newPwdConfirm', this.state.newPwdConfirm);
        let url = "http://localhost:8080/system/userManager/user/" + this.props.name + ".changePassword.html";

        fetch(url, {
            method: 'POST',
            credentials: 'include',
            body: formData
        })
        .then(() => {
            this.props.handleClose();
        })
        .catch((error) => {
            if (error.getElementById("Status") === 404) {
                console.log("missing user 404");
            } else {
                console.log("other error 505");
            }
            console.log(error);
        });
    }

    render() {
        return (
            <Dialog
                open={this.props.isOpen}
                onClose={() => this.props.handleClose()}
            >
                <DialogTitle>Change User Password of {this.props.name}</DialogTitle>
                <DialogContent>
                    <form
                        onSubmit={(event) => { event.preventDefault(); this.handlePasswordChange(); }}
                    >
                        <GridContainer>
                            <GridItem xs={12} sm={12} md={12}>
                                <TextField
                                    id="oldpwd"
                                    name="oldpwd"
                                    label="Old Password"
                                    onChange={(event) => { this.setState({ oldPwd: event.target.value }); }}
                                    autoFocus
                                />
                            </GridItem>
                            <GridItem xs={12} sm={12} md={12}>
                                <TextField
                                    id="newpwd"
                                    name="newpwd"
                                    label="New Password"
                                    onChange={(event) => { this.setState({ newPwd: event.target.value }); }}
                                />
                            </GridItem>
                            <GridItem xs={12} sm={12} md={12}>
                                <TextField
                                    id="newpwdconfirm"
                                    name="newpwdconfirm"
                                    label="Confirm New Password"
                                    onChange={(event) => { this.setState({ newPwdConfirm: event.target.value }); }}
                                />
                            </GridItem>
                        </GridContainer>
                        <Button
                            variant="contained"
                            color="info"
                            type="submit"
                        >
                            Change User Password
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

export default ChangeUserPasswordDialogue;