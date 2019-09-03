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

import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";
import GridContainer from "material-dashboard-react/dist/components/Grid/GridContainer.js";

import { Button, Dialog, DialogTitle, DialogActions, DialogContent, Table, TableBody, TableHead, TableRow, TableCell } from "@material-ui/core";

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

  handleLoadUsers() {
    if (this.props.name != "") {
      fetch("http://localhost:8080/system/userManager/group/" + this.props.name + ".1.json",
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
            username = username.substring(25);
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
    let url = "http://localhost:8080/system/userManager/group/" + this.props.name + ".update.html";

    let formData = new FormData();

    var i;
    for (i = 0; i < this.state.selectedUsers.length; ++i) {
      formData.append(':member@Delete', this.state.selectedUsers[i].name);
    }

    fetch(url,
      {
        method: 'POST',
        credentials: 'include',
        body: formData
      })
      .then(() => {
        this.props.handleClose();
      })
      .catch((error) => {
        console.log(error);
      });
  }

  componentWillMount() {
    this.handleLoadUsers();
  }

  handleSelectRowClick(event, row) {
    let chosens = this.state.selectedUsers;

    if (chosens.indexOf(row) === -1) {
      chosens.push(row);
      this.setState({ selectedUsers: chosens });
    }
  }

  handleDeselectRowClick(event, row) {
    let chosens = this.state.selectedUsers;
    chosens.splice(chosens.indexOf(row), 1);
    this.setState({ selectedUsers: chosens });
  }

  render() {
    return (
      <Dialog
        open={this.props.isOpen}
        onClose={() => this.props.handleClose()}
      >
        <DialogTitle>
          Remove users from group {this.props.name}
        </DialogTitle>
        <DialogContent>
          <GridContainer>
            <GridItem xs={12} sm={12} md={6}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>
                      Users in {this.props.name}
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {
                    this.state.groupUsers.map(
                      (row, index) => (
                        <TableRow
                          onClick={(event) => this.handleSelectRowClick(event, row)}
                          key={row.name}
                        >
                          <TableCell>{row.name}</TableCell>
                        </TableRow>
                      )
                    )
                  }
                </TableBody>
              </Table>
            </GridItem>
            <GridItem xs={12} sm={12} md={6}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>
                      Users to Remove
                  </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {
                    this.state.selectedUsers.map(
                      (row, index) => (
                        <TableRow
                          onClick={(event) => this.handleDeselectRowClick(event, row)}
                          key={row.name}
                        >
                          <TableCell>{row.name}</TableCell>
                        </TableRow>
                      )
                    )
                  }
                </TableBody>
              </Table>
            </GridItem>
          </GridContainer>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => this.handleRemoveUsers()}>Submit</Button>
          <Button onClick={() => this.props.handleClose()}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }
}

export default RemoveUserFromGroupDialogue;