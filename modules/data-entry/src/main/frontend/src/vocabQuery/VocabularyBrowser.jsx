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

import { useState, useEffect } from "react";

import { Alert, Snackbar } from "@mui/material";
import PropTypes from "prop-types";

import { checkPropTypes } from "../propTypes";
import InfoBox from "./InfoBox.jsx";
import { MakeRequest } from "./util.jsx";
import VocabularyTree from "./VocabularyTree.jsx";

// Component that renders a vocabulary info box and browser.
//
// Required arguments:
// infoboxRef: Reference to the term info box node
// browserRef: Reference to the vocabulary tree dialog node
//
// Optional arguments:
// onCloseInfo: Callback for the vocabulary info box close event
// onCloseBrowser: Callback for the vocabulary tree dialog close event
// infoButtonRefs: References to the term info buttons forwarded from the dropdown menu
// infoPath: Term @path to get the term info
// browserOpen: Boolean representing whether or not the vocabulary tree dialog is open
// enableSelection: Boolean enabler for term selection from vocabulary tree browser
// initialSelection: Existing answers
// questionDefinition: Object describing the Vocabulary Question for which this suggested input is displayed
//
function VocabularyBrowser(props) {
  checkPropTypes(VocabularyBrowser, props);
  const { browserOpen, onCloseInfo, onCloseBrowser, infoPath, infoButtonRefs, infoboxRef, browserRef, browseRoots,
    vocabulary, enableSelection, initialSelection, questionDefinition } = props;

  const [termInfoVisible, setTermInfoVisible] = useState(false);
  const [term, setTerm] = useState({});

  const [browsePath, setBrowsePath] = useState("");
  const [browserOpened, setBrowserOpened] = useState(!!browserOpen);
  const [closeupTimer, setCloseupTimer] = useState(null);

  const [vocab, setVocab] = useState(vocabulary || {});

  const [snackbarVisible, setSnackbarVisible] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  // Info box properties
  const [infoAboveBackground, setInfoAboveBackground] = useState(false);

  // References to the term info buttons generated in the vocabulary tree dialog
  const [buttonRefs, setButtonRefs] = useState({});

  let logError = (message) => {
    setSnackbarVisible(true);
    setSnackbarMessage(message);
  }

  // Event handler for clicking close button for the info box
  let closeInfo = (event) => {
    if (closeupTimer !== null) {
      clearTimeout(closeupTimer);
    }

    setCloseupTimer(setTimeout(() => {setTermInfoVisible(false);
      setTerm({});
      setInfoAboveBackground(false);
      onCloseInfo?.();}, 300));
  }

  let parseVocabInfo = (status, data) => {
    if (status === null) {
      setVocab( { acronym: data["identifier"] || "",
        name: data["name"],
        url: data["website"] || "",
        description: data["description"] || "",
        path: data["@path"]
      } );
    } else {
      logError("Failed to search vocabulary details");
    }
  }

  // callback for getInfo to populate info box
  let showInfo = (status, data, params) => {
    if (status === null && data) {
      setTerm({ name: data["label"],
        id: data["identifier"],
        definition: data["def"] || data["description"] || data["definition"],
        alsoKnownAs: data["synonym"] || data["has_exact_synonym"] || [],
        typeOf: data["parents"]?.filter(p => typeof p === 'object').map(p => p["label"] || p["name"] || p["identifier"] || p["id"]) || [],
        path: data["@path"],
        infoAnchor: browserOpened ? buttonRefs[data["identifier"] + params.parentInfoId] : infoButtonRefs[data["@path"]]
      });
      setTermInfoVisible(true);
      setInfoAboveBackground(browserOpened);
    } else {
      logError("Failed to search vocabulary term");
    }
  }

  // Grab information about the given ID and populate the info box
  let getInfo = (path, parentId = "") => {
    // If we don't yet know anything about our vocabulary, fill it in
    let vocabPath = path.split("/").slice(0, -1).join("/");
    let url = "";
    if (vocab.path != vocabPath) {
      url = new URL(vocabPath + ".json", window.location.origin);
      MakeRequest(url, parseVocabInfo);
    }

    url = new URL(path + ".info.json", window.location.origin);
    MakeRequest(url, showInfo, { parentInfoId : parentId });
  }

  useEffect(() => {
    if (infoPath) {
      getInfo(infoPath);
    } else {
      closeInfo();
    }
  }, [infoPath]);

  useEffect(() => {
    setBrowserOpened(browserOpen);
  }, [browserOpen]);

  // Clean up the closeup timer when the component unmounts
  useEffect(() => {
    return () => {
      if (closeupTimer !== null) {
        clearTimeout(closeupTimer);
      }
    };
  }, [closeupTimer]);

  // Event handler for clicking away from the info box
  let clickAwayInfo = (event) => {
    if (!infoAboveBackground && browserRef?.current?.contains(event.target)
         || infoboxRef?.current?.contains(event.target)) {
      return;
    }
    for (const [, value] of Object.entries(browserOpened ? buttonRefs : infoButtonRefs)) {
      if (value.contains(event.target)) {
        return;
      }
    }

    closeInfo();
  }

  // Register a button reference that the info box can use to align itself to
  let registerInfoButton = (id, node) => {
    // List items getting deleted will overwrite new browser button refs, so
    // we must ignore deregistration events
    if (node) {
      setButtonRefs(oldRefs => {
        let old = oldRefs;
        old[id] = node;
        return old;
      });
    }
  }

  let openBrowser = () => {
    setButtonRefs({});
    setBrowsePath(term.path);
    setInfoAboveBackground(false);
    setBrowserOpened(true);
  }

  let closeBrowser = (selectedTerms, removedTerms) => {
    if (closeupTimer !== null) {
      clearTimeout(closeupTimer);
    }

    setCloseupTimer(setTimeout(() => {
      setBrowserOpened(false);
      onCloseBrowser?.(selectedTerms, removedTerms);
    }, 300));
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
        browserOpened={browserOpened || false}
        infoAboveBackground={browseRoots || infoAboveBackground || false}
        onClickAway={clickAwayInfo}
      />
      { /* Browse dialog box */}
      {browserOpened && <VocabularyTree
        browserRef={browserRef}
        open={browserOpened || false}
        infoAboveBackground={infoAboveBackground}
        vocabulary={vocab}
        path={browsePath}
        onTermClick={!browseRoots ? focusTerm : null}
        onClose={closeBrowser}
        onCloseInfoBox={closeInfo}
        onError={logError}
        registerInfo={registerInfoButton}
        getInfo={getInfo}
        browseRoots={browseRoots}
        enableSelection={enableSelection}
        initialSelection={initialSelection}
        questionDefinition={questionDefinition}
      />}
      { /* Error snackbar */}
      <Snackbar
        open={snackbarVisible}
        onClose={() => {setSnackbarVisible(false); setSnackbarMessage("");}}
        autoHideDuration={6000}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'center',
        }}
        variant="error"
      >
        <Alert severity="error">
          { snackbarMessage }
        </Alert>
      </Snackbar>
    </>
  );
}

VocabularyBrowser.propTypes = {
  browserOpen: PropTypes.bool,
  onCloseInfo: PropTypes.func,
  onCloseBrowser: PropTypes.func,
  infoPath: PropTypes.string,
  infoButtonRefs: PropTypes.object,
  infoboxRef: PropTypes.object.isRequired,
  browserRef: PropTypes.object.isRequired,
  enableSelection: PropTypes.bool,
  initialSelection: PropTypes.array,
  questionDefinition: PropTypes.object,
}

export default VocabularyBrowser;
