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
import withStyles from '@mui/styles/withStyles';
import { loadExtensions } from "../../uiextension/extensionManager";
import { Drawer, List, ListItem, ListItemText } from "@mui/material";

import AdminNavbarLinks from "../Navbars/AdminNavbarLinks.jsx";
import sidebarStyle from "./sidebarStyle.jsx";
import AppInfo from "./AppInfo.jsx";

const Sidebar = ({ ...props }) => {
  // Verifies if routeName is the one active
  let isRouteActive = function(routeName) {
    return props.location.pathname.indexOf(routeName) > -1;
  }

  // Determine if the given defaultOrder makes the associated link an admin link (i.e. defaultOrder is in the 90s)
  // FIXME: Admin links should ideally be in a separate extension target
  let _isAdministrativeButton = function(order) {
    return Math.floor(order % 100 / 90);
  }
  const { classes, color, contentOffset, logoImage, image } = props;
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
      .sort((a, b) => a["cards:defaultOrder"] - b["cards:defaultOrder"]);
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
    const EntryIcon = entry["cards:icon"];

    return (
      <NavLink
        to={entry["cards:targetURL"]}
        className={classes.item}
        activeClassName="active"
        key={key}
      >
        <ListItem button className={classes.itemLink + listBackground}>
          <EntryIcon
              className={classNames(classes.itemIcon, listItemFont)}
            />
          <ListItemText
            primary={entry["cards:extensionName"]}
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
        [...Array(5)].map((_, index) => (
        <ListItem button className={classNames(classes.itemLink, classes.skeletonItem)} key={index}>
          <div className={classNames(classes.itemIcon, classes.skeletonButton)}></div>
          {/* The primary text here is a random amount of spaces between 1 and 30*/}
          <ListItemText primary="&nbsp;" className={classNames(classes.itemText, classes.skeletonText)}/>
        </ListItem>
        ))
      : entries.filter(entry => !_isAdministrativeButton(entry["cards:defaultOrder"]))
          .map((entry, key) => {
            return(generateListItem(entry, key, isRouteActive(entry["cards:targetURL"])));
          })}
    </List>
  );

  var adminLinks = (
    <List className={classes.adminSidebar}>
      {loading ? <></>
      : entries.filter(entry => _isAdministrativeButton(entry["cards:defaultOrder"]))
          .map((entry, key) => {
            const isActive = isRouteActive(entry["cards:targetURL"]);
            return(generateListItem(entry, key, isActive));
          })}
      <div className={classes.appInfo}>
        <AppInfo showTeamInfo />
      </div>
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
      </a>
    </div>
  );

  // Use different implementations depending on the screen size
  return (
    <div>
      {/* Render ourselves at the top right of the content page */}
      <Drawer
          sx={{ display: { md: 'none', xs: 'block' } }}
          variant="temporary"
          anchor="right"
          open={props.open}
          classes={{paper: classes.drawerPaper}}
          onClose={props.handleDrawerToggle}
          ModalProps={{
            keepMounted: true // Better open performance on mobile.
          }}
          PaperProps={ { style: { top: contentOffset + 'px', height: 'calc(100% - ' + contentOffset + 'px)' } } }
        >
          {brand}
          <div className={classes.sidebarWrapper} style={ { height: 'calc(100vh - ' + (75 + contentOffset) + 'px)' } }>
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
      {/* Render ourselves at the top of the sidebar */}
      <Drawer
          sx={{ display: { xs: 'none', md: 'block' } }}
          anchor="left"
          variant="permanent"
          open
          classes={{paper: classes.drawerPaper}}
          PaperProps={ { style: { top: contentOffset + 'px', height: 'calc(100vh - ' + contentOffset + 'px)' } } }
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
    </div>
  );
};

Sidebar.propTypes = {
  classes: PropTypes.object.isRequired
};

export default withStyles(sidebarStyle)(Sidebar);
