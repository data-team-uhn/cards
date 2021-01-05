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
import PropTypes from "prop-types";
import React, { useEffect, useRef, useState } from "react";
// @material-ui/core components
import { Avatar, ClickAwayListener, Grow, Hidden, IconButton, Link, MenuList, MenuItem, Paper, Popper, withStyles } from "@material-ui/core";

import HeaderSearchBar from "./HeaderSearchBar.jsx";
import headerLinksStyle from "./headerLinksStyle.jsx";

function HeaderLinks (props) {
  const { classes, closeSidebar, theme } = props;
  const [ popperOpen, setPopperOpen ] = useState(false);
  const [ username, setUsername ] = useState("");

  const avatarRef = useRef();
  const headerRef = useRef();

  // TODO: Should we make the username accessible to other components (e.g. via a context)?
  useEffect(() => {
    fetch("http://localhost:8080/system/sling/info.sessionInfo.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setUsername(json["userID"])
        console.log(json);
      })
      .catch((error) => console.log(error));
  }, []);

  // Parse the username into initials
  let initials = "?";
  if (username) {
    let usernameSplit = username.split(" ");
    // First name and either the first middle name, or the last name (depending on the number of spaces)
    initials = usernameSplit[0]?.[0] + usernameSplit[1]?.[0]
      // If there's three names or more, include the last name
      + (usernameSplit.length > 2 ? usernameSplit[usernameSplit.length-1][0] : "");
    initials = initials.toUpperCase();
  }

  // When the screen is larger than "MdUp" size, we alter some menu items
  // so that they show up white in the sidebar (rather than black on the
  // main page)
  const expand = window.innerWidth >= theme.breakpoints.values.md;

  return (
    <div ref={headerRef}>
      <HeaderSearchBar
        invertColors={!expand}
        onSelectFinish={expand ? undefined : closeSidebar}
        className={expand ? undefined : classes.buttonLink}
      />
      {/* Avatar + log out link */}
      <IconButton
        aria-label="Log out"
        className={classes.buttonLink + " " + classes.logout + " " + expand || classes.linkText}
        onClick={() => setPopperOpen(true)}
        title="Log out"
        ref={avatarRef}
      >
        <Avatar>{initials}</Avatar>
        <Hidden mdUp implementation="css">
          <p className={classes.linkText}>Log out</p>
        </Hidden>
      </IconButton>
      {/* Suggestions list using Popper */}
      <Popper
        open={popperOpen}
        anchorEl={avatarRef.current}
        className={classes.aboveBackground}
        modifiers={{
          keepTogether: {enabled: true}
        }}
        placement = "bottom-end"
        transition
        keepMounted
        >
        {({ TransitionProps }) => (
          <Grow
            {...TransitionProps}
            style={{transformOrigin: "top"}}
          >
            <Paper square className={classes.suggestionContainer}>
              <ClickAwayListener onClickAway={(event) => {
                // Ignore clickaway events if they're just clicking on any component in this object
                if (!headerRef.current.contains(event.target)) {
                  setPopperOpen(false);
                }}}>
                <MenuList role="menu" className={classes.suggestions}>
                  <Link href={"/system/sling/logout"}>
                    <MenuItem className={classes.dropdownItem}>
                      Log out
                    </MenuItem>
                  </Link>
                </MenuList>
              </ClickAwayListener>
            </Paper>
          </Grow>
        )}
      </Popper>
    </div>
  );
}

HeaderLinks.propTypes = {
  closeSidebar: PropTypes.func
}

export default withStyles(headerLinksStyle, {withTheme: true})(HeaderLinks);
