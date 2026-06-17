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

// Presentation metadata for questionnaire entries: how each JCR entry type is labelled
// (which field holds its title) and shown (icon + colour), plus the renderer that turns a
// stored conditional/conditional-group into a readable expression. The title-field part of
// the spec is also read by the tree model when deriving a node's title.

import React from "react";

import { deepPurple, orange, blueGrey, blue, purple, green } from '@mui/material/colors';

export const ENTRY_TITLE_FIELD_SPEC = {
  'cards:Questionnaire': {
    titleField: 'title',
    icon: 'assignment',
    color: blueGrey[700]
  },
  'cards:Section': {
    titleField: 'label',
    icon: 'view_stream',
    color: orange[800]
  },
  'cards:Question': {
    titleField: 'text',
    icon: 'Q',
    color: deepPurple[700]
  },
  'cards:Information': {
    // no title field should exclude it from title warning
    icon: 'info',
    color: blue[600]
  },
  'cards:ExternalLink': {
    icon: 'link',
    color: purple[300]
  },
  'cards:Conditional': {
    icon: 'C',
    color: green[800],
  },
  'cards:ConditionalGroup': {
    icon: 'C',
    color: green[800]
  },
}

export const jcrGetConditionalTitle = (jcrDataTitle) => {
  let jcrData;
  try {
    jcrData = JSON.parse(jcrDataTitle);
  } catch {
    return jcrDataTitle;
  }

  const stringifyConditionalOperand = (operand) => {
    const { value, isReference } = operand;

    if (typeof value == 'undefined') {
      return undefined;
    }
    if (isReference) {
      return <strong>{`${value[0]}`}</strong>;
    } else {
      return (
        <>
          {value.map((v, i) => { return (<span key={`${v}_${i}`}>&quot;{v}&quot;{i < value.length - 1 ? "," : ""}</span>) })}
        </>
      );
    }
  }

  const stringifyConditional = (jcrData) => {
    const operandA = jcrData['operandA'];
    const operandB = jcrData['operandB'];
    const comparator = jcrData['comparator'];

    const isUnary = typeof operandB.value == 'undefined';

    return (
      <>
        <span>
          {stringifyConditionalOperand(operandA)}
          {' '}
          {comparator}
          {!isUnary && ' '}
          {stringifyConditionalOperand(operandB)}
        </span>
      </>
    )
  }

  const stringifyConditionalGroup = (jcrData) => {
    const requireAll = jcrData['requireAll']
    const conditionalChildren = Object.keys(jcrData).filter(key => ['cards:Conditional', 'cards:ConditionalGroup'].includes(jcrData[key]['jcr:primaryType']))
    return (
      <>
        {conditionalChildren.map((conditionalKey, i) => {
          const primaryType = jcrData[conditionalKey]['jcr:primaryType'];
          return (
            <React.Fragment key={conditionalKey}>
              {i > 0 ? (requireAll ? ' AND ' : ' OR ') : ''}
              {primaryType === 'cards:Conditional'
                ? stringifyConditional(jcrData[conditionalKey])
                : <>({stringifyConditionalGroup(jcrData[conditionalKey])})</>}
            </React.Fragment>
          )
        })}
      </>
    )
  }

  if (['cards:Conditional'].includes(jcrData['jcr:primaryType'])) {
    return stringifyConditional(jcrData);
  } else if (['cards:ConditionalGroup'].includes(jcrData['jcr:primaryType'])) {
    return stringifyConditionalGroup(jcrData);
  }

  console.warn('jcrGetConditionalTitle called with invalid jcrData');
}
