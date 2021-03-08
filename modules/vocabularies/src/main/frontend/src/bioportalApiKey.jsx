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

import {
  Button,
  Grid,
  TextField,
  Typography,
  makeStyles
} from "@material-ui/core";

import React, {useEffect, useContext} from "react";
import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

const APIKEY_SERVLET_URL = "/Vocabularies.bioportalApiKey";

const JSON_KEY = "apikey";

export default function fetchBioPortalApiKey(globalLoginDisplay, func, errorHandler) {
  // Parse the response from our FilterServlet
  let parseKey = (keyJson) => {
    if (!keyJson[JSON_KEY]) {
      throw "no API key in APIKEY servlet response";
    }
    func(keyJson[JSON_KEY]);
  }

  fetchWithReLogin(globalLoginDisplay, APIKEY_SERVLET_URL)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(parseKey)
      .catch(errorHandler);
}


const useStyles = makeStyles(theme => ({
  header: {
    marginTop: theme.spacing(3)
  }
}));

export function BioPortalApiKey(props) {
  const classes = useStyles();
  const { bioPortalApiKey, updateKey } = props;
  const globalLoginDisplay = useContext(GlobalLoginContext);

  /* User input api key */
  const [customApiKey, setCustomApiKey] = React.useState('');
  const [displayChangeKey, setDisplayChangeKey] = React.useState(false);

  // function to create / edit node
  function addNewKey() {
    const URL = `/libs/lfs/conf/BioportalApiKey`;
    var request_data = new FormData();
    request_data.append('key', customApiKey);
    fetchWithReLogin(globalLoginDisplay, URL, { method: 'POST', body: request_data })
      .then((response) => response.ok ? response : Promise.reject(response))
      .then((data) => {
          updateKey(customApiKey);
      })
      .catch((error) => {
        console.error("Error creating BioportalApiKey node: " + error)
      }
    )
  }

  useEffect(() => {
    fetchBioPortalApiKey(globalLoginDisplay,
      (apiKey) => {
        updateKey(apiKey);
        setCustomApiKey(apiKey);
        setDisplayChangeKey(false);
      }, () => {
        updateKey(false);
        setDisplayChangeKey(true);
    });
  }, [bioPortalApiKey])

  return(
    <React.Fragment>
      <Grid item>
        <Typography className={classes.header} variant="h6">
          Find on <a href="https://bioportal.bioontology.org/" target="_blank">BioPortal</a>
        </Typography>
      </Grid>

      { !bioPortalApiKey &&
         <Grid item>
           <Typography>Your system does not have a <a href="https://bioportal.bioontology.org/help#Getting_an_API_key" target="_blank">Bioportal API Key</a> configured.</Typography>
           <Typography>Without an API key, you cannot access Bioportal services such as listing and installing vocabularies.</Typography>
         </Grid>
      }

      <Grid item>
        <Grid container
          direction="row"
          alignItems="center"
          spacing={1}
        >
          <Grid item xs={6}>
            <TextField
              InputProps={{
                readOnly: !displayChangeKey,
              }}
              variant={displayChangeKey ? "outlined" : "filled" }
              onChange={(evt) => {setCustomApiKey(evt.target.value)}}
              value={customApiKey}
              name="customApiKey"
              label={ displayChangeKey ? "Enter new Bioportal API key:" : "Bioportal API key:" }
              fullWidth={true}
            />
          </Grid>
          <Grid item>
            { !displayChangeKey && bioPortalApiKey &&
              <Button variant="contained" color="primary" onClick={() => {setDisplayChangeKey(true);}}>Change</Button>
            }
          </Grid>
          <Grid item>
            { displayChangeKey &&
              <Button color="primary" variant="contained" onClick={() => {addNewKey();}}>Submit</Button>
            }
          </Grid>
          <Grid item>
            { displayChangeKey && bioPortalApiKey &&
              <Button color="default" variant="contained" onClick={() => {setDisplayChangeKey(false);}}>Cancel</Button>
            }
          </Grid>
        </Grid>
      </Grid>
    </React.Fragment>
  );
}
