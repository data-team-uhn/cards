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

import { blackColor, grayColor } from "../themeStyle.jsx"

const adminStyle = theme => ({
  listButton: {
    color: grayColor[3]
  },
  listItem: {
    textDecoration: "none",
  },
  listText: {
    "&:hover,&:focus,&:visited,&": {
      color: blackColor
    }
  }
})

export default adminStyle;