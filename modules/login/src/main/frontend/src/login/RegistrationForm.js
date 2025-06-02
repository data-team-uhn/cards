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
import React, { useState, useRef } from 'react';
import {
  Button,
  Grid,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import { withStyles } from 'tss-react/mui';
import PropTypes from 'prop-types';
import { checkPropTypes } from "../propTypes";
import { Formik } from "formik";
import * as Yup from "yup";

import ErrorDialog from "../components/ErrorDialog";
import styles from "../styling/styles";

function FormFields(props) {
  const {
	classes,
    values: { username, email, password, confirmPassword, loginOnSuccess, closeButtonText, submitButtonText },
    errors,
    touched,
    handleSubmit,
    handleChange,
    handleReset,
    isValid,
    setFieldTouched
  } = props;

  const change = (name, e) => {
    e.persist();
    handleChange(e);
    setFieldTouched(name, true, false);
  };

  let getButton = () => <Button
                          type="submit"
                          variant="contained"
                          disabled={!isValid}
                          className={`${classes.submit}${!loginOnSuccess ? ' ' + classes.closeButton : ''}`}
                          fullWidth={loginOnSuccess}
                        >
                          {submitButtonText}
                        </Button>;

  return (
      <form
        onSubmit={handleSubmit}
        className={classes.form}
      >
        <TextField
          variant="standard"
          id="email"
          name="email"
          helperText={touched.email ? errors.email : ""}
          error={touched.email && Boolean(errors.email)}
          label="Email"
          fullWidth
          value={email}
          onChange={change.bind(null, "email")}
          className={classes.form}
          required
          autoFocus
        />
        <TextField
          variant="standard"
          id="username"
          name="username"
          helperText={touched.username ? errors.username : ""}
          error={touched.username && Boolean(errors.username)}
          label="Username"
          value={username}
          onChange={change.bind(null, "username")}
          fullWidth
          className={classes.form}
          required
        />
        <TextField
          variant="standard"
          id="password"
          name="password"
          helperText={touched.password ? errors.password : ""}
          error={touched.password && Boolean(errors.password)}
          label="Password"
          fullWidth
          type="password"
          value={password}
          onChange={change.bind(null, "password")}
          className={classes.form}
          required

        />
        <TextField
          variant="standard"
          id="confirmPassword"
          name="confirmPassword"
          helperText={touched.confirmPassword ? errors.confirmPassword : ""}
          error={touched.confirmPassword && Boolean(errors.confirmPassword)}
          label="Confirm Password"
          fullWidth
          type="password"
          value={confirmPassword}
          onChange={change.bind(null, "confirmPassword")}
          className={classes.form}
          required

        />
        <Grid container justifyContent="flex-end" alignItems="center" className={classes.actions}>
          { !loginOnSuccess &&
            <Grid>
              <Button
                variant="outlined"
                onClick={handleReset}
                className={classes.submit + " " + classes.closeButton}
              >
                {closeButtonText}
              </Button>
            </Grid>
          }
          <Grid>
          {!isValid ?
            // Render tooltip and button
            <Tooltip title="You must fill in all fields.">
              <div>
                { getButton() }
              </div>
            </Tooltip>
            :
            // Else just render the button
            getButton()
          }
          </Grid>
        </Grid>
      </form>
    );
}

const FormFieldsComponent = withStyles(FormFields, styles);

function RegistrationForm(props) {
  checkPropTypes(RegistrationForm, props);
  const { classes, handleLogin, handleSuccess, loginOnSuccess, handleExit, closeButtonText, submitButtonText } = props;

  let [ errorOpen, setErrorOpen ] = useState(false);
  let [ errorMsg, setErrorMsg ] = useState("");

  let form = useRef();

  let signIn = (username, password) => {
    fetch('/j_security_check',
      {
        method: 'POST',
        body: new URLSearchParams({
          "j_username": username,
          "j_password": password,
          "j_validate": true
        }),
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      }
    ).then(() => {
      if (handleLogin) {
        handleLogin(true);
      } else {
        window.location = new URLSearchParams(window.location.search).get('resource') || '/';
      }
    });
  }

  // submit function
  let submitValues = ({ username, email, confirmPassword, password }) => {

    // Build formData object.
    // We need to do this because sling does not accept JSON, need
    //  url encoded data
    let formData = new URLSearchParams();
    formData.append(":name", username);
    formData.append('pwd', password);
    formData.append('pwdConfirm', confirmPassword);
    formData.append('email', email);

    // Use native fetch, sort like the XMLHttpRequest so no need for other libraries.
    fetch('/system/userManager/user.create.html',
      {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: formData
      })
      .then((response) => {

        // Important note about native fetch, it does not reject failed
        // HTTP codes, it'll only fail when network error
        // Therefore, you must handle the error code yourself.
        if (!response.ok) {
          handleLogin?.(false);
          response.json().then((data) => {
            let errMsg = data?.error?.message;
            errMsg = (errMsg || "Unknown Error");
            setErrorOpen(true);
            setErrorMsg(errMsg);
            form.current.setFieldError("username", errMsg);
          })
          .catch(error => {
            setErrorOpen(true);
            setErrorMsg("Unknown Error (JSON Parsing Failed)");
            form.current.setFieldError("username", "Unknown Error (JSON Parsing Failed)");
          });
          throw Error(response.statusText);
        }

        handleSuccess?.();
        loginOnSuccess && signIn(username, password);
      })
      .catch(error => {
        console.log(error?.statusText ?? error);
        handleLogin?.(false);
      });
  }

  const values = {
      username: "",
      email: "",
      confirmPassword: "",
      password: "",
      loginOnSuccess: loginOnSuccess,
      closeButtonText: closeButtonText || "Close",
      submitButtonText: submitButtonText || "Submit"
  };

  const validationSchema = Yup.object({
      email: Yup.string("Enter your email")
        .email("Enter a valid email")
        .required("Email is required"),
      username: Yup.string("Enter a username")
        .required("The username is required"),
      password: Yup.string("")
        .min(8, "Password must contain at least 8 characters")
        .required("Enter your password"),
      confirmPassword: Yup.string("Enter your password")
        .required("Confirm your password")
        .oneOf([Yup.ref("password")], "Password does not match"),
    });

  return (
      <React.Fragment>
        <ErrorDialog open={errorOpen} onClose={() => setErrorOpen(false)}>
          <Typography>{errorMsg}</Typography>
        </ErrorDialog>
        <div className={classes.main}>
          <Formik
            initialValues={values}
            validationSchema={validationSchema}
            onSubmit={submitValues}
            onReset={handleExit}
            innerRef={el => (form = el)}
          >
            {props => <FormFieldsComponent {...props} />}
          </Formik>
        </div>
      </React.Fragment>
  );
}

RegistrationForm.propTypes = {
  handleLogin: PropTypes.func,
  handleSuccess: PropTypes.func,
  loginOnSuccess: PropTypes.bool,
  handleExit: PropTypes.func,
  closeButtonText: PropTypes.string,
  submitButtonText: PropTypes.string
};

export default withStyles(RegistrationForm, styles);
