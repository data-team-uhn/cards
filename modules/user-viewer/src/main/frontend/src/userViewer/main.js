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
import PropTypes from 'prop-types';
import classNames from 'classnames';
import AppBar from '@material-ui/core/AppBar';
import Button from '@material-ui/core/Button';
import CameraIcon from '@material-ui/icons/PhotoCamera';
import Card from '@material-ui/core/Card';
import CardActions from '@material-ui/core/CardActions';
import CardContent from '@material-ui/core/CardContent';
import CardMedia from '@material-ui/core/CardMedia';
import CssBaseline from '@material-ui/core/CssBaseline';
import Grid from '@material-ui/core/Grid';
import Toolbar from '@material-ui/core/Toolbar';
import Typography from '@material-ui/core/Typography';
import Link from '@material-ui/core/Link';
import { withStyles } from '@material-ui/core/styles';

import Dialog from '@material-ui/core/Dialog';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogContentText from '@material-ui/core/DialogContentText';
import DialogTitle from '@material-ui/core/DialogTitle';
import Slide from '@material-ui/core/Slide';
import { Snackbar } from '@material-ui/core';

const styles = theme => ({
  appBar: {
    position: 'relative',
  },
  icon: {
    marginRight: theme.spacing.unit * 2,
  },
  heroUnit: {
    backgroundColor: theme.palette.background.paper,
  },
  mainContent: {
    maxWidth: 600,
    margin: '0 auto',
    padding: `${theme.spacing.unit * 8}px 0 ${theme.spacing.unit * 6}px`,
  },
  heroButtons: {
    marginTop: theme.spacing.unit * 4,
  },
  layout: {
    width: 'auto',
    marginLeft: theme.spacing.unit * 3,
    marginRight: theme.spacing.unit * 3,
    [theme.breakpoints.up(1100 + theme.spacing.unit * 3 * 2)]: {
      width: 1100,
      marginLeft: 'auto',
      marginRight: 'auto',
    },
  },
  cardGrid: {
    padding: `${theme.spacing.unit * 8}px 0`,
  },
  card: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    maxHeight: '400',
  },
  cardMedia: {
    paddingTop: '56.25%', // 16:9
    objectFit: 'cover',
    flexDirection: 'column',
  },
  cardContent: {
    flexGrow: 1,
  },
  footer: {
    backgroundColor: theme.palette.background.paper,
    padding: theme.spacing.unit * 6,
  },
});

// COMMENTED OUT PORTION IS FOR POPUP USER DELETE FORM WHICH IS CURRENTLY NOT WORKING
/*
const UserCardComponent = withStyles(styles)(UserCard);

function Transition(props) {
  return <Slide direction="up" {...props} />
}

class DeleteUserDialogue extends React.Component {
  constructor (props) {
    super(props); 
    this.state = {
      open: true,
      isSubmitted: false,
      failedDelete: false
    };
    this.dialogueText = "Are you sure you want to delete user " + this.props.userName + "?";
  }
 
  handleDelete() {
    let url = "http://"+"localhost:8080"+"/system/userManager/user/"+this.props.userName+".delete.html";
    fetch(url, {
      method: 'POST',
      headers: {
        'Authorization' : 'Basic' + btoa('admin:admin')
      }
    })
    .then(() => {
      this.setState({
        isSubmitted: true,
        failedDelete: false
      });
      this.dialogueText = "Sucessfully deleted " + this.props.userName + ".";
    })
    .catch((error) => {
      this.setState({
        isSubmitted: true,
        failedDelete: true
      });
      if(error.getElementById("Status")===404) {
        this.dialogueText = "User " + this.props.userName + " could not be found. Delete failed.";
      }
      else {
        this.dialogueText = "An error has occured with your delete reques. Error: " + error;
      }
      console.log(error);
    })
  }

  handleClose() {
    this.setState({open: false});
  }

  render() {
    return (
      <div>
        <Dialog
          open={true}
          TransitionComponent={Transition}
          keepMounted
          onClose={() =>this.handleClose()}
        >
        <DialogContent>
          <DialogueTitle>
            Delete user + {this.props.userName}.
          </DialogueTitle>
          <DialogContentText>
            {this.dialogueText}
          </DialogContentText>
        </DialogContent>  
        <DialogActions>
          {!this.isSubmitted && <button>Delete</button>}
          <button onClick={() => this.handleClose()}>{this.isSubmitted===false? "Cancel" : "Close"}</button>
        </DialogActions>
        </Dialog>
      </div>
    );
  }
}*/


