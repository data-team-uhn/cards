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
import React from "react";
// @material-ui/core components
import { Button, Hidden, IconButton, withStyles } from "@material-ui/core";
// @material-ui/icons
import ExitToApp from "@material-ui/icons/ExitToApp";

import HeaderSearchBar from "./HeaderSearchBar.jsx";
import headerLinksStyle from "./headerLinksStyle.jsx";

class HeaderLinks extends React.Component {
  render() {
    const { classes, closeSidebar } = this.props;

    // When the screen is larger than "MdUp" size, we alter some menu items
    // so that they show up white in the sidebar (rather than black on the
    // main page)
    const expand = window.innerWidth >= this.props.theme.breakpoints.values.md;

    let redirectSearch = (event, row) => {
      if (row["@path"]) {
        props.history.push("/content.html" + row["@path"]);
        expand || closeSidebar;
      }
    }

    return (
      <div>
        <HeaderSearchBar
          invertColors={!expand}
          onSelect={redirectSearch}
          className={expand ? undefined : classes.buttonLink}
        />
        {/* Log out */}
        <IconButton
          aria-label="Log out"
          className={classes.buttonLink + " " + classes.logout + " " + expand || classes.linkText}
          href="/system/sling/logout"
          title="Log out"
        >
          <ExitToApp className={classes.icons} />
          <Hidden mdUp implementation="css">
            <p className={classes.linkText}>Log out</p>
          </Hidden>
        </IconButton>
      </div>
    );
  }
}

HeaderLinks.propTypes = {
  closeSidebar: PropTypes.func
}

export default withStyles(headerLinksStyle, {withTheme: true})(HeaderLinks);
