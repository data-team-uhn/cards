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

// Server API layer for the questionnaire editor: the Sling POST/GET calls used by the tree
// context to read a questionnaire and to check in/out, reorder and move its entries. Each
// action takes the GlobalLoginContext display so it can re-prompt for login on a 403.

import { fetchWithReLogin } from "../login/ReLoginDialog.js";

export const jcrActions = {
  checkIn: (globalLoginDisplay, { id }) => {
    let checkinForm = new FormData();
    checkinForm.set(":operation", "checkin");
    return fetchWithReLogin(globalLoginDisplay, `/Questionnaires/${id}`, {
      method: "POST",
      body: checkinForm
    });
  },
  checkOut: (globalLoginDisplay, { id }) => {
    let checkoutForm = new FormData();
    checkoutForm.set(":operation", "checkout");
    return fetchWithReLogin(globalLoginDisplay, `/Questionnaires/${id}`, {
      method: "POST",
      body: checkoutForm
    });
  },

  fetchQuestionnaireData: (globalLoginDisplay, { id }) => {
    // 'links' is an implicit processor (called by default) so we don't use it to format our 'cards:Links' children
    // '.-links' formats as an object field with 'jcr:primaryType' property
    // '.links' formats as an array
    // 'deep' is an explicit processor
    return fetchWithReLogin(globalLoginDisplay, `/Questionnaires/${id}.-links.deep.json`);
  },

  fetchResourceJSON: (globalLoginDisplay, { data }) => {
    return fetchWithReLogin(globalLoginDisplay, `${data["@path"]}.deep.json`)
  },

  // https://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html#order-1
  // :order index
  reorderEntry: (globalLoginDisplay, { reorderSourceNode, newPosition }) => {
    let reorderForm = new FormData();
    const order = newPosition;
    const path = reorderSourceNode.path;
    reorderForm.set(":order", order);
    reorderForm.set(":http-equiv-accept", "application/json");
    return fetchWithReLogin(globalLoginDisplay, path, {
      method: "POST",
      body: reorderForm
    });
  },

  // https://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html#order-1
  // POST /content/oldParentNode/childNode
  // :operation=move
  // :dest=/content/newParentNode/childNode
  // :order=before siblingNodeName || index
  moveEntryNested: (globalLoginDisplay, { reorderSourceNode, newParentNode, newPosition }) => {
    const reorderForm = new FormData();
    // :order is a 0-based index, or the literal 'last' to place last
    const order = newPosition;
    const dest = newParentNode.path.concat('/');
    const path = reorderSourceNode.path;
    reorderForm.set(":operation", "move");
    reorderForm.set(":order", order);
    reorderForm.set(":dest", dest);
    reorderForm.set(":http-equiv-accept", "application/json");
    return fetchWithReLogin(globalLoginDisplay, path, {
      method: "POST",
      body: reorderForm,
    });
  },
}
