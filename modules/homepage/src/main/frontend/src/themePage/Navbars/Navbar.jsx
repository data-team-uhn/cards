// Taken from https://www.creative-tim.com/product/material-dashboard-react
import React from "react";
import classNames from "classnames";
import PropTypes from "prop-types";
// @material-ui/core components
<<<<<<< HEAD
import { withStyles } from "@material-ui/core/styles";
import { AppBar, Toolbar, IconButton, Hidden } from "@material-ui/core";
=======
import withStyles from "@material-ui/core/styles/withStyles";
import AppBar from "@material-ui/core/AppBar";
import Toolbar from "@material-ui/core/Toolbar";
import IconButton from "@material-ui/core/IconButton";
import Hidden from "@material-ui/core/Hidden";
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
// @material-ui/icons
import Menu from "@material-ui/icons/Menu";
// core components
import AdminNavbarLinks from "./AdminNavbarLinks.jsx";
<<<<<<< HEAD
=======
import RTLNavbarLinks from "./RTLNavbarLinks.jsx";
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
import Button from "material-dashboard-react/dist/components/CustomButtons/Button.js";

import headerStyle from "./headerStyle.jsx";

function Header({ ...props }) {
<<<<<<< HEAD
  // Create the "brand", i.e. the route taken to get to the current page
  // (Usually displayed at the top left)
=======
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
  function makeBrand() {
    var name;
    props.routes.map((prop, key) => {
      if (prop.layout + prop.path === props.location.pathname) {
<<<<<<< HEAD
        name = prop.name;
=======
        name = props.rtlActive ? prop.rtlName : prop.name;
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
      }
      return null;
    });
    return name;
  }
<<<<<<< HEAD

=======
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
  const { classes, color } = props;
  const appBarClasses = classNames({
    [" " + classes[color]]: color
  });
<<<<<<< HEAD

  return (
    <AppBar className={classes.appBar + appBarClasses}>
      <Toolbar className={classes.container}>
        {/* Here we create navbar brand, based on route name */}
        <div className={classes.flex}>
=======
  return (
    <AppBar className={classes.appBar + appBarClasses}>
      <Toolbar className={classes.container}>
        <div className={classes.flex}>
          {/* Here we create navbar brand, based on route name */}
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
          <Button color="transparent" href="#" className={classes.title}>
            {makeBrand()}
          </Button>
        </div>
<<<<<<< HEAD
        {/* While the screen is wide enough, display the navbar at the topright */}
        <Hidden smDown implementation="css">
          <AdminNavbarLinks />
        </Hidden>
        {/* While the screen is too narrow, display the mini sidebar control */}
=======
        <Hidden smDown implementation="css">
          {props.rtlActive ? <RTLNavbarLinks /> : <AdminNavbarLinks />}
        </Hidden>
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
        <Hidden mdUp implementation="css">
          <IconButton
            color="inherit"
            aria-label="open drawer"
            onClick={props.handleDrawerToggle}
          >
            <Menu />
          </IconButton>
        </Hidden>
      </Toolbar>
    </AppBar>
  );
}

Header.propTypes = {
  classes: PropTypes.object.isRequired,
  color: PropTypes.oneOf(["primary", "info", "success", "warning", "danger"])
};

export default withStyles(headerStyle)(Header);
