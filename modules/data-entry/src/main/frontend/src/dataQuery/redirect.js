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

// Sanitize an input to make it safer for node traversal by removing anything outside normal charsets
// Adapted from https://stackoverflow.com/a/205967/2148998
function _sanitize(url) {
  return url.replace(/[^-A-Za-z0-9+&@#/%?=~_|!:,;\(\)]/, "");
}

// Create an input value for a form
function _create_input(key, value) {
  var field = document.createElement("input");

  field.setAttribute("type", "hidden");
  field.setAttribute("name", key);
  field.setAttribute("value", value);
  return(field);
}

// Generate a form and submit it
// Using javascript to prevent certain fields (jcr:primaryType) from being messed with
// This may not be sufficient since an end-user could create their own forms using e.g. Chrome's console
export function redirect() {
  var form = document.createElement("form");
  var nodeName = document.submitForm["name"].value;
  nodeName = _sanitize(nodeName);

  form.setAttribute("method", "POST");
  form.setAttribute("action", "/db/" + nodeName);
  form.setAttribute("target", "hidden-form");

  // Construct the form and invisibly push it
  form.append(_create_input("name", nodeName));
  form.append(_create_input("value", document.submitForm["value"].value));
  form.append(_create_input("jcr:primaryType", "lfs:dataEntry"));
  document.body.appendChild(form);
  form.submit();

  // Refresh the page after the form has been submit
  // (uses an arbitrary timeout, would be better if I used AJAX to capture form submission completion)
  setTimeout(
    function(){location.reload(true);},
    50);

  return false;
}