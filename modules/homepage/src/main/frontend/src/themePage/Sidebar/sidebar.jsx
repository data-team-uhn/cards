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
import { NavLink } from "react-router-dom";
// @material-ui/core components
import { withStyles } from "@material-ui/core";
import { Drawer, Hidden, IconButton, List, ListItem, ListItemText } from "@material-ui/core";

import AdminNavbarLinks from "../Navbars/AdminNavbarLinks.jsx";
import sidebarStyle from "./sidebarStyle.jsx";

const Sidebar = ({ ...props }) => {
  // verifies if routeName is the one active (in browser input)
  function activeRoute(routeName) {
    return props.location.pathname.indexOf(routeName) > -1 ? true : false;
  }
  const { classes, color, loading, logoImage, image, logoText, routes } = props;

  // Generate NavLinks and ListItems from the given route prop
  // activeStyle is true for anything that should look "active" (e.g. the admin link, the current page)
  function generateListItem(prop, key, activeStyle) {
    const listBackground = classNames({
      [" " + classes[color]]: activeStyle
    });
    const listItemFont = classNames({
      [" " + classes.whiteFont]: activeStyle
    });

    return (
      <NavLink
        to={prop.layout + prop.path}
        className={classes.item}
        activeClassName="active"
        key={key}
      >
        <ListItem button className={classes.itemLink + listBackground}>
          <prop.icon
              className={classNames(classes.itemIcon, listItemFont)}
            />
          <ListItemText
            primary={prop.name}
            className={classNames(classes.itemText, listItemFont)}
            disableTypography={true}
          />
        </ListItem>
      </NavLink>
    );
  }
  // Links
  var links = (
    <List className={classes.list}>
      {loading ?
        /* Add some skeleton UI of varying heights */
        [...Array(7)].map((_, index) => (
        <ListItem button className={classNames(classes.itemLink, classes.skeletonItem)} key={index}>
          <div className={classNames(classes.itemIcon, classes.skeletonButton)}></div>
          {/* The primary text here is a random amount of spaces between 1 and 30*/}
          <ListItemText primary={"\u00A0".repeat(Math.random()*29+1)} className={classNames(classes.itemText, classes.skeletonText)}/>
        </ListItem>
        ))
      : routes.filter((prop) => {
        // Only use non-admin links
        return !prop.isAdmin;
      }).map((prop, key) => {
        return(generateListItem(prop, key, activeRoute(prop.layout + prop.path)));
      })}
    </List>
  );

  var adminLinks = (
    <List className={classes.adminSidebar}>
      {loading ?
        /* Add some skeleton UI of varying heights */
        [...Array(3)].map((_, index) => (
        <ListItem button className={classNames(classes.itemLink, classes.skeletonItem, index == 0 && classes[color])} key={index}>
          <div className={classNames(classes.itemIcon, classes.skeletonButton)}></div>
          {/* The primary text here is a random amount of spaces between 1 and 30*/}
          <ListItemText primary={" "} className={classNames(classes.itemText, classes.skeletonText)}/>
        </ListItem>
        ))
      : routes.filter((prop, key) => {
        // Only use admin links
        return prop.isAdmin;
      }).map((prop, key) => {
        // To make it stand out, the admin link is also active
        const isActive = prop.path === "/admin.html" || activeRoute(prop.layout + prop.path);
        return(generateListItem(prop, key, isActive));
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
            {adminLinks}
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
          <div className={classes.sidebarWrapper}>
            {links}
            {adminLinks}
          </div>
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
