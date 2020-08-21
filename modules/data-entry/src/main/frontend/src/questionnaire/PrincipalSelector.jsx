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

import React, { useState } from "react";

import {
  Avatar,
  Button,
  Dialog,
  DialogTitle,
  DialogActions,
  DialogContent,
  Grid,
  withStyles
} from "@material-ui/core";
import MaterialTable from 'material-table';

import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

function PrincipalSelector(props) {
  const { classes, theme, onSelectionComplete, open, onClose } = props;
  const [ users, setUsers ] = useState([]);
  const [ selectedUsers, setSelectedUsers ] = useState([]);

  let handleSelectRowClick = (rows) => {
      let chosens = rows.map((row) => row.name);
      setSelectedUsers(chosens);
  }

  let loadUsers = () => {
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

  let loadGroups = (loadedUsers) => {
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

  let handleEntering = () => {
    setUsers([]);
    loadUsers().then((loadedUsers) => loadGroups(loadedUsers));
  }

  let handleNext = () => {
    onSelectionComplete(selectedUsers);
  }

  let handleExit = () => {
    onClose();
  }


  // const headerBackground = theme.palette.grey['200'];

  return (
    <Dialog
      maxWidth="sm"
      open={open}
      onEntering={handleEntering}
      onClose={handleExit}
    >
      <DialogTitle>Which user?</DialogTitle>
      <DialogContent>
        <Grid container>
          <div>
            <MaterialTable
              title=""
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
              onSelectionChange={handleSelectRowClick}
            />
          </div>
        </Grid>
      </DialogContent>
      <DialogActions className={classes.dialogActions}>
        <Button variant="contained" size="small" color="primary" onClick={handleNext} disabled={selectedUsers.length == 0}>Next</Button>
        <Button variant="contained" size="small" onClick={handleExit}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

export default withStyles(QuestionnaireStyle)(PrincipalSelector);
