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
// For storing structure of questionnaire for reordering
import { QuestionnaireTreeProvider } from "../questionnaireEditor/QuestionnaireTreeContext";


const DEFAULT_STATE = [];

const QuestionnaireReaderContext = React.createContext(DEFAULT_STATE);
const QuestionnaireWriterContext = React.createContext();

/**
 * A context provider for a questionnaire, which contains questions data and a way to set them
 * @param {Object} props the props to pass onwards to the child, generally its children
 * @returns {Object} a React component with the questionnaire provider
 */
export function QuestionnaireProvider(props) {
  const [questions, setQuestions] = React.useState(DEFAULT_STATE);

  return (
    <QuestionnaireReaderContext.Provider value={questions}>
      <QuestionnaireWriterContext.Provider value={setQuestions} >
        <QuestionnaireTreeProvider {...props} />
      </QuestionnaireWriterContext.Provider>
    </QuestionnaireReaderContext.Provider>
    );
}

/**
 * Obtain the context reader of the parent questionnaire.
 * @returns {Object} a React context of values from the parent questionnaire
 * @throws an error if it is not within a QuestionnaireProvider
 */
export function useQuestionnaireReaderContext() {
  const context = React.useContext(QuestionnaireReaderContext);

  if (context == undefined) {
    throw new Error("useQuestionnaireReaderContext must be used within a QuestionnaireProvider")
  }

  return context;
}

/**
 * Obtain a writer to the context of the parent questionnaire.
 * @returns {Object} a React context of values from the parent questionnaire
 * @throws an error if it is not within a QuestionnaireProvider
 */
export function useQuestionnaireWriterContext() {
  const context = React.useContext(QuestionnaireWriterContext);

  if (context == undefined) {
    throw new Error("useQuestionnaireWriterContext must be used within a QuestionnaireProvider")
  }

  return context;
}
