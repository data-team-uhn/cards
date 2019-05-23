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

import { Button, Dialog, DialogTitle, DialogActions, DialogContent, Grid } from "@material-ui/core";

import MaterialTable from 'material-table';

class RemoveUserFromGroupDialogue extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      groupUsers: [],
      selectedUsers: []
    };
  }

  addName(name) {
    return { name }
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
          var names = [];
          var i;
          for (i = 0; i < data.members.length; ++i) {
            let username = data.members[i];
            username = username.split('/').pop();
            names.push(this.addName(username));
          }
          this.setState({ groupUsers: names });
        })
        .catch((error) => {
          console.log(error);
        });
    }
  }

  handleRemoveUsers() {
    let url = "/system/userManager/group/" + this.props.name + ".update.html";

    let formData = new FormData();

    var i;
    for (i = 0; i < this.state.selectedUsers.length; ++i) {
      formData.append(':member@Delete', this.state.selectedUsers[i]);
    }

    fetch(url,
      {
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

  handleSelectRowClick(rows) {
    let chosens = [];
    var i;
    for (i = 0; i < rows.length; ++i) {
        chosens.push(rows[i].name);
    }
    this.setState({ selectedUsers: chosens });
  }

  render() {
    return (
      <Dialog
        maxWidth="sm"
        open={this.props.isOpen}
        onEntering={() => this.handleEntering()}
        onClose={() => this.props.handleClose()}
      >
        <DialogTitle>
          Remove users from group {this.props.name}
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
                  data={this.state.groupUsers}
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
          <Button variant="contained" size="small" color="primary" onClick={() => this.handleRemoveUsers()} disabled={this.state.selectedUsers.length == 0}>Remove</Button>
          <Button variant="contained" size="small" onClick={() => this.props.handleClose()}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }
}

export default RemoveUserFromGroupDialogue;