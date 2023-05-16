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

// The assets map, from simple asset name to the contenthashed real path
let assetsJson = null;
// If there is an ongoing request for the assets JSON, cache it to only have one request sent
let assetsJsonRequest = null;
// The assets dependencies map, from an asset name to an array of other asset names it depends on
let assetDependenciesJson = null;
// If there is an ongoing request for the asset dependencies JSON, cache it to only have one request sent
let assetDependenciesJsonRequest = null;
// A cache, mapping between asset URLs to modules loaded from the sources
let modules = {};
// A cache, mapping between asset URLs to React components loaded from the sources
let assets = {};
// A cache, mapping between asser URLs to ongoing fetch requests for the sources, to only have one request for each asset source
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

// Retrieves the JSON containing the asset dependencies.
// This is an asynchronous function, it will return a Promise that resolves to the actual JSON.
// At the moment, the JSON is only fetched once and reused, but this may change if live code update will be incorporated.
//
// @return a Promise that will resolve to the actual asset dependencies JSON
var getAssetDependenciesJson = async function() {
  if (!assetDependenciesJson) {
    if (!assetDependenciesJsonRequest) {
      assetDependenciesJsonRequest = fetch("/libs/cards/resources/assetDependencies.json")
        .then(response => response.ok ? response.json() : Promise.reject(response))
        .then(json => assetDependenciesJson = json)
        .catch (e => console.error('Failed to resolve asset dependencies', e))
        .finally(() => assetDependenciesJsonRequest = null);
    }
    return assetDependenciesJsonRequest;
  }
  return assetDependenciesJson;
};

// Get the base name of an asset, by removing the (optional) "asset:" prefix, and the (optional) query parameters.
// If the asset URL starts with `asset:`, this prefix is removed.
// If the asset URL contains query parameters, they are removed.
// Otherwise, the asset URL is returned as-is as the asset name.
//
// @param {string} assetURL the asset to resolve, may be an actual asset name, or a special `asset:`-prefixed string followed by the asset name
// @return a string, the asset name
var getAssetName = function(assetURL) {
  let assetName = assetURL;
  if (assetName.startsWith(ASSET_PREFIX)) {
    assetName = assetName.slice(ASSET_PREFIX.length);
  }
  if (assetName.includes("?")) {
    assetName = assetName.slice(0, assetName.indexOf("?"));
  }
  return assetName;
}

// Get the actual URL where an asset can be fetched from.
// This is an asynchronous function, it will return a Promise that resolves to the actual URL to use.
// If the asset URL starts with `asset:`, then it is interpreted as an asset with a content hash in its actual URL, and will be resolved from the `assets.json` resource.
// Otherwise, the asset URL is returned as-is.
//
// @param {string} assetURL the asset to resolve, may be an actual URL, or a special `asset:`-prefixed string followed by the asset name
// @return a Promise that will resolve to the actual URL to use
var getAssetURL = async function(assetURL) {
  if (!assetURL.startsWith(ASSET_PREFIX)) {
    return assetURL;
  }

  var assetName = getAssetName(assetURL);
  return getAssetsJson()
    .then(json => "/libs/cards/resources/" + json[assetName]);
}

// Get the (optional) dependencies needed by an asset.
// This is an asynchronous function, it will return a Promise that resolves to the actual dependencies.
// Dependencies are returned as a list of asset URLs.
// If there are no dependencies, an empty array is returned.
//
// @param {string} assetURL the asset to check, as a resource name like "cards-dataentry.Subjects.js"
// @return a Promise that will resolve to the actual list of dependencies, or an empty array if there are no dependencies
var getAssetDependencies = async function(assetURL) {
  var assetName = getAssetName(assetURL);
  return getAssetDependenciesJson()
    .then(json => json?.[assetName] || []);
}

// Get the URL parameters from the provided URL or asset URL string.
//
// @param {string} assetURL the URL to extract the parameters from, potentially prefixed with ASSET_PREFIX
// @return a URLSearchParams object containing the parameters from the input, or an empty URLSearchParams if the original URL didn't have any query parameters
var getURLParameters = (assetURL) => {
  if (!assetURL || !assetURL.includes("?")) {
    return new URLSearchParams();
  }
  return new URLSearchParams(assetURL.slice(assetURL.indexOf("?") + 1));
}

// Fetch a module from a URL.
// This is an asynchronous function, it will return a Promise that resolves to the actual module.
// The module will be cached, once loaded, further calls to it will return the same module.
//
// @param {string} assetURL the asset to load, may be an actual URL, or a special `asset:`-prefixed string followed by the asset name
// @return a Promise that will resolve to the actual module
var loadModule = async function(assetURL) {
  let realURL = await getAssetURL(assetURL);
  // If the URL is empty, return
  if (realURL === "") {
    return null;
  }
  return modules[realURL] ??= fetch(realURL)
    .then(response => response.ok ? response.text() : Promise.reject(response))
    .then(remoteComponentSrc => {
      var returnVal = window.eval(remoteComponentSrc);
      if (!returnVal) {
        console.error("Failed to load asset", assetURL);
        return "";
      }
      return returnVal;
    })
};

// Load a React component from a URL.
// This is an asynchronous function, it will return a Promise that resolves to the actual component.
// The component will be cached, once loaded, further calls to it will return the same component.
//
// @param {string} assetURL the asset to load, may be an actual URL, or a special `asset:`-prefixed string followed by the asset name
// @return a Promise that will resolve to the actual component
var loadAsset = async function(assetURL) {
  if (!assets[assetURL]) {
    let dependencies = await getAssetDependencies(assetURL);
    await Promise.all(dependencies.map(dependency => loadAsset(dependency)));
    return loadModule(assetURL)
      .then(module => {
        let parameters = getURLParameters(assetURL);
        return assets[assetURL] = parameters.has("component") ? module[parameters.get("component")] : module.default;
      });
  }

  return assets[assetURL];
};

export { getAssetURL, loadAsset };
