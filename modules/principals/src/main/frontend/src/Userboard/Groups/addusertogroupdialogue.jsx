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

import { Button, Dialog, DialogTitle, DialogActions, DialogContent, Grid, Table, TableBody, TableHead, TableRow, TableCell } from "@material-ui/core";

import MaterialTable from 'material-table';

class AddUserToGroupDialogue extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            allUserNames: [],
            freeUserNames: [],
            selectedUsers: []
        }
    }

    addName(name) {
        return { name }
    }

    handleLoadUsers() {
        fetch("/system/userManager/user.1.json",
            {
                method: 'GET',
                headers: {
                    'Authorization': 'Basic ' + btoa('admin:admin')
                }
            })
            .then((response) => {
                return response.json();
            })
            .then((data) => {
                var names = [];
                for (var username in data) {
                    !username.startsWith("sling-") && names.push(this.addName(username));
                }
                this.setState({ allUserNames: names });
            })
            .catch((error) => {
                console.log(error);
            });
    }

    handleAddUsers() {
        let url = "/system/userManager/group/" + this.props.name + ".update.html";
        let formData = new FormData();

        var i;
        for (i = 0; i < this.state.selectedUsers.length; ++i) {
            formData.append(':member', this.state.selectedUsers[i]);
        }

        fetch(url,
            {
                method: 'POST',
                credentials: 'include',
                body: formData
            })
            .then(() => {
                this.setState({ selectedUsers: [] });
                this.props.reload();
                this.handleExit();
            })
            .catch((error) => {
                console.log(error);
            });
    }

    componentDidMount() {
        this.handleLoadUsers();
    }

    // TODO - find more efficient way to add and remove users from list
    handleSelectRowClick(rows) {
        let chosens = [];
        var i;
        for (i = 0; i < rows.length; ++i) {
            chosens.push(rows[i].name);
        }
        this.setState({ selectedUsers: chosens });
    }

    handleEntering() {
        if (this.props.name != "") {
          fetch("/system/userManager/group/" + this.props.name + ".1.json",
            {
              method: 'GET',
              credentials: 'include'
            })
            .then((response) => {
              return response.json();
            })
            .then((data) => {
              var groupUsers = [];
              var i;
              for (i = 0; i < data.members.length; ++i) {
                let username = data.members[i];
                username = username.split('/').pop();
                groupUsers.push(username);
              }

              let filtered = [];
              filtered = this.state.allUserNames.filter( function( el ) {
                  return !groupUsers.includes( el.name );
                } );
              this.setState({ freeUserNames: filtered });
            })
            .catch((error) => {
              console.log(error);
            });
        }
    }

    handleExit() {
        this.setState({ selectedUsers: [] });
        this.state.allUserNames.forEach(function(item, index) {
          if (item.tableData) {
              item.tableData.checked = false;
          }
        })
        this.props.handleClose();
    }

    render() {
        return (
            <Dialog
                maxWidth="sm"
                open={this.props.isOpen}
                onEntering={() => this.handleEntering()}
                onClose={() => this.handleExit()}
            >
                <DialogTitle>
                    Add Users to the {this.props.name} group
                </DialogTitle>
                <DialogContent>
                    <Grid container>
                        <div>
                            <MaterialTable
                              title=""
                              style={{ boxShadow : 'none' }}
                              options={
                                { draggable: false },
                                { headerStyle: { backgroundColor: '#fafbfc'} },
                                { emptyRowsWhenPaging: false }
                              }
                              columns={[
                                { title: 'User Name', field: 'name' },
                              ]}
                              data={this.state.freeUserNames}
                              options={{
                                selection: true,
                                showSelectAllCheckbox : false,
                                showTextRowsSelected: false,
                                selectionProps: rowData => ({
                                    color: 'primary'
                                  })
                              }}
                              onSelectionChange={(rows) => {this.handleSelectRowClick(rows)}}
                            />
                        </div>
                    </Grid>
                </DialogContent>
                <DialogActions>
                    <Button variant="contained" size="small" color="primary" onClick={() => this.handleAddUsers()} disabled={this.state.selectedUsers.length == 0}>Add</Button>
                    <Button variant="contained" size="small" onClick={() => this.handleExit()}>Close</Button>
                </DialogActions>
            </Dialog>
        );
    }
}

export default AddUserToGroupDialogue;