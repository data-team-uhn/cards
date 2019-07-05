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

import PropTypes from "prop-types";

import  {withStyles} from "@material-ui/core/styles";

import Button from '@material-ui/core/Button';
import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableHead from '@material-ui/core/TableHead';
import TableRow from '@material-ui/core/TableRow';
import TableCell from '@material-ui/core/TableCell';
import Checkbox from '@material-ui/core/Checkbox';
import Hidden from '@material-ui/core/Hidden';

import Dialog from '@material-ui/core/Dialog';
import DialogTitle from '@material-ui/core/DialogTitle';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';

import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";
import GridContainer from "material-dashboard-react/dist/components/Grid/GridContainer.js";
import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
import CardFooter from "material-dashboard-react/dist/components/Card/CardFooter"
//import { Avatar } from "@material-ui/core";

import userboardStyle from './userboardStyle.jsx';

import {CreateUserDialogue, DeleteUserDialogue, ChangeUserPasswordDialogue, CreateGroupDialogue, DeleteGroupDialogue, AddUserToGroupDialogue, RemoveUserFromGroupDialogue} from './userboardDialogues.jsx';

class Userboard extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      users: [],
      groups: [],

      currentUserName: "",
      currentUserIndex: -1,
      returnedUserRows: 0,
      totalUserRows: 0,

      currentGroupName: "",
      currentGroupIndex: -1,
      returnedGroupRows: 0,
      totalGroupRows: 0,

      deployCreateUser: false,
      deployDeleteUser: false,
      deployChangeUserPassword: false,
      deployCreateGroup: false,
      deployDeleteGroup: false,
      deployAddGroupUsers: false,
      deployRemoveGroupUsers: false,
      deployMobileUserDialog: false,
      deployMobileGroupDialog: false
    };

    this.userColumnNames = [{id: "name", label: "User Names"}];
    this.groupColumnNames = [{id: "name", label: "Group Names"}];
  }

  handleLoadUsers (filter, offset, limit) {
    let url = "http://localhost:8080/home/users.json"
    let formData = new FormData();

    if (filter !== null) {
      formData.append('filter', filter);
    }

    if (offset !== null) {
      formData.append('offset', offset);
    }

    if (limit !== null) {
      formData.append('limit', limit);
    }

    fetch(url,
      {
        method: 'GET',
        headers: {
          'Authorization': 'Basic ' + btoa('admin:admin')
        },
        body: formData
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      this.setState({returnedUserRows: data.returnedrows});
      this.setState({totalUserRows: data.totalrows});
      this.setState({users: data.rows});
      //console.log(this.state.users); console.log(data.rows);
    })
    .catch((error) => {
      console.log(error);
    });
  }

  handleLoadGroups () {
    let url = "http://localhost:8080/home/groups.json"
    fetch(url,
      {
        method: 'GET',
        headers: {
          'Authorization' : 'Basic' + btoa('admin:admin')
        }
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      
      this.setState({returnedGroupRows: data.returnedrows});
      this.setState({totalGroupRows: data.totalrows});
      this.setState({groups: data.rows});
    })
    .catch((error) => {
      console.log(error);
    })
  }

  componentWillMount () {
    this.handleLoadUsers(null, null, null);
    this.handleLoadGroups();
  }

  handleUserRowClick(index, name) {
    if (index === this.state.currentUserIndex) {
      this.setState({currentUserName: ""});
      this.setState({currentUserIndex: -1});
    } else {
      this.setState({currentUserName: name});
      this.setState({currentUserIndex: index});
    }
  }

  handleGroupRowClick(index, name) {
    if (index === this.state.currentGroupIndex) {
      this.setState({currentGroupName: ""});
      this.setState({currentGroupIndex: -1});
    } else {
      this.setState({currentGroupName: name});
      this.setState({currentGroupIndex: index});
    }
  }

  render() {
    const { classes } = this.props;

    return (
      <div>
        
        {this.state.deployCreateUser && <CreateUserDialogue handleClose={() => {this.setState({deployCreateUser: false});}}/>}
        {this.state.deployDeleteUser && <DeleteUserDialogue handleClose={() => {this.setState({deployDeleteUser: false});}} name={this.state.currentUserName}/>}
        {this.state.deployChangeUserPassword && <ChangeUserPasswordDialogue handleClose={() => {this.setState({deployChangeUserPassword: false});}} name={this.state.currentUserName}/>}
        {this.state.deployCreateGroup && <CreateGroupDialogue handleClose={() => {this.setState({deployCreateGroup: false});}}/>}
        {this.state.deployDeleteGroup && <DeleteGroupDialogue handleClose={() => {this.setState({deployDeleteGroup: false});}} name={this.state.currentGroupName}/>}
        {this.state.deployAddGroupUsers && <AddUserToGroupDialogue handleClose={() => {this.setState({deployAddGroupUsers: false});}} name={this.state.currentGroupName}/>}
        {this.state.deployRemoveGroupUsers && <RemoveUserFromGroupDialogue handleClose={() => {this.setState({deployRemoveGroupUsers: false});}} name={this.state.currentGroupName}/>}
        
        <Hidden mdUp implementation="css">
          <Dialog
            open={this.state.deployMobileUserDialog}
            onClose={() => {this.setState({deployMobileUserDialog: false});}}
          >
            <Card>
              <CardHeader color = "success">
                {
                  this.state.currentUserIndex >= 0 && <h4 className={classes.cardTitleWhite}>User: {this.state.users[this.state.currentUserIndex].name}</h4>
                }
              </CardHeader>
              <CardBody>
              {
                this.state.currentUserIndex >= 0 &&
                <div>
                  <h3>Principal Name: {this.state.users[this.state.currentUserIndex].principalName}</h3>
                  <h3>Path: {this.state.users[this.state.currentUserIndex].path}</h3>
                  <h3>Admin Status: {this.state.users[this.state.currentUserIndex].isAdmin ? "True" : "False"}</h3>
                  <h3>Disabled: {this.state.users[this.state.currentUserIndex].isDisabled ? "True" : "False"}</h3>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableCell>Groups</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {this.state.users[this.state.currentUserIndex].memberOf.map(
                        (row) => (
                          <TableRow
                            key = {row.name}
                          >
                            <TableCell>{row.name}</TableCell>
                          </TableRow>
                        )
                      )}
                    </TableBody>
                  </Table>
                </div>
              }
                <GridContainer>
                  <Button onClick={() => {this.setState({deployDeleteUser: true});}} disabled={this.state.currentUserIndex < 0 ? true:false}>Delete User</Button>
                  <Button onClick={() => {this.setState({deployChangeUserPassword: true});}} disabled={this.state.currentUserIndex < 0 ? true:false}>Change Password</Button>
                  <Button onClick={() => {this.setState({deployMobileUserDialog: false});}}>Close</Button>
                </GridContainer>
              </CardBody>
            </Card>
          </Dialog>

          <Dialog
            open={this.state.deployMobileGroupDialog}
            onClose={() =>{this.setState({deployMobileGroupDialog: false});}}
          >
            <Card>
              <CardHeader color="success">
                <h4></h4>
              </CardHeader>
              <CardBody>
                {
                  this.state.currentGroupIndex >= 0 &&
                  <div>
                    <h1>Group Name: {this.state.groups[this.state.currentGroupIndex].name}</h1>
                    <h3>Principal Name: {this.state.groups[this.state.currentGroupIndex].principalName}</h3>
                    <h3>Path: {this.state.groups[this.state.currentGroupIndex].path}</h3>
                    <h3>Members: {this.state.groups[this.state.currentGroupIndex].members}</h3>
                    <h3>Declared Members: {this.state.groups[this.state.currentGroupIndex].declaredMembers}</h3>
                  </div>
                }
                <GridContainer>
                  <Button onClick={() => {this.setState({deployDeleteGroup: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Delete Group</Button>
                  <Button onClick={() => {this.setState({deployAddGroupUsers: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Add User to Group</Button>
                  <Button onClick={() => {this.setState({deployRemoveGroupUsers: false});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Remove User from Group</Button>
                </GridContainer>
              </CardBody>
            </Card>
          </Dialog>
        </Hidden>


        <GridContainer>
          <GridItem xs={12} sm={12} md={5}>
            <Card>
              <CardHeader color="warning">
                <h4 className={classes.cardTitleWhite}>Users</h4>
              </CardHeader>
              <CardBody>
                <Button onClick={() => {this.setState({deployCreateUser: true});}}>Create New User</Button>
                <form>
                  <Input></Input>
                </form>
                <Table> 
                  <TableHead>
                    <TableRow>
                      {this.userColumnNames.map(
                        row => (
                          <TableCell
                            key = {row.id}
                          >
                            {row.label}
                          </TableCell>
                        )
                      )}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {this.state.users.map(
                      (row, index) => (
                        <TableRow
                          onClick={(event) => {this.handleUserRowClick(index, row.name); this.setState({deployMobileUserDialog: true});}}
                          aria-checked={index === this.state.currentUserIndex ? true:false}
                          key = {row.name}
                          selected={index === this.state.currentUserIndex ? true:false}
                        >
                          <TableCell>
                            <Hidden smDown implementation="css">
                              <Checkbox
                                checked = {index === this.state.currentUserIndex ? true:false}
                              />
                            </Hidden>
                            {row.name}
                          </TableCell>
                        </TableRow>
                      )
                    )}
                  </TableBody>
                </Table>
              </CardBody>
            </Card>
          </GridItem>
          <GridItem xs={12} sm={12} md={7}>
            <Hidden smDown implementation="css">
              <Card>
                <CardHeader color = "success">
                  {
                    this.state.currentUserIndex < 0 ?
                    <h4 className={classes.cardTitleWhite}>No users selected.</h4>
                    :
                    <h4 className={classes.cardTitleWhite}>User: {this.state.users[this.state.currentUserIndex].name}</h4>
                  }
                </CardHeader>
                <CardBody>
                {
                  this.state.currentUserIndex >= 0 &&
                  <div>
                    <h3>Principal Name: {this.state.users[this.state.currentUserIndex].principalName}</h3>
                    <h3>Path: {this.state.users[this.state.currentUserIndex].path}</h3>
                    <h3>Admin Status: {this.state.users[this.state.currentUserIndex].isAdmin ? "True" : "False"}</h3>
                    <h3>Disabled: {this.state.users[this.state.currentUserIndex].isDisabled ? "True" : "False"}</h3>
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableCell>Groups</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {this.state.users[this.state.currentUserIndex].memberOf.map(
                          (row) => (
                            <TableRow
                              key = {row.name}
                            >
                              <TableCell>{row.name}</TableCell>
                            </TableRow>
                          )
                        )}
                      </TableBody>
                    </Table>
                  </div>
                }
                  <GridContainer>
                    <Button onClick={() => {this.setState({deployDeleteUser: true});}} disabled={this.state.currentUserIndex < 0 ? true:false}>Delete User</Button>
                    <Button onClick={() => {this.setState({deployChangeUserPassword: true});}} disabled={this.state.currentUserIndex < 0 ? true:false}>Change Password</Button>
                  </GridContainer>
                </CardBody>
              </Card>
            </Hidden>
          </GridItem>
        </GridContainer>

        <GridContainer>
          <GridItem xs={12} sm={12} md={5}>
            <Card>
              <CardHeader color="warning">
                <h4 className={classes.cardTitleWhite}>Groups</h4>
              </CardHeader>
              <CardBody>
                <Button onClick={() => {this.setState({deployCreateGroup: true});}}>Create New Group</Button>
                <Table>
                  <TableHead>
                    <TableRow>
                      {this.groupColumnNames.map(
                        row => (
                          <TableCell
                            key = {row.id}
                          >
                            {row.label}
                          </TableCell>
                        )
                      )}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                  {this.state.groups.map(
                      (row, index) => (
                        <TableRow
                          onClick={(event) => {this.handleGroupRowClick(index, row.name); this.setState({deployMobileGroupDialog: true});}}
                          aria-checked={index === this.state.currentGroupIndex ? true : false}
                          key = {row.name}
                          selected={index === this.state.currentGroupIndex ? true : false}
                        >
                          <TableCell>
                            <Hidden smDown implementation="css">
                              <Checkbox
                                checked = {index === this.state.currentGroupIndex ? true : false}
                              />
                            </Hidden>
                            {row.name}
                          </TableCell>
                        </TableRow>
                      )
                    )}
                  </TableBody>
                </Table>
              </CardBody>
            </Card>
          </GridItem>
          <GridItem xs={12} sm={12} md={7}>
            <Hidden smDown implementation="css">
              <Card>
                <CardHeader color="success">
                  <h4></h4>
                </CardHeader>
                <CardBody>
                  {
                    this.state.currentGroupIndex < 0 ?
                    <div>
                      <h1>No group selected.</h1>
                    </div>
                    :
                    <div>
                      <h1>Group Name: {this.state.groups[this.state.currentGroupIndex].name}</h1>
                      <h3>Principal Name: {this.state.groups[this.state.currentGroupIndex].principalName}</h3>
                      <h3>Path: {this.state.groups[this.state.currentGroupIndex].path}</h3>
                      <h3>Members: {this.state.groups[this.state.currentGroupIndex].members}</h3>
                      <h3>Declared Members: {this.state.groups[this.state.currentGroupIndex].declaredMembers}</h3>
                    </div>
                  }
                  <GridContainer>
                    <Button onClick={() => {this.setState({deployDeleteGroup: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Delete Group</Button>
                    <Button onClick={() => {this.setState({deployAddGroupUsers: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Add User to Group</Button>
                    <Button onClick={() => {this.setState({deployRemoveGroupUsers: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Remove User from Group</Button>
                  </GridContainer>
                </CardBody>
              </Card>
            </Hidden>
          </GridItem>
        </GridContainer>
      </div>
    );
  }
}

export default withStyles (userboardStyle)(Userboard);