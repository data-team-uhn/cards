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

let userNamesHolder = [];

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

{/* Stateless component for user cards */}
function UserCard(props) {
  const { classes } = props;

  return (
    <React.Fragment>
      <Grid item sm={6} md={4} lg={3}>
        <Card className={classes.card}>
          <CardContent className={classes.cardContent}>
            <Typography gutterBottom variant="h5" component="h2">
              Heading
            </Typography>
            <Typography>
              
            </Typography>
          </CardContent>
          <CardActions>
            <Button size="small" color="primary">
              View
            </Button>
            <Button size="small" color="primary">
              Edit
            </Button>
          </CardActions>
        </Card>
      </Grid>
    </React.Fragment>
  );
}

const UserCardComponent = withStyles(styles)(UserCard);

let user = "myuser";
let admin = null;
let systemUser = null;
let disabled = null;
let path = null;

class UserBoard extends React.Component {
  constructor (props) {
    super(props);
    this.state = {
      userNames: [],
      user: "myuser",
      admin: null,
      systemUser:null,
      disabled:null,
      path:null
    };

    this.handleLoadUsers = this.handleLoadUsers.bind(this);
    this.handleSetUser = this.handleSetUser.bind(this);
  }
  //"http://localhost:8080/bin/cpm/usermanagement.user"
  handleLoadUsers () {
    fetch("http://localhost:8080/system/userManager/user.1.json", 
      {
        method: 'GET',
        headers: {
          'Authorization': 'Basic ' + btoa('admin:admin')
        }
    })
    .then(function(response) {
      return response.json();
    })
    .then(function(data){
      console.log(JSON.stringify(data));
      var names = [];
      for (var name in data){
        names.push(name);
      }
      console.log(names);
      userNamesHolder = names;
      //this.setState({userNames: names});
      
    })
    .catch(function(error) {
      console.log(error);
    });
  }

  handleSetUser(userName) {
    this.setState({currentUser: userName});
    fetch("http://localhost:8080/bin/cpm/usermanagement.user.json/"+userName, 
      {
        method: 'GET',
        headers: {
          'Authorization': 'Basic ' + btoa('admin:admin')
        }
    })
    .then(function(response){
      return response.json();
    })
    .then(function(data){
      /*
      this.setState({
        admin: data.admin,
        systemUser: data.systemUser,
        disabled: data.disabled,
        path: data.path
      });*/
      admin = data.admin;
      systemUser = data.systemUser;
      disabled = data.disabled;
      path = data.path;
      console.log(data);
      console.log(admin+" "+systemUser+" "+disabled+" "+path);
    })
    .catch(function(error){
      console.log(error);
    })
  }

  /*
  handleUserPasswordChange(userName, oldPwd, newPwd, newPwdConfirm) {
    let formData = new FormData();
    formData.append('oldPwd', oldPwd);
    formData.append('newPwd', newPwd);
    formData.append('newPwdConfirm', newPwdConfirm);
    let url = "http://localhost:8080/system/userManager/user" + name + ".changePassword.html";
  
    fetch (url, {
      method: 'POST',
      body: formData
    })
    .then(function (response) {
      
    })
    .catch (

    );
  }
  
  handleDelete(userName) {
    let url = "http://localhost:8080/system/userManager/user/" + name + ".delete.html";

    fetch(url, {
      method: 'POST',
      headers: {
        'Authorization' : 'Basic' + btoa('admin:admin')
      }
    })
    .then(function (response) {
      alert("User"+name+" was deleted.")}
    .catch (
  
    );
  }
  */

  render() {
    const {classes} = this.props;
    const userList = userNamesHolder.map((value, index) => {
      return(
        <li key = {index}><Button onClick={this.handleSetUser(value)}>{value}</Button></li>
        
      );
    })
    return (
      <React.Fragment>
        {/* Blank navbar */}
        <AppBar position="static">
          <Toolbar>
            <Typography variant="h6" color="inherit" noWrap>
              LFS Repository
            </Typography>
          </Toolbar>
        </AppBar>
        
        <ul>
          {userList}
        </ul>

        <Card>
          <CardContent>
            <Typography gutterBottom variant="h5" component="h2">
              Heading
            </Typography>
            <Typography>User: {user}</Typography>
            <Typography>Admin status: {admin=== true ? "true" : "false"}</Typography>
            <Typography>System user status: {systemUser=== true ? "true" : "false"}</Typography>
            <Typography>Disabled: {disabled=== true ? "true" : "false"}</Typography>
            <Typography>Path: {path}</Typography>
          </CardContent>
          <CardActions>
            <Button size="small" color="primary">
              View
            </Button>
            <Button size="small" color="primary">
              Edit
            </Button>
            <button>
              Change User's Password
            </button>
            <button>
              Delete User
            </button>

          </CardActions>
        </Card>

        <button onClick={() => this.handleLoadUsers}>Load Users</button>
        <button onClick={() => this.handleSetUser("myuser")}>Load Admin</button>
      </React.Fragment>
    );
  }
}

export default withStyles (styles) (UserBoard);

ReactDOM.render(<UserBoard/>, document.getElementById('user-board'));