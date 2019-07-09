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

import {Button, Table, TableBody, TableHead, TableRow, TableCell, TableFooter, TablePagination, Checkbox, Hidden, Dialog, DialogTitle, DialogActions, DialogContent, TextField, IconButton} from "@material-ui/core"
import {FirstPage, KeyboardArrowLeft, KeyboardArrowRight, LastPage} from "@material-ui/icons"

import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";
import GridContainer from "material-dashboard-react/dist/components/Grid/GridContainer.js";
import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
import CardFooter from "material-dashboard-react/dist/components/Card/CardFooter"
//import { Avatar } from "@material-ui/core";

import userboardStyle from './userboardStyle.jsx';
import CreateUserDialogue from "./createuserdialogue.jsx";
import DeleteUserDialogue from "./deleteuserdialogue.jsx"; 
import ChangeUserPasswordDialogue from "./changeuserpassworddialogue.jsx"; 
/*
const useStyles1 = makeStyles(theme => ({
  root: {
    flexShrink: 0,
    color: theme.palette.text.secondary,
    marginLeft: theme.spacing(2.5),
  },
}));
*/

class DialogueActions extends React.Component {
  handleFirstPage = (event) => {
    this.props.onChangePage(event, 0);
  }

  handleNextPage = (event) => {
    if (this.props.page < Math.ceil(this.props.count/this.props.rowsPerPage) - 1) {
      this.props.onChangePage(event, this.props.page + 1);
    }
  }

  handlePrevPage = (event) => {
    if (this.props.page > 0) {
      this.props.onChangePage(event, this.props.page - 1);
    }
  }

  handleLastPage = (event) => {
    this.props.onChangePage(event, Math.max(0, Math.ceil(this.props.count/this.props.rowsPerPage) - 1));
  }

    //const classes = useStyles1();
  render () {
    const {count, page, rowsPerPage} = this.props;

    return (
      <div >
        <IconButton
          onClick={this.handleFirstPage}
          disabled={page === 0}
        >
          <FirstPage/>
        </IconButton>
        <IconButton
          onClick={this.handlePrevPage} 
          disabled={page === 0}
        >
          <KeyboardArrowLeft/>
        </IconButton>
        <IconButton
          onClick={this.handleNextPage}
          disabled={page >= Math.ceil(count / rowsPerPage) -1}
        >
          <KeyboardArrowRight/>
        </IconButton>
        <IconButton
          onClick={this.handleLastPage}
          disabled={page >= Math.ceil(count / rowsPerPage) -1}
        >
          <LastPage/>
        </IconButton>
      </div>
    );
  }
  
}
/*DialogueActions = (props) => {
  const {count, page, rowsPerPage, onChangePage} = props;

  handleFirstPage= (event) => {
    onChangePage(event, 0);
  }

  handleNextPage= (event) => {
    if (page < Math.ceil(count/rowsPerPage) - 1) {
      onChangePage(event, page + 1);
    }
  }

  handlePrevPage = (event) => {
    if (page > 0) {
      onChangePage(event, page - 1);
    }
  }

  handleLastPage = (event) => {
    onChangePage(event, Math.max(0, Math.ceil(count/rowsPerPage) - 1));
  }

    //const classes = useStyles1();
  return (
    <div >
      <IconButton
        onClick={handleFirstPage}
        disabled={page === 0}
      >
        <FirstPageIcon/>
      </IconButton>
      <IconButton
        onClick={handlePrevPage} 
        disabled={page === 0}
      >
        <KeyboardArrowLeft/>
      </IconButton>
      <IconButton
        onClick={handleNextPage}
        disabled={page >= Math.ceil(count / rowsPerPage) -1}
      >
        <KeyboardArrowRight/>
      </IconButton>
      <IconButton
        onClick={handleLastPage}
        disabled={page >= Math.ceil(count / rowsPerPage) -1}
      >
        <LastPageIcon/>
      </IconButton>
    </div>
  );
  
}*/

DialogueActions.propTypes = {
  count: PropTypes.number.isRequired,
  onChangePage: PropTypes.func.isRequired,
  page: PropTypes.number.isRequired,
  rowsPerPage: PropTypes.number.isRequired,
};

