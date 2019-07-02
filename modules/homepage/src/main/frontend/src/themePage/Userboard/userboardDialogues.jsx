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

import Button from '@material-ui/core/Button';
import Dialog from '@material-ui/core/Dialog';
import DialogTitle from '@material-ui/core/DialogTitle';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import TextField from '@material-ui/core/TextField';

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
      .catch((error) => {
        console.log(error);
      });
    }
  
    render(){
      return(
        <Dialog
          open= {true}
          onClose= {(event) => {event.preventDefault(); this.props.handleClose();}}
        >
          <DialogTitle>Create New User</DialogTitle>
          <DialogContent>
          <form
            onSubmit={()=>this.handleCreateUser()}
          >
            <GridContainer>
              <GridItem>
                <TextField
                  id="name"
                  name="name"
                  label="Name"
                  onChange={(event) => {this.setState({newName: event.target.value});}}
                />
              </GridItem>
              <GridItem >
                <TextField
                  id="password"
                  name="password"
                  label="Password"
                  onChange={(event) => {this.setState({newName: event.target.value});}}
                />
              </GridItem>
              <GridItem >
                <TextField
                  id="passwordconfirm"
                  name="passwordconfirm"
                  label="Confirm Password"
                  onChange={(event) => {this.setState({newName: event.target.value});}}
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
        .catch((error) => {
          if(error.getElementById("Status")===404) {
            console.log("missing user 404");
          }
          else {
            console.log("other error 505");
          }
          console.log(error);
        })
      }

    render() {
      return (
        <Dialog
          open={true}
          onClose={(event) =>{event.preventDefault(); this.props.handleClose();}}
        >
          <DialogTitle>Delete {this.props.name}</DialogTitle>
          <DialogContent>
              Are you sure you want to delete {this.props.name}?
          </DialogContent>
          <DialogActions>
           <Button onClick={() => this.handleDeleteUser(this.props.name)}>Delete</Button>
           <Button onClick={() => this.props.handleClose()}>Cancel</Button>
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
              onSubmit = {() => this.handleCreateGroup()}
            >
              <GridContainer>
                <GridItem>
                  <TextField
                    id = "name"
                    name = "name"
                    label = "Name"
                    onChange = {(event) => {this.setState({newName: event.target.value});}}
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
    .catch((error) => {
        if(error.getElementById("Status")===404) {
          console.log("missing group 404");
        }
        else {
          console.log("other error 505");
        }
        console.log(error);
      })
  }

  render() {
    return(
      <Dialog
        open={true}
        onClose={(event) => {event.preventDefault(); this.props.handleClose();}}
      >
        <DialogTitle>Delete {this.props.name}</DialogTitle>
        <DialogContent>
            Are you sure you want to delete group {this.props.name}?
          <Button onClick={() => this.handleDeleteGroup(this.props.name)}>Delete</Button>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => this.props.handleClose()}>Cancel</Button>
        </DialogActions>
      </Dialog>
    );
  }
}