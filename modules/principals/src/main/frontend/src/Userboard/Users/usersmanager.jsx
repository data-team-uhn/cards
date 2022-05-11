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
import { withRouter } from "react-router-dom";
import withStyles from '@material-ui/styles/withStyles';

import { Avatar, Button, Link, Card, CardHeader, CardContent, Grid, Table, TableBody, TableHead, TableRow, TableCell } from "@material-ui/core";

import userboardStyle from '../userboardStyle.jsx';
import CreateUserDialogue from "./createuserdialogue.jsx";
import DeletePrincipalDialogue from "../deleteprincipaldialogue.jsx";
import ChangeUserPasswordDialogue from "./changeuserpassworddialogue.jsx";
import NewItemButton from "../../components/NewItemButton.jsx";

import MaterialTable from 'material-table';

const USER_URL = "/system/userManager/user/";

class UsersManager extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      userFilter: null,

      currentUserName: "",
      currentGroupName: "",
      currentUserIndex: -1,

      deployCreateUser: false,
      deployDeleteUser: false,
      deployChangeUserPassword: false,
    };
  }

  getUserGroups (userGroups){
    //Get groups filtering all groups by user name
    let memberOf = userGroups.map((group) => group.name);
    let groups = this.props.groups.filter( (group) => {
              return memberOf.indexOf(group.name) > -1;
          });
    return groups;
  }

  clearSelectedUser () {
    this.setState(
      {
        currentUserName: "",
        currentUserIndex: -1,
      }
    );
  }

  handleUserRowClick(index, name) {
    this.setState(
      {
        currentUserName: name,
        currentUserIndex: index
      }
    );
  }

  handleGroupNameClick(name) {
    this.setState(
      {
        currentGroupName: name
      }
    );
  }

  handleUserDeleteClick(index, name) {
    this.handleUserRowClick(index, name);
    this.setState({deployDeleteUser: true});
  }

  handleReload () {
    this.clearSelectedUser();
    this.props.reload();
  }

  render() {
    const { classes, history } = this.props;
    const headerBackground = this.props.theme.palette.grey['200'];

    return (
      <div>
        <CreateUserDialogue isOpen={this.state.deployCreateUser} handleClose={() => {this.setState({deployCreateUser: false});}} reload={() => this.handleReload()}/>
        <DeletePrincipalDialogue isOpen={this.state.deployDeleteUser} handleClose={() => {this.setState({deployDeleteUser: false});}} name={this.state.currentUserName} reload={() => this.handleReload()} url={USER_URL} type={"user"} />
        <ChangeUserPasswordDialogue isOpen={this.state.deployChangeUserPassword} handleClose={() => {this.setState({deployChangeUserPassword: false});}} name={this.state.currentUserName}/>

        <div>
            <MaterialTable
              title=""
              style={{ boxShadow : 'none' }}
              options={{
                actionsColumnIndex: -1,
                headerStyle: {backgroundColor: headerBackground},
                emptyRowsWhenPaging: false
              }}
              columns={[
                { title: 'Avatar', field: 'imageUrl', render: rowData => <Avatar src={rowData.imageUrl} className={classes.info}>{rowData.initials}</Avatar>},
                { title: 'User Name', field: 'name' },
                { title: 'Admin', field: 'isAdmin', type: 'boolean' },
                { title: 'Disabled', field: 'isDisabled', type: 'boolean' },
              ]}
              data={this.props.users}
              actions={[
                {
                  icon: 'lock',
                  tooltip: 'Change Password',
                  onClick: (event, rowData) => this.setState({currentUserName: rowData.name, deployChangeUserPassword: true})
                },
                {
                  icon: 'delete',
                  tooltip: 'Delete User',
                  onClick: (event, rowData) => this.handleUserDeleteClick(rowData.tableData.id, rowData.name)
                }
              ]}
              onRowClick={(event, rowData, togglePanel) => {this.handleUserRowClick(rowData.tableData.id, rowData.name); togglePanel()}}
              detailPanel={rowData => {
                const user = rowData || this.props.users[this.state.currentUserIndex];
                const currentUserGroups = user.memberOf.length > 0 ? this.getUserGroups(user.memberOf) : [];
                const tableTitle = "User " + user.name + " Groups";

                return currentUserGroups.length > 0 && (
                  <div>
                    <Card className={classes.cardRoot}>
                      <CardContent>
                      {
                        <div>
                          <MaterialTable
                              title={tableTitle}
                              style={{ boxShadow : 'none' }}
                              options={{
                                headerStyle: { backgroundColor: headerBackground },
                                emptyRowsWhenPaging: false
                              }}
                              columns={[
                                  { title: 'Avatar', field: 'imageUrl', render: rowData => <Avatar src={rowData.imageUrl} className={classes.info}>{rowData.name.charAt(0)}</Avatar> },
                                  { title: 'Name', field: 'name', cellStyle: {textAlign: 'left'} },
                                  { title: 'Members', field: 'members', type: 'numeric', cellStyle: {textAlign: 'left'}, headerStyle: {textAlign: 'left', flexDirection: 'initial'} },
                                  { title: 'Declared Members', field: 'declaredMembers', type: 'numeric', cellStyle: {textAlign: 'left'}, headerStyle: {textAlign: 'left', flexDirection: 'initial'} },
                              ]}
                              data={currentUserGroups}
                          />
                        </div>
                      }
                    </CardContent>
                  </Card>
                </div> 
                ) || (<div>User is not in any group</div>)
              }}
            />
        </div>
        <NewItemButton
          title="Create new user"
          onClick={(event) => this.setState({deployCreateUser: true})}
        />
      </div>
    );
  }
}

export default withStyles (userboardStyle, {withTheme: true})(withRouter(UsersManager));
