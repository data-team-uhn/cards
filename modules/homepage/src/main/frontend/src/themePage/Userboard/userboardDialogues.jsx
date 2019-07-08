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

import {Button ,Dialog, DialogTitle, DialogActions, DialogContent, TextField, Table, TableBody, TableHead, TableRow, TableCell} from "@material-ui/core";

export class CreateUserDialogue extends React.Component {
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
      headers: {
        'Authorization': 'Basic' + btoa('admin:admin')
      },
      body:formData
    })
    .then(() => {
      this.props.handleClose();
    })
    .catch((error) => {
      console.log(error);
    });
  }
  
  render(){
    return(
      <Dialog
        open= {true}
        onClose= {() => this.props.handleClose()}
      >
        <DialogTitle>Create New User</DialogTitle>
        <DialogContent>
        <form
          onSubmit={(event) => {event.preventDefault(); this.handleCreateUser();}}
        >
          <GridContainer>
            <GridItem xs={12} sm={12} md={12}>
              <TextField
                id="name"
                name="name"
                label="Name"
                onChange={(event) => {this.setState({newName: event.target.value});}}
                autofocus
              />
            </GridItem>
            <GridItem xs={12} sm={12} md={12}>
              <TextField
                id="password"
                name="password"
                label="Password"
                onChange={(event) => {this.setState({newPwd: event.target.value});}}
              />
            </GridItem>
            <GridItem xs={12} sm={12} md={12}>
              <TextField
                id="passwordconfirm"
                name="passwordconfirm"
                label="Confirm Password"
                onChange={(event) => {this.setState({newPwdConfirm: event.target.value});}}
              />
            </GridItem>
          </GridContainer>
          <Button
            type = "submit"
          >
            Create User
          </Button>
        </form>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => this.props.handleClose()}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }
}

export class DeleteUserDialogue extends React.Component {
  constructor(props) {
    super(props);
  }