// Experimental Dialogue
class TestPopup extends React.Component {
  constructor(props) {
    super(props);
    this.state ={open: true};
    this.handleClose = this.handleClose.bind(this);
  }

  handleClose() {
    this.setState({open: false});
  }

  render() {
    return (
      <div>
        <Dialog
          open={this.state.open}
          onClose={() => this.handleClose()}
        >
          <DialogTitle>
            Test Dialogue some content
          </DialogTitle>
          <DialogActions>
            <Button onClick={this.handleClose}>Close</Button>
          </DialogActions>
        </Dialog>
      </div>
    );
  }
}


class UserBoard extends React.Component {
  constructor (props) {
    super(props);
    this.state = {
      userNames: [],
      user: "myuser",
      admin: null,
      systemUser:null,
      disabled:null,
      path:null,

      deleteUser: false,
      oldPwd: "",
      newPwd: "",
      newPwdConfirm: "",
      
      newUserName: "",
      newUserPwd: "",
      newUserPwdConfirm: "",
      deployPopup: false

    };

    this.handleLoadUsers = this.handleLoadUsers.bind(this);
    this.handleSetUserBin = this.handleSetUserBin.bind(this);
    this.handleSetUsersSystem = this.handleSetUsersSystem.bind(this);
  }


  //"http://localhost:8080/bin/cpm/usermanagement.user"

  // Loads simple list of local users (with only path and groups as user information)
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

  // Automatically loads full list of users when component is rendered 
  componentWillMount() {
    this.handleLoadUsers();
  }

  // Selects a specific local user and obtains more information from them using a POST request to home/users
  handleSetUsersSystem(userName) {
    let pathUrl = "http://"+"localhost:8080"+"/system/userManager/user/"+userName+".json";
    let path = "";
    fetch(pathUrl,
      {
        method: 'GET',
        headers: {
          'Authorization': 'Basic' + btoa('admin:admin')
        }
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      path = "http://"+"localhost:8080"+data.path+".json";
      this.setState({
        path: data.path,
      });
      return path;
    }).then((path) => {
      fetch(path, 
      {
        method: 'GET',
        headers: {
          'Authorization': 'Basic' + btoa('admin:admin')
        }
      })
      .then((response) =>{
        return response.json();
      })
      .then((data) => {
        this.setState({
          systemUser: data["jcr:primaryType"] === "rep:SystemUser" ? true : false,
          currentUser: userName,
        });
      })
    })
    .catch((error) => {
      console.log(error);
    });
  }  

