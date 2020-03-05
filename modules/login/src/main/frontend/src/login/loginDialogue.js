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
import ReactDOM from 'react-dom';
import SignUpForm from './signUpForm';
import SignIn from './loginForm';
import { Button, Dialog } from '@material-ui/core';

class DialogueLoginContainer extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      signInShown: true,
      opened: false
    }
    {document.getElementById('login-homepage-button') && document.getElementById('login-homepage-button').addEventListener('click', () => {this.setState({signInShown: true}); this.handleOpen();})}
    {document.getElementById('signup-homepage-button') && document.getElementById('signup-homepage-button').addEventListener('click', () => {this.setState({signInShown: false}); this.handleOpen();})}
  }

  handleOpen() {
    this.setState({opened: true});
  }

  handleClose() {
    this.setState({opened: false});
  }

  // Toggle between sign in and sign up
  handleSwap = () => {
    this.setState(prevState => ({
      signInShown: !prevState.signInShown,
    }));
  }

  render () {
    return (
      <Dialog
        open={this.state.opened}
        onClose={() => this.handleClose()}
      >
        {this.state.signInShown ? <SignIn /> : <SignUpForm loginOnSuccess={true} />}
        <Button onClick={()=>this.handleClose()}>Close</Button>
      </Dialog>
    );
  }
}

export default DialogueLoginContainer;

ReactDOM.render(<DialogueLoginContainer/>, document.getElementById('dialogue-login-container'));
