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

import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  makeStyles,
  Tooltip,
  Typography,
  Zoom
} from "@material-ui/core";

import CloseIcon from "@material-ui/icons/Close";

import VocabularyAction from "./vocabularyAction";
import VocabularyBrowser from "./vocabQuery/browse.jsx";
import InfoBox from "./vocabQuery/infoBox.jsx";
import { MakeRequest } from "./vocabQuery/util.jsx";

const useStyles = makeStyles(theme => ({
  about: {
    background: "#007bff",
    "&:hover": {
      background: "#2361b8"
    }
  },
  button: {
    margin: theme.spacing(1),
    textTransform: "none",
    color: "white",
    borderRadius: 3,
    border: 0
  },
  closeButton: {
    position: "absolute",
    right: theme.spacing(1),
    top: theme.spacing(1),
    marginLeft: theme.spacing(5),
    color: theme.palette.grey[500],
  },
  dialogTitle: {
    marginRight: theme.spacing(5)
  },
  browseAction: {
    margin: theme.spacing(1),
    textTransform: "none",
    marginRight: theme.spacing(37)
  },
}));


export default function VocabularyDetails(props) {
  const { install, uninstall, phase, vocabulary } = props;
  const [displayPopup, setDisplayPopup] = React.useState(false);
  const handleOpen = () => {setDisplayPopup(true);}
  const handleClose = () => {setDisplayPopup(false);}

  const [termInfoVisible, setTermInfoVisible] = useState(false);
  const [browserOpened, setBrowserOpened] = useState(false);
  const [browseID, setBrowseID] = useState("");
  const [browsePath, setBrowsePath] = useState("");
  const [snackbarVisible, setSnackbarVisible] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  // Strings used by the info box
  const [infoID, setInfoID] = useState("");
  const [infoName, setInfoName] = useState("");
  const [infoPath, setInfoPath] = useState("");
  const [infoDefinition, setInfoDefinition] = useState("");
  const [infoAlsoKnownAs, setInfoAlsoKnownAs] = useState([]);
  const [infoTypeOf, setInfoTypeOf] = useState([]);
  const [infoAboveBackground, setInfoAboveBackground] = useState(false);
  const [infoAnchor, setInfoAnchor] = useState(null);

  const [buttonRefs, setButtonRefs] = useState({});
  const [noResults, setNoResults] = useState(false);

  let infoRef = useRef();
  let menuPopperRef = useRef();
  let menuRef = useRef();

  const classes = useStyles();

  // Register a button reference that the info box can use to align itself to
  let registerInfoButton = (id, node) => {
    // List items getting deleted will overwrite new browser button refs, so
    // we must ignore deregistration events
    if (node) {
      buttonRefs[id] = node;
    }
  }

  // Grab information about the given ID and populate the info box
  let getInfo = (path) => {
    var url = new URL(path + ".info.json", window.location.origin);
    MakeRequest(url, showInfo);
  }

  // callback for getInfo to populate info box
  let showInfo = (status, data) => {
    if (status === null) {
      var typeOf = [];
      if ("parents" in data) {
        typeOf = data["parents"].map(element =>
          element["label"] || element["name"] || element["identifier"] || element["id"]
        ).filter(i => i);
      }

      setInfoID(data["identifier"]);
      setInfoPath(data["@path"]);
      setInfoName(data["label"]);
      setInfoDefinition(data["def"] || data["description"] || data["definition"]);
      setInfoAlsoKnownAs(data["synonyms"] || data["has_exact_synonym"] || []);
      setInfoTypeOf(typeOf);
      setInfoAnchor(buttonRefs[data["identifier"]]);
      setTermInfoVisible(true);
      setInfoAboveBackground(browserOpened);
    } else {
      logError("Failed to search vocabulary term");
    }
  }

  let clickAwayInfo = (event) => {
    if ((menuPopperRef && menuPopperRef.current.contains(event.target))
      || (infoRef && infoRef.current && infoRef.current.contains(event.target))) {
      return;
    }

    closeInfo();
  }

  // Event handler for clicking away from the info window while it is open
  let closeInfo = (event) => {
    setTermInfoVisible(false);
    setInfoAboveBackground(false);
  }

  let openDialog = () => {
    handleClose();
    setBrowserOpened(true);
    setBrowseID(infoID);
    setBrowsePath(infoPath);
  }

  let closeDialog = () => {
    setBrowserOpened(false);
    setTermInfoVisible(false);
    setInfoAboveBackground(false);
  }

  let changeBrowseTerm = (id, path) => {
    setBrowseID(id);
    setBrowsePath(path);
  }

  let logError = (message) => {
    setSnackbarVisible(true);
    setSnackbarMessage(message);
  }

  return(
    <React.Fragment>

      <Tooltip title="About this vocabulary" TransitionComponent={Zoom}>
        <Button onClick={handleOpen} variant="contained" className={classes.button + " " + classes.about} >About</Button>
      </Tooltip>

      <Dialog onClose={handleClose} open={displayPopup}>

        <DialogTitle disableTypography>
          <Typography variant="h4" className={classes.dialogTitle}>{vocabulary.name} ({vocabulary.acronym})</Typography>
        </DialogTitle>

        <DialogContent dividers>
          <Typography variant="subtitle1" paragraph>{vocabulary.version}</Typography>
          <Typography variant="body1"><span dangerouslySetInnerHTML={{__html: vocabulary.description}} /></Typography>
        </DialogContent>

        <DialogActions>
        { props.type == "local" &&
            <Button onClick={openDialog} variant="contained" className={classes.browseAction} color="primary">Browse</Button>
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

      { props.type == "local" && browserOpened && <>
        <InfoBox
          termInfoVisible={termInfoVisible}
          anchorEl={infoAnchor}
          infoAboveBackground={infoAboveBackground}
          infoRef={infoRef}
          menuPopperRef={menuPopperRef}
          infoVocabURL={props.url}
          infoVocabDescription={props.description}
          infoVocabAcronym={props.acronym}
          closeInfo={closeInfo}
          infoName={infoName}
          infoID={infoID}
          infoDefinition={infoDefinition}
          infoAlsoKnownAs={infoAlsoKnownAs}
          infoTypeOf={infoTypeOf}
          openDialog={openDialog}
          browserOpened={browserOpened}
        />
        <VocabularyBrowser
          open={browserOpened}
          title={`${props.name} (${props.acronym})`}
          id={browseID}
          path={browsePath}
          changeTerm={changeBrowseTerm}
          onClose={closeDialog}
          onError={logError}
          registerInfo={registerInfoButton}
          getInfo={getInfo}
          roots={props.roots}
        />
      </>}

    </React.Fragment>
    );
}
