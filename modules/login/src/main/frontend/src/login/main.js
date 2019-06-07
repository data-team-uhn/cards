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
import Slide from '@material-ui/core/Slide';
import Button from '@material-ui/core/Button'
import SignUpForm from './signUpForm';
import SignIn from './loginForm';
import Dialog from '@material-ui/core/Dialog';

class MainPageContainer extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      signInShown: true,
      show: false
    }
  }

  // Toggle between sign in and sign up
  handleSwap = () => {
    this.setState(prevState => ({
      signInShown: !prevState.signInShown,
    }));
  }

  handleOpen() {
    this.setState({show: true});
  }

  handleClose() {
    this.setState({show: false});
  }

  render () {
    return (
      <div>
        <Dialog
          open={this.state.show}
          onClose={() => this.handleClose()}
        >
          {this.state.signInShown ? <SignIn swapForm={this.handleSwap} /> : <SignUpForm swapForm={this.handleSwap} />}
          <Button onClick={()=>this.handleClose()}>Close</Button>
          {document.getElementById('login-main-button').addEventListener('click', () => {this.setState({signInShown: true}); this.handleOpen();})}
          {document.getElementById('signup-main-button').addEventListener('click', () => {this.setState({signInShown: false}); this.handleOpen();})}
        </Dialog>
        
      </div>
    );
  }
}

export default MainPageContainer;

ReactDOM.render(<MainPageContainer/>, document.getElementById('main-login-container'));
