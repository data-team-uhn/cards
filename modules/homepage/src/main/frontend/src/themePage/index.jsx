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
import React, { Suspense } from "react";
import ReactDOM from "react-dom";
import Sidebar from "./Sidebar/sidebar"
import sidebarRoutes, { loadRemoteComponents, loadRemoteIcons, contentNodes } from './routes';
import { withStyles } from '@material-ui/core/styles';
import { Redirect, Router, Route, Switch } from "react-router-dom";
import { createBrowserHistory } from "history";
import Navbar from "./Navbars/Navbar";
import IndexStyle from "./indexStyle.jsx";

class Main extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      image: "/libs/lfs/resources/cancer-cells.jpg",
      color: "blue",
      hasImage: true,
      fixedClasses: "dropdown show",
      mobileOpen: false,
      routes: sidebarRoutes
    };
  }

  handleDrawerToggle = () => {
    this.setState({ mobileOpen: !this.state.mobileOpen });
  };

  // Close the mobile menu if the window size changes
  // so that the mobile menu is out of place
  autoCloseMobileMenus = event => {
    if (window.innerWidth >= this.props.theme.breakpoints.values.md) {
      this.setState({ mobileOpen: false });
    }
  }

  // Register/unregister autoCloseMobileMenus to window resizing
  componentWillUnmount() {
    window.removeEventListener("resize", this.autoCloseMobileMenus);
  }

  _loadIcon = (data, icon) => {
    data.icon = loadJS(icon)
  }

  _buildSidebar = (uixData) => {
    var routes = sidebarRoutes.slice();
    for (var id in uixData) {
      var uixDatum = uixData[id];
      routes.push({
        path: uixDatum.path,
        name: uixDatum.name,
        icon: uixDatum.icon,
        component: uixDatum.reactComponent,
        rtlName: "rtl:test",
        layout: "/content.html"
      });
    }
    this.setState({routes: routes});
  };

  componentDidMount() {
    window.addEventListener("resize", this.autoCloseMobileMenus);
    loadRemoteComponents(contentNodes)
    .then(loadRemoteIcons)
    .then(this._buildSidebar)
    .catch(function(err) {
      console.log("Something went wrong: " + err);
    });
  };

  switchRoutes = (routes) => {
    return (<Switch>
      {routes.map((prop, key) => {
        return (
          <Route
            path={prop.layout + prop.path}
            component={prop.component}
            key={key}
          />
        );
      })}
    </Switch>)
  };

  render() {
    const { classes, ...rest } = this.props;

    return (
      <div className={classes.wrapper}>
        <Suspense fallback={<div>Loading...</div>}>
          <Sidebar
            routes={this.state.routes}
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
              <div className={classes.container}>{this.switchRoutes(this.state.routes)}</div>
            </div>
            <Navbar
              routes={ this.state.routes }
              handleDrawerToggle={this.handleDrawerToggle}
              {...rest}
            />
          </div>
        </Suspense>
      </div>
    );
  }
}

Main.propTypes = {
  classes: PropTypes.object.isRequired
};
const MainComponent = (withStyles(IndexStyle, {withTheme: true})(Main));

const hist = createBrowserHistory();
ReactDOM.render(
  <Router history={hist}>
    <Switch>
      <Route path="/content.html/" component={MainComponent} />
      <Redirect from="/" to="/content.html/dashboard.html"/>
      <Redirect from="/content" to="/content.html/dashboard.html" />
    </Switch>
  </Router>,
  document.querySelector('#main-container')
);

export default MainComponent;
