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

import React, { useState, useRef, useContext } from "react";
import PropTypes from "prop-types";
import { checkPropTypes } from "../../propTypes";
import { withStyles } from 'tss-react/mui'
import { Avatar, Box, Button, Grid, IconButton, Tooltip } from "@mui/material";
import userboardStyle from '../userboardStyle.jsx';
import CreateGroupDialog from "./CreateGroupDialog.jsx";
import DeletePrincipalDialog from "../DeletePrincipalDialog.jsx";
import AddUserToGroupDialog from "./AddUserToGroupDialog.jsx";
import NewItemButton from "../../components/NewItemButton.jsx"
import AdminScreen from "../../adminDashboard/AdminScreen.jsx";
import DeleteIcon from '@mui/icons-material/Delete';
import CheckIcon from '@mui/icons-material/Check';
import MaterialReactTable from 'material-react-table';
import { fetchWithReLogin, GlobalLoginContext } from "../../login/ReLoginDialog.js";

const GROUP_URL = "/system/userManager/group/";

function GroupsManager(props) {
  checkPropTypes(GroupsManager, props);
  const { classes, groups, users, reload } = props;

  let [ currentGroupUsers, setCurrentGroupUsers ] = useState([]);
  let [ currentGroupName, setCurrentGroupName ] = useState("");
  let [ deployCreateGroup, setDeployCreateGroup ] = useState(false);
  let [ deployDeleteGroup, setDeployDeleteGroup ] = useState(false);
  let [ deployAddGroupUsers, setDeployAddGroupUsers ] = useState(false);

  let tableRef = useRef();
  const globalLoginDisplay = useContext(GlobalLoginContext);

  let getGroupUsers = (groupName) => {
    //Get groups filtering all users by group name
    let groupUsers = users.filter( (user) => {
            let memberOf = user.memberOf.map((group) => group.name);
            return memberOf.indexOf(groupName) > -1;
        });
    return groupUsers;
  }

  let clearSelectedGroup = () => {
    setCurrentGroupName("");
  }

  let handleRemoveUsers = (currentGroupName, groupUsers) => {
    if (!tableRef.current) return;
    let formData = new FormData();

    let selectedUsers = Object.keys(tableRef.current?.getState().rowSelection);
    for (var i = 0; i < selectedUsers.length; ++i) {
      formData.append(':member@Delete', groupUsers[selectedUsers[i]].name);
    }

    fetchWithReLogin(globalLoginDisplay, GROUP_URL + currentGroupName + ".update.html",
      {
        method: 'POST',
        credentials: 'include',
        body: formData
      })
      .then(handleReload)
      .catch((error) => console.log(error?.statusText ?? error));
  }

  let handleReload = (doClear) => {
    doClear && clearSelectedGroup();
    tableRef.current?.resetRowSelection();
    reload();
  }

  return (
      <AdminScreen
        title="Groups"
        action={
          <NewItemButton
            title="Create new group"
            onClick={(event) => setDeployCreateGroup(true)}
          />
        }>
        <CreateGroupDialog
          isOpen={deployCreateGroup}
          handleClose={() => setDeployCreateGroup(false)}
          reload={() => handleReload(true)}
        />
        <DeletePrincipalDialog
          isOpen={deployDeleteGroup}
          handleClose={() => setDeployDeleteGroup(false)}
          name={currentGroupName}
          reload={() => handleReload(true)}
          url={GROUP_URL}
          type="group"
        />
        <AddUserToGroupDialog
          isOpen={deployAddGroupUsers}
          handleClose={() => setDeployAddGroupUsers(false)}
          name={currentGroupName}
          groupUsers={currentGroupUsers}
          allUsers={users}
          reload={handleReload}
        />
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
                    padding: '0',
                  },
                },
              },
              'mrt-row-expand': {
                size: 4,
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
            data={groups}
            enableRowActions
            positionActionsColumn="last"
            renderRowActions={({ row }) => (
              <Box sx={{ display: 'flex', flexWrap: 'nowrap', float: 'right' }}>
                <Tooltip title="Delete Group">
                  <IconButton
                    onClick={() => { setCurrentGroupName(row.original.name);
                                     setDeployDeleteGroup(true);}}
                  >
                    <DeleteIcon />
                  </IconButton>
                </Tooltip>
              </Box>
            )}
            renderDetailPanel={({ row }) => {
                const group = row.original;
                const groupUsers = group.members > 0 ? getGroupUsers(group.name) : [];
                const tableTitle = "Group " + group.name + " users";

                return (
                  <Grid container sx={(theme) => ({ py: theme.spacing(2) })}>
                    <Grid size={1}></Grid>
                    <Grid size={11}>
                        { groupUsers.length > 0 &&
                            <MaterialReactTable
                              tableInstanceRef={tableRef}
                              enableColumnActions={false}
                              enableColumnFilters={false}
                              enableSorting={false}
                              enableTopToolbar={false}
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
                            className={classes.containerButton}
                            onClick={() => { setCurrentGroupName(group.principalName);
                                             setDeployAddGroupUsers(true);
                                             setCurrentGroupUsers(groupUsers); }}
                          >
                            Add User to Group
                          </Button>
                          <Button
                            variant="contained"
                            color="secondary"
                            disabled={groupUsers.length == 0}
                            onClick={() => handleRemoveUsers(group.principalName, groupUsers)}
                          >
                            Remove User from Group
                          </Button>
                        </Grid>
                      </Grid>
                    </Grid>
                )
            }}
          />
        </div>
      </AdminScreen>
    );
}

GroupsManager.propTypes = {
  groups: PropTypes.array,
  users: PropTypes.array,
  reload: PropTypes.func.isRequired
}

export default withStyles (GroupsManager, userboardStyle);
