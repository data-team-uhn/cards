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

import VocabularyBrowser from "./browse.jsx";
import InfoBox from "./infoBox.jsx";
import { REST_URL, MakeRequest } from "./util.jsx";
import QueryStyle from "./queryStyle.jsx";

// Component that renders a vocabulary info box and browser.
//
function InfoBrowser(props) {
  const { browserOpen, onClose, infoPath, infoButtonRefs, classes } = props;

  const [termInfoVisible, setTermInfoVisible] = useState(false);
  const [term, setTerm] = useState({});

  const [browseID, setBrowseID] = useState("");
  const [browsePath, setBrowsePath] = useState("");
  const [browserOpened, setBrowserOpened] = useState(!!browserOpen);

  const [vocab, setVocab] = useState({});

  const [snackbarVisible, setSnackbarVisible] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  // Strings used by the info box
  const [infoAboveBackground, setInfoAboveBackground] = useState(false);
  const [infoAnchor, setInfoAnchor] = useState(null);

  const [buttonRefs, setButtonRefs] = useState(infoButtonRefs || {});
  const [noResults, setNoResults] = useState(false);

  useEffect(() => {
    infoPath && getInfo(infoPath);
  }, [infoPath])

  let infoRef = useRef();
  let browserRef = useRef();

  let clickAwayInfo = (event) => {
    if ( browserRef?.current?.contains(event.target)
         || infoRef?.current?.contains(event.target)) {
      return;
    }

    closeInfo();
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
    var vocabPath = `${path.split("/").slice(0, -1).join("/")}.json`;
    if (vocab.path != vocabPath) {
      var url = new URL(vocabPath, window.location.origin);
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
    if (status === null) {
      var typeOf = [];
      if ("parents" in data) {
        typeOf = data["parents"].map(element =>
          element["label"] || element["name"] || element["identifier"] || element["id"]
        ).filter(i => i);
      }

      setTerm({name: data["label"],
               id: data["identifier"],
               definition: data["def"] || data["description"] || data["definition"],
               alsoKnownAs: data["synonyms"] || data["has_exact_synonym"] || [],
               typeOf: typeOf,
               path: data["@path"],
               infoAnchor: buttonRefs[data["identifier"]]
             });
      setTermInfoVisible(true);
      setInfoAboveBackground(browserOpened);
    } else {
      logError("Failed to search vocabulary term");
    }
  }

  let changeBrowseTerm = (id, path) => {
    setBrowseID(id);
    setBrowsePath(path);
  }

  // Event handler for clicking away from the info window while it is open
  let closeInfo = (event) => {
    setTermInfoVisible(false);
    setInfoAboveBackground(false);
  }

  let openBrowser = () => {
    changeBrowseTerm(term.id, term.path);
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

  return (
      <>
        {/* Info box using Popper */}
        <InfoBox
          infoRef={infoRef}
          open={termInfoVisible}
          vocabulary={vocab}
          onClose={closeInfo}
          term={term}
          openBrowser={openBrowser}
          browserOpened={browserOpened}
          infoAboveBackground={infoAboveBackground}
          onClickAway={clickAwayInfo}
        />
        { /* Browse dialog box */}
        { browseID && browserOpened && <VocabularyBrowser
          browserRef={browserRef}
          open={browserOpened || false}
          id={browseID}
          path={browsePath}
          changeTerm={changeBrowseTerm}
          onClose={closeBrowser}
          onError={logError}
          registerInfo={registerInfoButton}
          getInfo={getInfo}
        /> }
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

export default withStyles(QueryStyle)(InfoBrowser);
