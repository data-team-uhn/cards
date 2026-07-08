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


import { useState, useEffect, useContext } from 'react';

import GroupsManager from './Groups/GroupsManager.jsx';
import UsersManager from './Users/UsersManager.jsx';
import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";

export default function PrincipalsContainer(props) {
  const [ users, setUsers ] = useState([]);
  const [ groups, setGroups ] = useState([]);
  const [ loading, setLoading ] = useState(true);
  const globalLoginDisplay = useContext(GlobalLoginContext);

  let handleLoadGroups = () => {
    fetchWithReLogin(globalLoginDisplay, "/home/groups.json",
      {
        method: 'GET',
        credentials: 'include'
      })
      .then((response) => response.json())
      .then((data) => setGroups(data.rows))
      .catch((error) => console.log(error?.statusText ?? error))
      .finally(() => {
        setLoading(false);
        // This event is needed in cases we do not want to collapse details panel after reload
        let reloadedEvent = new CustomEvent('principals-reloaded', {
          bubbles: true,
          cancelable: true
        });
        document.dispatchEvent(reloadedEvent);
      })
  }

  let handleLoadUsers = () => {
    setLoading(true);
    fetchWithReLogin(globalLoginDisplay, "/home/users.json",
      {
        method: 'GET',
        credentials: 'include'
      })
      .then((response) => response.json())
      .then((data) => {
        data.rows?.forEach((r) => {
          const firstInitial = r.firstname?.charAt(0) || '';
          const lastInitial = r.lastname?.charAt(0) || '';
          const combinedInitials = firstInitial + lastInitial;
          r.initials = combinedInitials || r.name?.charAt(0) || '?';
        });
        setUsers(data.rows);
      })
      .catch((error) => console.log(error?.statusText ?? error))
      .finally(() => handleLoadGroups());
  }

  useEffect(() => {
    handleLoadUsers();
  }, []);

  return (
    <div>
      { props.isUserListPage ? <UsersManager users={users} groups={groups} loading={loading} reload={handleLoadUsers}/>
        : <GroupsManager users={users} groups={groups} loading={loading} reload={handleLoadUsers}/> }
    </div>
  );
}
