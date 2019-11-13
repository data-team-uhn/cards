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

function ExtensionPoint(props) {
  const [ renderedResponse, setRenderedResponse ] = useState();
  const [ initialized, setInitialized ] = useState(false);

  let fetchExtension = (url) => {
    setInitialized(true);
    fetch(url)
      .then(handleResponse)
      .then((text) => {setRenderedResponse({__html: text})})
      .catch(handleError);
  }

  let handleResponse = (response) => {
    if (!response.ok) {
      return Promise.reject(`Fetching ExtensionPoint ${props.path} failed with response ${response.status}`);
    }

    // Check the headers to determine how to handle this respnse
    let contentType = response.headers.get('Content-Type');

    // Truncate the ';charset=utf-8'
    const sepPos = contentType.indexOf(";");
    if (sepPos >= 0) {
      contentType = contentType.substr(0, sepPos)
    }

    // Determine what to do depending on the value of the output
    if (contentType === 'application/json') {
      // jsonp
      // FIXME: Implement
      return(eval(response.text()));
    } else if (contentType === 'text/html') {
      // html -- include it inline
      return(response.text());
    } else {
      // FIXME: What if we don't understand what we're given?
    }
  }

  let handleError = (error) => {
    console.error(error);
  }

  if (!initialized) {
    fetchExtension(props.path);
  }


  return(
    <div dangerouslySetInnerHTML={renderedResponse}/>
  );
}

ExtensionPoint.propTypes = {
    path: PropTypes.string.isRequired
};

export default ExtensionPoint;
