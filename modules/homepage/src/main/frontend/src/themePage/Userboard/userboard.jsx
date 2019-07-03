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

//import Button from "material-dashboard-react/dist/components/CustomButtons/Button.js";


import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";
import GridContainer from "material-dashboard-react/dist/components/Grid/GridContainer.js";
//import Table from "material-dashboard-react/dist/components/Table/Table.js";

import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
import CardFooter from "material-dashboard-react/dist/components/Card/CardFooter"
//import { Avatar } from "@material-ui/core";
import CustomInput from "material-dashboard-react/dist/components/CustomInput/CustomInput.js";

import Button from '@material-ui/core/Button';
import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableHead from '@material-ui/core/TableHead';
import TableRow from '@material-ui/core/TableRow';
import TableCell from '@material-ui/core/TableCell';
import Checkbox from '@material-ui/core/Checkbox';

import userboardStyle from './userboardStyle.jsx';

import {CreateUserDialogue, DeleteUserDialogue, ChangeUserPasswordDialogue, CreateGroupDialogue, DeleteGroupDialogue} from './userboardDialogues.jsx';

class Userboard extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      userNames: [],
      groupNames: [],
      currentUserName: "",
      currentGroupName: "",
      deployCreateUser: false,
      deployDeleteUser: false,
      deployChangeUserPassword: false,
      deployCreateGroup: false,
      deployDeleteGroup: false,
    };

    this.userColumnNames = [{id: "name", label: "User Names"}];
    this.groupColumnNames = [{id: "name", label: "Group Names"}];
  }

  hideCreateUser () {
    this.setState({deployCreateUser: false});
  }

  showCreateUser () {
    this.setState({deployCreateUser: true});
  }

  hideDeleteUser () {
    this.setState({deployDeleteUser: false});
  }

  showDeleteUser () {
    this.setState({deployDeleteUser: true});
  }

  hideChangeUserPassword () {
    this.setState({deployChangeUserPassword: false});
  }

  showChangeUserPassword () {
    this.setState({deployChangeUserPassword: true});
  }
 
  hideCreateGroup () {
    this.setState({deployCreateGroup: false});
  }

  showCreateGroup () {
    this.setState({deployCreateGroup: true});
  }

  hideDeleteGroup () {
    this.setState({deployDeleteGroup: false});
  }

  showDeleteGroup () {
    this.setState({deployDeleteGroup: true});
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
      console.log(JSON.stringify(data));
      var names = [];
      for (var username in data){
        names.push(this.addName(username));
      }
      console.log(names);
      this.setState({userNames: names});
      
    })
    .catch((error) => {
      console.log(error);
    });
  }

  addGroup (name) {
    return {name};
  }

  handleLoadGroups () {
    fetch("http://localhost:8080/system/userManager/group.tidy.1.json",
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
      var groups = [];
      for (var group in data) {
        groups.push(this.addGroup(group));
      }
      this.setState({groupNames: groups});
    })
    .catch((error) => {
      console.log(error);
    })
  }

  componentWillMount () {
    this.handleLoadUsers();
    this.handleLoadGroups();
  }

  handleUserRowClick(event, name) {
    this.setState({currentUserName: name});
  }

  handleGroupRowClick(event, name) {
    this.setState({currentGroupName: name});
  }

  render() {
    const { classes } = this.props;

    return (
      <div>
        {this.state.deployCreateUser && <CreateUserDialogue handleClose={() => this.hideCreateUser()}></CreateUserDialogue>}
        {this.state.deployDeleteUser && <DeleteUserDialogue handleClose={() => this.hideDeleteUser()} name={this.state.currentUserName}></DeleteUserDialogue>}
        {this.state.deployChangeUserPassword && <ChangeUserPasswordDialogue handleClose={() => this.hideChangeUserPassword()} name={this.state.currentUserName}></ChangeUserPasswordDialogue>}
        {this.state.deployCreateGroup && <CreateGroupDialogue handleClose={() => this.hideCreateGroup()}></CreateGroupDialogue>}
        {this.state.deployDeleteGroup && <DeleteGroupDialogue handleClose={() => this.hideDeleteGroup()} name={this.state.currentGroupName}></DeleteGroupDialogue>}
        <GridContainer>
          <GridItem xs={12} sm={12} md={7}>
            <Card>
              <CardHeader color="warning">
                <h4 className={classes.cardTitleWhite}>Users</h4>
              </CardHeader>
              <CardBody>
                <Button onClick={() => this.showCreateUser()}>Create New User</Button>
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
                    {this.state.userNames.map(
                      (row, index) => (
                        <TableRow
                          onClick={(event) => this.handleUserRowClick(event, row.name)}
                          aria-checkted={row.name === this.state.currentUserName ? true: false}
                          key = {row.name}
                          selected={row.name === this.state.currentUserName ? true:false}
                        >
                          <TableCell>
                            <Checkbox
                              checked = {row.name === this.state.currentUserName ? true:false}
                            />
                          </TableCell>
                          <TableCell>{row.name}</TableCell>
                        </TableRow>
                      )
                    )}
                  </TableBody>
                </Table>
              </CardBody>
            </Card>
          </GridItem>
          <GridItem xs={12} sm={12} md={5}>
            <Card>
              <CardBody>
                User Name: {this.state.currentUserName}
                <Button onClick={() => this.showDeleteUser()}>Delete User</Button>
                <Button onClick={() => this.showChangeUserPassword()}>Change Password</Button>
              </CardBody>
            </Card>
          </GridItem>
        </GridContainer>

        <GridContainer>
          <GridItem xs={12} sm={12} md={7}>
            <Card>
              <CardHeader color="warning">
                <h4 className={classes.cardTitleWhite}>Groups</h4>
              </CardHeader>
              <CardBody>
                <Button onClick={() => this.showCreateGroup()}>Create New Group</Button>
                <Table>
                  <TableHead>
                    {this.groupColumnNames.map(
                      row => (
                        <TableCell
                          key = {row.id}
                        >
                          {row.label}
                        </TableCell>
                      )
                    )}
                  </TableHead>
                  <TableBody>
                  {this.state.groupNames.map(
                      (row, index) => (
                        <TableRow
                          onClick={(event) => this.handleGroupRowClick(event, row.name)}
                          aria-checkted={row.name === this.state.currentGroupName ? true: false}
                          key = {row.name}
                          selected={row.name === this.state.currentGroupName ? true:false}
                        >
                          <TableCell>
                            <Checkbox
                              checked = {row.name === this.state.currentGroupName ? true:false}
                            />
                          </TableCell>
                          <TableCell>{row.name}</TableCell>
                        </TableRow>
                      )
                    )}
                  </TableBody>
                </Table>
              </CardBody>
            </Card>
          </GridItem>
          <GridItem xs={12} sm={12} md={5}>
            <Card>
              <CardBody>
                Group Name: {this.state.currentGroupName}
                <Button onClick={() => this.showDeleteGroup()}>Delete Group</Button>
              </CardBody>
            </Card>
          </GridItem>
        </GridContainer>
        
      </div>
    );
  }
}

export default withStyles (userboardStyle)(Userboard);