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
import React, { useState, useEffect } from 'react';

import UsersManager from './Users/usersmanager.jsx';
import GroupsManager from './Groups/groupsmanager.jsx';

export default function PrincipalsContainer(props) {
  const [ users, setUsers ] = useState([]);
  const [ groups, setGroups ] = useState([]);

  useEffect(() => {
    handleLoadUsers();
  }, []);

  let handleLoadUsers = () => {
    fetch("/home/users.json",
      {
        method: 'GET',
        credentials: 'include'
    })
    .then((response) => response.json())
    .then((data) => {
      data.rows?.forEach((r) => r.initials = (r.firstname?.charAt(0) + r.lastname?.charAt(0)) || r.name?.charAt(0) || '?');
      setUsers(data.rows);
    })
    .catch((error) => console.log(error?.statusText ? error.statusText : error))
    .finally(() => handleLoadGroups());
  }

  let handleLoadGroups = () => {
    fetch("/home/groups.json",
      {
        method: 'GET',
        credentials: 'include'
    })
    .then((response) => response.json())
    .then((data) => setGroups(data.rows))
    .catch((error) => console.log(error?.statusText ? error.statusText : error))
    .finally(() => {
     // This event is needed in cases we do not want to collapse details panel after reload
      var reloadedEvent = new CustomEvent('principals-reloaded', {
          bubbles: true,
          cancelable: true
        });
      document.dispatchEvent(reloadedEvent);
    })
  }

  return (
    <div>
      { props.isUserListPage ? <UsersManager users={users} groups={groups} reload={() => handleLoadUsers()}/>
                                 : <GroupsManager users={users} groups={groups} reload={() => handleLoadUsers()}/> }
    </div>
  );
}
