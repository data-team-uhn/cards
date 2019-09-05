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

// Do not place an ending / in the URL below
export const REST_URL = "https://services.phenotips.org/rest/vocabularies";

// Make a request for a json file, calling callback with parameters (xhr status, json data)
export function MakeRequest(URL, callback) {
    var xhr = window.Sling.getXHR();
    xhr.open('GET', URL, true);
    xhr.responseType = 'json';
    xhr.onload = () => {
        var status = (xhr.status === 200 ? null : xhr.status);
        callback(status, xhr.response);
    }
    xhr.send();
}

// Find children of a node by id, calling callback with parameters (xhr status, json data)
export function MakeChildrenFindingRequest(vocabulary, requestOpt, callback) {
    // Start with the suggest URL
    var URL = `${REST_URL}/${vocabulary}/suggest?`;

    // Add default options
    if (!requestOpt.hasOwnProperty('sort')) {
        requestOpt['sort'] = 'nameSort asc';
    }
    if (!requestOpt.hasOwnProperty('maxResults')) {
        requestOpt['maxResults'] = '10000';
    }
    if (!requestOpt.hasOwnProperty('customFilter')) {
        var escapedId = requestOpt['input'].replace(":", "\\:"); // URI Escape the : from vocabulary term identifiers (e.g.HP:#######) for SolR
        requestOpt['customFilter'] = `is_a:${escapedId}`;
    }

    // Construct URL
    var ret = []
    for (let request in requestOpt) {
        if (request === "") {
            continue;
        }
        ret.push(encodeURIComponent(request) + '=' + encodeURIComponent(requestOpt[request]));
    }
    URL += ret.join('&');

    MakeRequest(URL, callback);
}
