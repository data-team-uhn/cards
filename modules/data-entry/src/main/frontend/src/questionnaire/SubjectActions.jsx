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
import { loadExtensions } from "../uiextension/extensionManager";

import SubjectActionContext from "./SubjectActionContext";

export default function SubjectActions(props) {
  let { subject, reloadSubject, className, size, variant } = props;

  // Store the list of actions locally as re-rendering doesn't seem to trigger properly
  // if the list of actions from SubjectActionContext is used directly for rendering.
  let [ actions, setActions ] = useState([]);

  const STATUS_FETCHING = "Fetching"
  const STATUS_LOADED = "Loaded"

  useEffect(() => {
    if (SubjectActionContext.status == STATUS_LOADED) {
      // Actions are already loaded: display them
      setActions(SubjectActionContext.value);
    } else if (SubjectActionContext.status != STATUS_FETCHING) {
      // Actions are not loaded and are not already being loadedy: load them
      SubjectActionContext.status = STATUS_FETCHING;
      loadExtensions("SubjectActions")
        .then((resp) => {
          // Once loaded, save them for other SubjectActions to use and display them
          let loadedComponents = [];
          for (let i = 0; i < resp.length; i++) {
            loadedComponents.push(resp[i]["cards:extensionRender"]);
          }
          SubjectActionContext.value = loadedComponents;
          SubjectActionContext.status = STATUS_LOADED;
          setActions(loadedComponents);
        });
      }
  }, []);

  return actions.map((ThisComp, index) => {
    return (
      <ThisComp
        key={`SubjectAction-${index}`}
        subject={subject}
        reloadSubject={reloadSubject}
        className={className}
        size={size}
        variant={variant}
      />
    );
  });
}
