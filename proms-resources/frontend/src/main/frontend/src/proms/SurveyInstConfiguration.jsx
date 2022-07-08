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

const useStyles = makeStyles(theme => ({
  header: {
    marginTop: theme.spacing(3),
  },
}));

function SurveyInstConfiguration() {
  const classes = useStyles();

  const [ surveyInstructions, setSurveyInstructions ] = useState();
  const [ hasChanges, setHasChanges ] = useState(false);

  const defaults = {
    noSurveysMessage: "You have no pending surveys to fill out"
  };

  // Fetch saved settings for Patient Portal Survey Instructions from the saved configuration
  let readSurveyInstructions = (json) => {
    setSurveyInstructions(json);
  }

  let buildConfigData = (formData) => {
    for (let key of Object.keys(surveyInstructions)) {
      !key.startsWith("jcr:") && formData.append(key, surveyInstructions[key] || defaults[key] || "");
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
          <List>
            <ListItem key="startScreen">
              <Typography variant="h5">Start screen</Typography>
            </ListItem>
            <ListItem key="eventLabel">
              <TextField
                multiline
                minRows={2}
                InputLabelProps={{ shrink: true }}
                variant="outlined"
                id="eventLabel"
                name="eventLabel"
                type="text"
                label="Event Label"
                value={surveyInstructions?.eventLabel}
                onChange={(event) => { setSurveyInstructions({...surveyInstructions, eventLabel: event.target.value}); }}
                fullWidth
              />
            </ListItem>
            <ListItem key="noSurveysMessage">
              <TextField
                multiline
                minRows={2}
                InputLabelProps={{ shrink: true }}
                variant="outlined"
                id="noSurveysMessage"
                name="noSurveysMessage"
                type="text"
                label="No Surveys Message Label"
                value={surveyInstructions?.noSurveysMessage || "You have no pending surveys to fill out"}
                onChange={(event) => { setSurveyInstructions({...surveyInstructions, noSurveysMessage: event.target.value || "You have no pending surveys to fill out"}); }}
                fullWidth
              />
            </ListItem>
            <ListItem key="surveyIntro">
              <TextField
                multiline
                minRows={2}
                InputLabelProps={{ shrink: true }}
                variant="outlined"
                id="surveyIntro"
                name="surveyIntro"
                type="text"
                label="Survey Intro"
                value={surveyInstructions?.surveyIntro}
                onChange={(event) => { setSurveyInstructions({...surveyInstructions, surveyIntro: event.target.value}); }}
                fullWidth
              />
            </ListItem>
            <ListItem key="summaryScreen" >
              <Typography variant="h5" className={classes.header}>Summary screen</Typography>
            </ListItem>
            <ListItem key="disclaimer">
              <TextField
                multiline
                minRows={4}
                InputLabelProps={{ shrink: true }}
                variant="outlined"
                id="disclaimer"
                name="disclaimer"
                type="text"
                label="Disclaimer"
                value={surveyInstructions?.disclaimer}
                onChange={(event) => { setSurveyInstructions({...surveyInstructions, disclaimer: event.target.value}); }}
                fullWidth
              />
            </ListItem>
            <ListItem key="summaryInstructions">
              <TextField
                multiline
                minRows={4}
                InputLabelProps={{ shrink: true }}
                variant="outlined"
                id="summaryInstructions"
                name="summaryInstructions"
                type="text"
                label="Summary Instructions"
                value={surveyInstructions?.summaryInstructions}
                onChange={(event) => { setSurveyInstructions({...surveyInstructions, summaryInstructions: event.target.value}); }}
                fullWidth
              />
            </ListItem>
            <ListItem key="interpretationInstructions">
              <TextField
                multiline
                minRows={4}
                InputLabelProps={{ shrink: true }}
                variant="outlined"
                id="interpretationInstructions"
                name="interpretationInstructions"
                type="text"
                label="Interpretation Instructions"
                value={surveyInstructions?.interpretationInstructions}
                onChange={(event) => { setSurveyInstructions({...surveyInstructions, interpretationInstructions: event.target.value}); }}
                fullWidth
              />
            </ListItem>
          </List>
      </AdminConfigScreen>
  );
}

export default SurveyInstConfiguration;
