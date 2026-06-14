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
import { createContext, useContext, useEffect, useMemo, useState } from "react";

import { ENTRY_TYPES, EXTLINK_TYPES, QUESTION_TYPES, QUESTIONNAIRE_TYPES } from "./FormEntry";
// For storing structure of questionnaire for reordering
import { useQuestionnaireTreeContext, findTreeEntries } from "../questionnaireEditor/QuestionnaireTreeContext";

// Custom hook to track which item is currently in view (nearest the top of the viewport)
export function useInViewTracker(items) {
  const [activeItem, setActiveItem] = useState(null);

  useEffect(() => {
    // Ids of items whose top has scrolled up to/past the header line and that are still on
    // screen. We observe the strip from the top of the viewport down to the header, so an
    // item joins this set exactly when its top crosses the header line.
    const passed = new Set();

    // Measured fresh each update so the collapsing sticky header is accounted for.
    const headerLine = () => {
      const header = document.getElementById('cards-resource-header');
      return header ? header.getBoundingClientRect().bottom : 0;
    };

    const update = () => {
      const lineY = headerLine();
      // The current item is the deepest one whose top has passed the line — the largest
      // top still at/above the line. That is the innermost question (its top sits below
      // its section's), and it persists through the gap between items until the next
      // item's top reaches the line, so the breadcrumb transitions smoothly with no gaps.
      let activeId = null;
      let bestTop = -Infinity;
      passed.forEach((id) => {
        const element = document.querySelector(`[in-view-data-id='${id}']`);
        if (!element) return;
        const top = element.getBoundingClientRect().top;
        if (top <= lineY + 1 && top > bestTop) {
          activeId = id;
          bestTop = top;
        }
      });
      if (activeId) setActiveItem(activeId);
    };

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const id = entry.target.getAttribute('in-view-data-id');
          if (!id) return;
          if (entry.isIntersecting) {
            passed.add(id);
          } else {
            passed.delete(id);
          }
        });
        update();
      },
      // Observe the strip from the top of the viewport down to the header line.
      { rootMargin: `0px 0px -${Math.max(0, window.innerHeight - headerLine())}px 0px`, threshold: 0 }
    );

    items.forEach(item => {
      const element = document.querySelector(`[in-view-data-id='${item.value}']`);
      if (element) {
        observer.observe(element);
      }
    });

    return () => observer.disconnect();
  }, [items]);

  const scrollToItem = (id) => {
    const target = document.querySelector(`[in-view-data-id='${id}']`);
    if (!target) return;
    const targetPosition = target.getBoundingClientRect().top + window.scrollY;
    window.scrollTo({
      top: targetPosition - 100,
      behavior: 'smooth'
    });
  };

  const [highlightedItems, setHighlightedItems] = useState(() => new Map());
  const highlighter = useMemo(() => ({
    highlightedItems,
    highlight: (id) => {
      setHighlightedItems((prev) => {
        const next = new Map(prev);
        next.set(id, true);
        return next;
      });
    },
    unhighlight: (id) => {
      setHighlightedItems((prev) => {
        const next = new Map(prev);
        next.delete(id);
        return next;
      });
    },
    unhighlightAll: () => setHighlightedItems(new Map()),
    isHighlighted: (id) => highlightedItems.has(id),
  }), [highlightedItems]);

  return { activeItem, scrollToItem, highlighter };
}


const DEFAULT_STATE = [];

const QuestionnaireReaderContext = createContext(DEFAULT_STATE);
const QuestionnaireInViewContext = createContext();

/**
 * Builds the ancestor path of a node, from the root down to and including the node.
 * @param {Object} nodes - the flat node map from the tree context
 * @param {string} id - the id of the node currently in view
 * @returns {Array.<Object>} ancestor nodes, root first and the node itself last
 */
export function getAncestorPath(nodes, id) {
  const path = [];
  let current = nodes?.[id];
  while (current) {
    path.unshift(current);
    current = current.parent ? nodes[current.parent] : null;
  }
  return path;
}

/**
 * A context provider for a questionnaire, which contains questions data and a way to set them
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