class UsersManager extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      users: [],
      userFilter: null,

      currentUserName: "",
      currentUserIndex: -1,
      returnedUserRows: 0,
      totalUserRows: 0,
      
      userPaginationLimit: 5,
      userPageNumber: 0,

      deployCreateUser: false,
      deployDeleteUser: false,
      deployChangeUserPassword: false,
      deployMobileUserDialog: false,
    };
  }

  clearSelectedUser () {
    this.setState(
      {
        currentUserName: "",
        currentUserIndex: -1,
        returnedUserRows: 0,
        totalUserRows: 0
      }
    );
  }

  handleLoadUsers (filter, offset, limit) {
    let url = new URL("http://localhost:8080/home/users.json");

    if (filter !== null && filter !== "") {
      url.searchParams.append('filter', filter);
    }

    if (offset !== null) {
      url.searchParams.append('offset', offset);
    }

    if (limit !== null) {
      url.searchParams.append('limit', limit);
    }

    fetch(url,
      {
        method: 'GET',
        headers: {
          'Authorization': 'Basic ' + btoa('admin:admin')
        },
    })
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      this.clearSelectedUser();
      this.setState(
        {
          returnedUserRows: data.returnedrows, 
          totalUserRows: data.totalrows,
          users: data.rows
        }
      );
    })
    .catch((error) => {
      console.log(error);
    });
  }

  componentWillMount () {
    this.handleLoadUsers(null, 0, this.state.userPaginationLimit);
  }

  handleUserRowClick(index, name) {
    if (index === this.state.currentUserIndex) {
      this.setState({currentUserName: ""});
      this.setState({currentUserIndex: -1});
    } else {
      this.setState({currentUserName: name});
      this.setState({currentUserIndex: index});
    }
  }

  handleMobileUserRowClick (index,name) {
    this.setState({currentUserName: name});
    this.setState({currentUserIndex: index});
    this.setState({deployMobileUserDialog: true});
  }

  abstractHandleLoadUsers(page) {
    console.log("adlsfkjasd;f");
    //
    //this.handleLoadUsers(this.state.userFilter, page * this.state.userPaginationLimit, this.state.userPaginationLimit);
  }

  handleChangePage (event, page) {
    console.log("kldfjlasf");
    this.setState({userPageNumber: page});
    this.abstractHandleLoadUsers(page);
  }

  handleChangeRowsPerPage (event) {
    //this.setState({userPaginationLimit: parseInt(event.target.value, 10)});
  }

