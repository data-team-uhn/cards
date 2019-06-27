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

import Button from "material-dashboard-react/dist/components/CustomButtons/Button.js";


import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";
import GridContainer from "material-dashboard-react/dist/components/Grid/GridContainer.js";
import Table from "material-dashboard-react/dist/components/Table/Table.js";
import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
import CardFooter from "material-dashboard-react/dist/components/Card/CardFooter"
//import { Avatar } from "@material-ui/core";
import CustomInput from "material-dashboard-react/dist/components/CustomInput/CustomInput.js";

class Userboard extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      columnNames: ["User"],
      userNames: [[]],
      groupColumnNames: ["Groups"],
      groupNames: [[]],
      currentUserName: "",
      newUserName: "",
      newUserPwd: "",
      newUserPwdConfirm: ""
    };

    this.title = "Users";
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
        names.push([name, <button onClick={() => {this.setState({currentUserName: name});}}>Select</button>]);
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

  handleCreateUser() {
    let formData = new FormData();
    formData.append(':name', this.state.newUserName);
    formData.append('pwd', this.state.newUserPwd);
    formData.append('pwdConfirm', this.state.newUserPwdConfirm);

    console.log(formData);

    let url = "http://localhost:8080/system/userManager/user.create.html";

    fetch(url, {
      method: 'POST',
      headers: {
        'Authorization': 'Basic' + btoa('admin:admin')
      },
      body:formData  
    })
    .catch((error) => {
      console.log(error);
    });
  }

  render() {
    const { classes } = this.props;

    return (
      <div>
        <p>{this.state.currentUserName}</p>
        <GridContainer>
          <GridItem xs={12} sm={12} md={12}>
            <Card>
              <CardHeader>
                <h4>{this.title}</h4>
              </CardHeader>
              <CardBody>
                <Table 
                  tableHeaderColor="warning"
                  tableHead={this.state.columnNames}
                  tableData={this.state.userNames}
                />
                <Table
                  tableHeaderColor="warning"
                  tableHead={this.state.groupColumnNames}
                  tableData={this.state.groupNames}
                />
              </CardBody>
            </Card>
          </GridItem>
          
          <GridItem >
            <Card>

            </Card>
          </GridItem>


        </GridContainer>

        <Card>
          <GridContainer>
            <GridItem xs={12} sm={12} md={3}>
              <CustomInput
                labelText="Username"
                id="username"
                formControlProps={{
                  fullWidth: true
                }}
                onChange={(event)=>{this.setState({newUserName: event.target.value})}}
              />
            </GridItem>
          </GridContainer>
            <GridContainer>
              <GridItem xs={12} sm={12} md={6}>
                <CustomInput
                  labelText="Password"
                  id="password"
                  formControlProps={{
                    fullWidth: true
                  }}
                  onChange={(event) => {this.setState({newUserPwd: event.target.value}); console.log(changes);}}
                />
              </GridItem>
              <GridItem xs={12} sm={12} md={6}>
                <CustomInput
                  labelText="Confirm Password"
                  id="password-confirm"
                  formControlProps={{
                    fullWidth: true
                  }}
                  onChange={(event)=>{this.setState({newUserPwdConfirm: event.target.value}); console.log("Changed");}}
                />
              </GridItem>
            </GridContainer>
            
          <CardFooter>
            <Button onClick={() => this.handleCreateUser()}>Submit</Button>
          </CardFooter>
        </Card>          
      </div>
    );
  }
}

export default Userboard;