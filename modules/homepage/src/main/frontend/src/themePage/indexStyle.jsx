// Taken from https://www.creative-tim.com/product/material-dashboard-react
import {
  drawerWidth,
  transition,
  container
} from "./themeStyle.jsx";

const appStyle = theme => ({
  wrapper: {
    position: "relative",
<<<<<<< HEAD
    top: "0"
=======
    top: "0",
    height: "100vh"
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
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
<<<<<<< HEAD
    padding: "30px 15px"
=======
    padding: "30px 15px",
    minHeight: "calc(100vh - 123px)"
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
  },
  container,
  map: {
    marginTop: "70px"
  }
});

export default appStyle;
