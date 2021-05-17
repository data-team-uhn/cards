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
import classNames from "classnames";
import React, { useRef, useState, useEffect } from "react";
import PropTypes from "prop-types";
import { Snackbar, SnackbarContent } from "@material-ui/core";
import { withStyles } from "@material-ui/core";

import VocabularyTree from "./VocabularyTree.jsx";
import InfoBox from "./InfoBox.jsx";
import { REST_URL, MakeRequest } from "./util.jsx";
import QueryStyle from "./queryStyle.jsx";

// Component that renders a vocabulary info box and browser.
//
function VocabularyBrowser(props) {
  const { browserOpen, onClose, infoPath, infoButtonRefs, infoboxRef, browserRef, classes } = props;

  const [termInfoVisible, setTermInfoVisible] = useState(false);
  const [term, setTerm] = useState({});

  const [browsePath, setBrowsePath] = useState("");
  const [browserOpened, setBrowserOpened] = useState(!!browserOpen);

  const [vocab, setVocab] = useState({});

  const [snackbarVisible, setSnackbarVisible] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  // Info box properties
  const [infoAboveBackground, setInfoAboveBackground] = useState(false);
  const [infoAnchor, setInfoAnchor] = useState(null);

  const [buttonRefs, setButtonRefs] = useState(infoButtonRefs || {});
  const [noResults, setNoResults] = useState(false);

  useEffect(() => {
    infoPath ? getInfo(infoPath) : setTermInfoVisible(false);
  }, [infoPath])

  // Event handler for clicking away from the info box or browser while they are open
  let clickAwayInfo = (event) => {
    if ( browserRef?.current?.contains(event.target)
         || infoboxRef?.current?.contains(event.target)) {
      return;
    }

    setTermInfoVisible(false);
    setInfoAboveBackground(false);
    onClose && onClose();
  }

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
    // If we don't yet know anything about our vocabulary, fill it in
    var vocabPath = path.split("/").slice(0, -1).join("/");
    if (vocab.path != vocabPath) {
      var url = new URL(vocabPath + ".json", window.location.origin);
      MakeRequest(url, parseVocabInfo);
    }

    var url = new URL(path + ".info.json", window.location.origin);
    MakeRequest(url, showInfo);
  }

  let parseVocabInfo = (status, data) => {
    if (status === null) {
      setVocab( { acronym: data["identifier"] || "",
                  url: data["website"] || "",
                  description: data["description"] || "",
                  path: data["@path"]
                } );
    } else {
      logError("Failed to search vocabulary details");
    }
  }

  // callback for getInfo to populate info box
  let showInfo = (status, data) => {
    if (status === null && data) {
      setTerm({name: data["label"],
               id: data["identifier"],
               definition: data["def"] || data["description"] || data["definition"],
               alsoKnownAs: data["synonyms"] || data["has_exact_synonym"] || [],
               typeOf: data["parents"]?.map(p => p["label"] || p["name"] || p["identifier"] || p["id"]) || [],
               path: data["@path"],
               infoAnchor: buttonRefs[data["identifier"]]
             });
      setTermInfoVisible(true);
      setInfoAboveBackground(browserOpened);
    } else {
      logError("Failed to search vocabulary term");
    }
  }

  // Event handler for clicking close button for the info box
  let closeInfo = (event) => {
    setTermInfoVisible(false);
    setInfoAboveBackground(false);
    onClose && onClose();
  }

  let openBrowser = () => {
    setBrowsePath(term.path);
    setInfoAboveBackground(false);
    setBrowserOpened(true);
  }

  let closeBrowser = () => {
    setBrowserOpened(false);
    setTermInfoVisible(false);
    setInfoAboveBackground(false);
  }

  let logError = (message) => {
    setSnackbarVisible(true);
    setSnackbarMessage(message);
  }

  let focusTerm = (path) => {
    setBrowsePath(path);
    setTermInfoVisible(false);
  }

  return (
      <>
        {/* Info box using Popper */}
        <InfoBox
          infoboxRef={infoboxRef}
          open={termInfoVisible}
          vocabulary={vocab}
          onClose={closeInfo}
          term={term}
          onActionClick={openBrowser}
          browserOpened={browserOpened}
          infoAboveBackground={infoAboveBackground}
          onClickAway={clickAwayInfo}
        />
        { /* Browse dialog box */}
        <VocabularyTree
          browserRef={browserRef}
          open={browserOpened || false}
          path={browsePath}
          onTermFocus={focusTerm}
          onClose={closeBrowser}
          onError={logError}
          registerInfo={registerInfoButton}
          getInfo={getInfo}
        />
        { /* Error snackbar */}
        <Snackbar
          open={snackbarVisible}
          onClose={() => {setSnackbarVisible(false);}}
          autoHideDuration={6000}
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'center',
          }}
          variant="error"
          >
            <SnackbarContent
              className={classes.errorSnack}
              role="alertdialog"
              message={snackbarMessage}
            />
          </Snackbar>
      </>
    );
}

export default withStyles(QueryStyle)(VocabularyBrowser);
