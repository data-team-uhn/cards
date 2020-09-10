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
  InputAdornment,
  ListItemAvatar,
  ListItemText,
  TextField,
  Tooltip,
  Typography,
  withStyles
} from "@material-ui/core";
import DescriptionIcon from "@material-ui/icons/Description";
import ErrorIcon from "@material-ui/icons/Error";
import LockIcon from "@material-ui/icons/Lock";
import MaterialTable from "material-table";

import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import SearchBar from "../SearchBar.jsx";

function PermissionsButton(props) {
  const states ={
    CLOSED: "closed",
    POLICIES: "policies",
    NEW: "new"
  }

  const privilegeNames = {
    NONE:"None",
    VIEW:"View",
    EDIT:"Edit",
    MANAGE: "Manage",
    ALL: "All",
    UNKNOWN: ""
  }
  const privilegeJcr = {
    NONE: [],
    VIEW: ["jcr:read"],
    EDIT: ["jcr:read","jcr:write"].sort(),
    MANAGE: ["jcr:read","jcr:write","jcr:readAccessControl","jcr:modifyAccessControl"].sort(),
    ALL: ["jcr:all"],
    UNKNOWN: []
  }

  const { classes, entryPath, entryName, warning, onComplete, entryType, size, shouldGoBack, ...rest } = props;
  const [ openState, setOpenState ] = useState(states.CLOSED);
  const [ policies, setPolicies ] = useState([]);
  const [ error, setError ] = useState();
  const [ hasSelectedValidSubject, setHasSelectedValidSubject ] = useState(false);
  const [ selectedUser, setSelectedUser ] = useState();
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
    setHasSelectedValidSubject(false);
    setError("");
  }

  let handlePrivilegeChange = (event) => {
    setPrivilege(event.target.value);
  }

  let handleIconClicked = () => {
    getPermissions().then(openPolicies);
  }

  let privilegeNameToJcr = (name) => {
    switch (name) {
      case privilegeNames.NONE:
        return privilegeJcr.NONE;
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

    if (arrayEquals(jcr, privilegeJcr.NONE)) return privilegeNames.NONE;
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
    let url = new URL(entryPath + ".permissions", window.location.origin);
    return fetch( url, {
      method: 'get',
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

  let setPermissions = () => {
    if (privilege !== "") {
      // Set /Subject/id permissions
      postPermissions(entryPath, "allow", privilegeNameToJcr(privilege), selectedUser, null, "add");
      var pathEntries = entryPath.split("/");
      var subjectId = pathEntries[pathEntries.length-1];
      // Set /Forms permissions
      postPermissions("/Forms", "allow", privilegeNameToJcr(privilege), selectedUser, "lfs:subject=" + subjectId,
        "add");
    }
    setState(close());
  }

  let deletePermissions = (rowData) => {
    var pathEntries = entryPath.split("/");
    var subjectId = pathEntries[pathEntries.length-1];
    var restrictionsWithSubject = rowData.restrictions.length > 0 ? rowData.restrictions.toString() + "," : "";
    restrictionsWithSubject += "lfs:subject=" + subjectId;

    postPermissions("/Forms", "allow", privilegeNameToJcr(rowData.privileges), rowData.principal,
      restrictionsWithSubject, "remove")
      .then((response) => {if (!response.ok) Promise.reject(response)})
      .then(() => {return postPermissions(entryPath, "allow", privilegeNameToJcr(rowData.privileges), rowData.principal,
        rowData.restrictions, "remove")})
      .then((response) => {if (!response.ok) Promise.reject(response)})
      .then(getPermissions);
  }

  let postPermissions = (path, rule, privileges, principal, restrictions, action) => {
    let request_data = new FormData();
    let url = new URL(path + ".permissions", window.location.origin);
    url.searchParams.set(":rule", rule);
    url.searchParams.set(":privileges", privileges);
    url.searchParams.set(":principal", principal);
    url.searchParams.set(":action", action);
    if (restrictions) {
      url.searchParams.set(":restrictions", restrictions);
    }
    return fetch( url, {
      method: 'POST',
      body: request_data,
      headers: {
        Accept: "application/json"
      }
    });
  }

  let invalidateInput = (event) => {
    setHasSelectedValidSubject(false);
  }

  let closePopper = () => {
    if (!hasSelectedValidSubject) {
      setError("Invalid subject selected");
    }
  }

  let selectSubject = (event, row) => {
    setSelectedUser(row["principalName"]);
    setHasSelectedValidSubject(true);
    setError(false);
  }

  let constructQuery = (query, requestID) => {
    let url = new URL("/home.json", window.location.origin);
    url.searchParams.set("limit", 10);
    url.searchParams.set("offset", 0);
    url.searchParams.set("req", requestID);
    if (query) {
      url.searchParams.set("nameFilter", query + "%");
    }
    return(url);
  }

  // Generate a human-readable info about the subject matching the query
  function QuickSearchResultHeader(props) {
    const {resultData} = props;
    return resultData && (
      <div>
        {resultData["principalName"] || ''}
      </div>
    ) || null
  }
  // Display a quick search result
  // If it's a resource, show avatar, category, and title
  // Otherwise, if it's a generic entry, simply display the name
  let QuickSearchResult = (props) => (
    <React.Fragment>
      <ListItemAvatar><Avatar className={classes.searchResultAvatar}><DescriptionIcon /></Avatar></ListItemAvatar>
      <ListItemText
        primary={(<QuickSearchResultHeader resultData={props.resultData} />)}
        className={classes.dropdownItem}
      />
    </React.Fragment>
  )

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
                    onClick: (event, rowData) => deletePermissions(rowData)
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
          <Typography variant="body1">Principal</Typography>
          <SearchBar
            onChange={invalidateInput}
            onPopperClose={closePopper}
            onSelect={selectSubject}
            queryConstructor={constructQuery}
            resultConstructor={QuickSearchResult}
            error={!!error /* Turn into a boolean to prevent PropTypes warnings */}
            className={(hasSelectedValidSubject ? "" : classes.invalidSubjectText)}
            startAdornment={
              error && <InputAdornment position="end">
                <Tooltip title={error}>
                  <ErrorIcon />
                </Tooltip>
              </InputAdornment> || undefined
              }
            {...rest}
            />
          <Typography variant="body1">Permission</Typography>
          <TextField select defaultValue={privilegeNames.VIEW} onChange={handlePrivilegeChange}>
            <option value={privilegeNames.NONE}>{privilegeNames.NONE}</option>
            <option value={privilegeNames.VIEW}>{privilegeNames.VIEW}</option>
            <option value={privilegeNames.EDIT}>{privilegeNames.EDIT}</option>
            <option value={privilegeNames.MANAGE}>{privilegeNames.MANAGE}</option>
          </TextField>
        </DialogContent>
        <DialogActions className={classes.dialogActions}>
          <Button variant="contained" color="secondary" size="small" onClick={setPermissions} disabled={!hasSelectedValidSubject}>Save</Button>
          <Button variant="contained" size="small" onClick={close}>
            Close
          </Button>
        </DialogActions>
      </Dialog>
      <IconButton onClick={handleIconClicked}>
        <LockIcon/>
      </IconButton>
    </React.Fragment>
  )
}

export default withStyles(QuestionnaireStyle)(withRouter(PermissionsButton));
