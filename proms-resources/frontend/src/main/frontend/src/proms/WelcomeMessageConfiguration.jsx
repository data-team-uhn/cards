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
    List,
    ListItem,
	Typography
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import MarkdownText from "../questionnaireEditor/MarkdownText";
import FormattedText from "../components/FormattedText.jsx";

const useStyles = makeStyles(theme => ({
  textField: {
    minWidth: "250px",
    paddingBottom: theme.spacing(2),
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
        <form onSubmit={handleSubmit}>
          <List>
            <ListItem key="form">
              {welcomeMessage != undefined && <MarkdownText value={welcomeMessage} height={350} onChange={value => { event.preventDefault(); setWelcomeMessage(value); }} />}
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
