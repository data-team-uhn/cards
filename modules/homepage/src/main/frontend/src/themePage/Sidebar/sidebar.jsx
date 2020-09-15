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
import React, { useState, useEffect } from "react";
import classNames from "classnames";
import PropTypes from "prop-types";
import { NavLink } from "react-router-dom";
// @material-ui/core components
import { withStyles } from "@material-ui/core";
import { loadExtensions } from "../../uiextension/extensionManager";
import { Drawer, Hidden, IconButton, List, ListItem, ListItemText } from "@material-ui/core";

import AdminNavbarLinks from "../Navbars/AdminNavbarLinks.jsx";
import sidebarStyle from "./sidebarStyle.jsx";

const Sidebar = ({ ...props }) => {
  // Verifies if routeName is the one active
  let isRouteActive = function(routeName) {
    return props.location.pathname.indexOf(routeName) > -1 ? true : false;
  }

  // Determine if the given defaultOrder makes the associated link an admin link (i.e. defaultOrder is in the 90s)
  // FIXME: Admin links should ideally be in a separate extension target
  let _isAdministrativeButton = function(order) {
    return Math.floor(order % 100 / 90);
  }
  const { classes, color, logoImage, image, logoText } = props;
  let [entries, setEntries] = useState();
  let [loading, setLoading] = useState(true);

  useEffect(() => {
    loadExtensions("SidebarEntry")
      .then(buildSidebar)
      .catch(err => console.log("Something went wrong: ", err))
      .finally(() => setLoading(false));
  }, []);

  var buildSidebar = (extensions) => {
    let result = extensions.slice()
      .sort((a, b) => a["lfs:defaultOrder"] - b["lfs:defaultOrder"]);
    setEntries(result);
  };

  // Generate NavLinks and ListItems from the given entry
  // activeStyle is true for anything that should look "active" (e.g. the admin link, the current page)
  function generateListItem(entry, key, activeStyle) {
    const listBackground = classNames({
      [" " + classes[color]]: activeStyle
    });
    const listItemFont = classNames({
      [" " + classes.whiteFont]: activeStyle
    });
    const EntryIcon = entry["lfs:icon"];

    return (
      <NavLink
        to={entry["lfs:targetURL"]}
        className={classes.item}
        activeClassName="active"
        key={key}
      >
        <ListItem button className={classes.itemLink + listBackground}>
          <EntryIcon
              className={classNames(classes.itemIcon, listItemFont)}
            />
          <ListItemText
            primary={entry["lfs:extensionName"]}
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
          <ListItemText primary="&nbsp;" className={classNames(classes.itemText, classes.skeletonText)}/>
        </ListItem>
        ))
      : entries.filter(entry => !_isAdministrativeButton(entry["lfs:defaultOrder"]))
          .map((entry, key) => {
            return(generateListItem(entry, key, isRouteActive(entry["lfs:targetURL"])));
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
          <ListItemText primary="&nbsp;" className={classNames(classes.itemText, classes.skeletonText)}/>
        </ListItem>
        ))
      : entries.filter(entry => _isAdministrativeButton(entry["lfs:defaultOrder"]))
          .map((entry, key) => {
            // To make it stand out, the admin link is also active
            const isActive = entry["lfs:targetURL"] === "/content.html/admin" || isRouteActive(entry["lfs:targetURL"]);
            return(generateListItem(entry, key, isActive));
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
            <AdminNavbarLinks closeSidebar={props.handleDrawerToggle}/>
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
