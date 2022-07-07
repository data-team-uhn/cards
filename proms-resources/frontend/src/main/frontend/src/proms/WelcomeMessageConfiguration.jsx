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
import React, { useState, useEffect } from 'react';
import {
    Alert,
    Card,
    CardContent,
    CardHeader,
    Grid,
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import MarkdownText from "../questionnaireEditor/MarkdownText";
import FormattedText from "../components/FormattedText.jsx";
import AdminConfigScreen from "../adminDashboard/AdminConfigScreen.jsx";

const useStyles = makeStyles(theme => ({
  editorContainer: {
    padding: theme.spacing(2, 1),
    "& > .MuiGrid-item > *": {
      height: "100% !important",
    },
    "& .w-md-editor-content": {
      height: "calc(100% - 29px) !important",
    },
    "& .w-md-editor-toolbar ul:last-child > li:not(:last-child), .w-md-editor-bar": {
      display: "none",
    },
  },
  previewHeader: {
    borderBottom: "1px solid #dfdfe0",
    backgroundColor: "#fbfbfb",
    padding: theme.spacing(0, 0, 0, 2),
    height: "30px"
  },
}));

function WelcomeMessageConfiguration() {
  const classes = useStyles();

  const [ welcomeMessage, setWelcomeMessage ] = useState();
  const [ hasChanges, setHasChanges ] = useState(false);

  const appName = document.querySelector('meta[name="title"]')?.content;

  // Read the welcome message from the saved configuration
  let readWelcomeMessage = (configJson) => {
    setWelcomeMessage(configJson.text || "");
  }

  let buildConfigData = (formData) => {
    formData.append('text', welcomeMessage);
  }

  useEffect(() => {
    setHasChanges(true);
  }, [welcomeMessage]);

  return (
      <AdminConfigScreen
        title="Patient Portal Welcome Message"
        configPath="/Proms/WelcomeMessage"
        onConfigFetched={readWelcomeMessage}
        hasChanges={hasChanges}
        buildConfigData={buildConfigData}
        onConfigSaved={() => setHasChanges(false)}
      >
        <Alert severity="info">
          Use APP_NAME to refer to the name configured for the application.
          On the Patient identification screen, all occurrences of APP_NAME will appear as {appName}.
        </Alert>
        { /* Wait for the welcomeMessage state to be set before displaying anything, as MDEditor sometimes gets stuck with an empty value */ }
        { typeof(welcomeMessage) != 'undefined' &&
          <Grid
            container
            spacing={2}
            justifyContent="center"
            alignItems="stretch"
            className={classes.editorContainer}
          >
            <Grid item xs={12} md={6}>
              <MarkdownText value={welcomeMessage} height={350} preview="edit" visiableDragbar="false" onChange={setWelcomeMessage} />
            </Grid>
            <Grid item xs={12} md={6}>
              <Card>
                <CardHeader
                  className={classes.previewHeader}
                  title="Preview"
                  titleTypographyProps={{variant: "overline"}}
                />
                <CardContent>
                  <FormattedText variant="body2">
                    { welcomeMessage?.replaceAll("APP_NAME", appName) }
                  </FormattedText>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        }
      </AdminConfigScreen>
  );
}

export default WelcomeMessageConfiguration;
