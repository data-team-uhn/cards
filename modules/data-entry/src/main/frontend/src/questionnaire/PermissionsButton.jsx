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
import PrincipalSelector from "./PrincipalSelector.jsx";

function PermissionsButton(props) {
  const states ={
    CLOSED: "closed",
    POLICIES: "policies",
    USERS: "users",
    NEW: "new"
  }

  const { classes, entryPath, entryName, warning, onComplete, entryType, size, shouldGoBack } = props;
  const [ openState, setOpenState ] = useState(states.CLOSED);
  const [ policies, setPolicies ] = useState([]);
  const [ users, setUsers ] = useState();
  const [ rule, setRule] = useState("allow");
  const [ privilege, setPrivilege] = useState("jcr:read");

  let setState = (newState) => {
    if (openState !== newState) {
      setOpenState(newState);
    }
  }

  let close = () => { setState(states.CLOSED); }
  let openPolicies = () => { setState(states.POLICIES); }
  let openUsers = () => { setState(states.USERS); }
  let openNew = () => { setState(states.NEW); }

  let handleRuleChange = (event) => {
    setRule(event.target.value);
  }
  let handlePrivilegeChange = (event) => {
    setPrivilege(event.target.value);
  }

  let handleUserSet = (incomingUsers) => {
    setUsers(incomingUsers);
    openNew();
  }

  let handleIconClicked = () => {
    getPermissions().then(openPolicies);
  }

  let handlePolicyDelete = (rowData) => {
    let request_data = new FormData();
    let url = new URL(entryPath + ".permissions", window.location.origin);
    url.searchParams.set(":rule", rowData.allow);
    url.searchParams.set(":privileges", rowData.privileges);
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
          setPolicies(json?.policies ? json.policies : []);
        })
        .catch(() => {setPolicies([])});
  }

  let setPermissions = () => {
    let request_data = new FormData();
    let url = new URL(entryPath + ".permissions", window.location.origin);
    url.searchParams.set(":rule", rule);
    url.searchParams.set(":privileges", privilege);
    url.searchParams.set(":principal", users[0]);
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
      <PrincipalSelector
        open={openState === states.USERS}
        onSelectionComplete={handleUserSet}
        onClose={close}
      />
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
          <Button variant="contained" size="small" color="primary" onClick={openUsers}>Add New</Button>
          <Button variant="contained" size="small" onClick={close}>Close</Button>
        </DialogActions>
      </Dialog>
      <Dialog open={openState === states.NEW} onClose={close}>
        <DialogTitle disableTypography>
        <Typography variant="h6">New Permission</Typography>
        </DialogTitle>
        <DialogContent>
          <Typography variant="body1">Rule</Typography>
          <TextField select value={rule} onChange={handleRuleChange}>
            <option value="allow">Allow</option>
            <option value="deny">Deny</option>
          </TextField>
          <Typography variant="body1">Permission</Typography>
          <TextField type="text" value={privilege} onChange={handlePrivilegeChange} />
          <Typography variant="body1">Restrictions</Typography>
          <TextField type="text" value={"Todo"} />
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
