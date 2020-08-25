//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//
import React, { useState } from "react";
import { withRouter } from "react-router-dom";

import {
  Avatar,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  TextField,
  Typography,
  withStyles
} from "@material-ui/core";
import LockIcon from "@material-ui/icons/Lock";
import MaterialTable from "material-table";

import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

function PermissionsButton(props) {
  const states ={
    CLOSED: "closed",
    POLICIES: "policies",
    NEW: "new"
  }

  const privilegeNames = {
    VIEW:"View",
    EDIT:"Edit",
    MANAGE: "Manage",
    ALL: "All",
    UNKNOWN: ""
  }
  const privilegeJcr = {
    VIEW: ["jcr:read"],
    EDIT: ["jcr:read","jcr:write"].sort(),
    MANAGE: ["jcr:read","jcr:write","jcr:readAccessControl","jcr:modifyAccessControl"].sort(),
    ALL: ["jcr:all"],
    UNKNOWN: []
  }

  const { classes, entryPath, entryName, warning, onComplete, entryType, size, shouldGoBack } = props;
  const [ openState, setOpenState ] = useState(states.CLOSED);
  const [ policies, setPolicies ] = useState([]);
  const [ users, setUsers ] = useState();
  const [ selectedUsers, setSelectedUsers ] = useState();
  const [ privilege, setPrivilege] = useState(privilegeNames.VIEW);

  let setState = (newState) => {
    if (openState !== newState) {
      setOpenState(newState);
    }
  }

  let close = () => { setState(states.CLOSED); }
  let openPolicies = () => { setState(states.POLICIES); }
  let openNew = () => {
    setState(states.NEW);
    setPrivilege(privilegeNames.VIEW);
    setUsers([]);
    setSelectedUsers([]);
    getUsers().then((loadedUsers) => getGroups(loadedUsers));
  }

  let handlePrivilegeChange = (event) => {
    setPrivilege(event.target.value);
  }

  let handleIconClicked = () => {
    getPermissions().then(openPolicies);
  }

  let handleUserRowClick = (rows) => {
    let chosens = rows.map((row) => row.name);
    setSelectedUsers(chosens);
}

  let handlePolicyDelete = (rowData) => {
    let request_data = new FormData();
    let url = new URL(entryPath + ".permissions", window.location.origin);
    url.searchParams.set(":rule", "allow");
    url.searchParams.set(":privileges", privilegeNameToJcr(rowData.privileges));
    url.searchParams.set(":principal", rowData.principal);
    url.searchParams.set(":restriction", rowData.restrictions);
    url.searchParams.set(":action", "remove");
    fetch( url, {
      method: 'POST',
      body: request_data,
      headers: {
        Accept: "application/json"
    }}).then((response) => {
      if (!response.ok) Promise.reject(response);
    }).then(getPermissions);
  }

  let privilegeNameToJcr = (name) => {
    switch (name) {
      case privilegeNames.VIEW:
        return privilegeJcr.VIEW;
      case privilegeNames.EDIT:
        return privilegeJcr.EDIT;
      case privilegeNames.MANAGE:
        return privilegeJcr.MANAGE;
      case privilegeNames.ALL:
        return privilegeJcr.ALL;
      default:
        return privilegeJcr.UNKNOWN;
    }
  }

  let privilegeJcrToName = (jcr) => {
    jcr.sort();

    if (arrayEquals(jcr, privilegeJcr.VIEW)) return privilegeNames.VIEW;
    if (arrayEquals(jcr, privilegeJcr.EDIT)) return privilegeNames.EDIT;
    if (arrayEquals(jcr, privilegeJcr.MANAGE)) return privilegeNames.MANAGE;
    if (arrayEquals(jcr, privilegeJcr.ALL)) return privilegeNames.ALL;

    return privilegeNames.UNKNOWN;
  }

  let arrayEquals = (arr1, arr2) => {
    if (arr1 === arr2) return true;
    if (arr1 == null || arr2 == null) return false;
    if (arr1.length !== arr2.length) return false;

    for (let i = 0; i < arr1.length; i++) {
      if (arr1[i] !== arr2[i]) return false;
    }
    return true;
  }

  let getPermissions = () => {
    let request_data = new FormData();
    let url = new URL(entryPath + ".permissions", window.location.origin);
    url.searchParams.set(":action", "get");
    return fetch( url, {
      method: 'post',
      body: request_data,
      headers: {
        Accept: "application/json"
      }}).then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {
          if (json && json.policies) {
            json.policies.forEach((policy, index, array) => {
              let priv = privilegeJcrToName(policy.privileges);
              if (priv !== privilegeNames.UNKNOWN) {
                array[index].privileges = priv;
              }
            });
          }
          setPolicies(json?.policies ? json.policies : []);
        })
        .catch(() => {setPolicies([])});
  }

  let getUsers = () => {
    return fetch("/home/users.json",
      {
        method: 'GET',
        credentials: 'include'
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      data.rows?.forEach((r) => r.initials = (r.firstname?.charAt(0) + r.lastname?.charAt(0)) || r.name?.charAt(0) || '?')
      return data.rows;
    })
    .catch((error) => {
      console.log(error);
    })
  }

  let getGroups = (loadedUsers) => {
    let savedUsers = loadedUsers;
    fetch("/home/groups.json",
      {
        method: 'GET',
        credentials: 'include'
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      setUsers(savedUsers.concat(data.rows));
    })
    .catch((error) => {
      console.log(error);
    })
  }

  let setPermissions = () => {
    let request_data = new FormData();
    let url = new URL(entryPath + ".permissions", window.location.origin);
    url.searchParams.set(":rule", "allow");
    url.searchParams.set(":privileges", privilegeNameToJcr(privilege));
    url.searchParams.set(":principal", selectedUsers[0]);
    // url.searchParams.set(":restriction", "lfs:Form=f6d0a74c-39b0-45c0-adbb-69e30edfed3a");
    url.searchParams.set(":action", "add");
    fetch( url, {
      method: 'POST',
      body: request_data,
      headers: {
        Accept: "application/json"
      }
    });

    setState(close());
  }

  return (
    <React.Fragment>
      <Dialog
        maxWidth="sm"
        open={openState === states.POLICIES}
        onClose={close}
      >
        <DialogTitle>Permissions</DialogTitle>
        <DialogContent>
          <Grid container>
            <div>
              <MaterialTable
                title=""
                style={{ boxShadow : 'none' }}
                options={{
                  actionsColumnIndex: -1,
                  emptyRowsWhenPaging: false
                }}
                columns={[
                  { title: 'Principal', field: 'principal' },
                  { title: 'Rule', field: 'allow' },
                  { title: 'Privileges', field: 'privileges' },
                  { title: 'Restrictions', field: 'restrictions' }
                ]}
                data={policies}
                actions={[
                  {
                    icon: 'delete',
                    tooltip: 'Delete Policy',
                    onClick: (event, rowData) => handlePolicyDelete(rowData)
                  },
                ]}
              />
            </div>
          </Grid>
        </DialogContent>
        <DialogActions className={classes.dialogActions}>
          <Button variant="contained" size="small" color="primary" onClick={openNew}>Add New</Button>
          <Button variant="contained" size="small" onClick={close}>Close</Button>
        </DialogActions>
      </Dialog>
      <Dialog open={openState === states.NEW} onClose={close}>
        <DialogTitle disableTypography>
        <Typography variant="h6">New Permission</Typography>
        </DialogTitle>
        <DialogContent>
          <Typography variant="body1">Permission</Typography>
          <TextField select defaultValue={privilegeNames.VIEW} onChange={handlePrivilegeChange}>
            <option value={privilegeNames.VIEW}>{privilegeNames.VIEW}</option>
            <option value={privilegeNames.EDIT}>{privilegeNames.EDIT}</option>
            <option value={privilegeNames.MANAGE}>{privilegeNames.MANAGE}</option>
          </TextField>
          <Typography variant="body1">Restrictions</Typography>
          <TextField type="text" value={"Todo"} />
          {/* // TODO: convert to AutoComplete/searchbar */}
          <Grid container>
            <div>
              <MaterialTable
                title="User"
                style={{ boxShadow : 'none' }}
                options={{
                  // headerStyle: { backgroundColor: headerBackground },
                  emptyRowsWhenPaging: false,
                  selection: true,
                  showSelectAllCheckbox : false,
                  showTextRowsSelected: false,
                  selectionProps: rowData => ({
                      color: 'primary'
                    })
                }}
                columns={[
                  { title: 'Avatar', field: 'imageUrl', render: rowData => <Avatar src={rowData.imageUrl} className={classes.info}>{rowData.initials}</Avatar>},
                  { title: 'User Name', field: 'name' },
                ]}
                data={users}
                onSelectionChange={handleUserRowClick}
              />
            </div>
          </Grid>
        </DialogContent>
        <DialogActions className={classes.dialogActions}>
          <Button variant="contained" color="secondary" size="small" onClick={setPermissions}>Save</Button>
          <Button variant="contained" size="small" onClick={close}>Close</Button>
        </DialogActions>
      </Dialog>
      <IconButton onClick={handleIconClicked}>
        <LockIcon/>
      </IconButton>
    </React.Fragment>
  )
}

export default withStyles(QuestionnaireStyle)(withRouter(PermissionsButton));
