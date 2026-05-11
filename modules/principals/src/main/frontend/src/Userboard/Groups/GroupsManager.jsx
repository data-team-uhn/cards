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

import { useState, useContext, useMemo, useCallback } from "react";

import CheckIcon from '@mui/icons-material/Check';
import DeleteIcon from '@mui/icons-material/Delete';
import {
  Alert,
  Avatar,
  Box,
  Button,
  Grid,
  IconButton,
  Tooltip
} from "@mui/material";
import { MaterialReactTable, useMaterialReactTable } from 'material-react-table';
import PropTypes from "prop-types";
import { withStyles } from 'tss-react/mui'

import AddUserToGroupDialog from "./AddUserToGroupDialog.jsx";
import CreateGroupDialog from "./CreateGroupDialog.jsx";
import AdminScreen from "../../adminDashboard/AdminScreen.jsx";
import NewItemButton from "../../components/NewItemButton.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../../login/ReLoginDialog.js";
import { checkPropTypes } from "../../propTypes";
import DeletePrincipalDialog from "../DeletePrincipalDialog.jsx";
import userboardStyles from '../userboardStyles.jsx';

const GROUP_URL = "/system/userManager/group/";

// separate component for the inner table to render/manage users belonging to the group
let GroupUsersTable = (props) => {
  let { group, classes, error, getGroupUsers, addUserToGroup, handleRemoveUsers } = props;

  // memoize data so it only changes when group changes to avoid infinite re-renders
  const groupUsers = useMemo(
    () => (group.members > 0 ? getGroupUsers(group.name) : []),
    [group, getGroupUsers]
  );

  const tableTitle = `Group ${group.name} users`;

  // memoize columns to avoid infinite re-renders
  const columns = useMemo(
    () => [
      {
        id: tableTitle,
        header: tableTitle,
        columns: [
          {
            header: "Avatar",
            accessorKey: "imageUrl",
            size: 10,
            Cell: ({ row }) => (
              <Avatar src={row.original.imageUrl} className={classes.info}>
                {row.original.initials}
              </Avatar>
            ),
          },
          { header: "User Name", accessorKey: "name", size: 300 },
          {
            header: "Admin",
            accessorKey: "isAdmin",
            size: 10,
            Cell: ({ row }) => (row.original.isAdmin ? <CheckIcon /> : ""),
          },
          {
            header: "Disabled",
            accessorKey: "isDisabled",
            size: 10,
            Cell: ({ row }) => (row.original.isDisabled ? <CheckIcon /> : ""),
          },
        ],
      },
    ],
    [classes.info, tableTitle]
  );

  // memoize row props callback to avoid infinite re-renders
  const muiTableBodyRowProps = useCallback(
    ({ row }) => ({
      onClick: row.getToggleSelectedHandler(),
      sx: { cursor: "pointer" },
    }),
    []
  );

  const table = useMaterialReactTable({
    enableColumnActions: false,
    enableColumnFilters: false,
    enableSorting: false,
    enableTopToolbar: false,
    enableRowSelection: true,
    enableSelectAll: false,
    enableRowActions: true,
    muiSelectCheckboxProps: { color: "primary" },
    displayColumnDefOptions: {
      "mrt-row-select": { size: 7 },
    },
    muiTableBodyRowProps,
    columns,
    data: groupUsers,
    renderRowActions: ({ cell, row, table }) => (
      <Tooltip title={"Remove from group"}>
        <IconButton component="span"
          onClick={() => handleRemoveUsers(group.name, groupUsers, table, [row.original.name])}
          size={"small"}>
          <DeleteIcon fontSize={"small"}/>
        </IconButton>
      </Tooltip>
    ),
  });

  return (
    <Grid container sx={{ py: 2 }}>
      <Grid size={1}></Grid>
      <Grid size={11}>
        {error && <Alert severity="error">{error}</Alert>}
        {groupUsers.length > 0 && <MaterialReactTable table={table} />}

        <Grid container className={classes.cardActions}>
          <Button
            variant="contained"
            className={classes.containerButton}
            onClick={() => addUserToGroup(group, groupUsers)}
          >
            Add User to Group
          </Button>
          <Button
            variant="contained"
            color="secondary"
            disabled={groupUsers.length == 0 || Object.keys(table.getState().rowSelection).length == 0}
            onClick={() => handleRemoveUsers(group.principalName, groupUsers, table)}
          >
            Remove User from Group
          </Button>
        </Grid>
      </Grid>
    </Grid>
  );
};


function GroupsManager(props) {
  checkPropTypes(GroupsManager, props);
  const { classes, groups, users, reload } = props;

  let [ currentGroupUsers, setCurrentGroupUsers ] = useState([]);
  let [ currentGroupName, setCurrentGroupName ] = useState("");
  let [ deployCreateGroup, setDeployCreateGroup ] = useState(false);
  let [ deployDeleteGroup, setDeployDeleteGroup ] = useState(false);
  let [ deployAddGroupUsers, setDeployAddGroupUsers ] = useState(false);
  let [ error, setError ] = useState("");

  const globalLoginDisplay = useContext(GlobalLoginContext);

  let getGroupUsers = (groupName) => {
    //Get groups filtering all users by group name
    let groupUsers = users.filter( (user) => {
      let memberOf = user.memberOf.map((group) => group.name);
      return memberOf.indexOf(groupName) > -1;
    });
    return groupUsers;
  }

  let addUserToGroup = (group, groupUsers) => {
    setCurrentGroupName(group.principalName);
    setDeployAddGroupUsers(true);
    setCurrentGroupUsers(groupUsers);
  }

  let clearSelectedGroup = () => {
    setCurrentGroupName("");
  }

  let handleRemoveUsers = (currentGroupName, groupUsers, table, users) => {
    setError("");
    if (!table) return;
    let formData = new FormData();

    let usersToRemove = users ? users : Object.keys(table.getState().rowSelection).map(user => groupUsers[user].name);
    if (usersToRemove.length == 0) return;
    for (let i = 0; i < usersToRemove.length; ++i) {
      formData.append(':member@Delete', usersToRemove[i]);
    }

    fetchWithReLogin(globalLoginDisplay, GROUP_URL + currentGroupName + ".update.html",
      {
        method: 'POST',
        credentials: 'include',
        body: formData
      })
      .then(() => handleReload(false, table))
      .catch((error) => setError(error?.statusText ?? error?.message ?? ("" + error)));
  }

  let handleReload = (doClear, table) => {
    doClear && clearSelectedGroup();
    table?.resetRowSelection();
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
              muiTableHeadCellProps: { align: 'right' },
              muiTableBodyCellProps: {
                sx: {
                  p: 0,
                },
              },
            },
            'mrt-row-expand': {
              size: 4,
            },
          }}
          columns={[
            { header: 'Avatar', accessorKey: 'imageUrl', size: 10,
              Cell: ({ row }) =>
                <Avatar src={row.original.imageUrl} className={classes.info}>{row.original.name.charAt(0)}</Avatar>
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
          renderDetailPanel={({ row }) =>
            <GroupUsersTable
              group={row.original}
              classes={classes}
              error={error}
              addUserToGroup={addUserToGroup}
              getGroupUsers={getGroupUsers}
              handleRemoveUsers={handleRemoveUsers}
            />
          }
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

export default withStyles (GroupsManager, userboardStyles);
