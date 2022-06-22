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
import React, { useState } from 'react';
import {
    Button,
    Card,
    CardContent,
    CardHeader,
    Grid,
    List,
    ListItem,
    Typography
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import MarkdownText from "../questionnaireEditor/MarkdownText";
import FormattedText from "../components/FormattedText.jsx";

const useStyles = makeStyles(theme => ({
  previewHeader: {
    borderBottom: "1px solid #dfdfe0",
    backgroundColor: "#fbfbfb",
    padding: theme.spacing(0, 0, 0, 2),
    height: "30px"
  },
  saveButton: {
    marginTop: theme.spacing(3),
  },
}));

function WelcomeMessageConfiguration() {
  const classes = useStyles();

  const [ path, setPath ] = useState();
  const [ welcomeMessage, setWelcomeMessage ] = useState();

  // Status tracking values of fetching/posting the data from/to the server
  const [ error, setError ] = useState();
  const [ isSaved, setIsSaved ] = useState(false);
  const [ fetched, setFetched ] = useState(false);

  const appName = document.querySelector('meta[name="title"]')?.content;

  const theme = useTheme();
  const breakpoint = useMediaQuery(theme.breakpoints.down("sm"));

  // Fetch saved admin config settings
  let getWelcomeMessage = () => {
    fetch('/Proms/WelcomeMessage.json')
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setFetched(true);
        setPath(json["@path"]);
        setWelcomeMessage(json.text || "");
      })
      .catch(setError);
  }

  // Submit function
  let handleSubmit = (event) => {

    // This stops the normal browser form submission
    event && event.preventDefault();

    // Build formData object.
    // We need to do this because sling does not accept JSON, need url encoded data
    let formData = new URLSearchParams();
    formData.append('text', welcomeMessage);

    // Use native fetch, sort like the XMLHttpRequest so no need for other libraries.
    fetch("/Proms/WelcomeMessage",
      {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: formData
      })
      .then((response) => {

        // Important note about native fetch, it does not reject failed
        // HTTP codes, it'll only fail when network error
        // Therefore, you must handle the error code yourself.
        if (!response.ok) {
          throw Error(response.statusText);
        }

        setIsSaved(true);
      })
      .catch(setError);
  }

  if (!fetched) {
    getWelcomeMessage();
  }

  return (
    <Card>
      <CardHeader
        title="Welcome Message text"
        titleTypographyProps={{variant: "h6"}}
      />
      <CardContent>
        {error && <Typography color='error'>{error}</Typography>}
        <Typography>
          In the welcome message, use APP_NAME to refer to the name configured for the application.
          On the Patient identification screen, all occurrences of APP_NAME will appear as {appName}.
        </Typography>
        <form onSubmit={handleSubmit}>
          <List>
            <ListItem key="form">
              { welcomeMessage != undefined &&
                <Grid
                  container
                  spacing={2}
                  direction={breakpoint ? "column" : "row"}
                  justifyContent="center"
                  alignItems="flex-start"
                >
                  <Grid item xs={12} sm={8} md={6}>
                    <MarkdownText value={welcomeMessage} height={350} preview="edit" onChange={value => { event.preventDefault(); setWelcomeMessage(value); }} />
                  </Grid>
                  <Grid item xs={12} sm={8} md={6}>
                    <Card>
                      <CardHeader
                        className={classes.previewHeader}
                        title="Preview"
                        titleTypographyProps={{variant: "overline"}}
                      />
                      <CardContent>
                        <FormattedText>
                          { welcomeMessage?.replaceAll("APP_NAME", appName) }
                        </FormattedText>
                      </CardContent>
                    </Card>
                  </Grid>
                </Grid>
              }
            </ListItem>
            <ListItem key="button">
              <Button
                type="submit"
                disabled={welcomeMessage == undefined}
                variant="contained"
                color="primary"
                size="small"
                className={classes.saveButton}
              >
                { isSaved ? "Saved" : "Save" }
              </Button>
            </ListItem>
          </List>
        </form>
      </CardContent>
    </Card>
  );
}

export default WelcomeMessageConfiguration;
