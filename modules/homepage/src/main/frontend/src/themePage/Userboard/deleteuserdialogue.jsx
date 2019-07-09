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
import { Button, Dialog, DialogTitle, DialogActions, DialogContent} from "@material-ui/core";

class DeleteUserDialogue extends React.Component {
    constructor(props) {
        super(props);
    }

    handleDeleteUser(name) {
        let url = "http://localhost:8080/system/userManager/user/" + name + ".delete.html";
        fetch(url, {
            method: 'POST',
            headers: {
                'Authorization': 'Basic' + btoa('admin:admin')
            }
        })
            .then(() => {
                this.props.handleClose();
            })
            .catch((error) => {
                if (error.getElementById("Status") === 404) {
                    console.log("missing user 404");
                }
                else {
                    console.log("other error 505");
                }
                console.log(error);
            });
    }

    render() {
        return (
            <Dialog
                open={true}
                onClose={() => this.props.handleClose()}
            >
                <DialogTitle>Delete {this.props.name}</DialogTitle>
                <DialogContent>
                    Are you sure you want to delete user {this.props.name}?
        </DialogContent>
                <DialogActions>
                    <Button onClick={() => this.handleDeleteUser(this.props.name)}>Delete</Button>
                    <Button onClick={() => this.props.handleClose()}>Close</Button>
                </DialogActions>
            </Dialog>
        );
    }
}

export default DeleteUserDialogue;