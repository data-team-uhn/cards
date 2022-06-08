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
import { Dialog } from '@mui/material';

import MainLoginComponent from './loginMainComponent';

export const GlobalLoginContext = React.createContext();

export function fetchWithReLogin(displayLoginCtx, url, fetchArgs, discardOnFailure) {
    return new Promise(function(resolve, reject) {
      function fetchFunc() {
        fetch(url, fetchArgs)
        .then((response) => {
          if (response.status == 401 || response.status == 500) {
              displayLoginCtx.dialogOpen(fetchFunc, discardOnFailure);
          } else if (response.ok && response.url.startsWith(window.location.origin + "/login")) {
              displayLoginCtx.dialogOpen(fetchFunc, discardOnFailure);
          } else {
              resolve(response);
          }
        })
        .catch((err) => {reject(err)});
      }
      fetchFunc();
    });
}

class DialogueLoginContainer extends React.Component {
  constructor(props) {
    super(props);
  }

  render () {
    const { classes } = this.props;

    return (
      <Dialog
        open={this.props.isOpen}
      >
        <MainLoginComponent handleLogin={this.props.handleLogin} redirectOnLogin={false}/>
      </Dialog>
    );
  }
}

export default DialogueLoginContainer;
