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


export function findQuestionnaireEntries(
  json,
  entryTypes=["cards:Question", "cards:Section"],
  rootPath = json['@path'],
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
          type: e['jcr:primaryType'].replace("cards:", '')
        });
      }
      findQuestionnaireEntries(e, entryTypes, rootPath, result);
    });
  }
  return result;
}
