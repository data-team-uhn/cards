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
import { useContext, useEffect, useRef, useState } from "react";

// @mui/material components
import CloseIcon from '@mui/icons-material/Close';
import ExitToAppIcon from '@mui/icons-material/ExitToApp';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import {
  Avatar,
  Box,
  ClickAwayListener,
  Grow,
  IconButton,
  ListItemIcon,
  ListItemText,
  MenuList,
  MenuItem,
  Paper,
  Popper,
  Snackbar,
  Tooltip,
} from "@mui/material";
import classNames from "classnames";
import PropTypes from "prop-types";
import { useLocation } from 'react-router';
import { withStyles } from 'tss-react/mui';

import { QuickSearchIdentifier } from "./QuickSearchIdentifier.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../../login/ReLoginDialog.js";
import { checkPropTypes } from "../../propTypes";
import SearchBar from "../../SearchBar.jsx"; // In the commons module
import { appTheme } from "../../themePalette.jsx";
import ChangeUserPasswordDialog from "../../Userboard/Users/ChangeUserPasswordDialog.jsx";
import sidebarStyles from "../Sidebar/sidebarStyles.jsx";

function HeaderLinks (props) {
  checkPropTypes(HeaderLinks, props);
  const { classes, closeSidebar, color } = props;
  const [ popperOpen, setPopperOpen ] = useState(false);
  const [ passwordDialogOpen, setPasswordDialogOpen ] = useState(false);
  const [ pwdResetSuccessSnackbarOpen, setPwdResetSuccessSnackbarOpen ] = useState(false);
  const [ username, setUsername ] = useState("");
  const [ isRemote, setRemote ] = useState(true);

  const avatarRef = useRef();
  const headerRef = useRef();

  const location = useLocation();
  const globalLoginDisplay = useContext(GlobalLoginContext);

  useEffect(() => {
    if (username?.length > 0) {
      fetchWithReLogin(globalLoginDisplay, `/system/userManager/user/${username}.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => setRemote((json["rep:externalId"] && json["rep:externalId"].length > 0) || (json["path"] && json["path"].startsWith("/home/users/saml/"))))
        .catch((error) => console.log(error));
    }
  }, [username]);

  // TODO: Should we make the username accessible to other components (e.g. via a context)?
  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, "/system/sling/info.sessionInfo.json")
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
  const expand = window.innerWidth >= appTheme.breakpoints.values.md;

  // Helper component to automatically enclose any children in a ListItemIcon if necessary
  let ExpandableIcon = (props) => {
    return expand ?
      <ListItemIcon {...props}>
        {props.children}
      </ListItemIcon>
      : props.children
  }

  const menuItems = <MenuList role="menu">
    {isRemote ? null :
      <MenuItem onClick={() => setPasswordDialogOpen(true)} className={expand ? "" : classes.itemLink}>
        <ExpandableIcon>
          <VpnKeyIcon className={expand ? "" : classNames(classes.itemIcon, classes.whiteFont)}/>
        </ExpandableIcon>
        <ListItemText primary="Change password" className={expand ? "" : classes.whiteFont}/>
      </MenuItem>
    }
    {/* Use an onClick instead of a Link to remove the unremovable underline styling */}
    <MenuItem onClick={() => window.location.href = "/system/sling/logout"} className={expand ? "" : classes.itemLink}>
      <ExpandableIcon>
        <ExitToAppIcon className={expand ? "" : classNames(classes.itemIcon, classes.whiteFont)}/>
      </ExpandableIcon>
      <ListItemText primary="Sign out" className={expand ? "" : classes.whiteFont}/>
    </MenuItem>
  </MenuList>

  return (
    <div ref={headerRef} id="adminnavbar">
      {  // Hide the global search bar in all admin screens
        !location.pathname.startsWith("/content.html/admin") &&
        <SearchBar
          invertColors={!expand}
          onSelectFinish={expand ? undefined : closeSidebar}
          className={classNames(classes.search, { [classes.buttonLink]: !expand })}
          resultConstructor={QuickSearchIdentifier}
          onSelect={() => {}}
          showAllResultsLink={true}
        />
      }
      {/* Avatar + sign out link */}
      {/* hide on screens sm and down */}
      <Tooltip title={username}>
        <Box sx={{ display: { xs: 'none', md: 'inline-flex' } }}>
          <IconButton
            className={classes.buttonLink + " " + classes.logout}
            onClick={() => setPopperOpen((open) => !open)}
            ref={avatarRef}
            size="large"
          >
            <Avatar className={classes[color]}>{initials}</Avatar>
          </IconButton>
        </Box>
      </Tooltip>
      {/* hide on screens md and up */}
      <Box sx={{ display: { md: 'none', xs: 'block' } }}>
        {menuItems}
      </Box>
      <Popper
        open={popperOpen}
        anchorEl={avatarRef.current}
        className={popperOpen ? classes.aboveBackground : ""}
        modifiers={[{
          name: 'preventOverflow',
          enabled: true,
          options: {
            tether: true,
          }
        }]}
        placement = "bottom-end"
        transition
      >
        {({ TransitionProps }) => (
          <Grow
            {...TransitionProps}
            style={{ transformOrigin: "top" }}
          >
            <Paper square>
              <ClickAwayListener onClickAway={(event) => {
                // Ignore clickaway events if they're just clicking on any component in this object
                if (!headerRef.current.contains(event.target)) {
                  setPopperOpen(false);
                }}}>
                {menuItems}
              </ClickAwayListener>
            </Paper>
          </Grow>
        )}
      </Popper>
      <ChangeUserPasswordDialog
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
        slotProps={{
          content: {
            className: classes.successSnackbar,
          },
        }}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
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

export default withStyles(HeaderLinks, sidebarStyles);
