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

import React, { useState } from "react";

import { 
  Button,
  CircularProgress,
  makeStyles,
  TextField,
  Tooltip
} from "@material-ui/core";

const Status = require("./statusCodes.json");

const useStyles = makeStyles(theme => ({
  owlInstaller: {
    margin: theme.spacing(1),
    textTransform: "none"
  },
  buttonProgress: {
    top: "50%",
    left: "50%",
    position: "absolute",
    marginTop: -12,
    marginLeft: -12,
    textTransform: "none"
  },
  install: {
    background: theme.palette.success.main,
    color: theme.palette.success.contrastText,
    "&:hover": {
      background: theme.palette.success.dark
    }
  },
  installed: {
    background: theme.palette.success.dark,
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
  wrapper: {
    position: "relative",
    display: "inline-block"
  }
}));

export default function OwlInstaller(props) {

  const classes = useStyles();

  let [ phase, setPhase ] = useState("Install");
  let [ owlSelected, setOwlSelected ] = useState("Select File");
  let [ owlIdentifier, setOwlIdentifier ] = useState("");
  let [ owlName, setOwlName ] = useState("");
  let [ owlVersion, setOwlVersion ] = useState("");

  let handleSubmit = (event) => {
    const form = event.target;
    fetch(form.action, {
      method: form.method,
      body: new FormData(form)
    })
    .then((res) => { if (!res.ok) { throw new Error("Server Ontology Error") } else { setPhase("Installed"); props.updateLocalList("add", {source: "fileupload", version: owlVersion, released: new Date().toISOString(), ontology: {acronym: owlIdentifier, name: owlName}}) } })
    .catch((err) => { setPhase("Failed") });
    setPhase("Installing");
    event.preventDefault();
  }

  return(
    <React.Fragment>
      <form action="/Vocabularies?source=fileupload&overwrite=true" method="POST" encType="multipart/form-data" onSubmit={handleSubmit}>
        <label htmlFor="owl-file">
          <input style={{ display: 'none' }} id="owl-file" name="filename" onChange={() => {setOwlSelected("File Selected")}} type={(phase == "Install") ? "file" : "button"}/>
          <Tooltip title={(phase == "Install") ? "Select a vocabulary to install" : ""}>
            <Button disabled={(phase == "Installing")} variant="contained" onClick={() => {setPhase("Install")}} color="primary" component="span">{owlSelected}</Button>
          </Tooltip>
        </label>

        {((phase == "Installing") ? (<TextField disabled variant="outlined" onChange={(evt) => {setOwlIdentifier(evt.target.value)}} value={owlIdentifier} name="identifier" label="Identifier"/>) : (<TextField variant="outlined" onChange={(evt) => {setOwlIdentifier(evt.target.value); setPhase("Install")}} value={owlIdentifier} name="identifier" label="Identifier"/>))}

        {((phase == "Installing") ? (<TextField disabled variant="outlined" onChange={(evt) => {setOwlName(evt.target.value)}} value={owlName} name="vocabName" label="Name"/>) : (<TextField variant="outlined" onChange={(evt) => {setOwlName(evt.target.value); setPhase("Install")}} value={owlName} name="vocabName" label="Name"/>))}

        {((phase == "Installing") ? (<TextField disabled variant="outlined" onChange={(evt) => {setOwlVersion(evt.target.value)}} value={owlVersion} name="version" label="Version"/>) : (<TextField variant="outlined" onChange={(evt) => {setOwlVersion(evt.target.value); setPhase("Install")}} value={owlVersion} name="version" label="Version"/>))}

        <label htmlFor="owl-install">
          <input style={{ display: 'none' }} id="owl-install" name="owl-install" type={(phase == "Install") ? "submit" : "button"}/>
          <Tooltip title={(phase == "Install") ? "Install this vocabulary" : ""}>
            <span className={classes.wrapper}>
                {(phase == "Install") && (<Button variant="contained" color="primary" component="span" className={classes.owlInstaller + " " + classes.install}>{phase}</Button>)}
                {(phase == "Installed") && (<Button variant="contained" color="primary" component="span" className={classes.owlInstaller + " " + classes.installed}>{phase}</Button>)}
                {(phase == "Failed") && (<Button variant="contained" color="primary" component="span" className={classes.owlInstaller + " " + classes.failed}>{phase}</Button>)}
                {(phase == "Installing") && (<Button disabled variant="contained" color="primary" component="span" className={classes.owlInstaller}>{phase}</Button>)}
                {(phase == "Installing") && (<CircularProgress size={24} className={classes.buttonProgress + " " + classes.installingColor} />)}
            </span>
          </Tooltip>
        </label>
      </form>
    </React.Fragment>
  );
}
