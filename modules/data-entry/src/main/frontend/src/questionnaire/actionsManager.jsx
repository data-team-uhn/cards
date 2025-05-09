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

import { loadExtensions } from "../uiextension/extensionManager";

// The list of actions for each item type, as component functions
let actions = {};
// If there is an ongoing request for the list of actions, cache it to only have one request sent
let actionsRequests = {};

let getActions = async function(itemType) {
  if (!actions[itemType]) {
    if (!actionsRequests[itemType]) {
      actionsRequests[itemType] = loadExtensions(itemType + "Actions")
        .then((extensions) => {
          let loadedComponents = [];
          for (let i = 0; i < extensions.length; i++) {
            loadedComponents.push(extensions[i]["cards:extensionRender"]);
          }
          actions[itemType] = loadedComponents;
          return actions[itemType];
        })
        .catch (e => console.error('Failed to resolve actions', e))
        .finally(() => delete actionsRequests[itemType]);
      return actionsRequests[itemType];
    }
    return actionsRequests[itemType];
  }
  return actions[itemType];
};

export default getActions;
