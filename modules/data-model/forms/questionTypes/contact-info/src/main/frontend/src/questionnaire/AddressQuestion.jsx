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

import { useEffect, useRef, useState } from "react";

import { TextField } from "@mui/material";
import PropTypes from "prop-types";

import Autocomplete from "../components/Autocomplete";
import { checkPropTypes } from "../propTypes";
import questionEditorHints from './AddressQuestion-editor-hints.json';
import questionEditorConfig from './AddressQuestion-editor.json';
import Answer from "./Answer";
import Question from "./Question";
import StyledTextQuestion from "./TextQuestion";


let googleApiKey;
const APIKEY_SERVLET_URL = "/libs/cards/conf/GoogleApiKey.googleApiKey";
fetch(APIKEY_SERVLET_URL)
  .then((response) => response.ok ? response.json() : Promise.reject(response))
  .then((keyJson) => {
    if (!keyJson.apikey) {
      // Not having a key configured is a valid setup, the question just falls back to a plain text field,
      // so this isn't an error
      console.log("No Google API key configured, address autocompletion is disabled");
      return;
    }
    googleApiKey = keyJson.apikey;
  })
  .catch((error) => {
    console.error("Error fetching GoogleApiKey node: " + error);
  });


// Loads the Google Maps API, at most once per page, and resolves with its Places library.
// Google expects the `loading=async` bootstrap parameter, and warns about suboptimal loading without it.
// Since the script then only bootstraps the API and fetches the requested libraries afterwards, its load
// event comes too early to be of any use; the only signal that the API is ready is the callback it invokes.
const MAPS_API_URL = "https://maps.googleapis.com/maps/api/js";
const MAPS_API_CALLBACK = "cardsGoogleMapsApiLoaded";
let placesLibraryPromise;

const loadPlacesLibrary = () => {
  placesLibraryPromise ||= new Promise((resolve, reject) => {
    window[MAPS_API_CALLBACK] = resolve;
    const script = document.createElement("script");
    script.async = true;
    script.src = `${MAPS_API_URL}?${new URLSearchParams({
      key: googleApiKey,
      libraries: "places",
      loading: "async",
      callback: MAPS_API_CALLBACK,
    })}`;
    script.onerror = () => reject(new Error("the Google Maps API script could not be loaded"));
    document.head.appendChild(script);
  }).then(() => google.maps.places);
  return placesLibraryPromise;
};

// Every request for suggestions is billed, so wait for a pause in the typing before asking Google.
// The same delay as the other search inputs in CARDS.
const SUGGESTION_DELAY = 500;

// The legacy API had an "address" type collection; the new one only has `(regions)` and `(cities)`, so the
// individual place types that together make up a precise postal address have to be listed. At most 5 fit.
const ADDRESS_TYPES = ["street_address", "subpremise", "premise", "route"];

