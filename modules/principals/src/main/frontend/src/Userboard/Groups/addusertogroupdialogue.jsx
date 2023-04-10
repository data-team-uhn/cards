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

import withStyles from '@mui/styles/withStyles';
import userboardStyle from '../userboardStyle.jsx';

import { Avatar, Button, Dialog, DialogTitle, DialogActions, DialogContent, Grid } from "@mui/material";

import MaterialReactTable from 'material-react-table';

const GROUP_URL="/system/userManager/group/";

class AddUserToGroupDialogue extends React.Component {
    constructor(props) {
      super(props);
      this.state = {
        freeUsers: []
      }
      this.tableRef = React.createRef();
    }

    handleAddUsers() {
        let formData = new FormData();

        let selectedUsers = Object.keys(this.tableRef.current.getState().rowSelection);
        for (var i = 0; i < selectedUsers.length; ++i) {
            formData.append(':member', this.state.freeUsers[selectedUsers[i]].name);
        }

        fetch(GROUP_URL + this.props.name + ".update.html",
            {
                method: 'POST',
                credentials: 'include',
                body: formData
            })
            .then(() => {
                this.props.reload();
                this.handleExit();
            })
            .catch((error) => {
                console.log(error);
            });
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
        this.props.handleClose();
    }

    render() {
        const { classes } = this.props;

        return (
            <Dialog
                maxWidth="sm"
                open={this.props.isOpen}
                onClose={() => this.handleExit()}
                TransitionProps={{
                    onEntering: () => this.handleEntering()
                }}>
                <DialogTitle>
                    Add Users to the {this.props.name} group
                </DialogTitle>
                <DialogContent>
                    <Grid container>
                        <div>
                            <MaterialReactTable
                              tableInstanceRef={this.tableRef}
                              enableColumnActions={false}
                              enableColumnFilters={false}
                              enableSorting={false}
                              enableTopToolbar={false}
                              enableToolbarInternalActions={false}
                              muiTableHeadCellProps={{
                                sx: (theme) => ({
                                  background: theme.palette.grey['200'],
                                }),
                              }}
                              enableRowSelection
                              enableSelectAll={false}
                              muiSelectCheckboxProps={{ color: 'primary' }}
                              muiTableBodyRowProps={({ row }) => ({
                                onClick: row.getToggleSelectedHandler(),
                                sx: {
                                  cursor: 'pointer',
                                },
                              })}
                              columns={[
                                { header: 'Avatar', accessorKey: 'imageUrl', size: 8,
                                  Cell: ({ renderedCellValue, row }) => <Avatar src={row.original.imageUrl} className={classes.info}>{row.original.initials}</Avatar>},
                                { header: 'User Name', accessorKey: 'name' },
                                { header: 'Admin', accessorKey: 'isAdmin', size: 10,
                                  Cell: ({ renderedCellValue, row }) => (row.original.isAdmin ? <CheckIcon /> : "")
                                },
                                { header: 'Disabled', accessorKey: 'isDisabled', size: 10,
                                  Cell: ({ renderedCellValue, row }) => (row.original.isDisabled ? <CheckIcon /> : "")
                                },
                              ]}
                              data={this.state.freeUsers}
                            />
                        </div>
                    </Grid>
                </DialogContent>
                <DialogActions className={classes.dialogActions}>
                    <Button variant="contained" size="small" color="primary" onClick={() => this.handleAddUsers()}>Add</Button>
                    <Button variant="outlined" size="small" onClick={() => this.handleExit()}>Close</Button>
                </DialogActions>
            </Dialog>
        );
    }
}

export default withStyles (userboardStyle, {withTheme: true})(AddUserToGroupDialogue);
