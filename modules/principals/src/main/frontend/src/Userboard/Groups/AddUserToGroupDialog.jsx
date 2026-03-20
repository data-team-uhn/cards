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

import { useState, useContext } from "react";

import CheckIcon from '@mui/icons-material/Check';
import {
  Avatar,
  Button,
  Dialog,
  DialogTitle,
  DialogActions,
  DialogContent,
  Grid
} from "@mui/material";
import { MaterialReactTable, useMaterialReactTable } from 'material-react-table';
import PropTypes from "prop-types";
import { withStyles } from 'tss-react/mui';

import { fetchWithReLogin, GlobalLoginContext } from "../../login/ReLoginDialog.js";
import { checkPropTypes } from "../../propTypes";
import userboardStyles from '../userboardStyles.jsx';

const GROUP_URL="/system/userManager/group/";

function AddUserToGroupDialog(props) {
  "use no memo";
  checkPropTypes(AddUserToGroupDialog, props);
  const { classes, name, allUsers, groupUsers, reload, isOpen, handleClose } = props;

  let [ freeUsers, setFreeUsers ] = useState([]);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  let handleAddUsers = () => {
    let formData = new FormData();

    let selectedUsers = Object.keys(table?.getState().rowSelection);
    for (let i = 0; i < selectedUsers.length; ++i) {
      formData.append(':member', freeUsers[selectedUsers[i]].name);
    }

    fetchWithReLogin(globalLoginDisplay, GROUP_URL + name + ".update.html",
      {
        method: 'POST',
        credentials: 'include',
        body: formData
      })
      .then(() => {
        reload(false, table);
        handleClose();
      })
      .catch((error) => {
        console.log(error?.statusText ?? error);
      });
  }

  let handleEntering = () => {
    setFreeUsers(allUsers);
    if (Array.isArray(groupUsers)) {
      let groupUsersArray = groupUsers.map((n) => n.name);
      let filtered = allUsers.filter(el => !groupUsersArray.includes( el.name ));
      setFreeUsers(filtered);
    }
  }

  let table = useMaterialReactTable({
    enableColumnActions: false,
    enableColumnFilters: false,
    enableSorting: false,
    enableTopToolbar: false,
    muiTableHeadCellProps: {
      sx: (theme) => ({
        background: theme.palette.grey['200'],
      }),
    },
    enableRowSelection: true,
    enableSelectAll: false,
    muiSelectCheckboxProps: { color: 'primary' },
    muiTableBodyRowProps: ({ row }) => ({
      onClick: row.getToggleSelectedHandler(),
      sx: {
        cursor: 'pointer',
      },
    }),
    columns: [
      { header: 'Avatar', accessorKey: 'imageUrl', size: 8,
        Cell: ({ row }) =>
          <Avatar src={row.original.imageUrl} className={classes.info}>{row.original.initials}</Avatar>
      },
      { header: 'User Name', accessorKey: 'name' },
      { header: 'Admin', accessorKey: 'isAdmin', size: 10,
        Cell: ({ row }) => (row.original.isAdmin ? <CheckIcon /> : "")
      },
      { header: 'Disabled', accessorKey: 'isDisabled', size: 10,
        Cell: ({ row }) => (row.original.isDisabled ? <CheckIcon /> : "")
      },
    ],
    data: freeUsers
  });

  return (
    <Dialog
      maxWidth="sm"
      open={isOpen}
      onClose={() => {
        table.resetRowSelection();
        handleClose();
      }}
      slotProps={{ transition: {
        onEntering: () => handleEntering(),
      },
      }}
    >
      <DialogTitle>
        Add Users to the {name} group
      </DialogTitle>
      <DialogContent>
        <Grid container>
          <div>
            <MaterialReactTable table={table}/>
          </div>
        </Grid>
      </DialogContent>
      <DialogActions className={classes.dialogActions}>
        <Button variant="outlined" onClick={() => { table.resetRowSelection(); handleClose(); }}>Cancel</Button>
        <Button variant="contained" onClick={handleAddUsers}>Add</Button>
      </DialogActions>
    </Dialog>
  );
}

AddUserToGroupDialog.propTypes = {
  name: PropTypes.string.isRequired,
  allUsers: PropTypes.array,
  groupUsers: PropTypes.array,
  isOpen: PropTypes.bool.isRequired,
  reload: PropTypes.func.isRequired,
  handleClose: PropTypes.func.isRequired
}

export default withStyles(AddUserToGroupDialog, userboardStyles);
