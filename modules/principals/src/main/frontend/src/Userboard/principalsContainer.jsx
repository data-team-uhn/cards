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
import React from 'react';

import UsersManager from './Users/usersmanager.jsx';
import GroupsManager from './Groups/groupsmanager.jsx';

class PrincipalsContainer extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      users: [],
      groups: [],
    };
  }

  handleLoadUsers () {
    fetch("/home/users.json",
      {
        method: 'GET',
        credentials: 'include'
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      data.rows?.forEach((r) => r.initials = (r.firstname?.charAt(0) + r.lastname?.charAt(0)) || r.name?.charAt(0) || '?')

      this.setState({ users: data.rows });
    })
    .catch((error) => {
      console.log(error);
    })
    .finally(() => 
       this.handleLoadGroups()
    )
  }

  handleLoadGroups () {
    fetch("/home/groups.json",
      {
        method: 'GET',
        credentials: 'include'
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      this.setState({ groups: data.rows });
    })
    .catch((error) => {
      console.log(error);
    })
    .finally(() => {
     // This event is needed in cases we do not want to collapse details panel after reload
      var reloadedEvent = new CustomEvent('principals-reloaded', {
          bubbles: true,
          cancelable: true
        });
      document.dispatchEvent(reloadedEvent);
    })
  }

  componentWillMount () {
    this.handleLoadUsers();
  }

  handleReload () {
    this.handleLoadUsers();
  }

  render () {
    return (
      <div>
        { this.props.isUserListPage ? <UsersManager users={this.state.users} groups={this.state.groups} reload={() => this.handleReload()}/>
                                   : <GroupsManager users={this.state.users} groups={this.state.groups} reload={() => this.handleReload()}/> }
      </div>
    );
  }
}

export default PrincipalsContainer;