  // Performs same function as above method but uses bin/cpm/usermanagement.user.json/ instad.
  // This provides more information than the previous method but will probably not be used because that directory
  // is only accessible when on dev mode.
  handleSetUserBin(userName) {
    let url = "http://"+"localhost:8080"+"/bin/cpm/usermanagement.user.json/" + userName;
    fetch(url, 
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
      
      this.setState({
        admin: data.admin,
        systemUser: data.systemUser,
        disabled: data.disabled,
        path: data.path
      });
      this.setState({currentUser: userName});
      admin = data.admin;
      systemUser = data.systemUser;
      disabled = data.disabled;
      path = data.path;
      console.log(data);
      console.log(admin+" "+systemUser+" "+disabled+" "+path);
    })
    .catch((error) => {
      console.log(JSON.stringify(error, Object.getOwnPropertyNames(error)));
    })
  }

  // Given a local user name, deletes the user
  handleDelete(name) {
    let url = "http://"+"localhost:8080"+"/system/userManager/user/"+name+".delete.html";
    fetch(url, {
      method: 'POST',
      headers: {
        'Authorization' : 'Basic' + btoa('admin:admin')
      }
    })
    .catch((error) => {

      if(error.getElementById("Status")===404) {
        console.log("missing user 404");
      }
      else {
        console.log("other error 505");
      }
      console.log(error);
    })
  }

  // Given a local user name, changes the password of the user
  handlePasswordChange(name) {
    let formData = new FormData();
    formData.append('oldPwd', this.state.oldPwd);
    formData.append('newPwd', this.state.newPwd);
    formData.append('newPwdConfirm', this.state.newPwdConfirm);
    let url = "http://localhost:8080/system/userManager/user/" + name + ".changePassword.html";
  
    console.log(formData);
    fetch (url, {
      method: 'POST',
      headers: {
        'Authorization': 'Basic' + btoa('admin:admin')
      },
      body: formData
    })
    .then(() =>{
      //alert("Password has been changed");
    })
    .catch ((error) =>{
      if(error.getElementById("Status")===404) {
        console.log("missing user 404");
      }
      else {
        console.log("other error 505");
      }
      console.log(error);
    });
  }


  // Creates a local user based on several state variables changed by form input
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

  hidePopup () {
    this.setState({deployPopup: false});
  }

  showPopup() {
    this.setState({deployPopup: true});
  }

  render() {
    const {classes} = this.props;
    const userList = this.state.userNames.map((value, index) => {//userNamesHolder.map((value, index) => {
      return(
        <li key = {index}><Button onClick={()=>this.handleSetUsersSystem(value)}>{value}</Button></li>
        
      );
    })
    return (
      <React.Fragment>
        {/* Blank navbar */}
       {this.state.deployPopup && 
          <TestPopup handleClose={() => this.hidePopup()}></TestPopup>
        }

        <AppBar position="static">
          <Toolbar>
            <Typography variant="h6" color="inherit" noWrap>
              LFS Repository
            </Typography>
          </Toolbar>
        </AppBar>
        
        <button>Create New User</button>

        <ul>
          {userList}
        </ul>
        
        <Card>
          <CardContent>
            <Typography gutterBottom variant="h5" component="h2">
              User Current: {this.state.currentUser}
            </Typography>
            <Typography>Admin status: {this.state.admin=== true ? "true" : "false"}</Typography>
            <Typography>System user status: {this.state.systemUser=== true ? "true" : "false"}</Typography>
            <Typography>Disabled: {this.state.disabled=== true ? "true" : "false"}</Typography>
            <Typography>Path: {this.state.path}</Typography>
          </CardContent>
          <CardActions>
            <button>
              Change User's Password
            </button>
            <button onClick={() => this.handleDelete(this.state.currentUser)}>
              Delete User
            </button>

          </CardActions>
        </Card>

        <button onClick={() => this.handleLoadUsers()}>Load Users</button>
        <button onClick={()=>this.showPopup()}>Trigger popup</button>

        <p>Change local user password.</p>
        <form
          onSubmit={() => this.handlePasswordChange(this.state.currentUser)}
        >

        <label>
          Old Password
          <textarea value={this.state.oldPwd} onChange={(event)=>{this.setState({oldPwd: event.target.value})}}></textarea>
        </label>
        <label>
          New Password
          <textarea value={this.state.newPwd} onChange={(event)=>{this.setState({newPwd: event.target.value})}}></textarea>
        </label>
        <label>
          Confirm New Password
           <textarea value={this.state.newPwdConfirm} onChange={(event)=>{this.setState({newPwdConfirm: event.target.value})}}></textarea>
        </label>
       
       <input type="submit" value="Submit" />

        </form>
        
        <p>Create new local user.</p>
        <form
          onSubmit={()=>this.handleCreateUser()}
        >
          <label>
            Name
            <textarea value={this.state.newUserName} onChange={(event) => {this.setState({newUserName: event.target.value})}}></textarea>
          </label>
          <label>
            Password
            <textarea value={this.state.newUserPwd} onChange={(event) => {this.setState({newUserPwd: event.target.value})}}></textarea>
          </label>
          <label>
            Confirm Password
            <textarea value={this.state.newUserPwdConfirm} onChange={(event) => {this.setState({newUserPwdConfirm: event.target.value})}}></textarea>
          </label>
          <input type="submit" value="Submit" />
        </form>
      </React.Fragment>
    );
  }
}

export default withStyles(styles)(UserBoard);

ReactDOM.render(<UserBoard/>, document.getElementById('user-board'));