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
import { Button, Dialog, DialogTitle, DialogActions, DialogContent, Typography } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import userboardStyle from './userboardStyle.jsx';

class DeletePrincipalDialogue extends React.Component {
    constructor(props) {
        super(props);
    }

    handleDelete() {
        let url = this.props.url + this.props.name + ".delete.html";

        fetch(url, {
            method: 'POST',
            credentials: 'include'
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
        const { classes } = this.props;

        return (
            <Dialog
                open={this.props.isOpen}
                onClose={() => this.props.handleClose()}
            >
                <DialogTitle>
                  Delete {this.props.name}
                </DialogTitle>
                <DialogContent>
                    <Typography variant="body1">Are you sure you want to delete {this.props.type} {this.props.name}?</Typography>
                </DialogContent>
                <DialogActions className={classes.dialogActions}>
                    <Button variant="contained" color="error" size="small" onClick={() => this.handleDelete()}>Delete</Button>
                    <Button variant="outlined" size="small" onClick={() => this.props.handleClose()}>Close</Button>
                </DialogActions>
            </Dialog>
        );
    }
}

export default withStyles(userboardStyle)(DeletePrincipalDialogue);
