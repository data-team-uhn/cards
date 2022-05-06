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

// This is a utility "class" which helps the QuestionnaireEntry component decide how each value should be rendered.
export default class ValueComponentManager {
  // Registers a new value displayer. This method is only supposed to be called by each value displayer.
  //
  // @param component A function which can be called to determine how well suited the registered component can display a specific question.
  //     The function receives the JSON of a question definition, and returns a tuple (array with 2 items), the actual React Component that can display the question, and the confidence that it is the right displayer.
  //     The confidence is a number between 0 and 100, with a bigger number indicating more confidence. The registered component with the highest confidence will be used to display the question.
  static registerValueComponent(component) {
    _registeredComponents.push(component);
  }

  // Picks and returns the registered component with the highest confidence that it can display the given value type
  //
  // @param definition the label of the question
  // @return a React Component that can render the question
  static getValueComponent(definition) {
    return (_registeredComponents
      .map(component => (component)(definition))
      .filter(displayer => displayer)
      .reduce(([chosenDisplayer, maxPriority], [displayer, priority]) => priority > maxPriority ? [displayer, priority] : [chosenDisplayer, maxPriority]))[0];
  }
}
