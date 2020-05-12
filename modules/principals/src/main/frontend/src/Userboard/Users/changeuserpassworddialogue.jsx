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
import { Button, Grid, Dialog, DialogTitle, DialogActions, DialogContent, TextField, Tooltip, Typography, withStyles } from "@material-ui/core";
import { Formik } from "formik";
import * as Yup from "yup";

import styles from "../../styling/styles";

class FormFields extends React.Component {
  constructor(props) {
    super(props);
  }

  render() {

    const { classes } = this.props;

    const {
      values: { oldPwd, newPwd, newPwdConfirm },
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
    }

    handlePasswordChange({ oldPwd, newPwd, newPwdConfirm }) {
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
        formData.append('oldPwd', oldPwd);
        formData.append('newPwd', newPwd);
        formData.append('newPwdConfirm', newPwdConfirm);
        let url = "/system/userManager/user/" + this.props.name + ".changePassword.html";

        // Use native fetch, sort like the XMLHttpRequest so no need for other libraries.
        fetch(url, {
            method: 'POST',
            headers: {
	          'Accept': 'application/json',
	          'Content-Type': 'application/x-www-form-urlencoded'
	        },
            body: formData
        })
        .then(handleErrors) // Handle errors first
        .then(() => {
            this.props.handleClose();
        })
        .catch((error) => {
            if (error.getElementById("Status") === 404) {
                this.setState({error: "Missing user"});
            } else {
                this.setState({error: "Invalid old password. Please try again"});
            }

            console.log(error);
        });
    }

    render() {
        const { classes, selfContained } = this.props;
        const values = { oldPwd: "", newPwd: "", newPwdConfirm: "" };

        const validationSchema = Yup.object({
          oldPwd: Yup.string("")
	        .min(8, "Password must contain at least 8 characters")
	        .required("Enter your password"),
	      newPwd: Yup.string("")
	        .min(8, "Password must contain at least 8 characters")
	        .required("Enter your password"),
	      newPwdConfirm: Yup.string("Enter new password")
	        .required("Confirm new password")
	        .oneOf([Yup.ref("newPwd")], "New password does not match"),
	    });

        return (
            <Dialog
                open={this.props.isOpen}
                onClose={() => this.props.handleClose()}
            >
                <DialogTitle>Change User Password of {this.props.name}</DialogTitle>
                <DialogContent>
                    <Grid container>
                        {this.state.error && <Typography component="h2" className={classes.errorMessage}>{this.state.error}</Typography>}
                        <Formik
				            render={props => <FormFieldsComponent {...props} />}
				            initialValues={values}
				            validationSchema={validationSchema}
				            onSubmit={this.handlePasswordChange}
				            onReset={this.props.handleClose}
				            ref={el => (this.form = el)}
				          />
                    </Grid>
                </DialogContent>
            </Dialog>
        );
    }
}

export default withStyles(styles)(ChangeUserPasswordDialogue);