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
 * @param {STRING} operand_a The first operand to compare
 * @param {STRING} comparator The operator to use as a comparison
 * @param {STRING} operand_b The second operand to compare (if necessary)
 */
export function isConditionalSatisfied(operand_a, comparator, operand_b) {
  if (comparator == "=") {
    return operand_a == operand_b;
  } else if (comparator == "<") {
    return operand_a < operand_b;
  } else if (comparator == ">") {
    return operand_a > operand_b;
  } else if (comparator == "<>") {
    return operand_a !== operand_b;
  } else if (comparator == "is empty") {
    return operand_a == null || operand_a == undefined;
  } else if (comparator == "is not empty") {
    return operand_a != null && operand_a != undefined;
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
  return isConditionalSatisfied(
    getValue(context, conditional["operandA"], conditional["operandAIsReference"]),
    conditional["comparator"],
    getValue(context, conditional["operandB"], conditional["operandBIsReference"])
    );
}

/**
 * Converts a potential reference into its value from the given context, or returns the id input.
 * @param {Object} context The React Context from which to pull values
 * @param {String} id The ID of the field to use, or its raw value
 * @param {Boolean} isReference Whether or not the input is a reference or not
 */
function getValue(context, id, isReference) {
    return (isReference ? (context[id] && context[id][0] && context[id][0][VALUE_POS]) : (id));
}