/*  handleFirstPage() {
    onChangePage(0);
  }

  handleNextPage() {
    if (this.state.userPageNumber < Math.ceil(this.state.totalUserRows/this.state.userPaginationLimit) - 1) {
      onChangePage(this.state.userPageNumber + 1);
    }
  }

  handlePrevPage () {
    if (this.state.userPageNumber > 0) {
      onChangePage(this.state.userPageNumber- 1);
    }
  }

  handleLastPage () {
    onChangePage(Math.ceil(this.state.totalUserRows/this.state.userPaginationLimit) - 1);
  }
  
  DialogueActions (props)
  {
  
    return (
      <div>
        <IconButton
          onClick={() => this.handleFirstPage()}
          //disabled={this.state.userPageNumber === 0}
        >
          <FirstPageIcon/>
        </IconButton>
        <IconButton
          onClick={() => this.handlePrevPage()} 
          //disabled={this.state.userPageNumber === 0}
        >
          <KeyboardArrowLeft/>
        </IconButton>
        <IconButton
          onClick={() => this.handleNextPage()}
          //disabled={this.state.userPageNumber >= Math.ceil(this.state.totalUserRows / this.state.userPaginationLimit) -1}
        >
          <KeyboardArrowRight/>
        </IconButton>
        <IconButton
          onClick={() => this.handleLastPage()}
          //disabled={this.state.userPageNumber >= Math.ceil(this.state.totalUserRows / this.state.userPaginationLimit) -1}
        >
          <LastPageIcon/>
        </IconButton>
      </div>
    );
  
  }
*/
  render() {
    const { classes } = this.props;

    return (
      <div>
        
        {<CreateUserDialogue isOpen={this.state.deployCreateUser} handleClose={() => {this.setState({deployCreateUser: false});}}/>}
        {this.state.deployDeleteUser && <DeleteUserDialogue handleClose={() => {this.setState({deployDeleteUser: false});}} name={this.state.currentUserName}/>}
        {this.state.deployChangeUserPassword && <ChangeUserPasswordDialogue handleClose={() => {this.setState({deployChangeUserPassword: false});}} name={this.state.currentUserName}/>}
                
        <Hidden mdUp implementation="css">
          <Dialog
            open={this.state.deployMobileUserDialog}
            onClose={() => {this.setState({deployMobileUserDialog: false});}}
          >
            <Card>
              <CardHeader color = "success">
                {
                  this.state.currentUserIndex >= 0 && <h2 className={classes.cardTitleWhite}>{this.state.users[this.state.currentUserIndex].name}</h2>
                }
              </CardHeader>
              <CardBody>
              {
                this.state.currentUserIndex >= 0 &&
                <div>
                  <p className={classes.cardCategory}>Principal Name</p>
                  <h3 className={classes.cardTitle}>{this.state.users[this.state.currentUserIndex].principalName}</h3>

                  <p className={classes.cardCategory}>Path</p>
                  <h3 className={classes.cardTitle}>{this.state.users[this.state.currentUserIndex].path}</h3>

                  <p className={classes.cardCategory}>Admin Status</p>
                  <h3 className={classes.cardTitle}>{this.state.users[this.state.currentUserIndex].isAdmin ? "True" : "False"}</h3>

                  <p className={classes.cardCategory}>Disabled</p>
                  <h3 className={classes.cardTitle}>{this.state.users[this.state.currentUserIndex].isDisabled ? "True" : "False"}</h3>

                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableCell>Groups</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {this.state.users[this.state.currentUserIndex].memberOf.map(
                        (row) => (
                          <TableRow
                            key = {row.name}
                          >
                            <TableCell>{row.name}</TableCell>
                          </TableRow>
                        )
                      )}
                    </TableBody>
                  </Table>
                </div>
              }
                <GridContainer>
                  <Button onClick={() => {this.setState({deployDeleteUser: true});}} disabled={this.state.currentUserIndex < 0 ? true:false}>Delete User</Button>
                  <Button onClick={() => {this.setState({deployChangeUserPassword: true});}} disabled={this.state.currentUserIndex < 0 ? true:false}>Change Password</Button>
                  <Button onClick={() => {this.setState({deployMobileUserDialog: false});}}>Close</Button>
                </GridContainer>
              </CardBody>
            </Card>
          </Dialog>
        </Hidden>


        <GridContainer>
          <GridItem xs={12} sm={12} md={5}>
            <Card>
              <CardHeader color="warning">
                <h4 className={classes.cardTitleWhite}>Users</h4>
              </CardHeader>
              <CardBody>
                <Button onClick={() => {this.setState({deployCreateUser: true});}}>Create New User</Button>
                <form
                  onSubmit={(event) => { event.preventDefault(); this.handleLoadUsers(this.state.userFilter, null, null);}}
                >
                  <TextField
                    id="user-filter"
                    name="user-filter"
                    label="Search User"
                    onChange={(event) => {this.setState({userFilter: event.target.value});}}
                  />
                </form>
                <Hidden smDown implementation="css">
                  <Table>
                    <TableHead>
                      <TableRow>
                        {/*this.userColumnNames.map(
                          row => (
                            <TableCell
                              key = {row.id}
                            >
                              {row.label}
                            </TableCell>
                          )
                          )*/}
                        <TableCell>User Names</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {this.state.users.map(
                        (row, index) => (
                          <TableRow
                            onClick={(event) => {this.handleUserRowClick(index, row.name);}}
                            aria-checked={index === this.state.currentUserIndex ? true:false}
                            key = {row.name}
                            selected={index === this.state.currentUserIndex ? true:false}
                          >
                            <TableCell>
                                <Checkbox
                                  checked = {index === this.state.currentUserIndex ? true:false}
                                />
                              {row.name}
                            </TableCell>
                          </TableRow>
                        )
                      )}
                    </TableBody>
                    
                    <TableFooter>
                      <TableRow>
                        <TablePagination
                          rowsPerPageOptions={[5, 10]}
                          colSpan={1}
                          count={this.state.totalUserRows}
                          rowsPerPage={this.state.userPaginationLimit}
                          page={this.state.userPageNumber}
                          SelectProps={{
                            inputProps: { 'aria-label': 'Rows per page' },
                            native: true,
                          }}
                          onChangePage={() => this.handleChangePage()}
                          onChangeRowsPerPage={this.handleChangeRowsPerPage}
                          ActionsComponent={DialogueActions}
                        />
                      </TableRow>
                    </TableFooter>
                  </Table>
                </Hidden>
                <Hidden mdUp implementation="css">
                  <Table>
                    <TableHead>
                      <TableRow>
                        {/*this.userColumnNames.map(
                          row => (
                            <TableCell
                              key = {row.id}
                            >
                              {row.label}
                            </TableCell>
                          )
                          )*/}
                        <TableCell>User Names</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {this.state.users.map(
                        (row, index) => (
                          <TableRow
                            onClick={(event) => {this.handleMobileUserRowClick(index, row.name);}}
                            aria-checked={index === this.state.currentUserIndex ? true:false}
                            key = {row.name}
                            selected={index === this.state.currentUserIndex ? true:false}
                          >
                            <TableCell>
                              {row.name}
                            </TableCell>
                          </TableRow>
                        )
                      )}
                    </TableBody>
                  </Table>
                </Hidden>
              </CardBody>
            </Card>
          </GridItem>
          <GridItem xs={12} sm={12} md={7}>
            <Hidden smDown implementation="css">
              <Card>
                <CardHeader color = "success">
                  {
                    this.state.currentUserIndex < 0 ?
                    <h2 className={classes.cardTitleWhite}>No user selected.</h2>
                    :
                    <h2 className={classes.cardTitleWhite}>{this.state.users[this.state.currentUserIndex].name}</h2>
                  }
                </CardHeader>
                <CardBody>
                {
                  this.state.currentUserIndex >= 0 &&
                  <div>
                    <p className={classes.cardCategory}>Principal Name</p>
                    <h3 className={classes.cardTitle}>{this.state.users[this.state.currentUserIndex].principalName}</h3>

                    <p className={classes.cardCategory}>Path</p>
                    <h3 className={classes.cardTitle}>{this.state.users[this.state.currentUserIndex].path}</h3>

                    <p className={classes.cardCategory}>Admin Status</p>
                    <h3 className={classes.cardTitle}>{this.state.users[this.state.currentUserIndex].isAdmin ? "True" : "False"}</h3>
 
                    <p className={classes.cardCategory}>Disabled</p>
                    <h3 className={classes.cardTitle}>{this.state.users[this.state.currentUserIndex].isDisabled ? "True" : "False"}</h3>

                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableCell>Groups</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {this.state.users[this.state.currentUserIndex].memberOf.map(
                          (row) => (
                            <TableRow
                              key = {row.name}
                            >
                              <TableCell>{row.name}</TableCell>
                            </TableRow>
                          )
                        )}
                      </TableBody>
                    </Table>
                  </div>
                }
                  <GridContainer>
                    <Button onClick={() => {this.setState({deployDeleteUser: true});}} disabled={this.state.currentUserIndex < 0 ? true:false}>Delete User</Button>
                    <Button onClick={() => {this.setState({deployChangeUserPassword: true});}} disabled={this.state.currentUserIndex < 0 ? true:false}>Change Password</Button>
                  </GridContainer>
                </CardBody>
              </Card>
            </Hidden>
          </GridItem>
        </GridContainer>
      </div>
    );
  }
}

export default withStyles (userboardStyle)(UsersManager);