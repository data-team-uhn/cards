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
// @material-ui/core components
import { withStyles } from "@material-ui/core";
import { Hidden } from "@material-ui/core";
// @material-ui/icons
import ExitToApp from "@material-ui/icons/ExitToApp";
// core components
import { Button } from "MaterialDashboardReact";

import headerLinksStyle from "./headerLinksStyle.jsx";

class HeaderLinks extends React.Component {
  render() {
    const { classes } = this.props;

    // When the screen is larger than "MdUp" size, we alter some menu items
    // so that they show up white in the sidebar (rather than black on the
    // main page)
    const expand = window.innerWidth >= this.props.theme.breakpoints.values.md;

    return (
      <div>
        {/* Log out */}
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
            <p className={classes.linkText}>Log out</p>
          </Hidden>
        </Button>
      </div>
    );
  }
}

export default withStyles(headerLinksStyle, {withTheme: true})(HeaderLinks);
