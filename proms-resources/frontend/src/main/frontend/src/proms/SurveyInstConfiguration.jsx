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
    List,
    ListItem,
    TextField,
    Typography
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import AdminConfigScreen from "../adminDashboard/AdminConfigScreen.jsx";
import { camelCaseToWords } from "../questionnaireEditor/LabeledField.jsx";

export const DEFAULT_INSTRUCTIONS = {
  noSurveysMessage: "You have no pending surveys to fill out"
};

const useStyles = makeStyles(theme => ({
  formEntries: {
    "& .MuiListItem-root:not(:first-child) .MuiTypography-root": {
      marginTop: theme.spacing(3),
    },
  },
}));

function SurveyInstConfiguration() {
  const classes = useStyles();

  const [ surveyInstructions, setSurveyInstructions ] = useState();
  const [ hasChanges, setHasChanges ] = useState(false);

  const labels = {
    startScreen: [ "eventLabel", "noSurveysMessage", "surveyIntro" ],
    summaryScreen: [ "disclaimer", "summaryInstructions", "interpretationInstructions" ]
  };

  // Fetch saved settings for Patient Portal Survey Instructions from the saved configuration
  let readSurveyInstructions = (json) => {
    setSurveyInstructions(json);
  }

  let buildConfigData = (formData) => {
    for (let key of Object.keys(surveyInstructions)) {
      !key.startsWith("jcr:") && formData.append(key, surveyInstructions[key] || "");
    }
  }

  useEffect(() => {
    setHasChanges(true);
  }, [surveyInstructions]);

  return (
      <AdminConfigScreen
        title="Patient Portal Survey Instructions"
        configPath="/Proms/SurveyInstructions"
        onConfigFetched={readSurveyInstructions}
        hasChanges={hasChanges}
        buildConfigData={buildConfigData}
        onConfigSaved={() => setHasChanges(false)}
        >
          <List className={classes.formEntries}>
            { Object.keys(labels).map(category => { return (<>
              <ListItem key={category}>
                <Typography variant="h5">{camelCaseToWords(category)}</Typography>
              </ListItem>
              { labels[category].map(key => { return (
                <ListItem key={key}>
	              <TextField
	                multiline
	                minRows={category == "startScreen" ? 2 : 4}
	                InputLabelProps={{ shrink: true }}
	                variant="outlined"
	                id={key}
	                name={key}
	                type="text"
	                label={camelCaseToWords(key)}
	                value={surveyInstructions ? surveyInstructions[key]|| "" : DEFAULT_INSTRUCTIONS[key] || ""}
	                onChange={(event) => { setSurveyInstructions({...surveyInstructions, [key]: event.target.value}); }}
	                fullWidth
	              />
                </ListItem>)
              })}
            </>)
          })}
        </List>
      </AdminConfigScreen>
  );
}

export default SurveyInstConfiguration;
