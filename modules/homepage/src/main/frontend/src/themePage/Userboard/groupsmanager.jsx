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

import  {withStyles} from "@material-ui/core/styles";

import {Button, Table, TableBody, TableHead, TableRow, TableCell, TableFooter, TablePagination, Checkbox, Hidden, Dialog, DialogTitle, DialogActions, DialogContent, TextField, IconButton} from "@material-ui/core"
import {FirstPageIcon, KeyboardArrowLeft, KeyboardArrowRigth, LastPageIcon} from "@material-ui/icons";

import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";
import GridContainer from "material-dashboard-react/dist/components/Grid/GridContainer.js";
import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
import CardFooter from "material-dashboard-react/dist/components/Card/CardFooter"
//import { Avatar } from "@material-ui/core";

import userboardStyle from './userboardStyle.jsx';
import CreateGroupDialogue from "./creategroupdialogue.jsx";
import DeleteGroupDialogue from "./deletegroupdialogue.jsx"; 
import AddUserToGroupDialogue from "./addusertogroupdialogue.jsx";
import RemoveUserFromGroupDialogue from "./removeuserfromgroup.jsx";


class GroupsManager extends React.Component {
  constructor(props) { 
    super(props);
    this.state = {
      groups: [],
      groupFilter: null,
      
      currentGroupName: "",
      currentGroupIndex: -1,
      returnedGroupRows: 0,
      totalGroupRows: 0,

      deployCreateGroup: false,
      deployDeleteGroup: false,
      deployAddGroupUsers: false,
      deployRemoveGroupUsers: false,
      deployMobileGroupDialog: false
    };
  }

  clearSelectedGroup () {
    this.setState(
      {
        currentGroupName: "",
        currentGroupIndex: -1,
        returnedGroupRows: 0,
        totalGroupRows: 0
      }
    );
  }