  handleDeleteUser (name) {
    let url = "http://localhost:8080/system/userManager/user/"+name+".delete.html";
    fetch(url, {
      method: 'POST',
      headers: {
        'Authorization' : 'Basic' + btoa('admin:admin')
      }
    })
    .then (() => {
      this.props.handleClose();
    })
    .catch((error) => {
      if(error.getElementById("Status")===404) {
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

export class ChangeUserPasswordDialogue extends React.Component {
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
  
    console.log(formData);
    fetch (url, {
      method: 'POST',
      headers: {
        'Authorization': 'Basic' + btoa('admin:admin')
      },
      body: formData
    })
    .then(() =>{
      this.props.handleClose();
    })
    .catch ((error) =>{
      if(error.getElementById("Status")===404) {
        console.log("missing user 404");
      }
      else {
        console.log("other error 505");
      }
      console.log(error);
    });
  }

  render () {
    return(
      <Dialog
        open = {true}
        onClose={() => this.props.handleClose()}
      >
        <DialogTitle>Change User Password of {this.props.name}</DialogTitle>
        <DialogContent>
          <form
            onSubmit={(event) => {event.preventDefault(); this.handlePasswordChange();}}
          >
            <GridContainer>
              <GridItem xs={12} sm={12} md={12}>
                <TextField
                  id= "oldpwd"
                  name="oldpwd"
                  label="Old Password"
                  onChange={(event) => {this.setState({oldPwd: event.target.value});}}
                  autofocus
                />
              </GridItem>
              <GridItem xs={12} sm={12} md={12}>
                <TextField
                  id = "newpwd"
                  name="newpwd"
                  label="New Password"
                  onChange={(event) => {this.setState({newPwd: event.target.value});}}
                />
              </GridItem>
              <GridItem xs={12} sm={12} md={12}>
                <TextField
                  id="newpwdconfirm"
                  name="newpwdconfirm"
                  label="Confirm New Password"
                  onChange={(event) => {this.setState({newPwdConfirm: event.target.value});}}
                />
              </GridItem>
            </GridContainer>
            <Button
              type="submit"
            >
              Change User Password
            </Button>
          </form>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => this.props.handleClose()}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }
}

export class CreateGroupDialogue extends React.Component {
  constructor(props) {
    super(props);
    this.state={
      newName: ""
    };
  }

  handleCreateGroup() {
    let formData = new FormData();
    formData.append(':name', this.state.newName);

    let url = "http://localhost:8080/system/userManager/group.create.json";

    fetch(url, {
      method: 'POST',
      headers: {
        'Authorization': 'Basic' + btoa('admin:admin')
      },
      body: formData
    })
    .then((response) => {
      this.props.handleClose();
      console.log(response);
    })
    .catch((error) => {
      console.log(error);
    })
  }

  render() {
    return(
      <Dialog
        open = {true}
        onClose = {() => this.props.handleClose()}
      >
        <DialogTitle>Create New Group</DialogTitle>
        <DialogContent>
          <form
            onSubmit = {(event) => {event.preventDefault(); this.handleCreateGroup();}}
          >
            <GridContainer>
              <GridItem>
                <TextField
                  id = "name"
                  name = "name"
                  label = "Name"
                  onChange = {(event) => {this.setState({newName: event.target.value});}}
                  autofocus
                />
              </GridItem>
            </GridContainer>
            <Button
              type = "submit"
            >
              Create Group
            </Button>
          </form>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => this.props.handleClose()}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }
}

export class DeleteGroupDialogue extends React.Component {
  constructor(props) {
    super(props);
  }

  handleDeleteGroup () {
    let url = "http://localhost:8080/system/userManager/group/"+this.props.name+".delete.html";

    fetch(url, {
        method: 'POST',
        headers: {
          'Authorization':'Basic' + btoa('admin:admin')
        }
      }
    )
    .then ( () => {
      this.props.handleClose();
    })
    .catch((error) => {
      if(error.getElementById("Status")===404) {
        console.log("missing group 404");
      }
      else {
        console.log("other error 505");
      }
      console.log(error);
    });
  }

  render() {
    return(
      <Dialog
        open={true}
        onClose={() => this.props.handleClose()}
      >
        <DialogTitle>Delete {this.props.name}</DialogTitle>
        <DialogContent>
          Are you sure you want to delete group {this.props.name}?
        </DialogContent>
        <DialogActions>
          <Button onClick={() => this.handleDeleteGroup(this.props.name)}>Delete</Button>
          <Button onClick={() => this.props.handleClose()}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }
}

export class AddUserToGroupDialogue extends React.Component {
  constructor (props) {
    super(props);
    this.state = {
      userNames: [],
      selectedUsers: []
    }
  }

  addName (name) {
    return {name}
  }

  handleLoadUsers () {
    fetch("http://"+"localhost:8080"+"/system/userManager/user.1.json", 
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
      for (var username in data){
        names.push(this.addName(username));
      }
      this.setState({userNames: names});
    })
    .catch((error) => {
      console.log(error);
    });
  }

  handleAddUsers () {
    let url = "http://localhost:8080/system/userManager/group/" + this.props.name + ".update.html";

    let formData = new FormData();
    console.log(this.state.selectedUsers);

    var i;
    for (i = 0; i < this.state.selectedUsers.length; ++i) {
      formData.append(':member', this.state.selectedUsers[i].name);
    }

    console.log(this.state.selectedUsers[0]);

    console.log(formData);

    fetch(url,
      {
        method: 'POST',
        headers: {
          'Authorization' : 'Basic' + btoa('admin:admin')
        },
        body: formData
    })
    .then(() => {
      this.props.handleClose();
    })
    .catch((error) => {
      console.log(error);
    });
  }

  componentWillMount () {
    this.handleLoadUsers();
  }

  // TODO - find more efficient way to add and remove users from list
  handleSelectRowClick(event, row) {
    let chosens = this.state.selectedUsers;
    if (chosens.indexOf(row) === -1)
    {
      chosens.push(row);
      this.setState({selectedUsers: chosens});
    }
    console.log(this.state.selectedUsers);    
  }

  handleDeselectRowClick(event, row) {
    let chosens = this.state.selectedUsers;
    chosens.splice(chosens.indexOf(row), 1);
    this.setState({selectedUsers: chosens});
    console.log(this.state.selectedUsers);
  }

  render () {
    return(
      <Dialog
        open={true}
        onClose={() => this.props.handleClose()}
      > 
      <DialogTitle>
        Add Users to Group
      </DialogTitle>
      <DialogContent>
        <GridContainer>
          <GridItem xs={12} sm={12} md ={6}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Users</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {
                  this.state.userNames.map(
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
          <GridItem xs={12} sm={12} md ={6}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Users to Add</TableCell>
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
        <Button onClick={() => this.handleAddUsers()}>Submit</Button>
        <Button onClick={() => this.props.handleClose()}>Close</Button>
      </DialogActions>
    </Dialog>
    );
  }
}

export class RemoveUserFromGroupDialogue extends React.Component {
  constructor (props) {
    super(props);
    this.state = {
      groupUsers: [],
      selectedUsers: []
    };
  }

  addName (name) {
    return {name}
  }

  handleLoadUsers () {
    fetch("http://localhost:8080/system/userManager/group/"+this.props.name+".1.json", 
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
      var i;
      for (i = 0; i < data.members.length; ++i){
        let username = data.members[i];
        username = username.substring(25);
        names.push(this.addName(username));
        console.log(data.members[i]);
      }
      this.setState({groupUsers: names});
    })
    .catch((error) => {
      console.log(error);
    });
  }

  handleRemoveUsers () {
    let url = "http://localhost:8080/system/userManager/group/" + this.props.name + ".update.html";

    let formData = new FormData();
    console.log(this.state.selectedUsers);

    var i;
    for (i = 0; i < this.state.selectedUsers.length; ++i) {
      formData.append(':member@Delete', this.state.selectedUsers[i].name);
    }

    console.log(this.state.selectedUsers[0]);
    console.log(formData);

    fetch(url,
      {
        method: 'POST',
        headers: {
          'Authorization' : 'Basic' + btoa('admin:admin')
        },
        body: formData
    })
    .then(() => {
      this.props.handleClose();
    })
    .catch((error) => {
      console.log(error);
    });
  }

  componentWillMount () {
    this.handleLoadUsers();
  }

  handleSelectRowClick(event, row) {
    let chosens = this.state.selectedUsers;
    console.log(chosens.indexOf(row));

    if (chosens.indexOf(row) === -1)
    {
      chosens.push(row);
      this.setState({selectedUsers: chosens});
    }
    console.log(this.state.selectedUsers);
  }

  handleDeselectRowClick(event, row) {
    let chosens = this.state.selectedUsers;
    console.log(row);
    chosens.splice(chosens.indexOf(row), 1);
    this.setState({selectedUsers: chosens});
    console.log(this.state.selectedUsers);
  }

  render () {
    return(
      <Dialog
        open={true}
        onClose={() => this.props.handleClose()}
      > 
      <DialogTitle>
        Remove users from group {this.props.name}
      </DialogTitle>
      <DialogContent>
        <GridContainer>
          <GridItem xs={12} sm={12} md ={6}>
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
          <GridItem xs={12} sm={12} md ={6}>
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
                        onClick={(event) => this.handleDeselectRowClick(event, row.name)}
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