// Component that renders a postal address question with suggestions powered by the Google Places API.
//
// Sample usage:
//
// <AddressQuestion
//   questionDefinition={{
//     text: "Please enter the address",
//     countries: "ca",
//     searchPlacesAround: '{"east": -79.3, "west": -79.5, "north": 43.7, "south": 43.6}',
//   }}
//   />
function AddressQuestion(props) {
  checkPropTypes(AddressQuestion, props);
  const { existingAnswer, pageActive, questionDefinition, ...rest } = props;

  const defaultValue = questionDefinition.defaultValue;
  let currentStartValue = existingAnswer && existingAnswer[1].value || defaultValue || "";
  // The answer, which is whatever is in the field: either free text or the address of a picked suggestion
  const [address, setAddress] = useState(currentStartValue);
  // Only what the user typed, so that filling the field in from a suggestion doesn't ask for more suggestions
  const [typedAddress, setTypedAddress] = useState("");
  const [suggestions, setSuggestions] = useState([]);
  const [isValidApi, setIsValidApi] = useState(true);
  // A session token groups the keystrokes of one search and the selection they lead to into a single billable
  // autocomplete session. Fetching the selected place's details closes the session, so the token is dropped
  // then and the next search starts a new one.
  const sessionToken = useRef(null);
  // Set when Google refuses a request, for example because the Places API isn't enabled for this key, so that
  // the failure is reported once instead of on every keystroke. The field remains usable as free text.
  const suggestionsFailed = useRef(false);

  const countries = questionDefinition.countries?.split(/\s*,\s*/) || undefined;
  let searchPlacesAround = undefined;
  try {
    if (questionDefinition.searchPlacesAround) {
      searchPlacesAround = JSON.parse(questionDefinition.searchPlacesAround);
    }
  } catch (e) {
    // No bounds
  }

  // Load the API up front, so that a key that doesn't work falls back to a plain text question before the
  // user starts typing rather than in the middle of it
  useEffect(() => {
    loadPlacesLibrary().catch((error) => {
      console.error("Address autocompletion is disabled: " + error);
      setIsValidApi(false);
    });
  }, []);

  useEffect(() => {
    if (!typedAddress || suggestionsFailed.current) {
      setSuggestions([]);
      return;
    }
    let discarded = false;
    const timer = setTimeout(() => {
      loadPlacesLibrary()
        .then(({ AutocompleteSessionToken, AutocompleteSuggestion }) => {
          sessionToken.current ||= new AutocompleteSessionToken();
          return AutocompleteSuggestion.fetchAutocompleteSuggestions({
            input: typedAddress,
            sessionToken: sessionToken.current,
            includedPrimaryTypes: ADDRESS_TYPES,
            ...(countries && { includedRegionCodes: countries }),
            ...(searchPlacesAround && { locationBias: searchPlacesAround }),
          });
        })
        .then((response) => {
          if (!discarded) {
            setSuggestions(response.suggestions);
          }
        })
        .catch((error) => {
          suggestionsFailed.current = true;
          console.error("Cannot fetch address suggestions: " + error);
          if (!discarded) {
            setSuggestions([]);
          }
        });
    }, SUGGESTION_DELAY);
    return () => {
      discarded = true;
      clearTimeout(timer);
    };
  }, [typedAddress]);

  // Suggestions carry only the text that Google displays in the dropdown, so the address itself has to be
  // fetched separately once one is picked
  const selectSuggestion = (suggestion) => {
    const prediction = suggestion.placePrediction;
    setAddress(prediction.text.toString());
    setSuggestions([]);
    prediction.toPlace().fetchFields({ fields: ["formattedAddress"] })
      .then((response) => {
        if (response.place.formattedAddress) {
          setAddress(response.place.formattedAddress);
        }
      })
      .catch((error) => {
        console.error("Cannot fetch the address of the selected place: " + error);
      })
      .finally(() => {
        sessionToken.current = null;
      });
  };

  // If google API authentication problem emerges due to to the invalid key or key with disabled Places service
  useEffect(() => {
    window.gm_authFailure = () => {
      console.error("Error in Google API authentication");
      setIsValidApi(false);
    };
    return () => {
      delete window.gm_authFailure;
    };
  }, []);

  if (!isValidApi) {
    return <StyledTextQuestion {...props} />
  }

  return (
    <Question
      disableInstructions
      {...props}
    >
      <Autocomplete
        freeSolo
        fullWidth
        options={suggestions}
        // Google has already matched the input, so every suggestion it returned is worth showing
        filterOptions={(options) => options}
        getOptionLabel={(option) => typeof option === "string" ? option : option.placePrediction.text.toString()}
        inputValue={address}
        onInputChange={(event, value, reason) => {
          setAddress(value);
          // A "reset" is the field being filled in from a picked suggestion, not the user searching
          if (reason !== "reset") {
            setTypedAddress(value);
          }
        }}
        onChange={(event, value) => {
          if (value && typeof value !== "string") {
            selectSuggestion(value);
          }
        }}
        renderOption={({ key, ...optionProps }, option) =>
          // Two suggestions can share the same text, so the place identifier is the only reliable key
          <li {...optionProps} key={option.placePrediction.placeId}>
            { option.placePrediction.text.toString() }
          </li>
        }
        renderInput={(params) =>
          <TextField
            {...params}
            variant="standard"
            slotProps={{
              // Keep the browser's own autofill dropdown from covering the suggestions
              htmlInput: { ...params.inputProps, autoComplete: "new-password" },
            }}
          />
        }
      />
      <Answer
        answers={[["value", address]]}
        questionDefinition={questionDefinition}
        answerNodeType="cards:AddressAnswer"
        valueType="String"
        existingAnswer={existingAnswer}
        pageActive={pageActive}
        {...rest}
      />
    </Question>);
}

AddressQuestion.propTypes = {
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    countries: PropTypes.string,
  }).isRequired,
};

AddressQuestion.canProcess = (questionDefinition) => {
  if (questionDefinition.dataType === "address" && googleApiKey) {
    return [AddressQuestion, 50];
  }
};

AddressQuestion.questionEditorConfig = questionEditorConfig;
AddressQuestion.questionEditorHints = questionEditorHints;

export default AddressQuestion;
