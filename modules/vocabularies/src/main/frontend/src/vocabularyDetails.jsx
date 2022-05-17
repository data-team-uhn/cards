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

import React, { useRef, useState } from "react";

import { styled } from '@mui/material/styles';

import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Tooltip,
  Typography,
  Zoom,
} from "@material-ui/core";

import makeStyles from '@material-ui/styles/makeStyles';

import CloseIcon from "@material-ui/icons/Close";

import VocabularyAction from "./vocabularyAction";
import VocabularyBrowser from "./vocabQuery/VocabularyBrowser.jsx";

const PREFIX = 'vocabularyDetails';

const classes = {
  about: `${PREFIX}-about`,
  button: `${PREFIX}-button`,
  closeButton: `${PREFIX}-closeButton`,
  dialogTitle: `${PREFIX}-dialogTitle`,
  browseAction: `${PREFIX}-browseAction`
};

// TODO jss-to-styled codemod: The Fragment root was replaced by div. Change the tag if needed.
const Root = styled('div')((
  {
    theme
  }
) => ({
  [`& .${classes.about}`]: {
    background: "#007bff",
    "&:hover": {
      background: "#2361b8"
    }
  },

  [`& .${classes.button}`]: {
    margin: theme.spacing(1),
    textTransform: "none",
    color: "white",
    borderRadius: 3,
    border: 0
  },

  [`& .${classes.closeButton}`]: {
    position: "absolute",
    right: theme.spacing(1),
    top: theme.spacing(1),
    marginLeft: theme.spacing(5),
    color: theme.palette.grey[500],
  },

  [`& .${classes.dialogTitle}`]: {
    marginRight: theme.spacing(5)
  },

  [`& .${classes.browseAction}`]: {
    margin: theme.spacing(1),
    textTransform: "none",
    marginRight: "auto"
  }
}));

const Phase = require("./phaseCodes.json");


export default function VocabularyDetails(props) {
  const { install, uninstall, phase, type, vocabulary } = props;

  const [displayPopup, setDisplayPopup] = React.useState(false);
  const handleOpen = () => {setDisplayPopup(true);}
  const handleClose = () => {setDisplayPopup(false);}

  const [browserOpened, setBrowserOpened] = useState(false);

  let infoboxRef = useRef();
  let browserRef = useRef();



  let closeBrowser = (event) => {
    setBrowserOpened(false);
  };

  return (
    <Root>

      <Tooltip title="About this vocabulary" TransitionComponent={Zoom}>
        <Button onClick={handleOpen} variant="contained" className={classes.button + " " + classes.about} >About</Button>
      </Tooltip>

      <Dialog onClose={handleClose} open={displayPopup}>

        <DialogTitle>
          <Typography variant="h4" className={classes.dialogTitle}>{vocabulary.name} ({vocabulary.acronym})</Typography>
        </DialogTitle>

        <DialogContent dividers>
          <Typography variant="subtitle1" paragraph>{vocabulary.version}</Typography>
          <Typography variant="body1"><span dangerouslySetInnerHTML={{__html: vocabulary.description}} /></Typography>
        </DialogContent>

        <DialogActions>
          {(phase == Phase["Latest"] || phase == Phase["Update Available"]) && 
            <Button onClick={() => {setBrowserOpened(true);}} variant="contained" className={classes.browseAction} color="primary">Browse</Button>
          }
          <VocabularyAction
            install={install}
            uninstall={uninstall}
            phase={phase}
            exit={handleClose}
            vocabulary={vocabulary}
          />
        </DialogActions>

      </Dialog>
      { browserOpened &&
        <VocabularyBrowser
          browserRef={browserRef}
          infoboxRef={infoboxRef}
          browserOpen={browserOpened}
          vocabulary={vocabulary}
          browseRoots={true}
          onCloseBrowser={closeBrowser}
        />
      }
    </Root>
  );
}
