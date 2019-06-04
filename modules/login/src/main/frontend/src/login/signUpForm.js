import React, { useState } from 'react';
import Avatar from '@material-ui/core/Avatar';
import Button from '@material-ui/core/Button';
import Paper from '@material-ui/core/Paper';
import Typography from '@material-ui/core/Typography';
import withStyles from '@material-ui/core/styles/withStyles';
import Icon from '@material-ui/core/Icon';
import { Formik } from "formik";
import * as Yup from "yup";
import TextField from '@material-ui/core/TextField';
import Tooltip from '@material-ui/core/Tooltip';

import UsernameTakenDialog from './ErrorDialogues';

import styles from "../styling/styles";

class FormFields extends React.Component {
	constructor(props) {
		super(props);

	}

	render() {

		const { classes } = this.props;

		const {
			values: { name, email, password, confirmPassword },
			errors,
			touched,
			handleSubmit,
			handleChange,
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
					id="name"
					name="name"
					helperText={touched.name ? errors.name : ""}
					error={touched.name && Boolean(errors.name)}
					label="Name"
					value={name}
					onChange={change.bind(null, "name")}
					fullWidth
					className={classes.form}
					required
					autoFocus

				/>
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

// export default withStyles(styles)(InputForm);
const FormFieldsComponent = withStyles(styles)(FormFields);

class SignUpForm extends React.Component {
	constructor(props) {
		super(props);
		this.state = {
			usernameError: false
		};

		this.displayError = this.displayError.bind(this);
		this.submitValues = this.submitValues.bind(this);
		this.hideError = this.hideError.bind(this);
		this.updateResource = this.updateResource.bind(this);
	}

	displayError() {
		this.setState({
			usernameError: true
		}, () => { console.log("State has changed"); });
	}

	hideError() {
		this.setState({
			usernameError: false
		}, () => { console.log("Error has been hidden"); });
	}

	//  componentDidUpdate() {
	//   const { errors } = this.props;

	//   this.form.setErrors(errors);
	// }

	// Hacky way to update resource for Sling User so that we
	// are able to render the page
	// Equivalent to: curl -F "resourceType=slingshot/User" http://admin:admin@localhost:8080/content/slingshot/users/slingshot15
	updateResource(username) {
		let url2 = "/content/slingshot/users/" + username;
		let formData2 = new formData();
		formData.append("sling:resource", "slingshot/User");

		fetch(url2, {
			method: 'POST',
			headers: {
				'Accept': 'application/json',
				'Authorization': 'Basic ' + btoa('admin:admin'),
			},
			body: formData2
		})
		.then(function (response) {
			console.log("Node has been changed");
		})
		.catch(function (error) {
			console.log("Node has NOT been changed");
		}); // Not sure why .bind(this) is needed here, setState will not work otherwise.
	}

	// submit function
	submitValues({ name, email, confirmPassword, password }) {
		console.log({ name, email, confirmPassword, password });

		// Use native fetch, sort like the XMLHttpRequest so 
		//  no need for other libraries.
		function handleErrors(response) {
			if (response.status == 500) {
				console.log('Detected 500 response');
			}
			if (!response.ok) {
				throw Error(response.statusText);
			}
			return response;
		}

		// Build formData object.
		// We need to do this because sling does not accept JSON, need
		//  url encoded data
		let formData = new FormData();
		formData.append(":name", name);
		formData.append('pwd', password);
		formData.append('pwdConfirm', confirmPassword);
		formData.append('email', email);

		// Important note about native fetch, it does not reject failed
		// HTTP codes, it'll only fail when network error
		// Therefore, you must handle the error code yourself.
		fetch('/system/userManager/user.create.html', {
			method: 'POST',
			headers: {
				'Accept': 'application/json',
				// 'Content-Type': 'application/x-www-form-urlencoded;charset=utf-8'
			},
			body: formData
		})
			.then(handleErrors) // Handle errors first
			.then(function (response) {
				// updateResource(name);
				// this.updateResource(name);
				alert("Created user!");
			})
			.catch(function (error) {
				// alert("Error creating user. Check console.");
				this.setState({
					usernameError: true
				}, () => { console.log("State has changed"); });
			}.bind(this)); // Not sure why .bind(this) is needed here, setState will not work otherwise.

		let url2 = "/content/slingshot/users/" + name;
		let formData2 = new FormData();
		formData2.append("sling:resourceType", "slingshot/User");

		fetch(url2, {
			method: 'POST',
			headers: {
				'Accept': 'application/json',
				'Authorization': 'Basic ' + btoa('admin:admin'),
			},
			body: formData2
		})
		.then(function (response) {
			console.log("Node has been changed");
		})
		.catch(function (error) {
			console.log("Node has NOT been changed");
		}); 
	}

	render() {
		const { classes } = this.props;
		const values = { name: "", email: "", confirmPassword: "", password: "" };

		const validationSchema = Yup.object({
			name: Yup.string("Enter a name")
				.required("Name is required"),
			email: Yup.string("Enter your email")
				.email("Enter a valid email")
				.required("Email is required"),
			password: Yup.string("")
				.min(8, "Password must contain at least 8 characters")
				.required("Enter your password"),
			confirmPassword: Yup.string("Enter your password")
				.required("Confirm your password")
				.oneOf([Yup.ref("password")], "Password does not match"),
		});

		// Hooks only work inside functional components
		// const formikRef = React.useRef();

		return (
			<React.Fragment>
				{(this.state.usernameError) &&
					<UsernameTakenDialog handleClose={this.hideError} ></UsernameTakenDialog>
				}
				<div className={classes.main}>
					<Paper elevation={1} className={classes.paper}>
						<Typography component="h1" variant="h5">
							Sign Up Form
			</Typography>
						<Avatar className={classes.avatar}>
							<Icon>group_add</Icon>
						</Avatar>
						<Formik
							render={props => <FormFieldsComponent {...props} />}
							initialValues={values}
							validationSchema={validationSchema}
							onSubmit={this.submitValues}
							//  ref={formikRef}
							ref={el => (this.form = el)}
						/>
						<Typography>
							Already have an account?
			</Typography>
						<Button
							fullWidth
							variant="contained"
							color="secondary"
							onClick={this.props.swapForm}
						>
							Sign In
			</Button>
					</Paper>
				</div>
			</React.Fragment>
		);
	}
}

export default withStyles(styles)(SignUpForm);
  // const InputFormComponent = withStyles(styles)(InputForm);