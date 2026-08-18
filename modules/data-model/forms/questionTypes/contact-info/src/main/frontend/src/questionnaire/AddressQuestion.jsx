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
import GlobalStyles from '@mui/material/GlobalStyles';
import PropTypes from "prop-types";

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


// Loads the Google Maps API, at most once per page, and reports when it is actually usable.
// Google expects `loading=async`, and warns about suboptimal loading without it. Since the script then only
// bootstraps the API and fetches the requested libraries afterwards, its load event comes too early to be of
// any use; the only reliable signal that the API is ready is the callback that it invokes itself.
const MAPS_API_URL = "https://maps.googleapis.com/maps/api/js";
const MAPS_API_CALLBACK = "cardsGoogleMapsApiLoaded";
let mapsApiPromise;

const loadMapsApi = () => {
  mapsApiPromise ||= new Promise((resolve, reject) => {
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
  });
  return mapsApiPromise;
};


// Easy way to overwrite global CSS styles using theme
// Styling Google Map Autocomplete dropdown list
// see details https://developers.google.com/maps/documentation/javascript/place-autocomplete#style-autocomplete
const inputGlobalStyles = <GlobalStyles
  styles={(theme) => ({
    body: {
      // to remove the "Powered by Google" logo from the bottom of the Google Map Autocomplete dropdown list
      "& .pac-container:after": {
        backgroundImage: "none !important",
        height: 0,
        padding: 0,
        margin: 0,
      },
      "& .pac-item-query": {
        // see https://mui.com/material-ui/customization/default-theme/?expand-path=$.typography
        fontFamily: `${theme.typography.fontFamily}  !important`,
        fontSize: theme.typography.htmlFontSize
      },
      "& .pac-matched": {
        fontWeight: theme.typography.fontWeightRegular,
      },
      "& .pac-item": {
        fontFamily: `${theme.typography.fontFamily} !important`,
        fontSize: theme.typography.htmlFontSize,
        lineHeight: `${theme.spacing(5)} !important`,
      },
      // remove the place pin icon from the dropdown list items
      "& .pac-icon": {
        display : "none",
      }
    }
  })}
/>

// Component that renders a postal address question with suggestions powered by the Goole API.
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
  const [address, setAddress] = useState(currentStartValue);
  const [isValidApi, setIsValidApi] = useState(true);

  const countries = questionDefinition.countries?.split(/\s*,\s*/) || undefined;
  let searchPlacesAround = undefined;
  try {
    if (questionDefinition.searchPlacesAround) {
      searchPlacesAround = JSON.parse(questionDefinition.searchPlacesAround);
    }
  } catch (e) {
    // No bounds
  }
  let options = {
    types: ["address"],
    fields: ["formatted_address"]
  };
  if (countries) {
    options.componentRestrictions = { country: countries };
  }
  if (searchPlacesAround) {
    options.bounds = searchPlacesAround;
  }
  // The suggestions are served by a Google widget bound to the text field below. The widget can only drive a
  // real <input>, which is why that field must not be multiline.
  const inputRef = useRef(null);
  useEffect(() => {
    let discarded = false;
    let listener;
    loadMapsApi()
      .then(() => {
        if (discarded || !inputRef.current) {
          return;
        }
        const autocomplete = new google.maps.places.Autocomplete(inputRef.current, options);
        // Attaching the widget resets the input to autocomplete="off", which Chrome ignores for address
        // fields, so its own autofill dropdown would cover the suggestions. It does honour "new-password".
        inputRef.current.autocomplete = "new-password";
        listener = autocomplete.addListener("place_changed", () => {
          // Accepting the typed text instead of picking a suggestion yields a place without an address
          const selectedAddress = autocomplete.getPlace()?.formatted_address;
          if (selectedAddress) {
            setAddress(selectedAddress);
          }
        });
      })
      .catch((error) => {
        console.error("Address autocompletion is disabled: " + error);
        setIsValidApi(false);
      });
    return () => {
      discarded = true;
      listener?.remove();
    };
  }, []);

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
      {inputGlobalStyles}
      <TextField
        fullWidth
        variant="standard"
        onChange={event => setAddress(event.target.value)}
        value={address}
        inputRef={inputRef}
        slotProps={{
          // Also set declaratively, to cover the window before the widget attaches
          htmlInput: { autoComplete: "new-password" },
        }}
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
