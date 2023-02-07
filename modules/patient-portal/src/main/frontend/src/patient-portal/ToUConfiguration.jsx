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
    FormHelperText,
    List,
    ListItem,
    TextField,
} from '@mui/material';
import { makeStyles } from '@mui/styles';
import AdminConfigScreen from "../adminDashboard/AdminConfigScreen.jsx";
import MarkdownText from "../questionnaireEditor/MarkdownText";

const useStyles = makeStyles(theme => ({
  text: {
    display: "block",
  },
}));

function ToUConfiguration() {
  const classes = useStyles();
  const [ acceptanceRequired, setAcceptanceRequired ] = useState(false);
  const [ title, setTitle ] = useState();
  const [ text, setText ] = useState();
  const [ version, setVersion ] = useState();
  const [ hasChanges, setHasChanges ] = useState(true);

  // Read the ToU properties from the saved configuration
  let readToUData = (json) => {
    setAcceptanceRequired(json.acceptanceRequired || false);
    setTitle(json.title || "");
    setVersion(json.version || "");
    setText(json.text || "");
  }

  let buildConfigData = (formData) => {
    formData.append('acceptanceRequired', acceptanceRequired);
    formData.append('title', title);
    formData.append('version', version);
    formData.append('text', text);
  }

  return (
    <AdminConfigScreen
      title="Patient Portal Terms of Use"
      configPath="/Survey/TermsOfUse"
      configTemplate={{"acceptanceRequired" : false, "title" : "", "version" : "", "text" : ""}}
      onConfigFetched={readToUData}
      hasChanges={hasChanges}
      buildConfigData={buildConfigData}
      onConfigSaved={() => setHasChanges(false)}
      >
       { /* Wait for the text state to be set before displaying anything, as MDEditor sometimes gets stuck with an empty value */ }
       { typeof(text) != 'undefined' &&
          <List>
            <ListItem key="title">
              <TextField
                InputLabelProps={{ shrink: true }}
                variant="standard"
                fullWidth
                id="title"
                name="title"
                type="text"
                label="Title"
                value={title}
                onChange={(event) => { setTitle(event.target.value); setHasChanges(true); }}
              />
            </ListItem>
            <ListItem key="version">
              <TextField
                InputLabelProps={{ shrink: true }}
                variant="standard"
                id="version"
                name="version"
                type="version"
                label="Version"
                value={version}
                onChange={(event) => { setVersion(event.target.value); setHasChanges(true); }}
                style={{'width' : '250px'}}
              />
            </ListItem>
            <ListItem key="text" className={classes.text}>
              <FormHelperText>Text</FormHelperText>
              <MarkdownText
                value={text}
                height={350}
                onChange={value => { setText(value); setHasChanges(true); }}
              />
            </ListItem>
            <ListItem key="acceptanceRequired">
              <FormControlLabel
                control={
                  <Checkbox
                    checked={acceptanceRequired}
                    onChange={(event) => { setAcceptanceRequired(event.target.checked); setHasChanges(true); }}
                    name="acceptanceRequired"
                  />
                }
                label="Patients must accept the Terms of Use before using the portal"
              />
            </ListItem>
          </List>
        }
    </AdminConfigScreen>
  );
}

export default ToUConfiguration;
