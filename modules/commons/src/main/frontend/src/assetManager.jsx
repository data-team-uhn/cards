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

// This is needed to support the `async` keyword,
// since Babel will replace it with calls to the `regeneratorRuntime` method,
// but it will not automatically detect that it needs to put it in the `vendor` bundle unless explicitly used.
import regeneratorRuntime from "regenerator-runtime";

const ASSET_PREFIX="asset:";

let assetsJson = null;
let assetsJsonRequest = null;
let assets = {};
let assetRequests = {};

// Retrieves the JSON containing the mapping between a simple asset name and its actual node name, including a content hash.
// This is an asynchronous function, it will return a Promise that resolves to the actual JSON.
// At the moment, the JSON is only fetched once and reused, but this may change if live code update will be incorporated.
//
// @return a Promise that will resolve to the actual asset mapping JSON
var getAssetsJson = async function() {
  if (!assetsJson) {
    if (!assetsJsonRequest) {
      assetsJsonRequest = fetch("/libs/cards/resources/assets.json")
        .then(response => response.ok ? response.json() : Promise.reject(response))
        .then(json => assetsJson = json)
        .catch (e => console.error('Failed to resolve assets', e))
        .finally(() => assetsJsonRequest = null);
    }
    return assetsJsonRequest;
  }
  return assetsJson;
};

// Get the actual URL where an asset can be fetched from.
// This is an asynchronous function, it will return a Promise that resolves to the actual URL to use.
// If the asset URL starts with `asset:`, then it is interpreted as an asset with a content hash in its actual URL, and will be resolved from the `assets.json` resource.
// Otherwise, the asset URL is returned as-is.
//
// @param {string} assetURL the asset to resolve, may be an actual URL, or a special `asset:`-prefixed string followed by the asset name
// @return a Promise that will resolve to the actual URL to use
var getAssetURL = async function(assetUrl) {
  if (!assetUrl.startsWith(ASSET_PREFIX)) {
    return assetUrl;
  }

  var assetName = assetUrl.slice(ASSET_PREFIX.length);
  if (assetName.includes("?")) {
    assetName = assetName.slice(0, assetName.indexOf("?"));
  }
  return getAssetsJson()
    .then(json => "/libs/cards/resources/" + json[assetName]);
}

// Get the URL parameters from the provided URL or asset URL string.
//
// @param {string} assetURL the URL to extract the parameters from, potentially prefixed with ASSET_PREFIX
// @return a URLSearchParams object containing the parameters from the input
var getURLParameters = (assetUrl) => {
  if (!assetUrl || !assetUrl.includes("?")) {
    return new URLSearchParams();
  }
  return new URLSearchParams(assetUrl.slice(assetUrl.indexOf("?") + 1));
}

// Load a React component from a URL.
// This is an asynchronous function, it will return a Promise that resolves to the actual component.
// The component will be cached, once loaded, further calls to it will return the same component.
//
// @param {string} assetURL the asset to load, may be an actual URL, or a special `asset:`-prefixed string followed by the asset name
// @return a Promise that will resolve to the actual component
var loadAsset = async function(assetURL) {
  if (!assets[assetURL]) {
    if (!assetRequests[assetURL]) {
      let parameters = getURLParameters(assetURL);
      assetRequests[assetURL] = getAssetURL(assetURL)
        .then(url => {
          // If the URL is empty, return an empty page
          if (url === "") {
            return null;
          }

          return fetch(url)
            .then(response => response.ok ? response.text() : Promise.reject(response))
            .then(remoteComponentSrc => {
              var returnVal = window.eval(remoteComponentSrc);
              return parameters.has("component") ? returnVal[parameters.get("component")] : returnVal.default;
            });
          })
        .finally(() => assetRequests[assetURL] = null);
    }
    return assetRequests[assetURL];
  }

  return assets[assetURL];
};

export { getAssetURL, loadAsset };
