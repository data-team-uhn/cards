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
    successColor,
    whiteColor,
    grayColor,
    hexToRgb
  } from "../themeStyle.jsx";

const thesaurusStyle = theme => ({
  // Info box typography styles
  infoPaper: {
    padding: theme.spacing(2),
  },
  infoCard: {
    maxWidth: "500px",
    fontFamily: "'Roboto', 'Helvetica', 'Arial', sans-serif",
    position: "static",
    zIndex: "4 !important",
  },
  vocabularyAvatar: {
    height: 64,
    minWidth: 64,
    width: "auto",
    padding: theme.spacing(0.5),
    backgroundColor: theme.palette.info.main,
    color: theme.palette.getContrastText(theme.palette.info.main)
  },
  infoSection: {
    padding: theme.spacing(1, 0),
  },
  // The following ensures poppers are placed below the presentation (zIndex 1300)
  // but above everything else
  popperListOnTop: {
    zIndex: "1301 !important",
  },
  popperInfoOnTop: {
    zIndex: "1302 !important",
  },
  popperNav: {
    // Old material-dashboard-react style, overridden because of issues with small screens
  },
  errorSnack: {
    backgroundColor: theme.palette.error.dark,
  },
  searchWrapper: {
    margin: theme.spacing(0),
    position: 'relative',
    display: 'inline-block',
    paddingBottom: theme.spacing(0),
    "& .MuiInputBase-root" : {
      minWidth: "250px",
    },
  },
  search: {
    paddingBottom: "0px",
    margin: "0px"
  },
  nestedSearchInput: {
    marginLeft: theme.spacing(-2.5),
    marginTop: theme.spacing(-1),
    "& .MuiInputBase-root" : {
      minWidth: "218px !important",
    },
    "& + .MuiLinearProgress-root": {
      marginLeft: theme.spacing(-2.5),
    }
  },
  searchInput: {
    marginTop: "6px !important",
  },
  searchLabel: {
    marginTop: theme.spacing(-1.5),
  },
  searchShrink: {
    transform: "translate(0, 12px) scale(0.7)",
  },
  searchButton: {
    cursor: "pointer"
  },
  dropdownItem: {
    whiteSpace: 'normal',
  },
  infoAboveBackdrop: {
    // When the info box is spawned from the browse menu,
    // it should no longer be greyed out
    zIndex: "1352 !important",
  },
  inactiveProgress: {
    visibility: "hidden"
  },
  progressIndicator: {
    marginBottom: theme.spacing(-.5),
    height: theme.spacing(.5)
  },
  infoButton: {
    marginLeft: theme.spacing(0.5),
    marginTop: theme.spacing(-0.25),
  },
  infoDialog: {
    zIndex: "1350 !important",
  },
  noResults: {
    marginBottom: theme.spacing(-1),
  },
  dropdownMessage: {
    opacity: "1 !important",
  },
  dropdownSynonymItem: {
    paddingTop: "0",
  },
  dropdownHasSynonymItem: {
    paddingBottom: "0",
  },
});

export default thesaurusStyle;
