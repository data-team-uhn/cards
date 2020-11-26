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

const APIKEY_SERVLET_URL = "/Vocabularies.bioportalApiKey";

const JSON_KEY = "apikey";

export default function fetchBioPortalApiKey(func, isNode, errorHandler) {
  // Parse the response from our FilterServlet
  let parseKey = (keyJson) => {
    console.log("key returned here")
    console.log(keyJson); // todo: send bool also
    if (!keyJson[JSON_KEY]) {
      throw "no API key in APIKEY servlet response";
    }
    func(keyJson[JSON_KEY]);
    isNode(keyJson[isNode]);
  }

  fetch(APIKEY_SERVLET_URL)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(parseKey)
      .catch(errorHandler);
}
