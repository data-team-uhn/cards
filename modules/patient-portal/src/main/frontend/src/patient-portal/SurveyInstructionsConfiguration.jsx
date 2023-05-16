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
import React, { useEffect, useState } from 'react';
import {
    Checkbox,
    FormControlLabel,
    List,
    ListItem,
    TextField,
    Typography
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import WelcomeMessageConfiguration from "./WelcomeMessageConfiguration.jsx";
import AdminConfigScreen from "../adminDashboard/AdminConfigScreen.jsx";
import { camelCaseToWords } from "../questionnaireEditor/LabeledField.jsx";

export const SURVEY_INSTRUCTIONS_PATH = "/Survey/SurveyInstructions";
export const DEFAULT_INSTRUCTIONS = {
  noEventsMessage: "We could not find any pending surveys to fill out.",
  noSurveysMessage: "You have no pending surveys to fill out.",
  surveyDraftInfo: "If you close your browser window before finishing the survey, your answers will be automatically saved.  \nYou can return to the survey to complete and submit it by following the link you received in your invitation email."
};

const useStyles = makeStyles(theme => ({
  formEntries: {
    "& .MuiListItem-root:not(:first-child) .MuiTypography-h6": {
      marginTop: theme.spacing(3),
    },
  },
}));

function SurveyInstructionsConfiguration() {
  const classes = useStyles();

  const [ surveyInstructions, setSurveyInstructions ] = useState();
  const [ hasChanges, setHasChanges ] = useState(false);

  const labels = {
    welcomeMessage: ["welcomeMessage"],
    eventSelectionScreen: ["noEventsMessage", "eventSelectionMessage"],
    startScreen: [ "enableStartScreen", "eventLabel", "noSurveysMessage", "surveyIntro", "surveyDraftInfo" ],
    reviewScreen: ["enableReviewScreen"],
    summaryScreen: [ "disclaimer", "summaryInstructions", "interpretationInstructions" ]
  };

  let buildConfigData = (formData) => {
    for (let key of Object.keys(surveyInstructions)) {
      !key.startsWith("jcr:") && formData.append(key, surveyInstructions[key] || getUnsetValue(key));
    }
  }

  let getUnsetValue = (key) => (key?.startsWith("enable") ? false : "");

  return (
      <AdminConfigScreen
        title="Patient Portal Survey Instructions"
        configPath={SURVEY_INSTRUCTIONS_PATH}
        configTemplate={Object.values(labels).flat().reduce((t, k) => ({...t, [k]: getUnsetValue(k)}), {})}
        onConfigFetched={setSurveyInstructions}
        hasChanges={hasChanges}
        buildConfigData={buildConfigData}
        onConfigSaved={() => setHasChanges(false)}
        >
          <List className={classes.formEntries}>
            { Object.keys(labels).map(category => { return (<>
              <ListItem key={category}>
                <Typography variant="h6">{camelCaseToWords(category)}</Typography>
              </ListItem>
              { labels[category].map(key => { return (
                <ListItem key={key+category}>
                  { key == "welcomeMessage" ?
                      <WelcomeMessageConfiguration
                        welcomeMessage={surveyInstructions?.[key]}
                        onChange={(text) => {
                          setSurveyInstructions({...surveyInstructions, [key]: text});
                          setHasChanges(true);
                        }}
                      />
                    :
                    key.startsWith("enable") ?
                      <FormControlLabel control={
                        <Checkbox
                          checked={!!(surveyInstructions?.[key])}
                          onChange={event => {
                            setSurveyInstructions({...surveyInstructions, [key]: !!event.target.checked});
                            setHasChanges(true);
                          }}
                        />}
                        label={camelCaseToWords(key)}
                      />
                    :
                      <TextField
                        multiline
                        minRows={3}
                        InputLabelProps={{ shrink: true }}
                        variant="outlined"
                        id={key}
                        name={key}
                        type="text"
                        label={camelCaseToWords(key)}
                        value={surveyInstructions?.[key] || ""}
                        placeholder={DEFAULT_INSTRUCTIONS[key] || ""}
                        onChange={(event) => {
                           setSurveyInstructions({...surveyInstructions, [key]: event.target.value});
                           setHasChanges(true);
                        }}
                        fullWidth
                      />
                  }
                </ListItem>)
              })}
            </>)
          })}
        </List>
      </AdminConfigScreen>
  );
}

export default SurveyInstructionsConfiguration;
