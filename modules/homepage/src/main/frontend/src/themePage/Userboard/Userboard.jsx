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

import PropTypes from "prop-types"

import  {withStyles} from "@material-ui/core/styles";

import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";

class Userboard extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      columnNames: ["User"],
      userNames: []
    };
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
        names.push(name);
      }
      console.log(names);
      this.setState({userNames: names});
      
    })
    .catch((error) => {
      console.log(error);
    });
  }

  componentWillMount () {
    this.handleLoadUsers();
  }

  render() {
    const { classes } = this.props;

    return (
      <div>
        <GridContainer>
          <Card></Card>
        </GridContainer>
      </div>
    )
  }


}