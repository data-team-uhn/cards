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

import withStyles from '@mui/styles/withStyles';

import { Avatar, Button, Card, CardContent, Grid, IconButton, Tooltip } from "@mui/material";

import userboardStyle from '../userboardStyle.jsx';
import CreateGroupDialogue from "./creategroupdialogue.jsx";
import DeletePrincipalDialogue from "../deleteprincipaldialogue.jsx";
import AddUserToGroupDialogue from "./addusertogroupdialogue.jsx";
import NewItemButton from "../../components/NewItemButton.jsx"
import AdminScreen from "../../adminDashboard/AdminScreen.jsx";
import DeleteIcon from '@mui/icons-material/Delete';
import CheckIcon from '@mui/icons-material/Check';
import MaterialReactTable from 'material-react-table';

const GROUP_URL = "/system/userManager/group/";

class GroupsManager extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      currentGroupUsers: [],
      currentGroupName: "",

      deployCreateGroup: false,
      deployDeleteGroup: false,
      deployAddGroupUsers: false,
      groupUsersLoaded: false
    };

    this.tableRef = React.createRef();
  }

  getGroupUsers (groupName){
    //Get groups filtering all users by group name
    let users = this.props.users.filter( (user) => {
            let memberOf = user.memberOf.map((group) => group.name);
            return memberOf.indexOf(groupName) > -1;
        });
    return users;
  }

  clearSelectedGroup () {
    this.setState(
      {
        currentGroupName: "",
      }
    );
  }

  handleRemoveUsers(currentGroupName, groupUsers) {
    let formData = new FormData();

    let selectedUsers = Object.keys(this.tableRef.current?.getState().rowSelection);
    for (var i = 0; i < selectedUsers.length; ++i) {
      formData.append(':member@Delete', groupUsers[selectedUsers[i]].name);
    }

    fetch(GROUP_URL + currentGroupName + ".update.html",
      {
        method: 'POST',
        credentials: 'include',
        body: formData
      })
      .then(() => {
        this.handleReload();
      })
      .catch((error) => {
        console.log(error);
      });
  }

  handleReload (doClear) {
    doClear && this.clearSelectedGroup();
    this.tableRef.current?.resetRowSelection();
    this.props.reload();
  }

  componentDidMount () {
    document.addEventListener("principals-reloaded", this.openDetailsPanel);
  }

  componentWillUnmount() {
    document.removeEventListener("principals-reloaded", this.openDetailsPanel);
  }

  render() {
    const { classes } = this.props;

    return (
      <AdminScreen
        title="Groups"
        action={
          <NewItemButton
            title="Create new group"
            onClick={(event) => this.setState({deployCreateGroup: true})}
          />
        }>
        <CreateGroupDialogue isOpen={this.state.deployCreateGroup} handleClose={() => {this.setState({deployCreateGroup: false});}} reload={() => this.handleReload(true)} />
        <DeletePrincipalDialogue isOpen={this.state.deployDeleteGroup} handleClose={() => {this.setState({deployDeleteGroup: false});}} name={this.state.currentGroupName} reload={() => this.handleReload(true)} url={GROUP_URL} type={"group"} />
        <AddUserToGroupDialogue isOpen={this.state.deployAddGroupUsers} handleClose={() => {this.setState({deployAddGroupUsers: false});}} name={this.state.currentGroupName} groupUsers={this.state.currentGroupUsers} allUsers={this.props.users}  reload={() => this.handleReload()} />
        <div className={classes.root}>
          <MaterialReactTable
            enableColumnActions={false}
            enableColumnFilters={false}
            enableSorting={false}
            enableToolbarInternalActions={false}
            initialState={{ showGlobalFilter: true }}
            muiTableHeadCellProps={{
              sx: (theme) => ({
                background: theme.palette.grey['200'],
              }),
            }}
            displayColumnDefOptions={{
              'mrt-row-actions': {
                size: 10,
                muiTableHeadCellProps: {align: 'right'},
                muiTableBodyCellProps: {
                  sx: {
                    textAlign: 'right'
                  },
                },
              },
              'mrt-row-expand': {
                size: 8,
              },
            }}
            columns={[
              { header: 'Avatar', accessorKey: 'imageUrl', size: 10,
                Cell: ({ row }) => (<Avatar src={row.original.imageUrl} className={classes.info}>{row.original.name.charAt(0)}</Avatar>)
              },
              { header: 'Name', accessorKey: 'name', size: 300, },
              { header: 'Members', accessorKey: 'members', size: 10, },
              { header: 'Declared Members', accessorKey: 'declaredMembers', size: 10, },
            ]}
            data={this.props.groups}
            enableRowActions
            positionActionsColumn="last"
            renderRowActions={({ row }) => (
              <Tooltip title="Delete Group">
                <IconButton onClick={ () => this.setState({currentGroupName: row.original.name, deployDeleteGroup: true}) } >
                  <DeleteIcon />
                </IconButton>
              </Tooltip>
            )}
            renderDetailPanel={({ row }) => {
                const group = row.original;
                const groupUsers = group.members > 0 ? this.getGroupUsers(group.name) : [];
                const tableTitle = "Group " + group.name + " users";

                return (
                  <div>
                    <Card className={classes.cardRoot}>
                      <CardContent>
                        { groupUsers.length > 0 &&
                            <MaterialReactTable
                              tableInstanceRef={this.tableRef}
                              enableColumnActions={false}
                              enableColumnFilters={false}
                              enableSorting={false}
                              enableTopToolbar={false}
                              muiTableHeadCellProps={{
                                sx: (theme) => ({
                                  color: theme.palette.text.primary,
                                }),
                              }}
                              enableRowSelection
                              enableSelectAll={false}
                              muiSelectCheckboxProps={{ color: 'primary' }}
                              displayColumnDefOptions={{
                                'mrt-row-select': {
                                  size: 7,
                                },
                              }}
                              muiTableBodyRowProps={({ row }) => ({
                                onClick: row.getToggleSelectedHandler(),
                                sx: {
                                  cursor: 'pointer',
                                },
                              })}
                              columns={[{
                                id: tableTitle,
                                header: tableTitle,
                                columns: [
                                  { header: 'Avatar', accessorKey: 'imageUrl', size: 10,
                                    Cell: ({ row }) => (<Avatar src={row.original.imageUrl} className={classes.info}>{row.original.initials}</Avatar>)
                                  },
                                  { header: 'User Name', accessorKey: 'name', size: 300, },
                                  { header: 'Admin', accessorKey: 'isAdmin', size: 10,
                                    Cell: ({ row }) => (row.original.isAdmin ? <CheckIcon /> : "")
                                  },
                                  { header: 'Disabled', accessorKey: 'isDisabled', size: 10,
                                    Cell: ({ row }) => (row.original.isDisabled ? <CheckIcon /> : "")
                                  },
                                ]
                              }]}
                              data={groupUsers}
                            />
                        }
                        <Grid container className={classes.cardActions}>
                          <Button
                            variant="contained"
                            color="primary"
                            size="small"
                            className={classes.containerButton}
                            onClick={() => { this.setState({currentGroupName: group.principalName,
                                                            deployAddGroupUsers: true,
                                                            currentGroupUsers: groupUsers});
                                           }
                            }
                          >
                            Add User to Group
                          </Button>
                          <Button
                            variant="contained"
                            color="secondary"
                            size="small"
                            onClick={() => { this.handleRemoveUsers(group.principalName, groupUsers) }}
                          >
                            Remove User from Group
                          </Button>
                        </Grid>
                      </CardContent>
                    </Card>
                  </div>
                )
            }}
          />
        </div>
      </AdminScreen>
    );
  }
}

export default withStyles (userboardStyle, {withTheme: true})(GroupsManager);
