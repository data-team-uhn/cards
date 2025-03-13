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

import React, { useEffect, useState } from "react";
import { useLocation } from "react-router";

import Form from "./Form";

/**
 * A shell component to render form view
 * @returns {Object} a React Form component
 */
export default function FormView() {
  let location = useLocation();
  let [ id, setId ] = useState(/Forms\/([^.\/]+)/.exec(location.pathname)[1]);
  let [ mode, setMode ] = useState(location.pathname.endsWith(".edit") ? "edit" : location.pathname.endsWith(".summary") ? "summary" : undefined);

  useEffect(() => {
    setId(/Forms\/([^.\/]+)/.exec(location.pathname)[1]);
    setMode(location.pathname.endsWith(".edit") ? "edit" : location.pathname.endsWith(".summary") ? "summary" : "view");
  }, [location]);

  return (
    <Form id={id} mode={mode} key={id}/>
    );
}
