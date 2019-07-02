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

//import Button from "material-dashboard-react/dist/components/CustomButtons/Button.js";


import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";
import GridContainer from "material-dashboard-react/dist/components/Grid/GridContainer.js";
import Table from "material-dashboard-react/dist/components/Table/Table.js";
import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
import CardFooter from "material-dashboard-react/dist/components/Card/CardFooter"
//import { Avatar } from "@material-ui/core";
import CustomInput from "material-dashboard-react/dist/components/CustomInput/CustomInput.js";

import Button from '@material-ui/core/Button';
import TextField from '@material-ui/core/TextField';
import TableBody from '@material-ui/core/TableBody';

import userboardStyle from './userboardStyle.jsx';

import {CreateUserDialogue, CreateGroupDialogue} from './userboardDialogues.jsx';

class Userboard extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      columnNames: ["User"],
      userNames: [[]],
      groupColumnNames: ["Groups"],
      groupNames: [[]],
      currentUserName: "",
      deployCreateUser: false,
      deployCreateGroup: false
    };
  }

  hideCreateUser () {
    this.setState({deployCreateUser: false});
  }

  showCreateUser () {
    this.setState({deployCreateUser: true});
  }

  hideCreateGroup () {
    this.setState({deployCreateGroup: false});
  }

  showCreateGroup () {
    this.setState({deployCreateGroup: true});
  }

  handleLoadUsers () {
    fetch("http://"+"localhost:8080"+"/system/userManager/user.1.json", 
      {
        method: 'GET',
        headers: {
          'Authorization': 'Basic ' + btoa('admin:admin')
        }
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      console.log(JSON.stringify(data));
      var names = [];
      for (var name in data){
        names.push([name]);
      }
      console.log(names);
      this.setState({userNames: names});
      
    })
    .catch((error) => {
      console.log(error);
    });
  }

  handleLoadGroups () {
    fetch("http://localhost:8080/system/userManager/group.tidy.1.json",
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
      var groups = [];
      for (var group in data) {
        groups.push([group]);
      }
      this.setState({groupNames: groups});
    })
    .catch((error) => {
      console.log(error);
    })
  }

  componentWillMount () {
    this.handleLoadUsers();
    this.handleLoadGroups();
  }

  render() {
    const { classes } = this.props;

    return (
      <div>
        {this.state.deployCreateUser && <CreateUserDialogue handleClose={() => this.hideCreateUser()}></CreateUserDialogue>}
        {this.state.deployCreateGroup && <CreateGroupDialogue handleClose={() => this.hideCreateGroup()}></CreateGroupDialogue>}
        <GridContainer>
          <GridItem xs={12} sm={12} md={7}>
            <Card>
              <CardHeader color="warning">
                <h4 className={classes.cardTitleWhite}>Users</h4>
              </CardHeader>
              <CardBody>
                <Button onClick={() => this.showCreateUser()}>Create New User</Button>
                <Table 
                  tableHeaderColor="warning"
                  tableHead={this.state.columnNames}
                  tableData={this.state.userNames}
                />
              </CardBody>
            </Card>

            <Card>
              <CardHeader color="warning">
                <h4 className={classes.cardTitleWhite}>Groups</h4>
              </CardHeader>
              <CardBody>
                <Button onClick={() => this.showCreateGroup()}>Create New Group</Button>
                <Table
                  tableHeaderColor="warning"
                  tableHead={this.state.groupColumnNames}
                  tableData={this.state.groupNames}
                />
              </CardBody>
            </Card>
          </GridItem>
          
          <GridItem xs={12} sm={12} md={5}>
            <Card>
              <CardBody>
                Hadfkljasfl
              </CardBody>
            </Card>
            <Card>
              <CardBody>
                djafkldjasfkljasfl
              </CardBody>
            </Card>
          </GridItem>
        </GridContainer>
        
      </div>
    );
  }
}

export default withStyles (userboardStyle)(Userboard);