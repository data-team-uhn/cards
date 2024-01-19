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

import { VALUE_POS } from "./Answer";
import ConditionalComponentManager from "./ConditionalComponentManager";

let transform = (values, transformerFunc) => values.map(v => Array.isArray(v) ? v.map(e => transformerFunc(e)) : transformerFunc(v));

const TRANSFORMATIONS = {
  "text": (a, b) => [a, b],
  "date": (a, b) => transform([a, b], v => new Date(v).getTime()),
  "long": (a, b) => transform([a, b], parseInt),
  "decimal": (a, b) => transform([a, b], parseFloat),
  "double": (a, b) => transform([a, b], parseFloat)
};

const OPERATIONS = {
  "=": (a, b) => a.length == b.length && a.every((e, idx) => b[idx] == e), // array equality
  "<": (a, b) => (a < b),
  ">": (a, b) => (a > b),
  "<=": (a, b) => (a <= b),
  ">=": (a, b) => (a >= b),
  "<>": (a, b) => a.length != b.length || a.some((e, idx) => b[idx] != e), // array inequality
  "is empty": (a) => a.length == 0,
  "is not empty": (a) => a.length > 0,
  "includes": (a, b) => b.every(e => a.includes(e)),
  "excludes": (a, b) => b.every(e => !a.includes(e))
};


/**
 * Determines if the given cards:Conditional is true or not.
 * @param {Object} conditional The cards:Conditional to assert the truth of
 * @param {Object} context The React Context from which to pull values
 */
export function isConditionalSatisfied(conditional, context) {
  if (!(conditional.comparator in OPERATIONS)) {
    // Invalid operation
    throw new Error("Invalid operation specified.")
  }
  var dataType = TRANSFORMATIONS[conditional.dataType] ? conditional.dataType : 'text';
  var operandA = getValue(conditional.operandA, context);
  var operandB = getValue(conditional.operandB, context);

  return compare(operandA, operandB, dataType, conditional.comparator);
}


/**
 * Converts a potential reference into its value from the given context, or returns the id input.
 * @param {String} operand an object {isReference: true/false, value} where value is the id of the
 *    question to use if isReference is true, or a set of raw values
 * @param {Object} context The React Context from which to pull values
 */
let getValue = function(operand, context) {
  let value = (operand?.isReference ?
    // Assume only one variable and retrieve its values from the form context
    context[operand.value]?.map(v => v[VALUE_POS])
    // Otherwise use the value as is
    : operand?.value
  // Filter out blanks, default to empty array
  )?.filter(v => v) || [];
  return value;
}

let compare = function(a, b, type, operation) {
  // Some preprocessing before comparison
  let _a = a, _b = b;

  if (["=", "<>"]. includes(operation)) {
    // Sort the arrays before comparing
    _a = Array.from(a).sort();
    _b = Array.from(b).sort();
  }
  if (["<", ">", "<=", ">="]. includes(operation)) {
    // These operations are not fit for arrays
    if (a.length > 1 || b.length > 1) return false;
    _a = a[0];
    _b = b[0];
  }
  return OPERATIONS[operation]?.(...(TRANSFORMATIONS[type])(_a, _b));
}

ConditionalComponentManager.registerConditionComponent((conditionDefinition) => {
  if (conditionDefinition["jcr:primaryType"] === "cards:Conditional") {
    return [isConditionalSatisfied, 50];
  }
}, ["cards:Conditional"]);
