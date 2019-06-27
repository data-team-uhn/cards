// Taken from https://www.creative-tim.com/product/material-dashboard-react
import React from "react";
import classNames from "classnames";
import PropTypes from "prop-types";
import { NavLink } from "react-router-dom";
// @material-ui/core components
<<<<<<< HEAD
import { withStyles } from "@material-ui/core/styles";
import { Drawer, Hidden, List, ListItem, ListItemText } from "@material-ui/core";

import AdminNavbarLinks from "../Navbars/AdminNavbarLinks.jsx";
=======
import withStyles from "@material-ui/core/styles/withStyles";
import Drawer from "@material-ui/core/Drawer";
import Hidden from "@material-ui/core/Hidden";
import List from "@material-ui/core/List";
import ListItem from "@material-ui/core/ListItem";
import ListItemText from "@material-ui/core/ListItemText";
<<<<<<< HEAD
import Icon from "@material-ui/core/Icon";

import AdminNavbarLinks from "../Navbars/AdminNavbarLinks.jsx";
import RTLNavbarLinks from "../Navbars/RTLNavbarLinks.jsx";
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
=======

import AdminNavbarLinks from "../Navbars/AdminNavbarLinks.jsx";
>>>>>>> ed391da... LFS-34: UI for user management
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
<<<<<<< HEAD
            [" " + classes[color]]: activeRoute(prop.layout + prop.path)
          });
        }
        const whiteFontClasses = classNames({
          [" " + classes.whiteFont]: activeRoute(prop.layout + prop.path)
=======
            [" " + classes[color]]: activeRoute(prop.path)
          });
        }
        const whiteFontClasses = classNames({
          [" " + classes.whiteFont]: activeRoute(prop.path)
>>>>>>> ed391da... LFS-34: UI for user management
        });

        // Handle prop.icon being either a class or the name of an icon class
        // NavLink allows us to set styles iff the link's URL matches the current URL
        return (
          <NavLink
<<<<<<< HEAD
            to={prop.layout + prop.path}
=======
            to={prop.path}
>>>>>>> ed391da... LFS-34: UI for user management
            className={adminButton + classes.item}
            activeClassName="active"
            key={key}
          >
            <ListItem button className={classes.itemLink + listItemClasses}>
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> ed391da... LFS-34: UI for user management
              <prop.icon
                  className={classNames(classes.itemIcon, whiteFontClasses)}
                />
              <ListItemText
                primary={prop.name}
                className={classNames(classes.itemText, whiteFontClasses)}
<<<<<<< HEAD
=======
              {typeof prop.icon === "string" ? (
                <Icon
                  className={classNames(classes.itemIcon, whiteFontClasses, {
                    [classes.itemIconRTL]: props.rtlActive
                  })}
                >
                  {prop.icon}
                </Icon>
              ) : (
                <prop.icon
                  className={classNames(classes.itemIcon, whiteFontClasses, {
                    [classes.itemIconRTL]: props.rtlActive
                  })}
                />
              )}
              <ListItemText
                primary={
                  props.rtlActive ? prop.rtlName : prop.name
                }
                className={classNames(classes.itemText, whiteFontClasses, {
                  [classes.itemTextRTL]: props.rtlActive
                })}
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
=======
>>>>>>> ed391da... LFS-34: UI for user management
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
<<<<<<< HEAD
<<<<<<< HEAD
        href="/"
        className={classes.logoLink}
=======
        href="https://phenotips.org/"
        className={classNames(classes.logoLink, {
          [classes.logoLinkRTL]: props.rtlActive
        })}
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
=======
        href="https://phenotips.org/"
        className={classes.logoLink}
>>>>>>> ed391da... LFS-34: UI for user management
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
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> ed391da... LFS-34: UI for user management
      {/* Render ourselves at the top right of the content page */}
      <Hidden mdUp implementation="css">
        <Drawer
          variant="temporary"
          anchor="right"
          open={props.open}
          classes={{paper: classes.drawerPaper}}
<<<<<<< HEAD
=======
      <Hidden mdUp implementation="css">
        <Drawer
          variant="temporary"
          anchor={props.rtlActive ? "left" : "right"}
          open={props.open}
          classes={{
            paper: classNames(classes.drawerPaper, {
              [classes.drawerPaperRTL]: props.rtlActive
            })
          }}
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
=======
>>>>>>> ed391da... LFS-34: UI for user management
          onClose={props.handleDrawerToggle}
          ModalProps={{
            keepMounted: true // Better open performance on mobile.
          }}
        >
          {brand}
          <div className={classes.sidebarWrapper}>
<<<<<<< HEAD
<<<<<<< HEAD
            <AdminNavbarLinks />
=======
            {props.rtlActive ? <RTLNavbarLinks /> : <AdminNavbarLinks />}
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
=======
            <AdminNavbarLinks />
>>>>>>> ed391da... LFS-34: UI for user management
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
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> ed391da... LFS-34: UI for user management
      {/* Render ourselves at the top of the sidebar */}
      <Hidden smDown implementation="css">
        <Drawer
          anchor="left"
          variant="permanent"
          open
          classes={{paper: classes.drawerPaper}}
<<<<<<< HEAD
=======
      <Hidden smDown implementation="css">
        <Drawer
          anchor={props.rtlActive ? "right" : "left"}
          variant="permanent"
          open
          classes={{
            paper: classNames(classes.drawerPaper, {
              [classes.drawerPaperRTL]: props.rtlActive
            })
          }}
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
=======
>>>>>>> ed391da... LFS-34: UI for user management
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
