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
import {Dialog, DialogTitle, DialogActions, DialogContent} from "@material-ui/core";
import Button from "material-dashboard-react/dist/components/CustomButtons/Button.js";

class DeleteGroupDialogue extends React.Component {
    constructor(props) {
        super(props);
    }

    handleDeleteGroup() {
        let url = "http://localhost:8080/system/userManager/group/" + this.props.name + ".delete.html";

        fetch(url, {
            method: 'POST',
            credentials: 'include'
        })
            .then(() => {
                this.props.reload();
                this.props.handleClose();
            })
            .catch((error) => {
                if (error.getElementById("Status") === 404) {
                    console.log("missing group 404");
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
                open={this.props.isOpen}
                onClose={() => this.props.handleClose()}
            >
                <DialogTitle>Delete {this.props.name}</DialogTitle>
                <DialogContent>
                    Are you sure you want to delete group {this.props.name}?
          </DialogContent>
                <DialogActions>
                    <Button variant="contained" color="danger" onClick={() => this.handleDeleteGroup(this.props.name)}>Delete</Button>
                    <Button variant="contained" onClick={() => this.props.handleClose()}>Cancel</Button>
                </DialogActions>
            </Dialog>
        );
    }
}

export default DeleteGroupDialogue;