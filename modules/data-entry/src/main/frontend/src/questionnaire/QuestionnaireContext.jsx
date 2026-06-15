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
import { createContext, useContext, useMemo } from "react";

import { ENTRY_TYPES, EXTLINK_TYPES, QUESTION_TYPES, QUESTIONNAIRE_TYPES } from "./FormEntry";
import { useInViewTracker } from "./useInViewTracker";
// For storing structure of questionnaire for reordering
import { useQuestionnaireTreeContext } from "../questionnaireEditor/QuestionnaireTreeContext";
import { findTreeEntries } from "../questionnaireEditor/treeQueries";

const DEFAULT_STATE = [];

const QuestionnaireReaderContext = createContext(DEFAULT_STATE);
const QuestionnaireInViewContext = createContext();

/**
 * A context provider for a questionnaire, exposing the (derived, read-only) list of questions
 * and an in-view tracker used by the location breadcrumb.
 * @param {Object} props the props to pass onwards to the child, generally its children
 * @returns {Object} a React component with the questionnaire provider
 */
export function QuestionnaireProvider(props) {
  const treeContext = useQuestionnaireTreeContext();

  const questions = useMemo(() => {
    return findTreeEntries(treeContext.state.nodes, QUESTION_TYPES);
  }, [treeContext.state.nodes]);

  const inViewEntries = useMemo(() => {
    return findTreeEntries(treeContext.state.nodes,
      ENTRY_TYPES.concat(QUESTIONNAIRE_TYPES).concat(EXTLINK_TYPES)
    )
  }, [treeContext.state.nodes]);

  // Use useInViewTracker for breadcrumb
  const inViewTracker = useInViewTracker(inViewEntries);
  return (
    <QuestionnaireReaderContext.Provider value={questions}>
      <QuestionnaireInViewContext.Provider value={inViewTracker} {...props}/>
    </QuestionnaireReaderContext.Provider>
  );
}

/**
 * Obtain the context reader of the parent questionnaire.
 * @returns {Object} a React context of values from the parent questionnaire
 * @throws an error if it is not within a QuestionnaireProvider
 */
export function useQuestionnaireReaderContext() {
  const context = useContext(QuestionnaireReaderContext);

  if (context == undefined) {
    throw new Error("useQuestionnaireReaderContext must be used within a QuestionnaireProvider")
  }

  return context;
}

/**
 * Obtain the inView state of the parent questionnaire
 */
export function useQuestionnaireInViewContext() {
  const context = useContext(QuestionnaireInViewContext);

  if (context == undefined) {
    throw new Error("useQuestionnaireInViewContext must be used within a QuestionnaireProvider")
  }

  return context;
}
