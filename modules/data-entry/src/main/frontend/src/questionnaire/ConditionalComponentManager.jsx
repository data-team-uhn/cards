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

let _registeredComponents = [];
let _registeredConditionalTypes = [];

// This is a utility class which helps the Section component decide how each to handle conditions on its display.
export default class ConditionalComponentManager {
  /**
   * Registers a new conditional handler. This method is only supposed to be called by each conditional handler.
   * @param {Function} component A function which can be called to determine how well suited the registered component can handle a given conditional object.
   *     The function receives the JSON of a conditional, and returns a tuple (array with 2 items), the actual function that can handle the conditional, and the confidence that it is the right handler.
   *     The confidence is a number between 0 and 100, with a bigger number indicating more confidence. The registered component with the highest confidence will be used to handle the conditional.
   * @param {Array} conditionalTypes The jcr:primaryTypes that denote that an object is a valid conditional.
   */
  static registerConditionComponent(component, conditionalTypes) {
    _registeredComponents.push(component);
    _registeredConditionalTypes = _registeredConditionalTypes.concat(conditionalTypes);
  }

  /**
   * Returns whether or not the given property a valid conditional.
   * @param {Object} property The JSON of a potential condition definition
   * @returns Whether or not the given property is a valid conditional
   */
  static isValidConditional(property) {
    return _registeredConditionalTypes.includes(property["jcr:primaryType"]);
  }

  /**
   * Determines whether the given condition is truthy or not, by querying each registered conditional component.
   * @param {Object} conditionDefinition The full JSON of the condition definition, as obtained from JCR
   * @param {Object} context The React Context to evaluate the condition within, usually given by a FormContext
   * @returns Whether or not the given conditional is truthy
   */
  static evaluateCondition(conditionDefinition, context) {
    return (_registeredComponents
      .map(component => (component)(conditionDefinition))
      .filter(handler => handler)
      .reduce(([chosenDisplayer, maxPriority], [displayer, priority]) => priority > maxPriority ? [displayer, priority] : [chosenDisplayer, maxPriority]))[0]
      (conditionDefinition, context);
  }
}
