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

import baseHints from './Question-hints.json';
import baseSpec from './Question.json';
import { loadExtensions } from '../uiextension/extensionManager';

// The current question editor spec, starts with the base Question.json content
let spec = baseSpec[0];
// The current merged hints
let hints = { ...baseHints };

// Display order for the dataType options in the question editor; lower numbers appear first.
// The dataTypes still configured inline in Question.json are seeded from their position there. dataTypes that
// self-register (built-in types extracted from Question.json) or are contributed by an extension provide their
// own order, mirroring the role `cards:defaultOrder` plays for extensions. Extension-contributed dataTypes are
// offset so that they always sort after the built-in ones.
const EXTENSION_ORDER_OFFSET = 1000;
let dataTypeOrders = Object.keys(spec.dataType || {})
  .reduce((orders, dataType, index) => ({ ...orders, [dataType]: index * 100 }), {});

// Listeners to be notified when the spec updates
let listeners = [];

// Merge additional dataType configurations into the current spec, optionally recording a display order for each
// contributed dataType, then notify the listeners with the freshly ordered spec.
function mergeDataTypeConfig(config, order) {
  spec = {
    ...spec,
    dataType: {
      ...spec.dataType,
      ...config,
    },
  };
  if (typeof order === 'number') {
    // A single number applies to every dataType in the config.
    Object.keys(config).forEach(dataType => { dataTypeOrders[dataType] = order; });
  } else if (order && typeof order === 'object') {
    // An object assigns a distinct order per dataType, for components that contribute several (e.g. long/decimal/double).
    Object.assign(dataTypeOrders, order);
  }
  listeners.forEach(listener => listener(getQuestionSpec()));
}

// Merge field hints contributed by a question type into the current hints. When a field already has a hint
// (such as the shared `defaultValue` description in Question-hints.json), the contributed hint is appended to
// it rather than replacing it, so each question type can add its own note to a field shared across types.
function mergeHints(extraHints) {
  if (!extraHints) {
    return;
  }
  hints = Object.entries(extraHints).reduce((merged, [field, hint]) => ({
    ...merged,
    [field]: merged[field] ? `${merged[field]}\n${hint}` : hint,
  }), hints);
}

// Registers a dataType configuration contributed by a statically-loaded (built-in) question type, so that the
// question editor offers it without it needing to be hardcoded in Question.json. This is the static counterpart
// to the `questionEditorConfig` that extension-based question types expose.
//
// @param config the dataType configuration, an object keyed by dataType name (e.g. `{ boolean: {...} }`)
// @param options.hints optional field hints to merge (appended to any existing hint for the same field)
// @param options.order optional display order for the contributed dataType(s) in the editor dropdown; either a
//   single number applied to all, or an object mapping each dataType name to its own order
export function registerQuestionEditorConfig(config, { hints: extraHints, order } = {}) {
  mergeHints(extraHints);
  mergeDataTypeConfig(config, order);
}

async function loadQuestionEditorConfigs() {
  try {
    const extensions = await loadExtensions("AnswerComponents");
    extensions
      .filter(Boolean)
      .forEach(ext => {
        const component = ext['cards:extensionRender'];
        if (component?.questionEditorConfig) {
          // Keep extension-contributed dataTypes after the built-in ones, ordered among themselves by cards:defaultOrder.
          mergeDataTypeConfig(component.questionEditorConfig, EXTENSION_ORDER_OFFSET + (ext['cards:defaultOrder'] || 0));
        }
        mergeHints(component?.questionEditorHints);
      });
  } catch (e) {
    console.error("Failed to load question editor configs from extensions", e);
  }
}

loadQuestionEditorConfigs();

// Get the current merged question editor spec, with the dataType options ordered for display.
export function getQuestionSpec() {
  const orderedDataType = Object.fromEntries(
    Object.entries(spec.dataType || {})
      .sort(([a], [b]) => (dataTypeOrders[a] ?? Infinity) - (dataTypeOrders[b] ?? Infinity))
  );
  return { ...spec, dataType: orderedDataType };
}

// Get the current merged question editor hints
export function getQuestionHints() {
  return hints;
}

// Subscribe to spec updates; returns a cleanup function to remove the listener
export function onQuestionSpecUpdate(listener) {
  listeners.push(listener);
  return () => {
    listeners = listeners.filter(l => l !== listener);
  };
}
