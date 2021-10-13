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
import {
  container,
  primaryColor,
  defaultBoxShadow,
  infoColor,
  successColor,
  warningColor,
  dangerColor,
  whiteColor,
  grayColor,
  hexToRgb
} from "./themeStyle.jsx";

const headerStyle = theme => ({
  appBar: {
    backgroundColor: "transparent",
    boxShadow: "none",
    borderBottom: "0",
    marginBottom: "0",
    position: "absolute",
    width: "100%",
    paddingTop: "10px",
    zIndex: "1029",
    color: grayColor[7],
    border: "0",
    borderRadius: "3px",
    padding: "10px 0",
    transition: "all 150ms ease 0s",
    minHeight: "50px",
    display: "block",
    "@media print" : {
      display: "none",
    },
  },
  container: {
    ...container,
    minHeight: "50px"
  },
  flex: {
    flex: 1
  },
  appResponsive: {
    top: "8px"
  },
  primary: {
    backgroundColor: primaryColor[0],
    color: whiteColor,
    ...defaultBoxShadow
  },
  info: {
    backgroundColor: infoColor[0],
    color: whiteColor,
    ...defaultBoxShadow
  },
  success: {
    backgroundColor: successColor[0],
    color: whiteColor,
    ...defaultBoxShadow
  },
  warning: {
    backgroundColor: warningColor[0],
    color: whiteColor,
    ...defaultBoxShadow
  },
  danger: {
    backgroundColor: dangerColor[0],
    color: whiteColor,
    ...defaultBoxShadow
  },
  skeletonHeader: {
    backgroundColor: "rgba(" + hexToRgb(grayColor[6]) + ", 0.3)",
    borderRadius: "15px",
    width: theme.spacing(32)
  },
  search: {
    marginTop: theme.spacing(1)
  },
  dropdownItem: {
    whiteSpace: "normal",
    "& .MuiListItem-root" : {
      margin: theme.spacing(-1, -2),
      width: "auto",
    }
  },
  suggestions: {
    width: theme.spacing(32)
  },
  suggestionContainer: {
    minHeight: "10px"
  },
  invertedColors: {
    color: whiteColor
  },
  aboveBackground: {
    zIndex: "1301"
  },
  searchResultAvatar: {
    color: theme.palette.getContrastText(theme.palette.info.main),
    backgroundColor: theme.palette.info.main,
  },
  queryMatchKey : {
    fontStyle: "italic"
  },
  queryMatchSeparator: {
  },
  queryMatchBefore: {
  },
  queryMatchAfter: {
  },
  highlightedText: {
    fontWeight: "bold",
    backgroundColor: theme.palette.warning.light,
    padding: "1px 2px",
    borderRadius: "4px"
  },
  quickSearchResultsTitle: {
    display: "inline-block"
  },
});

export default headerStyle;
