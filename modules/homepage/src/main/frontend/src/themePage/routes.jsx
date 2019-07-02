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
import { AccountBox, Assignment, Dashboard, Pets, Settings, Subtitles } from '@material-ui/icons';

import DashboardPage from "./Dashboard/dashboard.jsx";

var sidebarRoutes = [
    {
      path: "/dashboard.html",
      name: "Dashboard",
      icon: Dashboard,
      component: DashboardPage,
      layout: "/content.html"
    },
    {
      path: "/view.html",
      name: "Patients",
      icon: Assignment,
      component: "",
      layout: "/content.html"
    },
    {
      path: "/modelorganisms.html",
      name: "Model Organisms",
      icon: Pets,
      component: "",
      layout: "/content.html"
    },
    {
      path: "/variants.html",
      name: "Variants",
      icon: Subtitles,
      component: "",
      layout: "/content.html"
    },
    {
      path: "/userpage.html",
      name: "User Profile",
      icon: AccountBox,
      component: "",
      layout: "/content.html"
    },
    {
      path: "/admin.html",
      name: "Administration",
      icon: Settings,
      component: "",
      layout: "/content.html"
    },
];

var loadRemoteIcon = function(uixDatum) {
  return new Promise(function(resolve, reject) {
    var request = new XMLHttpRequest();

    request.onload = function() {
      if(request.status >= 200 && request.status < 400) {
        var remoteComponentSrc = request.responseText;
        var returnVal = window.eval(remoteComponentSrc);
        uixDatum.icon = returnVal.default;
        return resolve(uixDatum);
      } else {
        return reject();
      }
    };

    request.open('GET', uixDatum.iconUrl);
    request.send();
  });
}

// Find the icon and load them
var loadRemoteIcons = function(uixData) {
  return Promise.all(
    _.map(uixData, function(uixDatum) {
      return loadRemoteIcon(uixDatum);
    })
  );
};

// Load a react component from a URL
var loadRemoteComponent = function(component) {
  return new Promise(function(resolve, reject) {
    var request = new XMLHttpRequest();

    request.onload = function() {
      if(request.status >= 200 && request.status < 400) {
        var remoteComponentSrc = request.responseText;
        var returnVal = window.eval(remoteComponentSrc);
        return resolve({
          reactComponent: returnVal.default,
          path: "/" + component["lfs:extensionPointId"],
          name: component["lfs:extensionName"],
          iconUrl: component["lfs:icon"]
        });
      } else {
        return reject();
      }
    };

    request.open('GET', component['lfs:extensionRenderURL']);
    request.send();
  });
};

// Load each given component
var loadRemoteComponents = function(components) {
  return Promise.all(
    _.map(components, function(component) {
      return loadRemoteComponent(component);
    })
  );
};

var text = window.Sling.httpGet("/query?query=select%20*%20from%20[lfs:Extension]").responseText;
const contentNodes = JSON.parse(text);

export default sidebarRoutes
export { loadRemoteComponents, loadRemoteIcons, contentNodes }
