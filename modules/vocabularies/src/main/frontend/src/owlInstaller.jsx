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

import React, { useState, useContext } from "react";

import {
  Button,
  CircularProgress,
  Grid,
  TextField,
  Tooltip,
  Typography
} from "@mui/material";

import makeStyles from '@mui/styles/makeStyles';

import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

const Status = require("./statusCodes.json");

const useStyles = makeStyles(theme => ({
  buttonProgress: {
    top: "50%",
    left: "50%",
    position: "absolute",
    marginTop: -12,
    marginLeft: -12
  },
  install: {
    background: theme.palette.success.main,
    color: theme.palette.success.contrastText,
    "&:hover": {
      background: theme.palette.success.dark
    }
  },
  failed: {
    background: theme.palette.error.main,
    color: theme.palette.error.contrastText,
    "&:hover": {
      background: theme.palette.error.dark
    }
  },
  installingColor: {
    color: theme.palette.success.main
  },
  uninstallingColor: {
    color: theme.palette.error.main
  },
  update: {
    background: theme.palette.warning.main,
    color: theme.palette.warning.contrastText,
    "&:hover": {
      background: theme.palette.warning.dark
    }
  },
}));

export default function OwlInstaller(props) {

  const classes = useStyles();
  const globalLoginDisplay = useContext(GlobalLoginContext);

  let [ phase, setPhase ] = useState("install");
  let [ owlSelected, setOwlSelected ] = useState("Select File");
  let [ owlIdentifier, setOwlIdentifier ] = useState("");
  let [ owlName, setOwlName ] = useState("");
  let [ owlVersion, setOwlVersion ] = useState("");

  let disableInstall = (phase != "install")
      || (owlSelected == "Select File")
      || (owlIdentifier == "")
      || (owlName == "")
      || (owlVersion == "");

  let handleSubmit = (event) => {
    setPhase("installing");
    event.preventDefault();

    const form = event.target;
    fetchWithReLogin(globalLoginDisplay, form.action, {
      method: form.method,
      body: new FormData(form)
    })
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((res) => {
        setPhase("install");
        setOwlSelected("Select File");
        setOwlIdentifier("");
        setOwlName("");
        setOwlVersion("");
        props.reloadVocabList();
    })
    .catch((err) => { setPhase("failed") });
  }

  return(
    <React.Fragment>
      <Grid item>
        <Typography variant="h6">
          Install from local file
        </Typography>
      </Grid>
      <Grid item>
      <form
        action="/Vocabularies?source=fileupload&overwrite=true"
        method="POST"
        encType="multipart/form-data"
        onSubmit={handleSubmit}
      >
        <Grid container
          direction="row"
          alignItems="center"
          spacing={1}
        >
          <Grid item>
            <label htmlFor="owl-file">
              <input
                style={{ display: 'none' }}
                id="owl-file"
                name="filename"
                onChange={() => {setOwlSelected("File Selected")}}
                type={(phase == "install") ? "file" : "button"}
              />
              <Tooltip title={(phase == "install") ? "Select a vocabulary to install" : ""}>
                <Button
                  disabled={(phase == "installing")}
                  variant="contained"
                  onClick={() => {setPhase("install")}}
                  color="primary"
                  component="span">
                    {owlSelected}
                </Button>
              </Tooltip>
            </label>
          </Grid>

          <Grid item>
            <TextField
                 disabled={(phase == "installing") || (owlSelected == "Select File")}
                 variant="standard"
                 error={(phase != "installing") && (owlIdentifier == "") && (owlSelected != "Select File")}
                 onChange={(evt) => {
                   setOwlIdentifier(evt.target.value);
                   setPhase("install");
                 }}
                 value={owlIdentifier}
                 name="identifier"
                 label="Identifier"
                 size="small"
             />
          </Grid>
          <Grid item>
            <TextField
                 disabled={(phase == "installing") || (owlSelected == "Select File")}
                 variant="standard"
                 error={(phase != "installing") && (owlName == "") && (owlSelected != "Select File")}
                 onChange={(evt) => {
                   setOwlName(evt.target.value);
                   setPhase("install");
                 }}
                 value={owlName}
                 name="vocabName"
                 label="Name"
                 size="small"
             />
          </Grid>
          <Grid item>
            <TextField
                 disabled={(phase == "installing") || (owlSelected == "Select File")}
                 variant="standard"
                 error={(phase != "installing") && (owlVersion == "") && (owlSelected != "Select File")}
                 onChange={(evt) => {
                   setOwlVersion(evt.target.value);
                   setPhase("install");
                 }}
                 value={owlVersion}
                 name="version"
                 label="Version"
                 size="small"
             />
          </Grid>

          <Grid item>
            <label htmlFor="owl-install">
              <input
                style={{ display: 'none' }}
                id="owl-install"
                name="owl-install"
                type={disableInstall ? "button" : "submit"}
              />
              <Tooltip title={(phase == "install") ? "Install this vocabulary" : ""}>
                <Button
                   variant="contained"
                   color="primary"
                   component="span"
                   disabled={(phase == "installing") || (phase == "install" && disableInstall)}
                   className={classes[phase]}
                >
                  {phase}
                </Button>
              </Tooltip>
            </label>
            { (phase == "installing") &&
               <CircularProgress
                  size={24}
                  className={classes.buttonProgress + " " + classes.installingColor}
                />
             }
          </Grid>
        </Grid>
      </form>
      </Grid>
    </React.Fragment>
  );
}
