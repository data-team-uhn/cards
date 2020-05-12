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
import {
    Button,
    TextField,
    Tooltip,
    Typography,
    withStyles
} from '@material-ui/core';
import { Formik } from "formik";
import * as Yup from "yup";

import styles from "../styling/styles";

class FormFields extends React.Component {
  constructor(props) {
    super(props);
  }

  render() {

    const { classes } = this.props;

    const {
      values: { username, email, password, confirmPassword },
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
        {!isValid ?
          // Render hover over and button
          <React.Fragment>
            <Tooltip title="You must fill in all fields.">
              <div>
                <Button
                  type="submit"
                  fullWidth
                  variant="contained"
                  color="primary"
                  disabled={!isValid}
                  className={classes.submit}
                >
                  Submit
                </Button>
              </div>
            </Tooltip>
          </React.Fragment> :
          // Else just render the button
          <Button
          type="submit"
          fullWidth
          variant="contained"
          color="primary"
          disabled={!isValid}
          className={classes.submit}
          >
            Submit
          </Button>
        }
      </form>
    );
  }
}

const FormFieldsComponent = withStyles(styles)(FormFields);

class SignUpForm extends React.Component {
  constructor(props) {
    super(props);

    this.submitValues = this.submitValues.bind(this);
  }

  signIn(username, password) {
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
      window.location = new URLSearchParams(window.location.search).get('resource') || '/';
    });
  }

  // submit function
  submitValues({ username, email, confirmPassword, password }) {
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
      .then(handleErrors) // Handle errors first
      .then(() => {
        this.props.handleSuccess && this.props.handleSuccess();
        this.props.loginOnSuccess && this.signIn(username, password);
      })
      .catch(error => {
        this.form.setFieldError("username", "Looks like this user is taken. Please try a different username.");
      });
  }

  render() {
    const { classes, selfContained } = this.props;
    const values = { username: "", email: "", confirmPassword: "", password: "" };

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

    // Hooks only work inside functional components
    return (
      <React.Fragment>
        <div className={classes.main}>
          <Formik
            render={props => <FormFieldsComponent {...props} />}
            initialValues={values}
            validationSchema={validationSchema}
            onSubmit={this.submitValues}
            ref={el => (this.form = el)}
          />
        </div>
      </React.Fragment>
    );
  }
}

export default withStyles(styles)(SignUpForm);