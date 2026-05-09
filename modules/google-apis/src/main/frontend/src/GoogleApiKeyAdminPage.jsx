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

import { useState, useEffect, useContext } from "react";

import {
  Alert,
  Button,
  Grid,
  TextField,
} from "@mui/material";

import AdminScreen from "./adminDashboard/AdminScreen.jsx";
import FormattedText from "./components/FormattedText.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "./login/ReLoginDialog.js";

const APIKEY_SERVLET_URL = "/.googleApiKey";

export default function GoogleApiKeyAdminPage() {
  const [ googleApiKey, setGoogleApiKey ] = useState("");
  const [ hasChanges, setHasChanges ] = useState(false);
  const [ error, setError ] = useState();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, APIKEY_SERVLET_URL)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((keyJson) => {
        if (!keyJson.apikey) {
          console.log("No API key in APIKEY servlet response");
        }
        setGoogleApiKey(keyJson.apikey);
      })
      .catch((error) => {
        setError("Error fetching GoogleApiKey node: " + error);
      });
  }, []);

  // function to create / edit node
  function updateKey() {
    const URL = `/libs/cards/conf/GoogleApiKey`;
    let request_data = new FormData();
    request_data.append('key', googleApiKey);
    fetchWithReLogin(globalLoginDisplay, URL, { method: 'POST', body: request_data })
      .then((response) => response.ok ? response : Promise.reject(response))
      .then((data) => {
        // The AddressQuestion won't fetch new key untill the page is reloaded
        location.reload();
      })
      .catch((error) => {
        setError("Error creating GoogleApiKey node: " + error);
      });
  }

  return (
    <AdminScreen title="Google API key configuration">
      <Grid container spacing={5} sx={{ flexDirection: 'column', justifyContent: 'space-around' }}>
        <Grid>
          <FormattedText>A Google API key enables access to Google services such as address autocomplete. You can obtain an API key at https://developers.google.com/maps/documentation/javascript/get-api-key.</FormattedText>
        </Grid>
        { error && <Grid><Alert severity="error">{error}</Alert></Grid> }
        <Grid>
          <Grid container
            spacing={2}
            sx={{ alignItems: 'flex-start', justifyContent: 'space-between', alignContent: 'space-between' }}
          >
            <Grid size={10}>
              <TextField
                size="small"
                variant="outlined"
                onChange={(evt) => {setGoogleApiKey(evt.target.value); setHasChanges(true); setError("");}}
                value={googleApiKey}
                label="Google API key"
                fullWidth
              />
            </Grid>
            <Grid size={2}>
              <Button
                variant="contained"
                disabled={!hasChanges}
                onClick={updateKey}
              >
                Submit
              </Button>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
    </AdminScreen>
  );
}
