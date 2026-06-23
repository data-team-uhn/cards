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
// This loader is resilient: a problem with one extension (or even with the whole extension point) does not
// prevent the other extensions from loading. Specifically:
//  - If the extension point itself cannot be retrieved (e.g. it is not deployed), an empty list is returned.
//  - Any individual extension whose assets fail to load is logged and omitted from the result, so a single
//    broken extension can no longer take down every other extension of the same point.
// Every extension that IS returned is guaranteed to have all of its `asset:` properties resolved.
//
// @param {string} extensionPoint an extension point, either a repository path like `/apps/cards/ExtensionPoints/SidebarEntry`, or just a name that will be automatically prefixed with `/apps/cards/ExtensionPoints/`.
// @return a Promise that will resolve to an array of extensions, where each extension is the parsed JSON returned by the repository, with asset properties fetched and parsed
var loadExtensions = async function(extensionPoint) {
  let extensions;
  try {
    extensions = await getExtensions(extensionPoint);
  } catch (error) {
    console.error(`Could not retrieve the extension point [${extensionPoint}]; treating it as empty.`, error);
    return [];
  }

  const results = await Promise.allSettled(extensions.map(extension => loadRemoteComponents(extension)));
  return results
    .filter(result => {
      if (result.status === 'rejected') {
        console.error(`Skipping an extension of [${extensionPoint}] that failed to load.`, result.reason);
      }
      return result.status === 'fulfilled';
    })
    .map(result => result.value);
};

// Loads all remote assets of an extension.
// Any direct property of the extension that starts with the `asset:` string will be fetched and `eval`-uated.
// The resulting asset will be stored back in the extension under the key without the (case insensitive) `URL` suffix.
// For example, if there's a `"iconUrl": "asset:/path/to/icon.js"`, then the real `/path/to/icon.js` will be fetched and evaluated, and the result will be stored under the `"icon"` property.
// This is an asynchronous function, it will return a Promise that resolves to the actual extension after all remote assets have been fetched.
//
// If any asset cannot be loaded - either because the fetch fails, or because it resolves to nothing (e.g. an
// unknown asset name) - the returned Promise rejects, so that `loadExtensions` can omit this extension rather
// than hand back a half-loaded one that would later throw when a caller reads the missing component.
//
// @param {object} extension an extension, the parsed JSON returned by the repository
// @return a Promise that will resolve to the extension after all remote components have been fetched
var loadRemoteComponents = async function(extension) {
  // For each property that starts with `asset:`, we fetch it as an asset (all in parallel), and we store the
  // result in the extension under the key without the `URL` suffix.
  await Promise.all(
    Object.entries(extension)
      .filter(([, value]) => typeof value === 'string' && /^asset:/.test(value))
      .map(async ([key, value]) => {
        const asset = await loadAsset(value);
        if (asset == null) {
          throw new Error(`Asset [${value}] for extension [${extension['jcr:path'] || extension['cards:extensionName'] || 'unknown'}] resolved to nothing`);
        }
        extension[key.replace(/url$/i, '')] = asset;
      })
  );
  return extension;
};

export { getExtensions, loadExtensions };
