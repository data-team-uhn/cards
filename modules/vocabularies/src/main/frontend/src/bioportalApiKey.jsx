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
  Dialog,
  DialogTitle,
  DialogActions,
  DialogContent,
  IconButton,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";

import makeStyles from '@mui/styles/makeStyles';

import { styled } from '@mui/material/styles';

import React, {useEffect, useContext} from "react";
import SettingsIcon from '@mui/icons-material/Settings';
import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

const PREFIX = 'bioportalApiKey';

const classes = {
  dialogTitle: `${PREFIX}-dialogTitle`,
  vocabularyAction: `${PREFIX}-vocabularyAction`,
  noKeyInfo: `${PREFIX}-noKeyInfo`,
  settingIcon: `${PREFIX}-settingIcon`
};

// TODO jss-to-styled codemod: The Fragment root was replaced by div. Change the tag if needed.
const Root = styled('div')((
  {
    theme
  }
) => ({
  [`& .${classes.dialogTitle}`]: {
    marginRight: theme.spacing(5)
  },

  [`& .${classes.vocabularyAction}`]: {
    margin: theme.spacing(1)
  },

  [`& .${classes.noKeyInfo}`]: {
    padding: theme.spacing(1, 0)
  },

  [`& .${classes.settingIcon}`]: {
    marginTop: theme.spacing(-0.5)
  }
}));

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

export function BioPortalApiKey(props) {
  const { bioPortalApiKey, updateKey } = props;
  const globalLoginDisplay = useContext(GlobalLoginContext);


  /* User input api key */
  const [customApiKey, setCustomApiKey] = React.useState('');
  const [displayPopup, setDisplayPopup] = React.useState(false);

  // function to create / edit node
  function addNewKey() {
    const URL = `/libs/cards/conf/BioportalApiKey`;
    var request_data = new FormData();
    request_data.append('key', customApiKey);
    fetchWithReLogin(globalLoginDisplay, URL, { method: 'POST', body: request_data })
      .then((response) => response.ok ? response : Promise.reject(response))
      .then((data) => {
          updateKey(customApiKey);
          setDisplayPopup(false);
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
      }, () => {
        updateKey(false);
    });
  }, [bioPortalApiKey])

  let getBioportalKeyInfo = (enableEdit) => {
    return (
        <TextField
          InputProps={{
            readOnly: !enableEdit,
          }}
          variant={enableEdit ? "outlined" : "filled" }
          onChange={(evt) => {setCustomApiKey(evt.target.value)}}
          value={customApiKey}
          name="customApiKey"
          label={ enableEdit ? "Enter new Bioportal API key:" : "Bioportal API key:" }
          fullWidth={true}
        />
    );
  }

  return (
    <Root>
      <Grid item>
        <Typography variant="h6">
          Find on <a href="https://bioportal.bioontology.org/" target="_blank">BioPortal</a>
          { bioPortalApiKey &&
            <Tooltip title="Change BioPortal API key">
              <IconButton onClick={() => {setDisplayPopup(true)}} className={classes.settingIcon} size="large">
                <SettingsIcon/>
              </IconButton>
            </Tooltip>
          }
        </Typography>
      </Grid>

      { !bioPortalApiKey && <>
         <Grid item className={classes.noKeyInfo}>
           <Typography>Your system does not have a <a href="https://bioportal.bioontology.org/help#Getting_an_API_key" target="_blank">Bioportal API Key</a> configured.</Typography>
           <Typography>Without an API key, you cannot access Bioportal services such as listing and installing vocabularies.</Typography>
         </Grid>
        <Grid item>
          <Grid container
            direction="row"
            alignItems="center"
            justify="space-between"
            alignContent="space-between"
            spacing={2}
          >
            <Grid item xs={10}>
              { getBioportalKeyInfo(!bioPortalApiKey) }
            </Grid>
            <Grid item xs={2}>
              <Button color="primary" variant="contained" onClick={() => {addNewKey()}}>Submit</Button>
            </Grid>
          </Grid>
        </Grid>
      </> }

      <Dialog onClose={() => {setDisplayPopup(false)}} open={displayPopup} maxWidth="xs" fullWidth>
         <DialogTitle>
           Change BioPortal API key
         </DialogTitle>
         <DialogContent dividers>
           { getBioportalKeyInfo(true) }
          </DialogContent>
          <DialogActions>
            <Button color="primary" variant="contained" className={classes.vocabularyAction} onClick={() => {addNewKey()}}>Update</Button>
            <Button variant="contained" className={classes.vocabularyAction} onClick={() => {setDisplayPopup(false)}}>Cancel</Button>
          </DialogActions>
      </Dialog>
    </Root>
  );
}
