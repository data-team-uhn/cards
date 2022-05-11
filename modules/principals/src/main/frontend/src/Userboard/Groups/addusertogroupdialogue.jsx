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

import withStyles from '@material-ui/styles/withStyles';
import userboardStyle from '../userboardStyle.jsx';

import { Avatar, Button, Dialog, DialogTitle, DialogActions, DialogContent, Grid } from "@material-ui/core";

import MaterialTable from 'material-table';

const GROUP_URL="/system/userManager/group/";

class AddUserToGroupDialogue extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            selectedUsers: []
        }
    }

    handleAddUsers() {
        let formData = new FormData();

        var i;
        for (i = 0; i < this.state.selectedUsers.length; ++i) {
            formData.append(':member', this.state.selectedUsers[i]);
        }

        fetch(GROUP_URL + this.props.name + ".update.html",
            {
                method: 'POST',
                credentials: 'include',
                body: formData
            })
            .then(() => {
                this.setState({ selectedUsers: [] });
                this.props.reload();
                this.handleExit();
            })
            .catch((error) => {
                console.log(error);
            });
    }

    handleSelectRowClick(rows) {
        let chosens = rows.map((row) => row.name);
        this.setState({ selectedUsers: chosens });
    }

    handleEntering() {
        this.setState({
            freeUsers: this.props.allUsers
        });
        if (this.props.groupUsers != "") {
          var groupUsersArray = this.props.groupUsers?.map((n) => n.name);
          let filtered = this.props.allUsers.filter( (el) => {
              return !groupUsersArray.includes( el.name );
            } );
          this.setState({ freeUsers: filtered });
        }
    }

    handleExit() {
        this.setState({ selectedUsers: [] });
        this.props.allUsers.forEach(function(item, index) {
          if (item.tableData) {
              item.tableData.checked = false;
          }
        })
        this.props.handleClose();
    }

    render() {
        const { classes } = this.props;
        const headerBackground = this.props.theme.palette.grey['200'];

        return (
            <Dialog
                maxWidth="sm"
                open={this.props.isOpen}
                onEntering={() => this.handleEntering()}
                onClose={() => this.handleExit()}
            >
                <DialogTitle>
                    Add Users to the {this.props.name} group
                </DialogTitle>
                <DialogContent>
                    <Grid container>
                        <div>
                            <MaterialTable
                              title=""
                              style={{ boxShadow : 'none' }}
                              options={{
                                headerStyle: { backgroundColor: headerBackground },
                                emptyRowsWhenPaging: false,
                                selection: true,
                                showSelectAllCheckbox : false,
                                showTextRowsSelected: false,
                                selectionProps: rowData => ({
                                    color: 'primary'
                                  })
                              }}
                              columns={[
                                { title: 'Avatar', field: 'imageUrl', render: rowData => <Avatar src={rowData.imageUrl} className={classes.info}>{rowData.initials}</Avatar>},
                                { title: 'User Name', field: 'name' },
                                { title: 'Admin', field: 'isAdmin', type: 'boolean' },
                                { title: 'Disabled', field: 'isDisabled', type: 'boolean' },
                              ]}
                              data={this.state.freeUsers}
                              onSelectionChange={(rows) => {this.handleSelectRowClick(rows)}}
                            />
                        </div>
                    </Grid>
                </DialogContent>
                <DialogActions className={classes.dialogActions}>
                    <Button variant="contained" size="small" color="primary" onClick={() => this.handleAddUsers()} disabled={this.state.selectedUsers.length == 0}>Add</Button>
                    <Button variant="contained" size="small" onClick={() => this.handleExit()}>Close</Button>
                </DialogActions>
            </Dialog>
        );
    }
}

export default withStyles (userboardStyle, {withTheme: true})(AddUserToGroupDialogue);
