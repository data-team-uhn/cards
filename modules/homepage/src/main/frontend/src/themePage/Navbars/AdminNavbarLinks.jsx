// Taken from https://www.creative-tim.com/product/material-dashboard-react
import React from "react";
import classNames from "classnames";
// @material-ui/core components
<<<<<<< HEAD
import { withStyles } from "@material-ui/core/styles";
import { MenuItem, MenuList, Grow, Paper, ClickAwayListener, Hidden, Popper } from "@material-ui/core";
// @material-ui/icons
import { Person, Notifications, Search, ExitToApp } from "@material-ui/icons";
=======
import withStyles from "@material-ui/core/styles/withStyles";
import MenuItem from "@material-ui/core/MenuItem";
import MenuList from "@material-ui/core/MenuList";
import Grow from "@material-ui/core/Grow";
import Paper from "@material-ui/core/Paper";
import ClickAwayListener from "@material-ui/core/ClickAwayListener";
import Hidden from "@material-ui/core/Hidden";
import Poppers from "@material-ui/core/Popper";
// @material-ui/icons
import Person from "@material-ui/icons/Person";
import Notifications from "@material-ui/icons/Notifications";
import Search from "@material-ui/icons/Search";
import ExitToApp from "@material-ui/icons/ExitToApp";
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
// core components
import CustomInput from "material-dashboard-react/dist/components/CustomInput/CustomInput.js";
import Button from "material-dashboard-react/dist/components/CustomButtons/Button.js";

import headerLinksStyle from "./headerLinksStyle.jsx";

class HeaderLinks extends React.Component {
  state = {
    open: false
  };
<<<<<<< HEAD

  // Placeholder function for clicking on a notification
  placeholderDoNothing = () => {
    console.log("test");
  }

  // Obtain notifications, then returns list of <MenuItem>s
  getNotifications = (dropdownClass) => {
    // TODO: obtain notifications dynamically
    const notifications = {
      "New notification 1": this.placeholderDoNothing,
      "New notification 2": this.placeholderDoNothing
    };

    const retVal = [];
    for (var key in notifications) {
      retVal.push(
        <MenuItem
          onClick={notifications[key]}
          className={dropdownClass}
          key={key}
        >
          {key}
        </MenuItem>
      );
    }
    return retVal;
  }

  // Event handler for clicking on the notifications
  toggleNotifications = () => {
    this.setState(state => ({ open: !state.open }));
  };

