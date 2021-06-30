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

import { loadExtensions } from './uiextension/extensionManager';

let routes = null;
let routesRequest = null;

// Retrieves the registered "routes", React components that can display a "data view" in the main content area when the corresponding URL is opened.
// A route must have:
// - a path (`cards:targetURL`) that the view is responsible for displaying
// - a React component (`cards:extensionRender`) that does the actual display
// Additionally, a route may have:
// - a name (`cards:extensionName`)
// - a description (`cards:hint`)
// This is an asynchronous function, it will return a Promise that resolves to the actual list of routes.
//
// @return a Promise that will resolve to the actual list of routes
var getRoutes = async function() {
  if (!routes) {
    if (!routesRequest) {
      routesRequest = loadExtensions("Views")
        .then(extensions => routes = extensions)
        .catch(e => console.error('Failed to resolve routes', e))
        .finally(() => routesRequest = null);
    }
    return routesRequest;
  }
  return routes;
};

export { getRoutes };
