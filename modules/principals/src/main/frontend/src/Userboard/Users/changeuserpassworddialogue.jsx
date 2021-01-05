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
import { Button, Grid, Dialog, DialogTitle, DialogContent, TextField, Tooltip, Typography, withStyles } from "@material-ui/core";
import { Formik } from "formik";
import * as Yup from "yup";

import styles from "../../styling/styles";

class FormFields extends React.Component {
  constructor(props) {
    super(props);
  }

  render() {

    const { classes, requireOldPassword } = this.props;

    const {
      values: { newPwd, newPwdConfirm, oldPwd },
      errors,
      touched,
      handleSubmit,
      handleChange,
      handleReset,
      isValid,
      setFieldTouched
    } = this.props;

    const change = (name, e) => {
      e.persist();
      handleChange(e);
      setFieldTouched(name, true, false);
    };

    return (
      <form
        onSubmit={handleSubmit}
        className={classes.form}
      >
        { requireOldPassword &&
          <TextField
            id="oldPwd"
            name="oldPwd"
            helperText={touched.oldPwd ? errors.oldPwd : ""}
            error={touched.oldPwd && Boolean(errors.oldPwd)}
            label="Old Password"
            fullWidth
            type="password"
            value={oldPwd}
            onChange={change.bind(null, "oldPwd")}
            className={classes.form}
            required
          />
        }
        <TextField
          id="newPwd"
          name="newPwd"
          helperText={touched.newPwd ? errors.newPwd : ""}
          error={touched.newPwd && Boolean(errors.newPwd)}
          label="New Password"
          fullWidth
          type="password"
          value={newPwd}
          onChange={change.bind(null, "newPwd")}
          className={classes.form}
          required
        />
        <TextField
          id="newPwdConfirm"
          name="newPwdConfirm"
          helperText={touched.newPwdConfirm ? errors.newPwdConfirm : ""}
          error={touched.newPwdConfirm && Boolean(errors.newPwdConfirm)}
          label="Confirm New Password"
          fullWidth
          type="password"
          value={newPwdConfirm}
          onChange={change.bind(null, "newPwdConfirm")}
          className={classes.form}
          required
        />
        <Button variant="contained" color="default" size="small" className={classes.formAction} onClick={handleReset}>Close</Button>
        { !isValid ?
          // Render hover over and button
          <React.Fragment>
            <Tooltip title="You must fill in all fields.">
              <span>
                <Button type="submit" variant="contained" color="primary" size="small" className={classes.formAction} disabled={!isValid}>Change User Password</Button>
              </span>
            </Tooltip>
          </React.Fragment> :
          // Else just render the button
          <Button type="submit" variant="contained" color="primary" size="small" className={classes.formAction} disabled={!isValid}>Change User Password</Button>
        }
      </form>
    );
  }
}

const FormFieldsComponent = withStyles(styles)(FormFields);

class ChangeUserPasswordDialogue extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
          error: ""
        };

        this.handlePasswordChange = this.handlePasswordChange.bind(this);
        this.handleCloseDialog = this.handleCloseDialog.bind(this);
    }

    handlePasswordChange({ newPwd, newPwdConfirm, oldPwd }) {
        // Important note about native fetch, it does not reject failed
        // HTTP codes, it'll only fail when network error
        // Therefore, you must handle the error code yourself.
        function handleErrors(response) {
          if (!response.ok) {
            throw Error(response.statusText);
          }
          return response;
        }

        // Build formData object.
        // We need to do this because sling does not accept JSON, need url encoded data
        let formData = new FormData();
        formData.append('newPwd', newPwd);
        formData.append('newPwdConfirm', newPwdConfirm);
        if (oldPwd) {
          formData.append('oldPwd', oldPwd);
        }
        let url = "/system/userManager/user/" + this.props.name + ".changePassword.html";

        // Use native fetch, sort like the XMLHttpRequest so no need for other libraries.
        fetch(url, {
            method: 'POST',
            credentials: 'include',
            body: formData
        })
        .then(handleErrors) // Handle errors first
        .then(() => {
            this.handleCloseDialog(true);
        })
        .catch((error) => {
            this.setState({error: error.message});
        });
    }
    
    handleCloseDialog(success = false) {
        this.setState({error: ""});
        this.props.handleClose && this.props.handleClose(success);
    }

    render() {
        const { classes, requireOldPassword } = this.props;
        const values = { newPwd: "", newPwdConfirm: "" };

        let validationSchema = {
          newPwd: Yup.string("")
            .min(8, "Password must contain at least 8 characters")
            .required("Enter new password"),
          newPwdConfirm: Yup.string("Enter new password")
            .required("Confirm new password")
            .oneOf([Yup.ref("newPwd")], "New password does not match"),
        };

        if (requireOldPassword) {
          validationSchema['oldPwd'] = Yup.string("Enter old password")
            .required("Enter old password");
        }

        const validationSchemaObj = Yup.object(validationSchema);

        return (
            <Dialog
                open={this.props.isOpen}
                onClose={() => this.handleCloseDialog(false)}
            >
                <DialogTitle>Change User Password for {this.props.name}</DialogTitle>
                <DialogContent>
                    <Grid container>
                        {this.state.error && <Typography component="h2" className={classes.errorMessage}>{this.state.error}</Typography>}
                        <Formik
                          initialValues={values}
                          validationSchema={validationSchemaObj}
                          onSubmit={this.handlePasswordChange}
                          onReset={() => this.handleCloseDialog(false)}
                          >
                          {props => <FormFieldsComponent {...props} requireOldPassword={requireOldPassword} />}
                        </Formik>
                    </Grid>
                </DialogContent>
            </Dialog>
        );
    }
}

export default withStyles(styles)(ChangeUserPasswordDialogue);