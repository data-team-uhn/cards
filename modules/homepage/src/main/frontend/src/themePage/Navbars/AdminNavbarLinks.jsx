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
import { Avatar, ClickAwayListener, Grow, Hidden, IconButton, Link, ListItemIcon, ListItemText, MenuList, MenuItem, Paper, Popper, Snackbar, withStyles } from "@material-ui/core";
import CloseIcon from '@material-ui/icons/Close';
import VpnKeyIcon from '@material-ui/icons/VpnKey';
import ExitToAppIcon from '@material-ui/icons/ExitToApp';

import HeaderSearchBar from "./HeaderSearchBar.jsx";
import headerLinksStyle from "./headerLinksStyle.jsx";
import ChangeUserPasswordDialogue from "../../Userboard/Users/changeuserpassworddialogue.jsx";

function HeaderLinks (props) {
  const { classes, closeSidebar, theme } = props;
  const [ popperOpen, setPopperOpen ] = useState(false);
  const [ passwordDialogOpen, setPasswordDialogOpen ] = useState(false);
  const [ pwdResetSuccessSnackbarOpen, setPwdResetSuccessSnackbarOpen ] = useState(false);
  const [ username, setUsername ] = useState("");

  const avatarRef = useRef();
  const headerRef = useRef();

  // TODO: Should we make the username accessible to other components (e.g. via a context)?
  useEffect(() => {
    fetch("/system/sling/info.sessionInfo.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => setUsername(json["userID"]))
      .catch((error) => console.log(error));
  }, []);

  // Parse the username into initials
  let initials = "?";
  if (username) {
    let usernameSplit = username.split(" ");
    // First name and last name
    initials = usernameSplit[0]?.[0] + (usernameSplit.length > 1 ? usernameSplit[usernameSplit.length-1][0] : "");
    initials = initials.toUpperCase();
  }

  // When the screen is larger than "MdUp" size, we alter some menu items
  // so that they show up white in the sidebar (rather than black on the
  // main page)
  const expand = window.innerWidth >= theme.breakpoints.values.md;

  return (
    <div ref={headerRef} id="adminnavbar">
      <HeaderSearchBar
        invertColors={!expand}
        onSelectFinish={expand ? undefined : closeSidebar}
        className={expand ? undefined : classes.buttonLink}
      />
      {/* Avatar + log out link */}
      <Hidden smDown>
        <IconButton
          aria-label="Log out"
          className={classes.buttonLink + " " + classes.logout + " " + expand || classes.linkText}
          onClick={() => setPopperOpen(true)}
          title="Log out"
          ref={avatarRef}
          >
          <Avatar className={classes.avatar}>{initials}</Avatar>
        </IconButton>
      </Hidden>
      <Hidden mdUp implementation="css">
        <Link href={"/system/sling/logout"}>
          <IconButton
            aria-label="Log out"
            className={classes.buttonLink + " " + classes.logout + " " + expand || classes.linkText}
            title="Log out"
            >
            <p className={classes.linkText}>Log out</p>
          </IconButton>
        </Link>
        <IconButton
          aria-label="Change password"
          className={classes.buttonLink + " " + classes.logout + " " + expand || classes.linkText}
          onClick={() => setPasswordDialogOpen(true)}
          title="Change password"
          >
          <p className={classes.linkText}>Change password</p>
        </IconButton>
      </Hidden>
      <Popper
        open={popperOpen}
        anchorEl={avatarRef.current}
        className={classes.aboveBackground}
        modifiers={{
          keepTogether: {enabled: true}
        }}
        placement = "bottom-end"
        transition
        >
        {({ TransitionProps }) => (
          <Grow
            {...TransitionProps}
            style={{transformOrigin: "top"}}
          >
            <Paper square>
              <ClickAwayListener onClickAway={(event) => {
                // Ignore clickaway events if they're just clicking on any component in this object
                if (!headerRef.current.contains(event.target)) {
                  setPopperOpen(false);
                }}}>
                <MenuList role="menu">
                  <MenuItem onClick={() => setPasswordDialogOpen(true)}>
                    <ListItemIcon>
                      <VpnKeyIcon />
                    </ListItemIcon>
                    <ListItemText primary="Change password" />
                  </MenuItem>
                  <Link href={"/system/sling/logout"} color="inherit">
                    <MenuItem>
                      <ListItemIcon>
                        <ExitToAppIcon />
                      </ListItemIcon>
                      <ListItemText primary="Log out" />
                    </MenuItem>
                  </Link>
                </MenuList>
              </ClickAwayListener>
            </Paper>
          </Grow>
        )}
      </Popper>
      <ChangeUserPasswordDialogue
        handleClose={(success) => {
          setPwdResetSuccessSnackbarOpen(success);
          setPasswordDialogOpen(false);
        }}
        isOpen={passwordDialogOpen}
        name={username}
        requireOldPassword
        />
      <Snackbar
        open={pwdResetSuccessSnackbarOpen}
        autoHideDuration={6000}
        onClose={() => setPwdResetSuccessSnackbarOpen(false)}
        message="Password successfully changed"
        action={
          <IconButton size="small" aria-label="close" color="inherit" onClick={() => setPwdResetSuccessSnackbarOpen(false)}>
            <CloseIcon fontSize="small" />
          </IconButton>
        }
        />
    </div>
  );
}

HeaderLinks.propTypes = {
  closeSidebar: PropTypes.func
}

export default withStyles(headerLinksStyle, {withTheme: true})(HeaderLinks);
