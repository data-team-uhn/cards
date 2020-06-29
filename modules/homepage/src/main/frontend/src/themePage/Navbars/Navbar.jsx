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
// @material-ui/core components
import { withStyles } from "@material-ui/core";
import { AppBar, Button, Toolbar, IconButton, Hidden } from "@material-ui/core";
// @material-ui/icons
import Menu from "@material-ui/icons/Menu";
// core components
import AdminNavbarLinks from "./AdminNavbarLinks.jsx";

import headerStyle from "../../headerStyle.jsx";

function Header({ ...props }) {
  // Create the "brand", i.e. the route taken to get to the current page
  // (Usually displayed at the top left)

  // TODO: extend props.routes and adminroutes ? should also handle routes from admindashboard

  function makeBrand() {
    var matching_routes = props.routes.filter((prop) => {
      return (prop.layout + prop.path === props.location.pathname);
    });
    return matching_routes.length > 0 ? matching_routes[0].name : " ";
  }

  const { classes, color, loading } = props;
  const appBarClasses = classNames({
    [" " + classes[color]]: color
  });

  return (
    <AppBar className={classes.appBar + appBarClasses}>
      <Toolbar className={classes.container}>
        {/* Here we create navbar brand, based on route name */}
        <div className={classes.flex}>
          <Button href="#" className={classes.title}>
            {loading ?
              <span className={classes.skeletonHeader}>&nbsp;</span>
            : makeBrand()}
          </Button>
        </div>
        {/* While the screen is wide enough, display the navbar at the topright */}
        <Hidden smDown implementation="css">
          <AdminNavbarLinks closeSidebar={props.handleDrawerToggle} />
        </Hidden>
        {/* While the screen is too narrow, display the mini sidebar control */}
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
