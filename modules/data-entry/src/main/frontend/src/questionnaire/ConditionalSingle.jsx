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

// A mapping from operands to conditionals
const COMPARE_MAP = {
  "=": (a, b) => (a == b),
  "<": (a, b) => (a < b),
  ">": (a, b) => (a > b),
  "<>": (a, b) => (a !== b),
  "is empty": (a) => (a == null || a == undefined),
  "is not empty": (a) => (a != null && a != undefined)
}

/**
 * Determines whether or not the conditional is satisfied.
 * @param {STRING} comparator The operator to use as a comparison
 * @param {STRING...} operands Any number of operands used for the comparison
 */
export function isConditionalSatisfied(comparator, ...operands) {
  if (!(comparator in COMPARE_MAP)) {
    // Invalid operand
    throw new Error("Invalid operand specified.")
  }

  return COMPARE_MAP[comparator]?.apply(null, operands);
}

/**
 * Determines if the given lfs:Conditional is true or not.
 * @param {Object} conditional The lfs:Conditional to assert the truth of
 * @param {Object} context The React Context from which to pull values
 */
export function isConditionalObjSatisfied(conditional, context) {
  const requireAllOperandA = conditional["operandA"]["requireAll"];
  const requireAllOperandB = conditional["operandB"]?.requireAll;

  const operandA = getValue(context, conditional["operandA"]);
  const operandB = getValue(context, conditional["operandB"]);

  // Don't try to run if operandA isn't loaded yet
  if (!operandA) {
    return false;
  }

  const firstCondition = requireAllOperandA ? ((func) => operandA.every(func)) : ((func) => operandA.some(func));
  const secondCondition = requireAllOperandB ? ((func) => operandB.every(func)) : ((func) => operandB.some(func));

  return firstCondition( (valueA) => {
    return secondCondition( (valueB) => {
      return isConditionalSatisfied(conditional["comparator"], valueA, valueB);
    })
  })
}

/**
 * Converts a potential reference into its value from the given context, or returns the id input.
 * @param {Object} context The React Context from which to pull values
 * @param {String} valueObj The ID of the field to use, or its raw value
 */
function getValue(context, valueObj) {
  return (valueObj && (valueObj["isReference"] ?
    // Find the value from the referred position, and remap it to only include its values
    (context[valueObj["value"]]?.map((element) => (element[VALUE_POS])))
    // Otherwise use the value as is
    : valueObj["value"]));
}

ConditionalComponentManager.registerConditionComponent((conditionDefinition) => {
  if (conditionDefinition["jcr:primaryType"] === "lfs:Conditional") {
    return [isConditionalObjSatisfied, 50];
  }
}, ["lfs:Conditional"]);
