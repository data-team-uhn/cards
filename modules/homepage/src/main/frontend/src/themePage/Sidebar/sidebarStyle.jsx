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
    drawerWidth,
    transition,
    boxShadow,
    primaryColor,
    primaryBoxShadow,
    infoColor,
    tealColor,
    roseColor,
    successColor,
    warningColor,
    dangerColor,
    whiteColor,
    grayColor,
    blackColor,
    hexToRgb
  } from "../../themeStyle.jsx";

  const sidebarStyle = theme => ({
    drawerPaper: {
      border: "none",
      position: "fixed",
      bottom: "0",
      left: "0",
      zIndex: "1030",
      ...boxShadow,
      width: drawerWidth,
      // At medium size and up, use the full sidebar
      [theme.breakpoints.up("md")]: {
        width: drawerWidth,
        position: "fixed"
      },
      // At small size and lower, switch to being on the opposite side of the screen
      [theme.breakpoints.down('md')]: {
        width: drawerWidth,
        ...boxShadow,
        position: "fixed",
        display: "block",
        right: "0",
        left: "auto",
        zIndex: "1032",
        visibility: "visible",
        overflowY: "visible",
        borderTop: "none",
        textAlign: "left",
        paddingRight: "0px",
        paddingLeft: "0",
        transform: `translate3d(${drawerWidth}px, 0, 0)`,
        ...transition
      }
    },
    logo: {
      position: "relative",
      padding: "15px 15px",
      zIndex: "4",
      "&:after": {
        content: '""',
        position: "absolute",
        bottom: "0",

        height: "1px",
        right: "15px",
        width: "calc(100% - 30px)",
        backgroundColor: "rgba(" + hexToRgb(grayColor[6]) + ", 0.3)"
      }
    },
    logoLink: {
      textTransform: "uppercase",
      padding: "5px 0",
      display: "block",
      fontSize: "18px",
      fontWeight: "400",
      lineHeight: "30px",
      textDecoration: "none",
      backgroundColor: "transparent",
      "&,&:hover": {
        color: whiteColor
      }
    },
    logoImage: {
      width: "100%",
      display: "inline-block",
    },
    img: {
      maxWidth: "70%",
      maxHeight: "80px",
      top: "19px",
      display: "block",
      verticalAlign: "middle",
      marginLeft: "auto",
      marginRight: "auto",
      border: "0"
    },
    background: {
      position: "absolute",
      zIndex: "1",
      height: "100%",
      width: "100%",
      display: "block",
      top: "0",
      left: "0",
      backgroundSize: "cover",
      backgroundPosition: "center center",
      "&:after": {
        position: "absolute",
        zIndex: "3",
        width: "100%",
        height: "100%",
        content: '""',
        display: "block",
        background: blackColor,
        opacity: ".8"
      }
    },
    list: {
      marginTop: "20px",
      paddingLeft: "0",
      paddingTop: "0",
      paddingBottom: "0",
      marginBottom: "0",
      listStyle: "none",
      position: "unset"
    },
    item: {
      position: "relative",
      display: "block",
      textDecoration: "none",
      "&:hover,&:focus,&:visited,&": {
        color: whiteColor
      }
    },
    itemLink: {
      width: "auto",
      transition: "all 300ms linear",
      margin: "10px 15px 0",
      borderRadius: "3px",
      position: "relative",
      display: "block",
      padding: "10px 15px",
      backgroundColor: "transparent"
    },
    itemIcon: {
      width: "24px",
      height: "30px",
      fontSize: "24px",
      lineHeight: "30px",
      float: "left",
      marginRight: "15px",
      textAlign: "center",
      verticalAlign: "middle",
      color: "rgba(" + hexToRgb(whiteColor) + ", 0.8)"
    },
    itemText: {
      margin: "0",
      lineHeight: "30px",
      fontSize: "14px",
      fontWeight: "400",
      color: whiteColor
    },
    whiteFont: {
      color: whiteColor
    },
    appInfo: {
      textAlign: "center",
      color: whiteColor,
      margin: theme.spacing(2, 2, 0),
      padding: theme.spacing(2, 0, 0),
      borderTop: "1px solid rgba(" + hexToRgb(grayColor[6]) + ", 0.3)",
      "& a" : {
        marginLeft: theme.spacing(1),
      },
    },
    purple: {
      backgroundColor: primaryColor[0],
      ...primaryBoxShadow,
      "&:hover": {
        backgroundColor: primaryColor[0],
        ...primaryBoxShadow
      }
    },
    blue: {
      backgroundColor: infoColor[0],
      boxShadow:
        "0 12px 20px -10px rgba(" +
        hexToRgb(infoColor[0]) +
        ",.28), 0 4px 20px 0 rgba(" +
        hexToRgb(blackColor) +
        ",.12), 0 7px 8px -5px rgba(" +
        hexToRgb(infoColor[0]) +
        ",.2)",
      "&:hover": {
        backgroundColor: infoColor[0],
        boxShadow:
          "0 12px 20px -10px rgba(" +
          hexToRgb(infoColor[0]) +
          ",.28), 0 4px 20px 0 rgba(" +
          hexToRgb(blackColor) +
          ",.12), 0 7px 8px -5px rgba(" +
          hexToRgb(infoColor[0]) +
          ",.2)"
      }
    },
    teal: {
      backgroundColor: tealColor[0],
      boxShadow:
        "0 12px 20px -10px rgba(" +
        hexToRgb(tealColor[0]) +
        ",.28), 0 4px 20px 0 rgba(" +
        hexToRgb(blackColor) +
        ",.12), 0 7px 8px -5px rgba(" +
        hexToRgb(tealColor[0]) +
        ",.2)",
      "&:hover": {
        backgroundColor: tealColor[0],
        boxShadow:
          "0 12px 20px -10px rgba(" +
          hexToRgb(tealColor[0]) +
          ",.28), 0 4px 20px 0 rgba(" +
          hexToRgb(blackColor) +
          ",.12), 0 7px 8px -5px rgba(" +
          hexToRgb(tealColor[0]) +
          ",.2)"
      }
    },
    green: {
      backgroundColor: successColor[0],
      boxShadow:
        "0 12px 20px -10px rgba(" +
        hexToRgb(successColor[0]) +
        ",.28), 0 4px 20px 0 rgba(" +
        hexToRgb(blackColor) +
        ",.12), 0 7px 8px -5px rgba(" +
        hexToRgb(successColor[0]) +
        ",.2)",
      "&:hover": {
        backgroundColor: successColor[0],
        boxShadow:
          "0 12px 20px -10px rgba(" +
          hexToRgb(successColor[0]) +
          ",.28), 0 4px 20px 0 rgba(" +
          hexToRgb(blackColor) +
          ",.12), 0 7px 8px -5px rgba(" +
          hexToRgb(successColor[0]) +
          ",.2)"
      }
    },
    orange: {
      backgroundColor: warningColor[0],
      boxShadow:
        "0 12px 20px -10px rgba(" +
        hexToRgb(warningColor[0]) +
        ",.28), 0 4px 20px 0 rgba(" +
        hexToRgb(blackColor) +
        ",.12), 0 7px 8px -5px rgba(" +
        hexToRgb(warningColor[0]) +
        ",.2)",
      "&:hover": {
        backgroundColor: warningColor[0],
        boxShadow:
          "0 12px 20px -10px rgba(" +
          hexToRgb(warningColor[0]) +
          ",.28), 0 4px 20px 0 rgba(" +
          hexToRgb(blackColor) +
          ",.12), 0 7px 8px -5px rgba(" +
          hexToRgb(warningColor[0]) +
          ",.2)"
      }
    },
    red: {
      backgroundColor: dangerColor[0],
      boxShadow:
        "0 12px 20px -10px rgba(" +
        hexToRgb(dangerColor[0]) +
        ",.28), 0 4px 20px 0 rgba(" +
        hexToRgb(blackColor) +
        ",.12), 0 7px 8px -5px rgba(" +
        hexToRgb(dangerColor[0]) +
        ",.2)",
      "&:hover": {
        backgroundColor: dangerColor[0],
        boxShadow:
          "0 12px 20px -10px rgba(" +
          hexToRgb(dangerColor[0]) +
          ",.28), 0 4px 20px 0 rgba(" +
          hexToRgb(blackColor) +
          ",.12), 0 7px 8px -5px rgba(" +
          hexToRgb(dangerColor[0]) +
          ",.2)"
      }
    },
    rose: {
      backgroundColor: roseColor[2],
      boxShadow:
        "0 12px 20px -10px rgba(" +
        hexToRgb(roseColor[2]) +
        ",.28), 0 4px 20px 0 rgba(" +
        hexToRgb(blackColor) +
        ",.12), 0 7px 8px -5px rgba(" +
        hexToRgb(roseColor[2]) +
        ",.2)",
      "&:hover": {
        backgroundColor: roseColor[2],
        boxShadow:
          "0 12px 20px -10px rgba(" +
          hexToRgb(roseColor[2]) +
          ",.28), 0 4px 20px 0 rgba(" +
          hexToRgb(blackColor) +
          ",.12), 0 7px 8px -5px rgba(" +
          hexToRgb(roseColor[2]) +
          ",.2)"
      }
    },
    sidebarWrapper: {
      position: "relative",
      height: "calc(100vh - 75px)",
      overflow: "auto",
      width: "260px",
      zIndex: "4",
      overflowScrolling: "touch"
    },
    adminButton: {
    },
    adminSidebar: {
      [theme.breakpoints.up("md")]: {
        position: "absolute",
        width: "100%",
        bottom: "13px"
      }
    },
    skeletonText: {
      backgroundColor: "rgba(" + hexToRgb(grayColor[6]) + ", 0.3)",
      borderRadius: "15px",
      width: theme.spacing(15),
      display: "inline-block"
    },
    skeletonButton: {
      backgroundColor: "rgba(" + hexToRgb(grayColor[6]) + ", 0.3)",
      borderRadius: "15px",
      width: "24px",
      height: "24px"
    },
    skeletonItem: {
      height: "50px"
    },
    logout: {
      paddingRight: theme.spacing(2)
    },
    aboveBackground: {
      zIndex: "1200"
    },
    successSnackbar: {
      backgroundColor: theme.palette.success.main
    },
    buttonLink: {
      [theme.breakpoints.down('md')]: {
        display: "flex",
        margin: "10px 15px 0",
        width: "-webkit-fill-available",
        color: whiteColor,
        "& svg": {
          width: "24px",
          height: "30px",
          marginTop: "0px",
          marginBottom: "0px"
        },
        "& .fab,& .fas,& .far,& .fal,& .material-icons": {
          fontSize: "24px",
          lineHeight: "30px",
          width: "24px",
          height: "30px",
          marginRight: "15px",
          marginLeft: "-15px"
        },
        "& > span": {
          justifyContent: "flex-start",
          width: "100%"
        }
      }
    },
  });

  export default sidebarStyle;
