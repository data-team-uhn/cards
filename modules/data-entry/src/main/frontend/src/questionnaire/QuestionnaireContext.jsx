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

import React, { useEffect, useMemo, useState } from "react";
// For storing structure of questionnaire for reordering
import { useQuestionnaireTreeContext, findTreeEntries } from "../questionnaireEditor/QuestionnaireTreeContext";
import { ENTRY_TYPES, EXTLINK_TYPES, QUESTION_TYPES, QUESTIONNAIRE_TYPES } from "./FormEntry";

// Custom hook to track which item is in view
export function useInViewTracker(items, options = { threshold: 0.3 }) {
  const [activeItem, setActiveItem] = useState(null);
  const [lastIntersectingItem, setLastIntersectingItem] = useState(null);
  
  // Set up Intersection Observer
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const id = entry.target.getAttribute('in-view-data-id');
  
          if (entry.isIntersecting) {
            // console.log('entry is intersecting', id);
            setActiveItem(id);
            setLastIntersectingItem(id);
          }
        });
      },
      { ...options }
    );

    items.forEach(item => {
      const element = document.querySelector(`[in-view-data-id='${item.value}']`);
      if (element) {
        observer.observe(element);
      }
    })

    // Cleanup observer on unmount
    return () => {
      observer.disconnect();
    };
  }, [items, options, lastIntersectingItem]);

  // Function to scroll to item card when clicked
  const scrollToItem = (id) => {
    const target = document.querySelector(`[in-view-data-id='${id}']`);
    const targetPosition = target.getBoundingClientRect().top + window.scrollY;
    const offsetPosition = targetPosition - 100;
  
    window.scrollTo({
      top: offsetPosition,
      behavior: 'smooth'
    });
  }



  // Map of which items are highlighted
  const [highlightedItems, setHighlightedItems] = useState(new Map());
  const highlighter = {
    highlightedItems,
    highlight: (id) => {
      setHighlightedItems(new Map(highlightedItems.set(id, true)));
    },
    unhighlight: (id) => {
      highlightedItems.delete(id);
      setHighlightedItems(new Map(highlightedItems));
    },
    unhighlightAll: (ids) => {
      console.log('unhighlight all')
      highlightedItems.forEach((_, id) => {
        highlightedItems.delete(id);
      })
      setHighlightedItems(new Map(highlightedItems));
    },
    isHighlighted: (id) => highlightedItems.has(id),
  }

  return { activeItem, scrollToItem, highlighter,};
};


const DEFAULT_STATE = [];

const QuestionnaireReaderContext = React.createContext(DEFAULT_STATE);
// const QuestionnaireWriterContext = React.createContext();
const QuestionnaireInViewContext = React.createContext();

/**
 * A context provider for a questionnaire, which contains questions data and a way to set them
 * @param {Object} props the props to pass onwards to the child, generally its children
 * @returns {Object} a React component with the questionnaire provider
 */
export function QuestionnaireProvider(props) {
  const treeContext = useQuestionnaireTreeContext();

  const questions = useMemo(() => {
    return findTreeEntries(treeContext.state.nodes, QUESTION_TYPES)
  }, [treeContext.state.nodes]);

  const inViewEntries = useMemo(() => {
    return findTreeEntries(treeContext.state.nodes,
      ENTRY_TYPES.concat(QUESTIONNAIRE_TYPES).concat(EXTLINK_TYPES)
    )
  }, [treeContext.state.nodes]);

  // Use useInViewTracker for breadcrumb
  const inViewTracker = useInViewTracker(inViewEntries);
  // console.log(inViewTracker);
  return (
    <QuestionnaireReaderContext.Provider value={questions}>
      <QuestionnaireInViewContext.Provider value={inViewTracker} {...props} />
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
 * Obtain the inView state of the parent questionnaire
 */
export function useQuestionnaireInViewContext() {
  const context = React.useContext(QuestionnaireInViewContext);

  if (context == undefined) {
    throw new Error("useQuestionnaireInViewContext must be used within a QuestionnaireProvider")
  }

  return context;
}
