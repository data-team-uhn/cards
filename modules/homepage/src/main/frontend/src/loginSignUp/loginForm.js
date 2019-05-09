import React from 'react';
import PropTypes from 'prop-types';
import Avatar from '@material-ui/core/Avatar';
import Button from '@material-ui/core/Button';
import CssBaseline from '@material-ui/core/CssBaseline';
import FormControl from '@material-ui/core/FormControl';
import FormControlLabel from '@material-ui/core/FormControlLabel';
import Checkbox from '@material-ui/core/Checkbox';
import Input from '@material-ui/core/Input';
import InputLabel from '@material-ui/core/InputLabel';
import Paper from '@material-ui/core/Paper';
import Typography from '@material-ui/core/Typography';
import withStyles from '@material-ui/core/styles/withStyles';
import InputAdornment from '@material-ui/core/InputAdornment';
import IconButton from '@material-ui/core/IconButton';
import Tooltip from '@material-ui/core/Tooltip';
import Icon from '@material-ui/core/Icon';

import styles from "../styling/styles";

class SignIn extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      passwordIsMasked: false,
    };
  }

  togglePasswordMask = () => {
    this.setState(prevState => ({
      passwordIsMasked: !prevState.passwordIsMasked,
    }));
  };

  render () {
    const { classes } = this.props;
    const { passwordIsMasked } = this.state;
  
    return (
      <main className={classes.main}>
        <CssBaseline />
        <Paper className={classes.paper}>
          <Avatar className={classes.avatar}>
            {/*<LockOutlinedIcon />*/}
            <Icon>lock</Icon>
          </Avatar>
          <Typography component="h1" variant="h5">
            Sign in
          </Typography>
          <form className={classes.form} method="POST" action={loginValidationPOSTPath}>
            <input type="hidden" name="resource" value={loginRedirectPath} />
            <FormControl margin="normal" required fullWidth>
              <InputLabel htmlFor="j_username">Username</InputLabel>
              <Input id="j_username" name="j_username" autoComplete="email" autoFocus />
            </FormControl>
            <FormControl margin="normal" required fullWidth>
              <InputLabel htmlFor="j_password">Password</InputLabel>
              <Input name="j_password" type={this.state.passwordIsMasked ? 'text' : 'password'} id="j_password" autoComplete="current-password"
                endAdornment={
                <InputAdornment position="end">
                  <Tooltip title={this.state.passwordIsMasked ? "Mask Password" : "Show Password"}>
                    <IconButton
                      aria-label="Toggle password visibility"
                      onClick={this.togglePasswordMask}
                    >
                      {this.state.passwordIsMasked ? <Icon>visibility</Icon> : <Icon >visibility_off</Icon>}
                    </IconButton>
                  </Tooltip>
                </InputAdornment>
              }
             />
            </FormControl>
            <FormControlLabel
              control={<Checkbox value="remember" color="primary" />}
              label="Remember me"
            />
            <Button
              type="submit"
              fullWidth
              variant="contained"
              color="primary"
              className={classes.submit}
            >
              Sign in
            </Button>
          </form>
          <Typography>
            Don't have an account?
          </Typography>
          <Button
            fullWidth
            variant="contained"
            color="secondary"
            onClick={this.props.swapForm}
          >
            Register
          </Button>
        </Paper>
      </main>
    );
  }
}

SignIn.propTypes = {
  classes: PropTypes.object.isRequired,
};

export default withStyles(styles)(SignIn);