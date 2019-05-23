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

import PropTypes from "prop-types";

import { withStyles } from "@material-ui/core/styles";

import { Avatar, Button, Card, CardHeader, CardContent, Grid, Table, TableCell, TableBody, TableHead, TableRow } from "@material-ui/core";

import userboardStyle from '../userboardStyle.jsx';
import CreateGroupDialogue from "./creategroupdialogue.jsx";
import DeleteGroupDialogue from "./deletegroupdialogue.jsx";
import AddUserToGroupDialogue from "./addusertogroupdialogue.jsx";
import RemoveUserFromGroupDialogue from "./removeuserfromgroup.jsx";

import MaterialTable from 'material-table';

class GroupsManager extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      groups: [],

      currentGroupName: "",
      currentGroupIndex: -1,

      deployCreateGroup: false,
      deployDeleteGroup: false,
      deployAddGroupUsers: false,
      deployRemoveGroupUsers: false
    };
  }

  clearSelectedGroup () {
    this.setState(
      {
        currentGroupName: "",
        currentGroupIndex: -1,
      }
    );
  }

  handleLoadGroups () {
    this.clearSelectedGroup();

    fetch("/home/groups.json",
      {
        method: 'GET',
        headers: {
          'Authorization' : 'Basic' + btoa('admin:admin')
        }
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      this.clearSelectedGroup();
      this.setState(
        {
          groups: data.rows
        }
      );
    })
    .catch((error) => {
      console.log(error);
    })
  }

  componentWillMount () {
    this.handleLoadGroups();
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

  handleReload () {
    this.handleLoadGroups();
  }

  render() {
    const { classes } = this.props;
    return (
      <div>
        <CreateGroupDialogue isOpen={this.state.deployCreateGroup} handleClose={() => {this.setState({deployCreateGroup: false});}} reload={() => this.handleReload()} />
        <DeleteGroupDialogue isOpen={this.state.deployDeleteGroup} handleClose={() => {this.setState({deployDeleteGroup: false});}} name={this.state.currentGroupName} reload={() => this.handleReload()} />
        <AddUserToGroupDialogue isOpen={this.state.deployAddGroupUsers} handleClose={() => {this.setState({deployAddGroupUsers: false});}} name={this.state.currentGroupName} reload={() => this.handleReload()} />
        <RemoveUserFromGroupDialogue isOpen={this.state.deployRemoveGroupUsers} handleClose={() => {this.setState({deployRemoveGroupUsers: false});}} name={this.state.currentGroupName} reload={() => this.handleReload()} />
        <div>
          <MaterialTable
            title="Group list"
            style={{ boxShadow : 'none' }}
            options={
              { draggable: false },
              { actionsColumnIndex: -1 },
              { headerStyle: { backgroundColor: '#fafbfc'} },
              { emptyRowsWhenPaging: false }
            }
            columns={[
              { title: 'Avatar', field: 'imageUrl', render: rowData => <Avatar src={rowData.imageUrl} className={classes.info}>{rowData.name.charAt(0)}</Avatar> },
              { title: 'Name', field: 'name', cellStyle: {textAlign: 'left'} },
              { title: 'Members', field: 'members', type: 'numeric', cellStyle: {textAlign: 'left'}, headerStyle: {textAlign: 'left', flexDirection: 'initial'} },
              { title: 'Declared Members', field: 'declaredMembers', type: 'numeric', cellStyle: {textAlign: 'left'}, headerStyle: {textAlign: 'left', flexDirection: 'initial'} },
            ]}
            data={this.state.groups}
            actions={[
              /*{
                icon: 'edit',
                tooltip: 'Edit Group',
                onClick: (event, rowData) => this.handleGroupRowClick(rowData.tableData.id, rowData.name)
              },*/
              {
                icon: 'delete',
                tooltip: 'Delete Group',
                onClick: (event, rowData) => this.handleGroupDeleteClick(rowData.tableData.id, rowData.name)
              },
              {
                icon: 'add_circle',
                tooltip: 'Create New Group',
                isFreeAction: true,
                onClick: (event) => this.setState({deployCreateGroup: true})
              }
             ]}
            onRowClick={(event, rowData, togglePanel) => {this.handleGroupRowClick(rowData.tableData.id, rowData.name); togglePanel()}}
            detailPanel={rowData => {
                const group = rowData || this.state.groups[this.state.currentGroupIndex];
                return (
                <div>
                    <Card className={classes.cardRoot}>
                      <CardContent>
                        {
                          <div>
                            <Table>
                            <TableBody>
                              <TableRow>
                                <TableCell className={classes.cardCategory}>Principal Name</TableCell>
                                <TableCell className={classes.cardTitle}>{group.principalName}</TableCell>
                              </TableRow>
                              <TableRow>
                                <TableCell className={classes.cardCategory}>Members</TableCell>
                                <TableCell className={classes.cardTitle}>{group.members}</TableCell>
                              </TableRow>
                              <TableRow>
                                <TableCell className={classes.cardCategory}>Declared Members</TableCell>
                                <TableCell className={classes.cardTitle}>{group.declaredMembers}</TableCell>
                              </TableRow>
                            </TableBody>
                            </Table>
                          </div>
                        }
                        <Grid container className={classes.cardActions}>
                          <Button
                            variant="contained"
                            color="primary"
                            size="small"
                            className={classes.containerButton}
                            onClick={() => {this.setState({currentGroupName: group.principalName, deployAddGroupUsers: true});}}
                          >
                            Add User to Group
                          </Button>
                          <Button
                            variant="contained"
                            color="secondary"
                            size="small"
                            disabled={group.members == 0}
                            onClick={() => {this.setState({currentGroupName: group.principalName, deployRemoveGroupUsers: true});}}
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
      </div>
    );
  }
}

export default withStyles (userboardStyle)(GroupsManager);
