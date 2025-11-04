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
import Menu from "@mui/icons-material/Menu";
import { AppBar, Box, Toolbar, IconButton } from "@mui/material";
import classNames from "classnames";
import PropTypes from "prop-types";
import { withStyles } from 'tss-react/mui';
// @mui/icons-material

// core components
import AdminNavbarLinks from "./AdminNavbarLinks.jsx";
import headerStyle from "../../headerStyle.jsx";
import { checkPropTypes } from "../../propTypes";


function Header({ ...props }) {
  checkPropTypes(Header, props);
  const { classes, color } = props;
  const appBarClasses = classNames({
    [" " + classes[color]]: color
  });

  return (
    <AppBar className={classes.appBar + appBarClasses}>
      <Toolbar className={classes.container}>
        <div className={classes.flex} />
        {/* While the screen is wide enough, display the navbar at the topright */}
        <Box sx={{ display: { md: 'inline-flex', xs: 'none' } }}>
          <AdminNavbarLinks closeSidebar={props.handleDrawerToggle} color={color} />
        </Box>
        {/* While the screen is too narrow, display the mini sidebar control */}
        <Box sx={{ display: { md: 'none', xs: 'inline-flex' } }}>
          <IconButton
            size="large"
            color="inherit"
            aria-label="open drawer"
            onClick={props.handleDrawerToggle}
            className={classes.drawerToggle}
          >
            <Menu />
          </IconButton>
        </Box>
      </Toolbar>
    </AppBar>
  );
}

Header.propTypes = {
  color: PropTypes.oneOf(["primary", "info", "success", "warning", "danger", "blue", "teal", "rose", "bronze", "red", "orange", "green", "purple"])
};

export default withStyles(Header, headerStyle);
