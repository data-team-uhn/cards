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
import React, { useState, createContext, useContext } from "react";

var knownContexts;

let initialize = function(contexts) {
  let result = {};
  for (var c of contexts) {
    console.log("creating context for " + c);
    let Context = createContext();
    console.log("created context for " + c, Context);
    result[c] = Context;
  }
  return result;
}

export default function AppContext(props) {
  let [contexts, setContexts] = useState();
  if (!contexts) {
    contexts = initialize(["FormContext", "ViewContext", "LoginContext"]);
     setContexts(contexts);
  }
  let result = props.children;
  console.log(result);
  for (var Context of Object.values(contexts)) {
    let [state, setState] = useState()
    result = <Context.Provider value={[state, setState]}>{result}</Context.Provider>;
  }
  knownContexts = contexts;
  console.log("Final known contexts", knownContexts);
  return result;
}

export function useAppContext(contextName) {
  console.log(`Requested context ${contextName}`, knownContexts);
  const context = React.useContext(knownContexts[contextName]);
  console.log("Found this", context);

  if (context == undefined) {
    throw new Error("useFormReaderContext must be used within a FormProvider")
  }

  return context;
}
