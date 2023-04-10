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
import withStyles from '@mui/styles/withStyles';

import { Avatar, Box, Card, CardContent, IconButton, Tooltip } from "@mui/material";

import userboardStyle from '../userboardStyle.jsx';
import CreateUserDialogue from "./createuserdialogue.jsx";
import DeletePrincipalDialogue from "../deleteprincipaldialogue.jsx";
import ChangeUserPasswordDialogue from "./changeuserpassworddialogue.jsx";
import NewItemButton from "../../components/NewItemButton.jsx";
import AdminScreen from "../../adminDashboard/AdminScreen.jsx";

import MaterialReactTable from 'material-react-table';
import LockIcon from '@mui/icons-material/Lock';
import DeleteIcon from '@mui/icons-material/Delete';
import CheckIcon from '@mui/icons-material/Check';



const USER_URL = "/system/userManager/user/";

class UsersManager extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      currentUserName: "",
      currentGroupName: "",

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

  handleReload () {
    this.setState({currentUserName: ""});
    this.props.reload();
  }

  render() {
    const { classes, history } = this.props;

    return (
      <AdminScreen
        title="Users"
        action={
          <NewItemButton
            title="Create new user"
            onClick={() => this.setState({deployCreateUser: true})}
          />
        }>
        <CreateUserDialogue
          isOpen={this.state.deployCreateUser}
          handleClose={() => {this.setState({deployCreateUser: false});}}
          reload={() => this.handleReload()}
        />
        <DeletePrincipalDialogue
          isOpen={this.state.deployDeleteUser}
          handleClose={() => {this.setState({deployDeleteUser: false});}}
          name={this.state.currentUserName}
          reload={() => this.handleReload()}
          url={USER_URL} type={"user"}
        />
        <ChangeUserPasswordDialogue 
          isOpen={this.state.deployChangeUserPassword}
          handleClose={() => {this.setState({deployChangeUserPassword: false});}}
          name={this.state.currentUserName}
        />

        <div className={classes.root}>
          <MaterialReactTable
              enableColumnActions={false}
              enableColumnFilters={false}
              enableSorting={false}
              muiTableHeadCellProps={{
                sx: (theme) => ({
                  background: theme.palette.grey['200'],
                }),
              }}
              columns={[
                { header: 'Avatar', accessorKey: 'imageUrl', size: 10,
                  Cell: ({ renderedCellValue, row }) => (<Avatar src={row.original.imageUrl} className={classes.info}>{row.original.initials}</Avatar>)
                },
                { header: 'User Name', accessorKey: 'name' },
                { header: 'Admin', accessorKey: 'isAdmin', size: 10,
                  Cell: ({ renderedCellValue, row }) => (row.original.isAdmin ? <CheckIcon /> : "")
                },
                { header: 'Disabled', accessorKey: 'isDisabled', size: 10,
                  Cell: ({ renderedCellValue, row }) => (row.original.isDisabled ? <CheckIcon /> : "")
                },
              ]}
              displayColumnDefOptions={{
                'mrt-row-actions': {
                  muiTableHeadCellProps: {align: 'right'},
                  muiTableBodyCellProps: ({ cell }) => ({
                    sx: {
                      padding: '0',
                      textAlign: 'right'
                    },
                  }),
                },
                'mrt-row-expand': {
                  size: 8,
                },
              }}
              data={this.props.users}
              enableRowActions
              positionActionsColumn="last"
              renderRowActions={({ row }) => (
                <Box sx={{ display: 'flex', flexWrap: 'nowrap', gap: '0', float: 'right' }}>
                <>
                  <Tooltip title="Change Password">
                    <IconButton onClick={ () => this.setState({currentUserName: row.original.name, deployChangeUserPassword: true}) } >
                      <LockIcon />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Delete User">
                    <IconButton onClick={ () => this.setState({currentUserName: row.original.name, deployDeleteUser: true}) } >
                      <DeleteIcon />
                    </IconButton>
                  </Tooltip>
                </>
                </Box>
              )}
              renderDetailPanel={({ row }) => {
                const user = row.original;
                const currentUserGroups = user.memberOf.length > 0 ? this.getUserGroups(user.memberOf) : [];
                const tableTitle = "User " + user.name + " Groups";

                return currentUserGroups.length > 0 && (
                  <div>
                    <Card className={classes.cardRoot}>
                      <CardContent>
                      {
                        <div>
                          <MaterialReactTable
                              enableColumnActions={false}
                              enableColumnFilters={false}
                              enableSorting={false}
                              enableTopToolbar={false}
                              columns={[{
                                id: tableTitle,
                                header: tableTitle,
                                columns: [
                                  { header: 'Avatar', accessorKey: 'imageUrl', size: 10,
                                    Cell: ({ renderedCellValue, row }) => ( <Avatar src={row.original.imageUrl} className={classes.info}>{row.original.name.charAt(0)}</Avatar> )
                                  },
                                  { header: 'Name', accessorKey: 'name', muiTableBodyCellProps: {align: 'left'} },
                                  { header: 'Members', accessorKey: 'members', size: 10,
                                    muiTableBodyCellProps: {align: 'left'}, muiTableHeadCellProps: {align: 'left'}
                                  },
                                  { header: 'Declared Members', accessorKey: 'declaredMembers', size: 10,                                   
                                    muiTableBodyCellProps: {align: 'left'}, muiTableHeadCellProps: {align: 'left'}
                                  },
                                ]
                              }]}
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
      </AdminScreen>
    );
  }
}

export default withStyles (userboardStyle, {withTheme: true})(withRouter(UsersManager));
