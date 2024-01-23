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

// Check if an answer has been automatically flagged as incomplete or invalid
// The data parameter can come either as a [key, value] array or as an object
export function hasWarningFlags (data) {
    return (
      Array.isArray(data) ?
        hasWarningFlags(data?.[1])
      :
        !!(data?.statusFlags?.some(f => ["INCOMPLETE", "INVALID", "DRAFT"].includes(f)))
    );
}

// A helper recursive function to loop through the sections/questions data 
// and collect all answers that have the incomplete status flag
// Recursively build a map answerId->questionPath of all incomplete questions
export function getIncompleteQuestionsMap (sectionJson) {
    let retFields = {};
    Object.entries(sectionJson).map(([title, object]) => {
        // We only care about children that are cards/Answers or cards:AnswerSections
        if (object["sling:resourceSuperType"] == "cards/Answer" && hasWarningFlags(object)) {
          // If this is an cards:Question, we copy the entire path to the array
          retFields[object["@name"]] = object.question["@path"];
        } else if (object["jcr:primaryType"] == "cards:AnswerSection" && hasWarningFlags(object)) {
          // If this is a matrix, save the section path as this section is rendered as a Question component
          if ("matrix" === object.section["displayMode"]) {
            retFields[object["@name"]] = object.section["@path"];
          } else {
            // If this is a normal cards:Section, recurse deeper
            retFields = {...retFields, ...getIncompleteQuestionsMap(object)};
          }
        }
        // Otherwise, we don't care about this object
    });
    return retFields;
}

// Gets the first visible question element with an incomplete answer element from the form json
export function getFirstIncompleteQuestionEl (json) {
    // If the form is incomplete
    if (hasWarningFlags(json)) {
      // Get the map answerId->questionPath of incomplete answers
      let missingAnswers = getIncompleteQuestionsMap(json);

      // If we have incomplete answers
      if (Object.keys(missingAnswers).length > 0) {
        // Obtain their corresponding question card elements
        let incompleteQuestions = (
          // Each question card contains a hidden input with the answer node name as value
          // Obtain an array of these inputs in the order they appear in on the screen
          Array.from(document.getElementsByClassName("cards-answer-id"))
            // Retain only the missing answers
            .filter(idElt => missingAnswers[idElt.value])
            // Obtain the corresponding question card elements
            .map(idElt => idElt.closest('[id="' + missingAnswers[idElt.value] + '"]'))
            // Retain only visible questions
            .filter(questionElt => questionElt && getComputedStyle(questionElt.parentElement).display != 'none')
        );
        // Return the first question card with an incomplete answer
        return incompleteQuestions[0];
      }
    }
    return null;
}
