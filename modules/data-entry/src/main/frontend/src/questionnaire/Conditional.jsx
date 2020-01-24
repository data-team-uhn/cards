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

/**
 * Determines whether or not the conditional is satisfied.
 * @param {STRING} operandA The first operand to compare
 * @param {STRING} comparator The operator to use as a comparison
 * @param {STRING} operandB The second operand to compare (if necessary)
 */
export function isConditionalSatisfied(operandA, comparator, operandB) {
  if (comparator == "=") {
    return operandA == operandB;
  } else if (comparator == "<") {
    return operandA < operandB;
  } else if (comparator == ">") {
    return operandA > operandB;
  } else if (comparator == "<>") {
    return operandA !== operandB;
  } else if (comparator == "is empty") {
    return operandA == null || operandA == undefined;
  } else if (comparator == "is not empty") {
    return operandA != null && operandA != undefined;
  } else {
    // Invalid operand
    throw new Error("Invalid operand specified.")
  }
}

/**
 * Determines if the given lfs:Conditional is true or not.
 * @param {Object} conditional The lfs:Conditional to assert the truth of
 * @param {Object} context The React Context from which to pull values
 */
export function isConditionalObjSatisfied(conditional, context) {
  const requireAllOperandA = conditional["operandA"]["requireAll"];
  const requireAllOperandB =conditional["operandB"]?.requireAll;

  const operandA = getValue(context, conditional["operandA"]);
  const operandB = getValue(context, conditional["operandB"]);

  // Don't try to run if operandA isn't loaded yet
  if (!operandA) {
    return false;
  }

  const firstCondition = requireAllOperandA ? ((func) => operandA.some(func)) : ((func) => operandA.every(func));
  const secondCondition = requireAllOperandB ? ((func) => operandB.some(func)) : ((func) => operandB.every(func));

  return firstCondition( (valueA) => {
    return secondCondition( (valueB) => {
      return isConditionalSatisfied(valueA, conditional["comparator"], valueB);
    })
  })
}

const VALID_CONDITIONALS = ["lfs:Conditional", "lfs:ConditionalGroup"];

/**
 * Determines if a conditional child is truthy or not.
 * For non-conditional elements, this returns whatever is in defaultReturn.
 */
function _evaluateConditional(conditional, context, defaultReturn) {
  if (conditional["jcr:primaryType"] == "lfs:Conditional") {
    return isConditionalObjSatisfied(conditional, context);
  } else if (conditional["jcr:primaryType"] == "lfs:ConditionalGroup") {
    return isConditionalGroupSatisfied(conditional, context);
  }
  return defaultReturn;
}

/**
 * Determines if an lfs:ConditionalGroup object is truthy or not.
 * @param {Object} conditional The lfs:ConditionalGroup object to evaluate the truthiness of
 * @param {Object} context The React Context from which to pull values
 */
export function isConditionalGroupSatisfied(conditional, context) {
  conditionalChildren = Object.values(conditional)
    .filter((child) => VALID_CONDITIONALS.includes(child["jcr:primaryType"]));
  if (conditional["requireAll"]) {
    return Object.values(conditional)
      .every( (child) => _evaluateConditional(child, context, true));
  } else {
    return Object.values(conditional)
      .some( (child) => _evaluateConditional(child, context, false));
  }
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
