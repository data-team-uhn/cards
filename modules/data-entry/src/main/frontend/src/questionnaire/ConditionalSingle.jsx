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
const EMPTY = [null, undefined, ""];
const TRANSFORMATIONS = {
  "text": (a, b) => ([a, b]),
  "date": (a, b) => ([new Date(a).getTime(), new Date(b).getTime()]),
  "long": (a, b) => ([parseInt(a), parseInt(b)]),
  "decimal": (a, b) => ([parseFloat(a), parseFloat(b)]),
  "double": (a, b) => ([parseFloat(a), parseFloat(b)])
};

const OPERATIONS = {
  "=": (a, b) => (a == b),
  "<": (a, b) => (a < b),
  ">": (a, b) => (a > b),
  "<=": (a, b) => (a <= b),
  ">=": (a, b) => (a >= b),
  "<>": (a, b) => (a != b),
  "is empty": (a, b) => (EMPTY.indexOf(a) >= 0 || EMPTY.indexOf(b) >= 0),
  "is not empty": (a, b) => (EMPTY.indexOf(a) < 0 || EMPTY.indexOf(b) < 0)
};

let compare = function(a, b, type, operation) {
  if (operation === "is empty" || operation === "is not empty") {
    return OPERATIONS[operation](a, b);
  } else if (EMPTY.indexOf(a) >= 0 || EMPTY.indexOf(b) >= 0) {
    return false;
  }
  return OPERATIONS?.[operation]?.(...(TRANSFORMATIONS[type] || TRANSFORMATIONS["text"])(a, b));
}

/**
 * Determines whether or not the conditional is satisfied.
 * @param {STRING} comparator The operator to use as a comparison
 * @param {STRING...} operands Any number of operands used for the comparison
 */
export function isConditionalSatisfied(compareDataType, comparator, ...operands) {
  if (!(compareDataType in TRANSFORMATIONS)) {
    // Invalid data type
    throw new Error("Invalid data type specified.")
  }
  if (!(comparator in OPERATIONS)) {
    // Invalid operand
    throw new Error("Invalid operand specified.")
  }

  return compare(...operands, compareDataType, comparator);
}

/**
 * Determines if the given cards:Conditional is true or not.
 * @param {Object} conditional The cards:Conditional to assert the truth of
 * @param {Object} context The React Context from which to pull values
 */
export function isConditionalObjSatisfied(conditional, context) {
  const requireAllOperandA = conditional["operandA"]["requireAll"];
  const requireAllOperandB = conditional["operandB"]?.requireAll;

  // If the operands aren't loaded yet, treat them as being empty
  var operandA = getValue(context, conditional["operandA"]) || [""];
  if (!allIsNull(operandA)) {
      operandA = removeAllNull(operandA);
  }

  var operandB = getValue(context, conditional["operandB"]) || [""];
  if (!allIsNull(operandB)) {
      operandB = removeAllNull(operandB);
  }

  const firstCondition = requireAllOperandA ? ((func) => operandA.every(func)) : ((func) => operandA.some(func));
  const secondCondition = requireAllOperandB ? ((func) => operandB.every(func)) : ((func) => operandB.some(func));

  return firstCondition( (valueA) => {
    return secondCondition( (valueB) => {
      return isConditionalSatisfied(conditional["dataType"], conditional["comparator"], valueA, valueB);
    })
  })
}

function removeAllNull(lst) {
  return lst.filter(item => (EMPTY.indexOf(item) < 0));
}

function allIsNull(lst) {
  for (var i = 0; i < lst.length; i++) {
    if (EMPTY.indexOf(lst[i]) < 0) {
      return false;
    }
  }
  return true;
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
  if (conditionDefinition["jcr:primaryType"] === "cards:Conditional") {
    return [isConditionalObjSatisfied, 50];
  }
}, ["cards:Conditional"]);
