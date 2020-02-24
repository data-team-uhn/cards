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

import { withStyles } from "@material-ui/core/styles";

import { Avatar, Button, Link, Card, CardHeader, CardContent, Grid, Table, TableBody, TableHead, TableRow, TableCell} from "@material-ui/core";

import userboardStyle from '../userboardStyle.jsx';
import CreateUserDialogue from "./createuserdialogue.jsx";
import DeleteUserDialogue from "./deleteuserdialogue.jsx";
import GroupDetailsDialogue from "./groupdetailsdialogue.jsx";
import ChangeUserPasswordDialogue from "./changeuserpassworddialogue.jsx";

import MaterialTable from 'material-table';

class UsersManager extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      users: [],
      userFilter: null,

      currentUserName: "",
      currentGroupName: "",
      currentUserIndex: -1,

      deployCreateUser: false,
      deployDeleteUser: false,
      deployChangeUserPassword: false,
      deployGroupDetails: false
    };
  }

  clearSelectedUser () {
    this.setState(
      {
        currentUserName: "",
        currentUserIndex: -1,
      }
    );
  }

  handleLoadUsers () {
    this.clearSelectedUser();

    fetch("/home/users.json",
      {
        method: 'GET',
        credentials: 'include'
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      var i;
      for (i = 0; i < data.rows.length; ++i) {
        if (data.rows[i].firstName && data.rows[i].lastName) {
            data.rows[i].initials = data.rows[i].fistname.charAt(0)+data.rows[i].lastname.charAt(0);
        } else {
            data.rows[i].initials = data.rows[i].name.charAt(0);
        }
      }

      this.setState(
        {
          users: data.rows,
        }
      );
    })
    .catch((error) => {
      console.log(error);
    });
  }

  componentWillMount () {
    this.handleLoadUsers();
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
        currentGroupName: name,
        deployGroupDetails: true
      }
    );
  }

  handleUserDeleteClick(index, name) {
    this.handleUserRowClick(index, name);
    this.setState({deployDeleteUser: true});
  }
  
  handleReload () {
    this.handleLoadUsers();
  }

  render() {
    const { classes } = this.props;
    const headerBackground = this.props.theme.palette.grey['200'];

    return (
      <div>
        <CreateUserDialogue isOpen={this.state.deployCreateUser} handleClose={() => {this.setState({deployCreateUser: false});}} reload={() => this.handleReload()}/>
        <DeleteUserDialogue isOpen={this.state.deployDeleteUser} handleClose={() => {this.setState({deployDeleteUser: false});}} name={this.state.currentUserName} reload={() => this.handleReload()}/>
        <ChangeUserPasswordDialogue isOpen={this.state.deployChangeUserPassword} handleClose={() => {this.setState({deployChangeUserPassword: false});}} name={this.state.currentUserName}/>

        <div>
            <MaterialTable
              title="User list"
              style={{ boxShadow : 'none' }}
              options={{
                actionsColumnIndex: -1,
                headerStyle: {backgroundColor: headerBackground},
                emptyRowsWhenPaging: false
              }}
              columns={[
                { title: 'Avatar', field: 'imageUrl', render: rowData => <Avatar src={rowData.imageUrl} className={classes.info}>{rowData.initials}</Avatar>},
                { title: 'User Name', field: 'name' },
                { title: 'First Name', field: 'firstName' },
                { title: 'Last Name', field: 'lastName' },
                { title: 'Admin', field: 'isAdmin', type: 'boolean' },
                { title: 'Disabled', field: 'isDisabled', type: 'boolean' },
              ]}
              data={this.state.users}
              actions={[
                {
                  icon: 'delete',
                  tooltip: 'Delete User',
                  onClick: (event, rowData) => this.handleUserDeleteClick(rowData.tableData.id, rowData.name)
                },
                {
                  icon: 'add_circle',
                  tooltip: 'Create New User',
                  isFreeAction: true,
                  onClick: (event) => this.setState({deployCreateUser: true})
                }
              ]}
              onRowClick={(event, rowData, togglePanel) => {this.handleUserRowClick(rowData.tableData.id, rowData.name); togglePanel()}}
              detailPanel={rowData => {
                const user = rowData || this.state.users[this.state.currentUserIndex];
                const isAdmin = user.isAdmin ? "True" : "False";
                const isDisabled = user.isDisabled ? "True" : "False";
                const hasGroup = user.memberOf.length > 0;
                return (
                <div>
                  <GroupDetailsDialogue isOpen={this.state.deployGroupDetails} name={this.state.currentGroupName} handleClose={() => {this.setState({deployGroupDetails: false, currentUserName: ""});}} />
                  <Card className={classes.cardRoot}>
                      <CardContent>
                      {
                        <div>
                          <Table>
                            <TableBody>
                              <TableRow>
                                <TableCell className={classes.cardCategory}>Principal Name</TableCell>
                                <TableCell className={classes.cardTitle}>{user.principalName}</TableCell>
                              </TableRow>
                              <TableRow>
                                <TableCell className={classes.cardCategory}>First Name</TableCell>
                                <TableCell className={classes.cardTitle}>{user.firstName}</TableCell>
                              </TableRow>
                              <TableRow>
                                <TableCell className={classes.cardCategory}>Last Name</TableCell>
                                <TableCell className={classes.cardTitle}>{user.lastName}</TableCell>
                              </TableRow>
                              <TableRow>
                                <TableCell className={classes.cardCategory}>Admin Status</TableCell>
                                <TableCell className={classes.cardTitle}>{isAdmin}</TableCell>
                              </TableRow>
                              <TableRow>
                                <TableCell className={classes.cardCategory}>Disabled</TableCell>
                                <TableCell className={classes.cardTitle}>{isDisabled}</TableCell>
                              </TableRow>
                              { hasGroup && <TableRow>
                                <TableCell className={classes.cardCategory}>Groups</TableCell>
                                <TableCell className={classes.cardTitle}>
                                  {user.memberOf.map(
                                    (row, index) => (
                                        <Link href="#" key = {row.name} onClick={() => {this.handleGroupNameClick(row.name)}}>
                                          {row.name}{(index < user.memberOf.length-1)&&<span>,</span>}
                                        </Link>
                                    )
                                  )}
                                </TableCell>
                              </TableRow>}
                            </TableBody>
                          </Table>
                        </div>
                      }
                    <Grid container className={classes.cardActions}>
                      <Button variant="contained" size="small" color="primary" onClick={() => {this.setState({currentUserName: user.principalName, deployChangeUserPassword: true});}}>Change Password</Button>
                    </Grid>
                  </CardContent>
                </Card>
                </div>
                )
              }}
            />
        </div>
      </div>
    );
  }
}

export default withStyles (userboardStyle, {withTheme: true})(UsersManager);
