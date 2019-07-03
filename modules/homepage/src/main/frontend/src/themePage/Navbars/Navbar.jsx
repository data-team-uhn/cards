// Taken from https://www.creative-tim.com/product/material-dashboard-react
import React from "react";
import classNames from "classnames";
import PropTypes from "prop-types";
// @material-ui/core components
import { withStyles } from "@material-ui/core/styles";
import { AppBar, Toolbar, IconButton, Hidden } from "@material-ui/core";
// @material-ui/icons
import Menu from "@material-ui/icons/Menu";
// core components
import AdminNavbarLinks from "./AdminNavbarLinks.jsx";
import Button from "../MaterialDashboardReact/CustomButtons/Button.jsx";

import headerStyle from "./headerStyle.jsx";

function Header({ ...props }) {
  // Create the "brand", i.e. the route taken to get to the current page
  // (Usually displayed at the top left)
  function makeBrand() {
    var name;
    props.routes.map((prop, key) => {
      if (prop.layout + prop.path === props.location.pathname) {
        name = prop.name;
      }
      return null;
    });
    return name;
  }

  const { classes, color } = props;
  const appBarClasses = classNames({
    [" " + classes[color]]: color
  });

  return (
    <AppBar className={classes.appBar + appBarClasses}>
      <Toolbar className={classes.container}>
        {/* Here we create navbar brand, based on route name */}
        <div className={classes.flex}>
          <Button color="transparent" href="#" className={classes.title}>
            {makeBrand()}
          </Button>
        </div>
        {/* While the screen is wide enough, display the navbar at the topright */}
        <Hidden smDown implementation="css">
          <AdminNavbarLinks />
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
