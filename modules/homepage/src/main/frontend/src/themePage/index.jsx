//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//
import PropTypes from 'prop-types';
import React from "react";
import ReactDOM from "react-dom";
import Sidebar from "./Sidebar/sidebar"
import sidebarRoutes from './routes';
import { withStyles } from '@material-ui/core/styles';
import { Redirect, Router, Route, Switch } from "react-router-dom";
import { createBrowserHistory } from "history";
import Navbar from "./Navbars/Navbar";
import IndexStyle from "./indexStyle.jsx";

const switchRoutes = (
  <Switch>
    {sidebarRoutes.map((prop, key) => {
      if (prop.layout === "") {
        return (
          <Route
            path={prop.layout + prop.path}
            component={prop.component}
            key={key}
          />
        );
      }
    })}
  </Switch>
);

class Main extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      image: "/libs/lfs/resources/cancer-cells.jpg",
      color: "blue",
      hasImage: true,
      fixedClasses: "dropdown show",
      mobileOpen: false
    };
  }

  handleDrawerToggle = () => {
    this.setState({ mobileOpen: !this.state.mobileOpen });
  };

  // Close the mobile menu if the window size changes
  // so that the mobile menu is out of place
  autoCloseMobileMenus = event => {
    // TODO: Move this magic number and the one in AdminNavbarLinks elsewhere
    console.log(window.innerWidth);
    if (window.innerWidth > 959) {
      this.setState({ mobileOpen: false });
    }
  }

  // Register autoCloseMobileMenus to itself being mounted
  componentDidMount() {
    window.addEventListener("resize", this.autoCloseMobileMenus);
  };

  // Deregister autoCloseMobileMenus if it is no longer mounted
  componentWillUnmount() {
    window.removeEventListener("resize", this.autoCloseMobileMenus);
  };

  render() {
    const { classes, ...rest } = this.props;

    return (
      <div className={classes.wrapper}>
        <Sidebar
          routes={sidebarRoutes}
          logoText={"LFS Data Core"}
          logoImage={"/libs/lfs/resources/lfs-logo-tmp-cyan.png"}
          image={this.state.image}
          handleDrawerToggle={this.handleDrawerToggle}
          open={this.state.mobileOpen}
          color={ "blue" }
          {...rest}
        />
        <div className={classes.mainPanel} ref="mainPanel">
          <div className={classes.content}>
            <div className={classes.container}>{switchRoutes}</div>
          </div>
          <Navbar
            routes={ sidebarRoutes }
            handleDrawerToggle={this.handleDrawerToggle}
            {...rest}
          />
        </div>
      </div>
    );
  }
}

Main.propTypes = {
  classes: PropTypes.object.isRequired
};
const MainComponent = (withStyles(IndexStyle)(Main));

const hist = createBrowserHistory();
ReactDOM.render(
  <Router history={hist}>
    <Switch>
      <Route path="/" component={MainComponent} />
      <Redirect from="/" to="/content" />
    </Switch>
  </Router>,
  document.querySelector('#main-container')
);

export default MainComponent;
