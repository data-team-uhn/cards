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

import React, { useState } from "react";
import withStyles from '@mui/styles/withStyles';

import { TextField } from "@mui/material";

import PropTypes from "prop-types";

import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";
import Answer from "./Answer";
import AnswerComponentManager from "./AnswerComponentManager";
import StyledTextQuestion from "./TextQuestion";
import { usePlacesWidget } from "react-google-autocomplete";
import GlobalStyles from '@mui/material/GlobalStyles';


let googleApiKey;
const APIKEY_SERVLET_URL = "/.googleApiKey";
fetch(APIKEY_SERVLET_URL)
  .then((response) => response.ok ? response.json() : Promise.reject(response))
  .then((keyJson) => {
    if (!keyJson.apikey) {
      throw "no API key in APIKEY servlet response";
    }
    googleApiKey = keyJson.apikey;
  })
  .catch((error) => {
    console.error("Error fetching GoogleApiKey node: " + error);
});
let isValidApiKey = true;


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
//   }}
//   />
function AddressQuestion(props) {
  const { existingAnswer, classes, pageActive, questionDefinition, ...rest} = props;

  let currentStartValue = existingAnswer && existingAnswer[1].value || "";
  const [address, setAddress] = useState(currentStartValue);
  const [isValidApi, setIsValidApi] = useState(true);

  const countries = questionDefinition.countries?.split(/\s*,\s*/) || undefined;
  let options = {
    types: ["address"],
    fields: ["formatted_address"]
  };
  if (countries) {
    options.componentRestrictions = { country: countries };
  }
  const { ref: materialRef } = usePlacesWidget({
    apiKey: googleApiKey,
    onPlaceSelected: (place) => setAddress(place.formatted_address),
    options: options,
  });

  // If google API authentication problem emerges due to to the invalid key or key with disabled Places service
  window.gm_authFailure = () => {
    console.error("Error in Google API authentication");
    setIsValidApi(false);
    isValidApiKey = false;
  };

  if (!isValidApiKey || !isValidApi) {
    return <StyledTextQuestion {...props} />
  }

  return (
    <Question
      disableInstructions
      {...props}
      >
      {inputGlobalStyles}
      <TextField
        className={classes.textField}
        multiline
        maxRows={4}
        variant="standard"
        onChange={event => setAddress(event.target.value)}
        value={address}
        inputRef={materialRef}
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
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    countries: PropTypes.string,
  }).isRequired,
};


const StyledAddressQuestion = withStyles(QuestionnaireStyle)(AddressQuestion)
export default StyledAddressQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "address" && googleApiKey) {
    return [StyledAddressQuestion, 50];
  }
});
