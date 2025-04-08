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

import React, { useState, useContext } from "react";
import PropTypes from "prop-types";
import { checkPropTypes } from "../../propTypes";
import { Alert, Button, Dialog, DialogTitle, DialogContent, TextField, Tooltip } from "@mui/material";
import { withStyles } from 'tss-react/mui';
import { Formik } from "formik";
import * as Yup from "yup";
import { fetchWithReLogin, GlobalLoginContext } from "../../login/loginDialog.js";

import styles from "../../styling/styles";

function FormFields(props) {
  const { classes, requireOldPassword } = props;

  const {
    values: { newPwd, newPwdConfirm, oldPwd },
    errors,
    touched,
    handleSubmit,
    handleChange,
    handleReset,
    isValid,
    setFieldTouched
  } = props;

  let change = (name, e) => {
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
            variant="standard"
            id="oldPwd"
            name="oldPwd"
            helperText={touched.oldPwd ? errors.oldPwd : ""}
            error={touched.oldPwd && Boolean(errors.oldPwd)}
            label="Old Password"
            fullWidth
            type="password"
            value={oldPwd || ""}
            onChange={change.bind(null, "oldPwd")}
            className={classes.form}
            required
          />
        }
        <TextField
          variant="standard"
          id="newPwd"
          name="newPwd"
          helperText={touched.newPwd ? errors.newPwd : ""}
          error={touched.newPwd && Boolean(errors.newPwd)}
          label="New Password"
          fullWidth
          type="password"
          value={newPwd || ""}
          onChange={change.bind(null, "newPwd")}
          className={classes.form}
          required
        />
        <TextField
          variant="standard"
          id="newPwdConfirm"
          name="newPwdConfirm"
          helperText={touched.newPwdConfirm ? errors.newPwdConfirm : ""}
          error={touched.newPwdConfirm && Boolean(errors.newPwdConfirm)}
          label="Confirm New Password"
          fullWidth
          type="password"
          value={newPwdConfirm || ""}
          onChange={change.bind(null, "newPwdConfirm")}
          className={classes.form}
          required
        />
        { !isValid ?
          // Render hover over and button
          <React.Fragment>
            <Tooltip title="You must fill in all fields.">
              <span>
                <Button
                  type="submit"
                  variant="contained"
                  className={classes.formAction}
                  disabled={!isValid}
                >
                  Change User Password
                </Button>
              </span>
            </Tooltip>
          </React.Fragment> :
          // Else just render the button
          <Button
            type="submit"
            variant="contained"
            className={classes.formAction}
            disabled={!isValid}
          >
            Change User Password
          </Button>
        }
        <Button
          variant="outlined"
          className={classes.formAction}
          onClick={handleReset}
        >
          Cancel
        </Button>
      </form>
    );
}

const FormFieldsComponent = withStyles(FormFields, styles);

function ChangeUserPasswordDialog(props) {
  checkPropTypes(ChangeUserPasswordDialog, props);
  const { handleClose, isOpen, name, requireOldPassword } = props;

  const [ error, setError ] = useState("");
  const values = { newPwd: "", newPwdConfirm: "" };
  const globalLoginDisplay = useContext(GlobalLoginContext);

  let handlePasswordChange = ({ newPwd, newPwdConfirm, oldPwd }) => {
    // Build formData object.
    // We need to do this because sling does not accept JSON, need url encoded data
    let formData = new FormData();
    formData.append('newPwd', newPwd);
    formData.append('newPwdConfirm', newPwdConfirm);
    if (oldPwd) {
      formData.append('oldPwd', oldPwd);
    }
    let url = "/system/userManager/user/" + name + ".changePassword.html";

    fetchWithReLogin(globalLoginDisplay, url, {
        method: 'POST',
        credentials: 'include',
        body: formData
    })
    .then(response => response.ok ? response.json() : Promise.reject(response))
    .then(() => handleCloseDialog(true))
    .catch((error) => handleError(error));
  }

  let handleError = (error) => {
    if (error.status == "500") {
      // Determine the exact error
      error.text()
        .then((text) => {
          // There should be a line that looks like <td><div id="Message">javax.jcr.RepositoryException: ...</div></td>
          // Parse it out
          let msg_re = /div id="Message">(.+)<\/div/;
          let match = msg_re.exec(text);
          // Under most cases (invalid password, old password does not match),
          // we can display a friendlier error by geting rid of the javax.jcr.RepositoryException
          let friendly_re = /javax.jcr.RepositoryException:(.+)/;
          let friendly_match = friendly_re.exec(match[1]);

          setError(friendly_match?.[1] || match?.[1] || error.statusText);
        })
    } else {
      setError(error.statusText);
    }
  }

  let handleCloseDialog = (success = false) => {
    setError("");
    handleClose && handleClose(success);
  }

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
      open={isOpen}
      onClose={() => handleCloseDialog(false)}
    >
      <DialogTitle>Change User Password for {name}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error">{error}</Alert>}
          <Formik
            initialValues={values}
            validationSchema={validationSchemaObj}
            onSubmit={handlePasswordChange}
            onReset={() => handleCloseDialog(false)}
            >
            {props => <FormFieldsComponent {...props} requireOldPassword={requireOldPassword} />}
          </Formik>
      </DialogContent>
    </Dialog>
  );
}

ChangeUserPasswordDialog.propTypes = {
  handleClose: PropTypes.func,
  isOpen: PropTypes.bool,
  name: PropTypes.string,
  requireOldPassword: PropTypes.bool
}

export default withStyles(ChangeUserPasswordDialog, styles);
