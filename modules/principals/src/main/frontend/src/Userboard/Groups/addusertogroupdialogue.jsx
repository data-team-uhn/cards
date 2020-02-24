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

import { withTheme } from "@material-ui/core/styles";

import { Button, Dialog, DialogTitle, DialogActions, DialogContent, Grid, Table, TableBody, TableHead, TableRow, TableCell } from "@material-ui/core";

import MaterialTable from 'material-table';

const GROUP_URL="/system/userManager/group/";

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
                    names = Object.keys(data).filter((username) => !username.startsWith("sling-")).map((username) => this.addName(username));
                }
                this.setState({ allUserNames: names });
            })
            .catch((error) => {
                console.log(error);
            });
    }

    handleAddUsers() {
        let formData = new FormData();

        var i;
        for (i = 0; i < this.state.selectedUsers.length; ++i) {
            formData.append(':member', this.state.selectedUsers[i]);
        }

        fetch(GROUP_URL + this.props.name + ".update.html",
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

    handleSelectRowClick(rows) {
        let chosens = rows.map((row) => row.name);
        this.setState({ selectedUsers: chosens });
    }

    handleEntering() {
        if (this.props.name != "") {
          fetch(GROUP_URL + this.props.name + ".1.json",
            {
              method: 'GET',
              credentials: 'include'
            })
            .then((response) => {
              return response.json();
            })
            .then((data) => {
              var groupUsers = data?.members?.map((n) => n?.split('/').pop());

              let filtered = this.state.allUserNames.filter( (el) => {
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
        const headerBackground = this.props.theme.palette.grey['200'];

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
                              options={{
                                headerStyle: { backgroundColor: headerBackground },
                                emptyRowsWhenPaging: false
                              }}
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

export default withTheme(AddUserToGroupDialogue);