  // Event handler for clicking away from notifications while it is open
  closeNotifications = event => {
=======
  handleToggle = () => {
    this.setState(state => ({ open: !state.open }));
  };

  handleClose = event => {
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
    if (this.anchorEl.contains(event.target)) {
      return;
    }

    this.setState({ open: false });
  };

  render() {
<<<<<<< HEAD
    const { classes } = this.props;
    const { open } = this.state;
    const notifications = this.getNotifications(classes.dropdownItem);

    // When the screen is larger than "MdUp" size, we alter some menu items
    // so that they show up white in the sidebar (rather than black on the
    // main page)
    const expand = window.innerWidth >= this.props.theme.breakpoints.values.md;

    return (
      <div>
        {/* Searchbar */}
=======
    const { classes, theme } = this.props;
    const { open } = this.state;
    //const theme = useTheme();
    //const shrink = useMediaQuery(theme.breakpoints.up('md'));
    const expand = window.innerWidth > 959;

    return (
      <div>
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
        <div className={classes.searchWrapper}>
          <CustomInput
            formControlProps={{
              className: classes.margin + " " + classes.search
            }}
            inputProps={{
              placeholder: "Search",
              inputProps: {
                "aria-label": "Search"
              }
            }}
          />
          <Button color="white" aria-label="edit" justIcon round>
            <Search />
          </Button>
        </div>
<<<<<<< HEAD

        {/* Notifications */}
=======
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
        <div className={classes.manager}>
          <Button
            buttonRef={node => {
              this.anchorEl = node;
            }}
            color={expand ? "transparent" : "white"}
            justIcon={expand}
            simple={!(expand)}
            aria-owns={open ? "menu-list-grow" : null}
            aria-haspopup="true"
<<<<<<< HEAD
            onClick={this.toggleNotifications}
            className={classes.buttonLink}
          >
            <Notifications className={classes.icons} />
            <span className={classes.notifications}>{notifications.length}</span>
            <Hidden mdUp implementation="css">
              <p onClick={this.handleClick} className={classes.linkText}>
                Notifications
              </p>
            </Hidden>
          </Button>
          <Popper
=======
            onClick={this.handleToggle}
            className={classes.buttonLink}
          >
            <Notifications className={classes.icons} />
            <span className={classes.notifications}>5</span>
            <Hidden mdUp implementation="css">
              <p onClick={this.handleClick} className={classes.linkText}>
                Notification
              </p>
            </Hidden>
          </Button>
          <Poppers
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
            open={open}
            anchorEl={this.anchorEl}
            transition
            disablePortal
            className={
              classNames({ [classes.popperClose]: !open }) +
              " " +
<<<<<<< HEAD
              classes.popperNav
=======
              classes.pooperNav
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
            }
          >
            {({ TransitionProps, placement }) => (
              <Grow
                {...TransitionProps}
                id="menu-list-grow"
                style={{
                  transformOrigin:
                    placement === "bottom" ? "center top" : "center bottom"
                }}
              >
                <Paper>
<<<<<<< HEAD
                  <ClickAwayListener onClickAway={this.closeNotifications}>
                    <MenuList role="menu">
                      {notifications}
=======
                  <ClickAwayListener onClickAway={this.handleClose}>
                    <MenuList role="menu">
                      <MenuItem
                        onClick={this.handleClose}
                        className={classes.dropdownItem}
                      >
                        Mike John responded to your email
                      </MenuItem>
                      <MenuItem
                        onClick={this.handleClose}
                        className={classes.dropdownItem}
                      >
                        You have 5 new tasks
                      </MenuItem>
                      <MenuItem
                        onClick={this.handleClose}
                        className={classes.dropdownItem}
                      >
                        You're now friend with Andrew
                      </MenuItem>
                      <MenuItem
                        onClick={this.handleClose}
                        className={classes.dropdownItem}
                      >
                        Another Notification
                      </MenuItem>
                      <MenuItem
                        onClick={this.handleClose}
                        className={classes.dropdownItem}
                      >
                        Another One
                      </MenuItem>
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
                    </MenuList>
                  </ClickAwayListener>
                </Paper>
              </Grow>
            )}
<<<<<<< HEAD
          </Popper>
        </div>

        {/* Profile */}
=======
          </Poppers>
        </div>
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
        <Button
          color={expand ? "transparent" : "white"}
          justIcon={expand}
          simple={!(expand)}
          aria-label="Person"
          className={classes.buttonLink}
        >
          <Person className={classes.icons} />
          <Hidden mdUp implementation="css">
            <p className={classes.linkText}>Profile</p>
          </Hidden>
        </Button>
<<<<<<< HEAD

        {/* Log out */}
=======
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
        <Button
          color={expand ? "transparent" : "white"}
          justIcon={expand}
          simple={!(expand)}
          aria-label="Log out"
          className={classes.buttonLink}
          href="/system/sling/logout"
          title="Log out"
        >
          <ExitToApp className={classes.icons} />
          <Hidden mdUp implementation="css">
<<<<<<< HEAD
            <p className={classes.linkText}>Log out</p>
=======
            <p className={classes.linkText}>Profile</p>
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
          </Hidden>
        </Button>
      </div>
    );
  }
}

<<<<<<< HEAD
export default withStyles(headerLinksStyle, {withTheme: true})(HeaderLinks);
=======
export default withStyles(headerLinksStyle)(HeaderLinks);
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
