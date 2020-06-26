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

var sidebarRoutes = [
];

const ASSET_PREFIX="asset:";

// Begin a fetch for the asset URL to use
var fetchAssetURL = function(url) {
  if (!url.startsWith(ASSET_PREFIX)) {
    return new Promise(function(resolve, reject) {resolve(url)});
  }

  var asset_id = url.slice(ASSET_PREFIX.length);
  return fetch("/libs/lfs/resources/assets.json")
    .then(response => response.ok ? response.json() : Promise.reject(response))
    .then(json => "/libs/lfs/resources/" + json[asset_id]);
}

var loadRemoteIcon = function(uixDatum) {
  return fetchAssetURL(uixDatum.iconUrl)
    .then(url => fetch(url))
    .then(response => response.ok ? response.text() : Promise.reject(response))
    .then(remoteComponentSrc => {
      var returnVal = window.eval(remoteComponentSrc);
      uixDatum.icon = returnVal.default;
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
  return fetchAssetURL(component['lfs:extensionRenderURL'])
    .then(url => {
      // If the URL is empty, return an empty page
      if (url === "") {
        return ({
          reactComponent: null,
          path: "/" + component["lfs:targetURL"],
          name: component["lfs:extensionName"],
          iconUrl: component["lfs:icon"],
          order: component["lfs:defaultOrder"]
        });
      }

      return fetch(url)
        .then(response => response.ok ? response.text() : Promise.reject(response))
        .then(remoteComponentSrc => {
          var returnVal = window.eval(remoteComponentSrc);
          return({
            reactComponent: returnVal.default,
            path: "/" + component["lfs:targetURL"],
            name: component["lfs:extensionName"],
            iconUrl: component["lfs:icon"],
            order: component["lfs:defaultOrder"]
          })
        })
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
