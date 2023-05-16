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
import React from "react";
import PropTypes from "prop-types";

import VocabularyBrowser from "./VocabularyBrowser.jsx";
import { REST_URL, MakeRequest } from "./util.jsx";
import ResourceQuery, { MAX_RESULTS } from "../resourceQuery/ResourceQuery";
import QueryMatchingUtils from "../resourceQuery/QueryMatchingUtils";


// Component that renders a search bar for vocabulary terms.
//
// Required arguments:
//  clearOnClick: Whether selecting an option will clear the search bar (default: true)
//  onClick: Callback when the user clicks on this element
//  focusAfterSelecting: focus after selecting (default: true)
//  questionDefinition: Object describing the Vocabulary Question for which this suggested input is displayed
//
// Optional arguments:
//  disabled: Boolean representing whether or not this element is disabled
//  variant: Adds the label to the search wrapper, values: default|labeled, defaulting on default
//  isNested: If true, restyles the element to remove most padding and apply a negative margin for better nesting
//  placeholder: String to display as the input element's placeholder
//  value: String to use as the input element value
//  onChange: Callback in term input change event
//  enableSelection: Boolean enabler for term selection from vocabulary tree browser
//  initialSelection: Existing answers
//  onRemoveOption: Function to remove added answer
//
function VocabularyQuery(props) {
  const { questionDefinition } = props;

  // Make sequential requests to source vocabularies
  // Callback onSuccess/onFailure when all requests have responded
  let makeMultiRequest = (queue, input, statuses, prevData, onSuccess, onFailure) => {
    // Get vocabulary to search through
    var selectedVocab = queue.pop();
    if (selectedVocab === undefined) {
      // Finished making all the requests and received all responses
      onFetchDone(statuses, prevData, onSuccess, onFailure);
      return;
    }
    var url = new URL(`./${selectedVocab}.search.json`, REST_URL);
    url.searchParams.set("suggest", input.replace(/[^\w\s]/g, ' '));

    //Are there any filters that should be associated with this request?
    if (questionDefinition?.vocabularyFilters?.[selectedVocab]) {
      var filter = questionDefinition.vocabularyFilters[selectedVocab].map((category) => {
        return (`term_category:${category}`);
      }).join(" OR ");
      url.searchParams.set("customFilter", `(${filter})`);
    }

    MakeRequest(url, (status, data) => {
      statuses[selectedVocab] = status;
      makeMultiRequest(queue, input, statuses, prevData.concat(!status && data && data['rows'] ? data['rows'] : []), onSuccess, onFailure);
    });
  }

  // Fetch suggestions for the given input
  let fetchSuggestions = (input, onSuccess, onFailure) => {
    var vocabQueue = questionDefinition.sourceVocabularies.slice();
    makeMultiRequest(vocabQueue, input, {}, [], onSuccess, onFailure);
  }

  // Process the statuses from all the requests to call the appropriate handler
  let onFetchDone = (statuses, data, onSuccess, onFailure) => {
    var allRequestsFailed = Object.keys(statuses).filter(vocab => !statuses[vocab]).length == 0;
    var allRequestsSucceded = Object.keys(statuses).filter(vocab => statuses[vocab]).length == 0;

    if (!allRequestsFailed && !allRequestsSucceded) {
      data.splice(0, 0, {
        error: true,
        message: "Some answer suggestions for this question could not be loaded"
      })
    }
    Object.keys(statuses).filter(vocab => statuses[vocab]).map( vocab => {
      console.error("Cannot load answer suggestions from " + vocab);
    });
    if (!allRequestsFailed) {
      onSuccess({rows: data.slice(0, MAX_RESULTS)});
    } else {
      onFailure();
    }
  }

  //
  let formatSuggestionData = (element, query) => {
    let suggestion = {
      "@path" : element["@path"],
      matchedFields : []
    };
    var name = element["label"] || element["name"] || element["identifier"];
    var synonyms = element["synonym"] || element["has_exact_synonym"] || [];
    var definition = Array.from(element["def"] || element["description"] || element["definition"] || [])[0] || "";

    suggestion.label = name;
    if (name.toLowerCase() == query.toLowerCase() || synonyms.find(s => s.toLowerCase() == query.toLowerCase())) {
      suggestion.isPerfectMatch = true;
    }

    // Display an existing synonym or definition if the user's search query doesn't
    // match the term's label but matches that synonym/definition
    // TODO: this logic will have to be revisited once vocabulary indexing is improved to
    // acount for typos
    if (!QueryMatchingUtils.matches(query, name)) {
      suggestion.matchedFields = QueryMatchingUtils.getMatchingSubset(query, synonyms);
      if (!suggestion.matchedFields.length && QueryMatchingUtils.matches(query, definition)) {
         suggestion.matchedFields.push(QueryMatchingUtils.getMatchingExcerpt(query, definition));
      }
    }
    return suggestion;
  }

  return (
      <ResourceQuery
        {... props}
        infoDisplayer={questionDefinition?.enableVocabularyBrowser ? VocabularyBrowser : undefined}
        fetchSuggestions={fetchSuggestions}
        formatSuggestionData={formatSuggestionData}
      />
    );
}

VocabularyQuery.propTypes = {
    clearOnClick: PropTypes.bool.isRequired,
    onClick: PropTypes.func.isRequired,
    focusAfterSelecting: PropTypes.bool.isRequired,
    disabled: PropTypes.bool,
    variant: PropTypes.string,
    isNested: PropTypes.bool,
    placeholder: PropTypes.string,
    value: PropTypes.string,
    questionDefinition: PropTypes.object.isRequired,
    onChange: PropTypes.func,
    enableSelection: PropTypes.bool,
    initialSelection: PropTypes.array,
    onRemoveOption: PropTypes.func
};

VocabularyQuery.defaultProps = {
  clearOnClick: true,
  focusAfterSelecting: true,
  variant: 'default'
};

export default VocabularyQuery;