  handleLoadGroups (filter, offset, limit) {
    let url = new URL ("http://localhost:8080/home/groups.json");

    if (filter !== null && filter !== "") {
      url.searchParams.append('filter', filter);
    }

    if (offset !== null) {
      url.searchParams.append('offset', offset);
    }

    if (limit !== null) {
      url.searchParams.append('limit', limit);
    }

    fetch(url,
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
          returnedGroupRows: data.returnedrows,
          totalGroupRows: data.totalrows,
          groups: data.rows
        }
      );
    })
    .catch((error) => {
      console.log(error);
    })
  }

  componentWillMount () {
    this.handleLoadGroups(null, null, null);
  }

  handleGroupRowClick(index, name) {
    if (index === this.state.currentGroupIndex) {
      this.setState({currentGroupName: ""});
      this.setState({currentGroupIndex: -1});
    } else {
      this.setState({currentGroupName: name});
      this.setState({currentGroupIndex: index});
    }
  }

  handleMobileGroupRowClick (index, name) {
    this.setState({currentGroupName: name});
    this.setState({currentGroupIndex: index});
    this.setState({deployMobileGroupDialog: true});
  }

  render() {
    const { classes } = this.props;

    return (
      <div>
        {this.state.deployCreateGroup && <CreateGroupDialogue handleClose={() => {this.setState({deployCreateGroup: false});}}/>}
        {this.state.deployDeleteGroup && <DeleteGroupDialogue handleClose={() => {this.setState({deployDeleteGroup: false});}} name={this.state.currentGroupName}/>}
        {this.state.deployAddGroupUsers && <AddUserToGroupDialogue handleClose={() => {this.setState({deployAddGroupUsers: false});}} name={this.state.currentGroupName}/>}
        {this.state.deployRemoveGroupUsers && <RemoveUserFromGroupDialogue handleClose={() => {this.setState({deployRemoveGroupUsers: false});}} name={this.state.currentGroupName}/>}
        
        <Hidden mdUp implementation="css">
          <Dialog
            open={this.state.deployMobileGroupDialog}
            onClose={() =>{this.setState({deployMobileGroupDialog: false});}}
          >
            <Card>
              <CardHeader color="success">
                {
                  this.state.currentGroupIndex >= 0 && <h2 className={classes.cardTitleWhite}>{this.state.groups[this.state.currentGroupIndex].name}</h2>
                }
              </CardHeader>
              <CardBody>
                {
                  this.state.currentGroupIndex >= 0 &&
                  <div>
                    <p className={classes.cardCategory}>Principal Name</p>
                    <h3 className={classes.cardTitle}> {this.state.groups[this.state.currentGroupIndex].principalName}</h3>

                    <p className={classes.cardCategory}>Path</p>
                    <h3 className={classes.cardTitle}>{this.state.groups[this.state.currentGroupIndex].path}</h3>

                    <p className={classes.cardCategory}>Members</p>
                    <h3 className={classes.cardTitle}>{this.state.groups[this.state.currentGroupIndex].members}</h3>

                    <p className={classes.cardCategory}>Declared Members</p>
                    <h3 className={classes.cardTitle}>{this.state.groups[this.state.currentGroupIndex].declaredMembers}</h3>
                  </div>
                }
                <GridContainer>
                  <Button onClick={() => {this.setState({deployDeleteGroup: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Delete Group</Button>
                  <Button onClick={() => {this.setState({deployAddGroupUsers: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Add User to Group</Button>
                  <Button onClick={() => {this.setState({deployRemoveGroupUsers: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Remove User from Group</Button>
                  <Button onClick={() => {this.setState({deployMobileGroupDialog: false});}}>Close</Button>
                </GridContainer>
              </CardBody>
            </Card>
          </Dialog>
        </Hidden>

        <GridContainer>
          <GridItem xs={12} sm={12} md={5}>
            <Card>
              <CardHeader color="warning">
                <h4 className={classes.cardTitleWhite}>Groups</h4>
              </CardHeader>
              <CardBody>
                <Button onClick={() => {this.setState({deployCreateGroup: true});}}>Create New Group</Button>
                <form
                  onSubmit={(event) => { event.preventDefault(); this.handleLoadGroups(this.state.groupFilter, null, null);}}
                >
                  <TextField
                    id="group-filter"
                    name="group-filter"
                    label="Search Group"
                    onChange={(event) => {this.setState({groupFilter: event.target.value});}}
                  />
                </form>
                <Hidden smDown implementation="css">
                  <Table>
                    <TableHead>
                      <TableRow>
                        {/*this.groupColumnNames.map(
                          row => (
                            <TableCell
                              key = {row.id}
                            >
                              {row.label}
                            </TableCell>
                          )
                          )*/}
                        <TableCell>Group Names</TableCell>
                      </TableRow>
                      </TableHead>
                        <TableBody>
                          {this.state.groups.map(
                            (row, index) => (
                              <TableRow
                                onClick={(event) => {this.handleGroupRowClick(index, row.name);}}
                                aria-checked={index === this.state.currentGroupIndex ? true : false}
                                key = {row.name}
                                selected={index === this.state.currentGroupIndex ? true : false}
                              >
                                <TableCell>
                                    <Checkbox
                                      checked = {index === this.state.currentGroupIndex ? true : false}
                                    />
                                  {row.name}
                                </TableCell>
                              </TableRow>
                            )
                          )}
                        </TableBody>
                  </Table>
                </Hidden>
                <Hidden mdUp implementation="css">
                  <Table>
                    <TableHead>
                      <TableRow>
                        {/*this.groupColumnNames.map(
                          row => (
                            <TableCell
                              key = {row.id}
                            >
                              {row.label}
                            </TableCell>
                          )
                          )*/}
                        <TableCell>Group Names</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {this.state.groups.map(
                        (row, index) => (
                          <TableRow
                            onClick={(event) => {this.handleMobileGroupRowClick(index, row.name);}}
                            aria-checked={index === this.state.currentGroupIndex ? true : false}
                            key = {row.name}
                            selected={index === this.state.currentGroupIndex ? true : false}
                          >
                            <TableCell>
                              {row.name}
                            </TableCell>
                          </TableRow>
                        )
                      )}
                    </TableBody>
                  </Table>
                </Hidden>
              </CardBody>
            </Card>
          </GridItem>
          <GridItem xs={12} sm={12} md={7}>
            <Hidden smDown implementation="css">
              <Card>
                <CardHeader color="success">
                  {
                    this.state.currentGroupIndex < 0 ?
                    <h2 className={classes.cardTitleWhite}>No group selected.</h2>
                    :
                    <h2 className={classes.cardTitleWhite}>{this.state.groups[this.state.currentGroupIndex].name}</h2>
                  }
                </CardHeader>
                <CardBody>
                  {
                    this.state.currentGroupIndex >= 0 &&
                    <div>
                      <p className={classes.cardCategory}>Principal Name</p>
                      <h3 className={classes.cardTitle}>{this.state.groups[this.state.currentGroupIndex].principalName}</h3>

                      <p className={classes.cardCategory}>Path</p>
                      <h3 className={classes.cardTitle}>{this.state.groups[this.state.currentGroupIndex].path}</h3>

                      <p className={classes.cardCategory}>Members</p>
                      <h3 className={classes.cardTitle}>{this.state.groups[this.state.currentGroupIndex].members}</h3>

                      <p className={classes.cardCategory}>Declared Members</p>
                      <h3 className={classes.cardTitle}>{this.state.groups[this.state.currentGroupIndex].declaredMembers}</h3>
                    </div>
                  }
                  <GridContainer>
                    <Button onClick={() => {this.setState({deployDeleteGroup: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Delete Group</Button>
                    <Button onClick={() => {this.setState({deployAddGroupUsers: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Add User to Group</Button>
                    <Button onClick={() => {this.setState({deployRemoveGroupUsers: true});}} disabled={this.state.currentGroupIndex < 0 ? true:false}>Remove User from Group</Button>
                  </GridContainer>
                </CardBody>
              </Card>
            </Hidden>
          </GridItem>
        </GridContainer>
      </div>
    );
  }
}

export default withStyles (userboardStyle)(GroupsManager);