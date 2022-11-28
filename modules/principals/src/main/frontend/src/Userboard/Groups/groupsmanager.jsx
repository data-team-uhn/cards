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

import { Avatar, Button, Card, CardContent, Grid } from "@mui/material";

import userboardStyle from '../userboardStyle.jsx';
import CreateGroupDialogue from "./creategroupdialogue.jsx";
import DeletePrincipalDialogue from "../deleteprincipaldialogue.jsx";
import AddUserToGroupDialogue from "./addusertogroupdialogue.jsx";
import NewItemButton from "../../components/NewItemButton.jsx"
import AdminScreen from "../../adminDashboard/AdminScreen.jsx";

import MaterialTable from 'material-table';

const GROUP_URL = "/system/userManager/group/";

class GroupsManager extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      selectedUsers: [],
      currentGroupUsers: [],

      currentGroupName: "",
      currentGroupIndex: -1,

      deployCreateGroup: false,
      deployDeleteGroup: false,
      deployAddGroupUsers: false,
      groupUsersLoaded: false
    };

    this.tableRef = React.createRef();
    this.openDetailsPanel = this.openDetailsPanel.bind(this);
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
        currentGroupIndex: -1,
        selectedUsers: [],
      }
    );
  }

  clearSelectedUsers () {
    this.setState(
      {
        selectedUsers: [],
      }
    );
  }

  addName(name) {
    return { name }
  }

  handleSelectRowClick(rows) {
    let chosens = rows.map((row) => row.name);
    this.setState({ selectedUsers: chosens });
  }

  handleRemoveUsers() {
    let formData = new FormData();

    var i;
    for (i = 0; i < this.state.selectedUsers.length; ++i) {
      formData.append(':member@Delete', this.state.selectedUsers[i]);
    }

    fetch(GROUP_URL + this.state.currentGroupName + ".update.html",
      {
        method: 'POST',
        credentials: 'include',
        body: formData
      })
      .then(() => {
        this.clearSelectedUsers();
        this.handleReload();
      })
      .catch((error) => {
        console.log(error);
      });
  }

  handleGroupRowClick(index, name) {
    this.setState(
      {
        currentGroupName: name,
        currentGroupIndex: index
      }
    );
  }

  handleGroupDeleteClick(index, name) {
    this.handleGroupRowClick(index, name);
    this.setState({deployDeleteGroup: true});
  }

  handleReload (doClear) {
    doClear && this.clearSelectedGroup();
    this.props.reload();
  }

  openDetailsPanel (event) {
    event.preventDefault();
    if (this.state.currentGroupIndex > -1 && this.props.groups[this.state.currentGroupIndex]) {
      this.tableRef.current.onToggleDetailPanel(
          [this.state.currentGroupIndex],
          this.tableRef.current.props.detailPanel
       );
    }
  }

  componentDidMount () {
    document.addEventListener("principals-reloaded", this.openDetailsPanel);
  }

  componentWillUnmount() {
    document.removeEventListener("principals-reloaded", this.openDetailsPanel);
  }

  render() {
    const { classes } = this.props;
    const headerBackground = this.props.theme.palette.grey['200'];
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
          <MaterialTable
            tableRef={this.tableRef}
            title=""
            style={{ boxShadow : 'none' }}
            options={{
              actionsColumnIndex: -1,
              headerStyle: {backgroundColor: headerBackground},
              emptyRowsWhenPaging: false
            }}
            columns={[
              { title: 'Avatar', field: 'imageUrl', render: rowData => <Avatar src={rowData.imageUrl} className={classes.info}>{rowData.name.charAt(0)}</Avatar> },
              { title: 'Name', field: 'name', cellStyle: {textAlign: 'left'} },
              { title: 'Members', field: 'members', type: 'numeric', cellStyle: {textAlign: 'left'}, headerStyle: {textAlign: 'left', flexDirection: 'initial'} },
              { title: 'Declared Members', field: 'declaredMembers', type: 'numeric', cellStyle: {textAlign: 'left'}, headerStyle: {textAlign: 'left', flexDirection: 'initial'} },
            ]}
            data={this.props.groups}
            actions={[
              {
                icon: 'delete',
                tooltip: 'Delete Group',
                onClick: (event, rowData) => this.handleGroupDeleteClick(rowData.tableData.id, rowData.name)
              }
             ]}
            onRowClick={(event, rowData, togglePanel) => {this.handleGroupRowClick(rowData.tableData.id, rowData.name); togglePanel()}}
            detailPanel={rowData => {
                const group = rowData || this.props.groups[this.state.currentGroupIndex];
                const groupUsers = group.members > 0 ? this.getGroupUsers(group.name) : [];
                const tableTitle = "Group " + group.name + " users";

                return (
                  <div>
                    <Card className={classes.cardRoot}>
                      <CardContent>
                        { groupUsers.length > 0 && 
                          <div>
                            <MaterialTable
                                title={tableTitle}
                                style={{ boxShadow : 'none' }}
                                options={{
                                  emptyRowsWhenPaging: false,
                                  selection: true,
                                  showSelectAllCheckbox : false,
                                  showTextRowsSelected: false,
                                  headerStyle: {backgroundColor: headerBackground},
                                  selectionProps: rowData => ({
                                    color: 'primary'
                                  })
                                }}
                              columns={[
                                { title: 'Avatar', field: 'imageUrl', render: rowData => <Avatar src={rowData.imageUrl} className={classes.info}>{rowData.initials}</Avatar>},
                                { title: 'User Name', field: 'name' },
                                { title: 'Admin', field: 'isAdmin', type: 'boolean' },
                                { title: 'Disabled', field: 'isDisabled', type: 'boolean' },
                              ]}
                              data={groupUsers}
                                onSelectionChange={(rows) => {this.handleSelectRowClick(rows)}}
                              />
                          </div>
                        }
                        <Grid container className={classes.cardActions}>
                          <Button
                            variant="contained"
                            color="primary"
                            size="small"
                            className={classes.containerButton}
                            onClick={() => {this.setState({currentGroupName: group.principalName, currentGroupIndex: rowData.tableData.id, deployAddGroupUsers: true, currentGroupUsers: groupUsers});}}
                          >
                            Add User to Group
                          </Button>
                          <Button
                            variant="contained"
                            color="secondary"
                            size="small"
                            disabled={this.state.selectedUsers.length == 0}
                            onClick={() => {this.setState({currentGroupName: group.principalName, currentGroupIndex: rowData.tableData.id}); this.handleRemoveUsers();}}
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
