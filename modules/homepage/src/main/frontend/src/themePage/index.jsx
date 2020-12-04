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
import { getRoutes } from '../routes';
import { withStyles } from '@material-ui/core';
import { Redirect, Router, Route, Switch } from "react-router-dom";
import { createBrowserHistory } from "history";
import Navbar from "./Navbars/Navbar";
import Page from "./Page";
import PageStart from "./PageStart/PageStart";
import IndexStyle from "./indexStyle.jsx";

class Main extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      image: "/libs/lfs/resources/background.jpg",
      color: "blue",
      hasImage: true,
      fixedClasses: "dropdown show",
      mobileOpen: false,
      routes: [],
      contentOffset: 0,
      title: document.querySelector('meta[name="title"]').content
    };

    getRoutes().then(routes => this.setState({routes: routes}));
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

  componentDidMount() {
    window.addEventListener("resize", this.autoCloseMobileMenus);
  };

  switchRoutes = (routes) => {
    return (<Switch>
      {routes.map((route, key) => {
        return (
          <Route
            path={route["lfs:targetURL"]}
            exact={Boolean(route["lfs:exactURLMatch"])}
            render={(props) => {
                let ThisComponent = route["lfs:extensionRender"];
                let newProps = {...props, contentOffset: this.state.contentOffset };
                let title = this.state.title + " - ";
                return (
                  <Page title={title} pageDefaultName={route["lfs:extensionName"]}>
                    <ThisComponent {...newProps} />
                  </Page>
                  );
              }
            }
            key={key}
          />
        );
      })}
    </Switch>)
  };

  render() {
    const { classes, ...rest } = this.props;

    return (
      <React.Fragment>
        <PageStart
          setTotalHeight={(th) => {
              if (this.state.contentOffset != th) {
                this.setState({contentOffset: th});
              }
            }
          }
        />
        <div className={classes.wrapper} style={ { position: 'relative', top: this.state.contentOffset + 'px' } }>
          <Suspense fallback={<div>Loading...</div>}>
            <Sidebar
              contentOffset={this.state.contentOffset}
              logoText={this.state.title + " Data Core"}
              logoImage={"/libs/lfs/resources/logo.png"}
              image={this.state.image}
              handleDrawerToggle={this.handleDrawerToggle}
              open={this.state.mobileOpen}
              color={ "blue" }
              {...rest}
            />
            <div className={classes.mainPanel} ref={this.mainPanel} id="main-panel">
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
      </React.Fragment>
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
      <Redirect from="/" to="/content.html/Questionnaires/User"/>
      <Redirect from="/content" to="/content.html/Questionnaires/User" />
    </Switch>
  </Router>,
  document.querySelector('#main-container')
);

export default MainComponent;
