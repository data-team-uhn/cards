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

// Retrieves the JSON that lists all the extensions available for the given extension point.
// This is an asynchronous function, it will return a Promise that resolves to the actual JSON.
//
// @param {string} extensionPoint an extension point, either a repository path like `/apps/cards/ExtensionPoints/SidebarEntry`, or just a name that will be automatically prefixed with `/apps/cards/ExtensionPoints/`.
// @return a Promise that will resolve to the extension point JSON
var getExtensions = async function(extensionPoint) {
  return fetch(/^\//.test(extensionPoint) ? extensionPoint : `/apps/cards/ExtensionPoints/${extensionPoint}`)
    .then(response => response.ok ? response.json() : Promise.reject(response));
}

// Loads all the extensions for the given extension point.
// Other than retrieving and parsing the extension point JSON, it will also fetch remote assets such as code to execute or icons to display.
// This is an asynchronous function, it will return a Promise that resolves to the actual list of extensions.
//
// @param {string} extensionPoint an extension point, either a repository path like `/apps/cards/ExtensionPoints/SidebarEntry`, or just a name that will be automatically prefixed with `/apps/cards/ExtensionPoints/`.
// @return a Promise that will resolve to an array of extensions, where each extension is the parsed JSON returned by the repository, with asset properties fetched and parsed
var loadExtensions = async function(extensionPoint) {
  let extensions = await getExtensions(extensionPoint);
  return Promise.all(extensions.map(extension => loadRemoteComponents(extension)));
};

// Loads all remote assets of an extension.
// Any direct property of the extension that starts with the `asset:` string will be fetched and `eval`-uated.
// The resulting asset will be stored back in the extension under the key without the (case insensitive) `URL` suffix.
// For example, if there's a `"iconUrl": "asset:/path/to/icon.js"`, then the real `/path/to/icon.js` will be fetched and evaluated, and the result will be stored under the `"icon"` property.
// This is an asynchronous function, it will return a Promise that resolves to the actual extension after all remote assets have been fetched.
//
// @param {object} extension an extension, the parsed JSON returned by the repository
// @return a Promise that will resolve to the extension after all remote components have been fetched
var loadRemoteComponents = async function(extension) {
  // For each property that starts with `asset:`,
  // we fetch it as an asset (all in parallel),
  // and we store the result in the extension under the key without the `URL` suffix.
  //
  // After all the components have been loaded, return the extension.
  return Promise.all(
    Object.entries(extension)
      .filter(([key, value]) => /^asset:/.test(value))
      .map(([key, value]) => loadAsset(value)
        .then(asset => extension[key.replace(/url$/i, '')] = asset))
    )
    .then(() => extension);
};

export { getExtensions, loadExtensions };
