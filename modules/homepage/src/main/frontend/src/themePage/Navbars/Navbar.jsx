/*!
=========================================================
* Material Dashboard React - v1.7.0
=========================================================
* Product Page: https://www.creative-tim.com/product/material-dashboard-react
* Copyright 2019 Creative Tim (https://www.creative-tim.com)
* Licensed under MIT (https://github.com/creativetimofficial/material-dashboard-react/blob/master/LICENSE.md)
* Coded by Creative Tim
=========================================================
* The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
*/
import React from "react";
import classNames from "classnames";
import PropTypes from "prop-types";
import withStyles from '@material-ui/styles/withStyles';
import { AppBar, Button, Toolbar, IconButton, Hidden } from "@material-ui/core";
// @material-ui/icons
import Menu from "@material-ui/icons/Menu";
// core components
import AdminNavbarLinks from "./AdminNavbarLinks.jsx";

import headerStyle from "../../headerStyle.jsx";

function Header({ ...props }) {
  const { classes, color } = props;
  const appBarClasses = classNames({
    [" " + classes[color]]: color
  });

  return (
    <AppBar className={classes.appBar + appBarClasses}>
      <Toolbar className={classes.container}>
        <div className={classes.flex} />
        {/* While the screen is wide enough, display the navbar at the topright */}
        <Hidden smDown implementation="css">
          <AdminNavbarLinks closeSidebar={props.handleDrawerToggle} color={color} />
        </Hidden>
        {/* While the screen is too narrow, display the mini sidebar control */}
        <Hidden mdUp implementation="css">
          <IconButton
            size="large"
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
  color: PropTypes.oneOf(["primary", "info", "success", "warning", "danger", "blue", "teal", "rose", "red", "orange", "green", "purple"])
};

export default withStyles(headerStyle)(Header);
