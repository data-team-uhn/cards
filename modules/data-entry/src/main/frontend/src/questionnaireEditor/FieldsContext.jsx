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

import React from "react";

const DEFAULT_STATE = {};

const FieldsReaderContext = React.createContext(DEFAULT_STATE);
const FieldsWriterContext = React.createContext();

/**
 * A context provider for a set of fields, which contains answers and a way to set them
 * @param {Object} props the props to pass onwards to the child, generally its children
 * @returns {Object} a React component with the fields provider
 */
export function FieldsProvider(props) {
  const [answers, setAnswers] = React.useState(DEFAULT_STATE);
  const {additionalFieldData, ...rest} = props

  return (
    <FieldsReaderContext.Provider value={{...answers, ...additionalFieldData}}>
      <FieldsWriterContext.Provider value={setAnswers} {...rest}/>
    </FieldsReaderContext.Provider>
    );
}

/**
 * Obtain the context reader of the parent fields component.
 * @returns {Object} a React context of values from the parent fields component
 * @throws an error if it is not within a FieldsProvider
 */
export function useFieldsReaderContext() {
  const context = React.useContext(FieldsReaderContext);

  if (context == undefined) {
    throw new Error("useFieldsReaderContext must be used within a FieldsProvider")
  }

  return context;
}

/**
 * Obtain a writer to the context of the parent fields component.
 * @returns {Object} a React context of values from the parent fields component
 * @throws an error if it is not within a FieldsProvider
 */
export function useFieldsWriterContext() {
  const context = React.useContext(FieldsWriterContext);

  if (context == undefined) {
    throw new Error("useFieldsWriterContext must be used within a FieldsProvider")
  }

  return context;
}
