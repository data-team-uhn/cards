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

// Utility functions used by the VocabularyQuery component
// for selecting which data fields of each vocabulary term should
// be displayed in the suggestions list to show thw user how each
// suggestion matched their query.
//
// The implementation must be revisited once vocabulary indexing
// starts allowing for typos.
//
export default class QueryMatchingUtils {

    // Split the query string into words to be matched separately
    static parseQuery = (query) => {
      return query?.toLowerCase().split(/[^a-zA-Z0-9_]/) || [];
    }

    // Return true if all the words in the query can be found in the text,
    // false otherwise
    static matches = (query, text) => {
      let words = QueryMatchingUtils.parseQuery(query);
      let isMatch = true;
      words.forEach(w => {
        isMatch &&= (text?.toLowerCase().indexOf(w) >= 0);
      })
      return isMatch;
    }

    // Out of a set of text items, return a subset that matches
    // all the words in the query
    static getMatchingSubset = (query, items) => {
      // If any item matches the query verbatim (except for casing), return that item
      let identicalItem = items.filter(i => i.toLowerCase() == query.toLowerCase()).slice(0,1);
      if (identicalItem.length == 1) return identicalItem;
      let words = QueryMatchingUtils.parseQuery(query);
      // Identify and build subsets of words contained by each item
      let matchList = {};
      items.forEach(item => {
        if (!item) return;
        matchList[item] = [];
        words.forEach(w => {
          if (item.toLowerCase().indexOf(w) >= 0) {
            matchList[item].push(w);
          }
        })
      })
      // Build the set cover
      let uncoveredWords = words.slice();
      let setCover = [];
      while (uncoveredWords.length) {
        let maxWordsCovered = 0;
        let crtMax = null;
        Object.keys(matchList).forEach(item => {
          let crtCover = matchList[item].filter(w => uncoveredWords.includes(w));
          if (crtCover.length > maxWordsCovered) {
            maxWordsCovered = crtCover.length;
            crtMax = item;
          }
        });
        if (crtMax) {
          setCover.push(crtMax);
          uncoveredWords = uncoveredWords.filter(w => !matchList[crtMax].includes(w));
          delete matchList[crtMax];
        } else {
          break;
        }
      }
      return setCover;
    }

    // Finds a small excerpt of the text that matches the first word of the
    // query. Returns the excerpt padded with "..." left and/or right if applicable.
    static getMatchingExcerpt = (query, text) => {
      let word = QueryMatchingUtils.parseQuery(query)[0];
      let sentences = text.split(/[\.;]\s+/);
      // Find the sentence containing the first word
      let result = sentences.filter(sentence => (sentence.toLowerCase().indexOf(word) >= 0))[0] || "";
      let MAX_CONTEXT = 25;
      let start = 0, end = result.length, position = result.toLowerCase().indexOf(word);
      let prefix = "", suffix = "";
      if (position > MAX_CONTEXT) {
        start = result.indexOf(" ", position - MAX_CONTEXT) + 1;
        prefix = "...";
      }
      if (position + word.length + MAX_CONTEXT < result.length) {
        end = result.substring(0, position + word.length + MAX_CONTEXT).lastIndexOf(" ");
        suffix = "...";
      }
      return prefix + result.substring(start, end) + suffix;
    }
}
