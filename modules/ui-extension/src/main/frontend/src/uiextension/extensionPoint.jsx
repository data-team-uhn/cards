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
import React, { useState } from "react";
import PropTypes from "prop-types";

const UIXP_FINDER_URL = "/uixp";

// Component that allows the user to insert an extension from the given URL.
//
// Required props:
//  path: Extension Point ID (e.g. cards/coreUI/sidebar/entry).
// Optional props:
//  callback: function to callback with the json data if the extension is a json object
//
// Sample usage:
// <ExtensionPoint
//    path="/testRig.js"
//    />
function ExtensionPoint(props) {
  const { path, callback } = props;
  const [ renderedResponse, setRenderedResponse ] = useState(null);
  const [ initialized, setInitialized ] = useState(false);

  // Fetch the extension, called once on load
  let fetchExtension = (url) => {
    setInitialized(true);

    // From the extension point path, locate the URL of the ExtensionPoint
    const uixpFinder = new URL(`${UIXP_FINDER_URL}?uixp=${url}`, window.location.origin);
    fetch(uixpFinder)
      .then(grabUIXP)
      .then(handleResponse)
      .catch(handleError);
  }

  // Parse the UIXP URL from our UIXP Finder
  let grabUIXP = (response) => {
    if (!response.ok) {
      return Promise.reject(`Finding ExtensionPoint ${path} failed with response ${response.status}`);
    }

    return response.text().then( (url) => {
      const parsedURL = new URL(url, window.location.origin);
      return(fetch(parsedURL));
    });
  }

  // Parse the content from the given Response object
  let handleResponse = (response) => {
    if (!response.ok) {
      return Promise.reject(`Fetching ExtensionPoint ${path} failed with response ${response.status}`);
    }

    // Check the headers to determine how to handle this respnse
    let contentType = response.headers.get('Content-Type');

    // Truncate the ';charset=utf-8'
    const sepPos = contentType.indexOf(";");
    if (sepPos >= 0) {
      contentType = contentType.substring(0, sepPos)
    }

    // Determine what to do depending on the value of the output
    if (['text/javascript', 'application/javascript'].indexOf(contentType) >= 0) {
      // javascript -- evaluate as-is
      response.text().then( (text) => {
        return(eval(text));
      })
    } else if (contentType === 'application/json') {
      // json -- call the provided callback
      if (callback !== undefined) {
        response.json().then( (json) => callback(json));
      } else {
        return(Promise.reject(`Fetching ExtensionPoint ${path} returned json data, but no callback was provided to its ExtensionPoint`));
      }
    } else if (contentType === 'text/html') {
      // html -- include it inline
      return(response.text().then((text) => {
        setRenderedResponse((<div dangerouslySetInnerHTML={{__html: text}}/>));
      }));
    } else {
      // Reject any other content type
      return(Promise.reject(`Fetching ExtensionPoint ${path} returned unknown contentType: ${contentType}`));
    }
  }

  let handleError = (error) => {
    console.error(error);
  }

  if (!initialized) {
    fetchExtension(path);
  }


  return renderedResponse;
}

ExtensionPoint.propTypes = {
    path: PropTypes.string.isRequired,
    callback: PropTypes.func
};

export default ExtensionPoint;
