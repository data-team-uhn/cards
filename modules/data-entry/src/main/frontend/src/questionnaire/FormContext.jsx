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

const FormReaderContext = React.createContext(DEFAULT_STATE);
const FormWriterContext = React.createContext();

/**
 * A context provider for a form, which contains answers and a way to set them
 */
export function FormProvider(props) {
  const [answers, setAnswers] = React.useState(DEFAULT_STATE);

  return (
    <FormReaderContext.Provider value={answers}>
      <FormWriterContext.Provider value={setAnswers} {...props}/>
    </FormReaderContext.Provider>
    );
}

/**
 * Read from the context of the parent form.
 * This will throw an error if it is not within a FormProvider
 */
export function useFormReaderContext() {
  const context = React.useContext(FormReaderContext);

  if (context == undefined) {
    throw new Error("useFormReaderContext must be used within a FormProvider")
  }

  return context;
}

/**
 * Write to the context of the parent form.
 * This will throw an error if it is not within a FormProvider
 */
export function useFormWriterContext() {
  const context = React.useContext(FormWriterContext);

  if (context == undefined) {
    throw new Error("useFormWriterContext must be used within a FormProvider")
  }

  return context;
}
