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

import React from "react";

import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  makeStyles,
  Typography,
} from "@material-ui/core";

import CloseIcon from "@material-ui/icons/Close";

import About from "./About"
import Action from "./Action"
import Uninstall from "./Uninstall"

const Phase = require("./phaseCodes.json");

const useStyles = makeStyles(theme => ({
  closeButton: {
    position: 'absolute',
    right: theme.spacing(1),
    top: theme.spacing(1),
    color: theme.palette.grey[500],
  },
  title: {
    marginRight: theme.spacing(4)
  }
}));

/*
  This function keeps track of the state of the current vocabulary. It also keeps track of any error messages needed to be displayed.
  Then it renders an action button and an about button. It also renders a dialog box for any installation / uninstallation errors
*/
export default function Actions(props) {
  // The following facillitates the usage of the same code to report errors for both installation and uninstallation
  const [action, setAction] = React.useState("");

  const [err, setErr] = React.useState(false);
  const [errMsg, setErrMsg] = React.useState("");
  const [phase, setPhase] = React.useState(props.initPhase);

  const classes = useStyles();

  const handleClose = () => {setErr(false)};

  function install() {
    const oldPhase = phase;
    var badResponse = false;

    setPhase(Phase["Installing"]);

    fetch("/Vocabularies?source=bioontology&identifier="+props.acronym+"&overwrite=true", {method: "POST"})
    .then((resp) => resp.json())
    .then((resp) => {
      if(!resp["isSuccessful"]) {
        setPhase(oldPhase);
        setAction("Install");
        setErrMsg(resp["error"]);
        setErr(true);
        badResponse = true;
      }
    })
    .catch(function(error) {
      setPhase(oldPhase);
      setAction("Install");
      setErrMsg(error);
      setErr(true);
      badResponse = true;
    })
    .finally(function() {
      if(!badResponse) {
        setPhase(Phase["Latest"]);
      }
    });
  }

  function uninstall() {
    const oldPhase = phase;
    var badResponse = false;

    setPhase(Phase["Uninstalling"]);

    fetch("/Vocabularies/"+props.acronym, {method: "DELETE"})
    .then((resp) => {
      const code = resp.status;
      if(Math.floor(code/100) !== 2) {
        setPhase(oldPhase);
        setAction("Uninstall");
        setErrMsg("Error "+code+": "+resp.statusText);
        setErr(true);
        badResponse = true;
      }
    })
    .catch(function(error) {
      setPhase(oldPhase);
      setAction("Uninstall");
      setErrMsg(error);
      setErr(true);
      badResponse = true;
    })
    .finally(function() {
      if(!badResponse) {
        setPhase(Phase["Not Installed"]);
      }
    });
  }

  return(
    <React.Fragment>
      <Action
        acronym={props.acronym}
        install={install}
        phase={phase}
      />

      {(phase == Phase["Update Available"] || phase == Phase["Latest"]) && 
        <Uninstall uninstall={uninstall} />}

      <About
        acronym={props.acronym}
        install={install}
        uninstall={uninstall}
        phase={phase}
        name={props.name}
        description={props.description}
      />

      <Dialog open={err} onClose={handleClose}>

        <DialogTitle onClose={handleClose}>
          <Typography variant="h5" color="error" className={classes.title}>Failed to {action}</Typography>
          <IconButton onClick={handleClose} className={classes.closeButton}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>

        <DialogContent>
          <Typography variant="h6">{props.name}</Typography>
          <Typography variant="body1">Version {props.version}</Typography>
        </DialogContent>

        <DialogContent>
          <Typography variant="body1">{errMsg}</Typography>
        </DialogContent>

        <DialogActions/>

      </Dialog>
    </React.Fragment>
  );
}
