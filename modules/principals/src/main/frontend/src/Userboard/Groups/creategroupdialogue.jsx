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

import { Button, Grid, Dialog, DialogTitle, DialogActions, DialogContent, TextField, Typography } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import userboardStyle from '../userboardStyle.jsx';

class CreateGroupDialogue extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            newName: "",
            error: ""
        };
    }

    handleCreateGroup() {
        this.setState({ error: "" });
        let formData = new FormData();
        formData.append(':name', this.state.newName);
        let url = "/system/userManager/group.create.json";

        fetch(url, {
            method: 'POST',
            credentials: 'include',
            body: formData
        })
        .then((response) => {
            if (!response.ok) {
              this.setState({ error: response.statusText });
              return;
            }
            this.props.reload();
            this.props.handleClose();
        });
    }

    render() {
        const { classes } = this.props;

        return (
            <Dialog
                open={this.props.isOpen}
                onClose={this.props.handleClose}
            >
                <DialogTitle>Create New Group</DialogTitle>
                <DialogContent>
                    <Grid container>
                        <Grid item>
                            <TextField
                                variant="standard"
                                id="name"
                                name="name"
                                label="Name"
                                onChange={(event) => { this.setState({ newName: event.target.value, error: "" }); }}
                                autoFocus
                            />
                        </Grid>
                    </Grid>
                    {this.state.error && <Typography color='error'>{this.state.error}</Typography>}
                </DialogContent>
                <DialogActions className={classes.dialogActions}>
                    <Button color="primary" variant="contained" size="small" onClick={(event) => { event.preventDefault(); this.handleCreateGroup(); }}>Create Group</Button>
                    <Button variant="contained" size="small" onClick={this.props.handleClose}>Close</Button>
                </DialogActions>
            </Dialog>
        );
    }
}

export default withStyles(userboardStyle)(CreateGroupDialogue);
