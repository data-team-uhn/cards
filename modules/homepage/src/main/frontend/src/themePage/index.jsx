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
import Assignment from '@material-ui/icons/Assignment';
import Drawer from '@material-ui/core/Drawer';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemIcon from '@material-ui/core/ListItemIcon';
import ListItemText from '@material-ui/core/ListItemText';
import HomeIcon from '@material-ui/icons/Home';
import PropTypes from 'prop-types';
import React from "react";
import ReactDOM from "react-dom";
import Sidebar from "./Sidebar/sidebar"
import sidebarRoutes from './routes';
import { withStyles } from '@material-ui/core/styles';
import { Router, Route, Switch, Redirect } from "react-router-dom";
import { createBrowserHistory } from "history";

const styles = {
  drawerPaper: {
    color: "white",
    backgroundColor: "#141414ff",
    backgroundSize: "cover",
    backgroundPosition: "center center",
  },
  icon: {
    color: "#ffffffff"
  }
};

class Main extends React.Component {
  constructor(props) {
    super(props);
  }

  handleDrawerToggle = () => {
    this.setState({ mobileOpen: !this.state.mobileOpen });
  }

  render() {
    const { classes, ...rest } = this.props;

    return (
      <React.Fragment>
        <Sidebar
        routes={ sidebarRoutes }
        layout="/Sidebar"
        path="/Sidebar"
        icon={ HomeIcon }
        rtlActive={ false }
        name="asdf" // Unsure what this is used for
        open={ true }
        onClose={ this.handleDrawerToggle }
        classes={ classes }
        image="/libs/lfs/resources/cancer-cells.jpg"
        {...rest}
        ></Sidebar>
      </React.Fragment>
    );
  }
}

const MainComponent = (withStyles(styles)(Main));

const hist = createBrowserHistory();
ReactDOM.render(
  <Router history={hist}>
    <Switch>
      <Route path="/" component={MainComponent} />
      {/*<Redirect from="/" to="/view.html" />*/}
    </Switch>
  </Router>,
  document.querySelector('#main-container')
);
