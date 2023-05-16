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

// Removes the "cards:" prefix from a jcr:primaryType string
// Example: "cards:Question" -> "Question"
export function stripCardsNamespace (str) { return str?.replaceAll(/^cards:/g, ""); }

// Returns an array of all the questionnaire entries of specified types found in a json
//
// Parameters:
// * json: a JSON object, usually the response of `/Questionaire/<QuestionnaireID>.deep.json
// * entryTypes: an array of strings specifying the `jcr:primaryType`s of the entities that
//   should be retrieved. By default, Questions and Sections are targeted.
// * rootPath: a string that specifies the jcr path relatively to which the `relativePath` of
//   the results should be computed. Defaults to the `@path` field in the `json`
// * result: an array holding the retrieved entries, populated by the function's recursive
//   calls and returned at the end. Can be prepopulated with some entries, is [] by default
// Returns: an array of objects containing all entries found in the json whose jcr:primaryType
//   match one of the provided entryTypes. The shape of the objects is:
//   {
//     id: the uuid (string),
//     name: the @name (string),
//     text: the text or label (string)
//     path: the @path (string)
//     relativePath: the path after removing rootPath form the start and @name from the end (string)
//     type: the primary type, withoug the "cards:" prefix
//   }
export function findQuestionnaireEntries(
  json,
  entryTypes=["cards:Question", "cards:Section"],
  rootPath = (json['@path'] || ''),
  result = []
) {
  if (typeof(json) == "object") {
    Object.entries(json || {}).forEach(([k,e]) => {
      if (entryTypes.includes(e?.['jcr:primaryType'])) {
        let relativePath = e['@path']?.replace(`${rootPath}/`, '') || '';
        relativePath = relativePath.substring(0, relativePath.lastIndexOf("/") + 1);
        result.push({
          id: e['jcr:uuid'],
          name: e['@name'],
          text: e['text'] || e['label'],
          path: e['@path'],
          relativePath: relativePath,
          type: stripCardsNamespace(e['jcr:primaryType'])
        });
      }
      findQuestionnaireEntries(e, entryTypes, rootPath, result);
    });
  }
  return result;
}
