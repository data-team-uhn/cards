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
// Parse out asset URLs
var parseAssetURL = function(url) {
  if (!url.startsWith(ASSET_PREFIX)) {
    return url;
  }

  var asset_id = url.slice(ASSET_PREFIX.length);
  var assets = window.Sling.getContent("/libs/lfs/resources/assets.json", 1, "");
  return "/libs/lfs/resources/" + assets[asset_id];
}

var loadRemoteIcon = function(uixDatum) {
  return new Promise(function(resolve, reject) {
    var request = new XMLHttpRequest();
    var url = parseAssetURL(uixDatum.iconUrl);

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

    request.open('GET', url);
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
    var url = parseAssetURL(component['lfs:extensionRenderURL']);

    // If the URL is empty, return an empty page
    if (url === "") {
      return resolve({
        reactComponent: null,
        path: "/" + component["lfs:targetURL"],
        name: component["lfs:extensionName"],
        iconUrl: component["lfs:icon"]
      });
    }

    request.onload = function() {
      if(request.status >= 200 && request.status < 400) {
        var remoteComponentSrc = request.responseText;
        var returnVal = window.eval(remoteComponentSrc);
        return resolve({
          reactComponent: returnVal.default,
          path: "/" + component["lfs:targetURL"],
          name: component["lfs:extensionName"],
          iconUrl: component["lfs:icon"]
        });
      } else {
        return reject();
      }
    };

    request.open('GET', url);
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

var text = window.Sling.httpGet("/apps/lfs/ExtensionPoints/SidebarEntry").responseText;
const contentNodes = JSON.parse(text);

export default sidebarRoutes
export { loadRemoteComponents, loadRemoteIcons, contentNodes }
