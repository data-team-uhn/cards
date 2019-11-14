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

// Component that allows the user to insert an extension from the given URL.
//
// Required props:
//  path: Location of the extension to insert. This must correspond to a .js or .html file
//  on this server.
// Optional props:
//  id: string ID to give to the containing <div /> element
//
// Sample usage:
// <ExtensionPoint
//    path="/testRig.js"
//    />
function ExtensionPoint(props) {
  const { path, id } = props;
  const [ renderedResponse, setRenderedResponse ] = useState();
  const [ initialized, setInitialized ] = useState(false);

  // Fetch the extension, called once on load
  let fetchExtension = (url) => {
    setInitialized(true);
    const parsedURL = new URL(url, window.location.origin);
    if (isSafe(parsedURL)) {
      fetch(url)
        .then(handleResponse)
        .then((text) => {setRenderedResponse({__html: text})})
        .catch(handleError);
    }
  }

  // Determine if content at the URL is safe to include
  let isSafe = (url) => {
    return (
      // The origins must match
      window.location.origin === url.origin
      // FIXME: The node must actually be of type lfs:Extension
      )
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
      contentType = contentType.substr(0, sepPos)
    }

    // Determine what to do depending on the value of the output
    if (contentType === 'text/javascript' || contentType === 'application/javascript') {
      // jsonp
      response.text().then( (text) => {
        return(eval(text));
      })

      // As per jsonp standard, we assume that the above eval inserted this ExtensionPoint by itself,
      // so we do not do anything with the response
      return;
    } else if (contentType === 'text/html') {
      // html -- include it inline
      return(response.text());
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


  return(
    <div id={id} dangerouslySetInnerHTML={renderedResponse}/>
  );
}

ExtensionPoint.propTypes = {
    path: PropTypes.string.isRequired,
    id: PropTypes.string
};

export default ExtensionPoint;
