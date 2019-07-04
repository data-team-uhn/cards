// Taken from https://www.creative-tim.com/product/material-dashboard-react
import React from "react";
import classNames from "classnames";
import PropTypes from "prop-types";
import { NavLink } from "react-router-dom";
// @material-ui/core components
import { withStyles } from "@material-ui/core";
import { Drawer, Hidden, List, ListItem, ListItemText } from "@material-ui/core";

import AdminNavbarLinks from "../Navbars/AdminNavbarLinks.jsx";
import sidebarStyle from "./sidebarStyle.jsx";

const Sidebar = ({ ...props }) => {
  // verifies if routeName is the one active (in browser input)
  function activeRoute(routeName) {
    return props.location.pathname.indexOf(routeName) > -1 ? true : false;
  }
  const { classes, color, logoImage, image, logoText, routes } = props;

  // Links
  var links = (
    <List className={classes.list}>
      {routes.map((prop, key) => {
        var adminButton = " ";
        var listItemClasses;

        // Generate a list of class names for each item in the sidebar
        // We colour two links in: the currently active
        // link, and the administration link
        if (prop.path === "/admin.html") {
          adminButton = classes.adminButton + " ";
          listItemClasses = classNames({
            [" " + classes[color]]: true
          });
        } else {
          listItemClasses = classNames({
            [" " + classes[color]]: activeRoute(prop.layout + prop.path)
          });
        }
        const whiteFontClasses = classNames({
          [" " + classes.whiteFont]: activeRoute(prop.layout + prop.path)
        });

        // Handle prop.icon being either a class or the name of an icon class
        // NavLink allows us to set styles iff the link's URL matches the current URL
        return (
          <NavLink
            to={prop.layout + prop.path}
            className={adminButton + classes.item}
            activeClassName="active"
            key={key}
          >
            <ListItem button className={classes.itemLink + listItemClasses}>
              <prop.icon
                  className={classNames(classes.itemIcon, whiteFontClasses)}
                />
              <ListItemText
                primary={prop.name}
                className={classNames(classes.itemText, whiteFontClasses)}
                disableTypography={true}
              />
            </ListItem>
          </NavLink>
        );
      })}
    </List>
  );

  // Setup the div containing the logo at the top of the sidebar
  var brand = (
    <div className={classes.logo}>
      <a
        href="/"
        className={classes.logoLink}
      >
        <div className={classes.logoImage}>
          <img src={logoImage} alt="logo" className={classes.img} />
        </div>
        {logoText}
      </a>
    </div>
  );

  // Use different implementations depending on the screen size
  return (
    <div>
      {/* Render ourselves at the top right of the content page */}
      <Hidden mdUp implementation="css">
        <Drawer
          variant="temporary"
          anchor="right"
          open={props.open}
          classes={{paper: classes.drawerPaper}}
          onClose={props.handleDrawerToggle}
          ModalProps={{
            keepMounted: true // Better open performance on mobile.
          }}
        >
          {brand}
          <div className={classes.sidebarWrapper}>
            <AdminNavbarLinks />
            {links}
          </div>
          {image !== undefined ? (
            <div
              className={classes.background}
              style={{ backgroundImage: "url(" + image + ")" }}
            />
          ) : null}
        </Drawer>
      </Hidden>
      {/* Render ourselves at the top of the sidebar */}
      <Hidden smDown implementation="css">
        <Drawer
          anchor="left"
          variant="permanent"
          open
          classes={{paper: classes.drawerPaper}}
        >
          {brand}
          <div className={classes.sidebarWrapper}>{links}</div>
          {image !== undefined ? (
            <div
              className={classes.background}
              style={{ backgroundImage: "url(" + image + ")" }}
            />
          ) : null}
        </Drawer>
      </Hidden>
    </div>
  );
};

Sidebar.propTypes = {
  classes: PropTypes.object.isRequired
};

export default withStyles(sidebarStyle)(Sidebar);
