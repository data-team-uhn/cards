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

import { loadAsset } from '../assetManager';

var sidebarRoutes = [
];

var loadRemoteIcon = function(uixDatum) {
  return loadAsset(uixDatum.iconUrl)
    .then(asset => {
      uixDatum.icon = asset;
      return(uixDatum);
    });
}

// Find the icon and load them
var loadRemoteIcons = function(uixData) {
  return Promise.all(
    uixData.map(function(uixDatum) {
      return loadRemoteIcon(uixDatum);
    })
  );
};

// Load a react component from a URL
var loadRemoteComponent = function(component) {
  return loadAsset(component['lfs:extensionRenderURL'])
    .then(asset => {
      return ({
        reactComponent: asset,
        path: "/" + component["lfs:targetURL"],
        name: component["lfs:extensionName"],
        iconUrl: component["lfs:icon"],
        order: component["lfs:defaultOrder"],
        hint: component["lfs:hint"]
      });
    });
};

// Load each given component
var loadRemoteComponents = function(components) {
  return Promise.all(
    components.map(function(component) {
      return loadRemoteComponent(component);
    })
  );
};

// Load the content nodes
var loadContentNodes = function(name) {
  return fetch(`/apps/lfs/ExtensionPoints/${name}`)
    .then(response => response.ok ? response.json() : Promise.reject(response));
}


export default sidebarRoutes
export { loadRemoteComponents, loadRemoteIcons, loadContentNodes }
