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

import React, { useEffect, useContext } from "react";

import { styled } from '@mui/material/styles';

import { Dialog, DialogContent, DialogTitle, IconButton, Typography } from "@material-ui/core";

import makeStyles from '@material-ui/styles/makeStyles';
import withStyles from '@material-ui/styles/withStyles';

import CloseIcon from "@material-ui/icons/Close";

import VocabularyDetails from "./vocabularyDetails"
import VocabularyAction from "./vocabularyAction"
import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

const PREFIX = 'vocabularyActions';

const classes = {
  closeButton: `${PREFIX}-closeButton`,
  title: `${PREFIX}-title`
};

// TODO jss-to-styled codemod: The Fragment root was replaced by div. Change the tag if needed.
const Root = styled('div')((
  {
    theme
  }
) => ({
  [`& .${classes.closeButton}`]: {
    position: 'absolute',
    right: theme.spacing(1),
    top: theme.spacing(1),
    color: theme.palette.grey[500]
  },

  [`& .${classes.title}`]: {
    marginRight: theme.spacing(5)
  }
}));

const vocabLinks = require('./vocabularyLinks.json');
const Phase = require("./phaseCodes.json");

/*
  This function keeps track of the state of the current vocabulary. It also keeps track of any error messages needed to be displayed.
  Then it renders an action button and an about button. It also renders a dialog box for any installation / uninstallation errors
*/
export default function VocabularyActions(props) {
  const { vocabulary, updateLocalList, initPhase } = props;
  // The following facilitates the usage of the same code to report errors for both installation and uninstallation
  const [error, setError] = React.useState(false);
  const [action, setAction] = React.useState("");
  const [errorMessage, setErrorMessage] = React.useState("");

  const [phase, setPhase] = React.useState(initPhase);



  const globalLoginDisplay = useContext(GlobalLoginContext);

  const handleClose = () => {setError(false)};

  useEffect(() => {
    setPhase(initPhase);
  }, [initPhase])

  function install() {
    const oldPhase = phase;
    var badResponse = false;
    setPhase(Phase["Installing"]);
    props.setPhase(Phase["Installing"]);

    fetchWithReLogin(globalLoginDisplay,
      vocabLinks["install"]["base"] + "&identifier=" + vocabulary.acronym +
      Object.keys(vocabLinks["install"]["params"]).map(
        key => ("&" + key + "=" + vocabLinks["install"]["params"][key])
      ).join(""),
      {method: "POST"}
    )
    .then((resp) => resp.json())
    .then((resp) => {
      if(resp["isSuccessful"]) {
        props.setPhase(Phase["Latest"]);
        updateLocalList("add", vocabulary);
      } else {
        throw new Error(resp["error"]);
      }
    })
    .catch(function(error) {
      setPhase(oldPhase);
      props.setPhase(oldPhase);
      setAction("Install");
      setErrorMessage(error.message || "Server Error");
      setError(true);
    });
  }

  function uninstall() {
    const oldPhase = phase;
    var badResponse = false;
    props.setPhase(Phase["Uninstalling"]);

    fetchWithReLogin(globalLoginDisplay, vocabLinks["uninstall"]["base"] + vocabulary.acronym, {method: "DELETE"})
    .then((resp) => resp.ok ? resp : Promise.reject(resp))
    .then((resp) => {
      props.setPhase(Phase["Not Installed"]);
      updateLocalList("remove", vocabulary);
    })
    .catch(function(error) {
      let statusText = error.statusText;
      error.json().then((json) => {
        const code = json["status.code"];
        const errorText = (statusText || ("Error " + code)) + ": ";
        props.setPhase(oldPhase);
        setAction("Uninstall");
        setErrorMessage(errorText + json["status.message"]);
        setError(true);
      });
    });
  }
  React.useEffect(() => {props.addSetter(setPhase);},[0]);

  return (
    <Root>
      <VocabularyAction
        install={install}
        uninstall={uninstall}
        phase={phase}
        vocabulary={vocabulary}
      />
      <VocabularyDetails
        install={install}
        uninstall={uninstall}
        phase={phase}
        vocabulary={vocabulary}
        type={props.type}
      />
      <Dialog open={error} onClose={handleClose}>

        <DialogTitle>
          <Typography variant="h6" color="error" className={classes.title}>Failed to {action}</Typography>
          <IconButton onClick={handleClose} className={classes.closeButton} size="large">
            <CloseIcon />
          </IconButton>
        </DialogTitle>

        <DialogContent dividers>
          <Typography variant="h6">{vocabulary.name}</Typography>
          <Typography variant="subtitle2" gutterBottom>Version: {vocabulary.version}</Typography>
          <Typography paragraph color="error">{errorMessage}</Typography>
        </DialogContent>

      </Dialog>
    </Root>
  );
}
