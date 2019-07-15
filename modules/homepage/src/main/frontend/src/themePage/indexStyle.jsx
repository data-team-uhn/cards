// Taken from https://www.creative-tim.com/product/material-dashboard-react
import {
  drawerWidth,
  transition,
  container
} from "./themeStyle.jsx";

const appStyle = theme => ({
  wrapper: {
    position: "relative",
    top: "0"
  },
  mainPanel: {
    [theme.breakpoints.up("md")]: {
      width: `calc(100% - ${drawerWidth}px)`
    },
    overflow: "auto",
    position: "relative",
    float: "right",
    ...transition,
    maxHeight: "100%",
    width: "100%",
    overflowScrolling: "touch"
  },
  content: {
    marginTop: "70px",
    padding: "30px 15px"
  },
  container,
  map: {
    marginTop: "70px"
  }
});

export default appStyle;
