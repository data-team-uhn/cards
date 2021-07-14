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

import ConditionalComponentManager from "./ConditionalComponentManager";

/**
 * Determines if an cards:ConditionalGroup object is truthy or not.
 * @param {Object} conditional The cards:ConditionalGroup object to evaluate the truthiness of
 * @param {Object} context The React Context from which to pull values
 */
export function isConditionalGroupSatisfied(conditional, context) {
  let conditionalChildren = Object.values(conditional)
    .filter((child) => ConditionalComponentManager.isValidConditional(child) && child?.["jcr:primaryType"] != "cards:Section");

  if (conditionalChildren.length == 0) {
    return true;
  }

  if (conditional["requireAll"]) {
    return conditionalChildren.every( (child) => ConditionalComponentManager.evaluateCondition(child, context) );
  } else {
    return conditionalChildren.some( (child) => ConditionalComponentManager.evaluateCondition(child, context) );
  }
}

const HANDLED_TYPES = ["cards:ConditionalGroup", "cards:Section"];

ConditionalComponentManager.registerConditionComponent((conditionDefinition) => {
  if (HANDLED_TYPES.includes(conditionDefinition["jcr:primaryType"])) {
    return [isConditionalGroupSatisfied, 50];
  }
}, HANDLED_TYPES);