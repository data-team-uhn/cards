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

import {
  Grid,
  Typography
} from "@material-ui/core";

import React from "react";

import VocabularyDirectory from "./vocabularyDirectory";

const Phase = require("./phaseCodes.json");
const vocabLinks = require("./vocabularyLinks.json");

export default function VocabulariesAdminPage() {
  const [remoteVocabList, setRemoteVocabList] = React.useState([]);
  const [localVocabList, setLocalVocabList] = React.useState([]);
  /*
    The following object will map Acronym -> Release Date for a vocabulary. 
    This allows for efficiently figuring out whether an installed vocabulary is up to date 
  */
  const [acronymDateObject, setAcronymDateObject] = React.useState({});
  /* 
    The Phase represents the state of Vocabulary. It can be 1 of:
      1) Not Installed
      2) Installing
      3) Update Available
      4) Latest
      5) Uninstalling
  */
  const [acronymPhaseObject, setAcronymPhaseObject] = React.useState({});
  const [acronymPhaseSettersObject, setAcronymPhaseSettersObject] = React.useState({});
  const [remoteLoaded, setRemoteLoaded] = React.useState(false);
  const [localLoaded, setLocalLoaded] = React.useState(false);
  const [displayTables, setDisplayTables] = React.useState(false);

  function processLocalVocabList(vocabList) {
    setLocalVocabList(vocabList);
    var tempObject = {};
    vocabList.map((vocab) => {
      tempObject[vocab.ontology.acronym] = vocab.released;
    });
    setAcronymDateObject(tempObject);
    setLocalLoaded(true);
  }

  function processRemoteVocabList(vocabList) {
    setRemoteVocabList(vocabList);
    setRemoteLoaded(true);
  }

  function addSetter(acronym, setFunction, type) {
    var copy = acronymPhaseSettersObject;
    if (copy.hasOwnProperty(acronym)) {
      copy[acronym][type] = setFunction;
    } else {
      var temp = {};
      temp[type] = setFunction;
      copy[acronym] = temp;
    }
    setAcronymPhaseSettersObject(copy);
  }

  function setPhase(acronym, phase) {
    const setters = acronymPhaseSettersObject[acronym];
    if (setters.hasOwnProperty("local")) {
      setters["local"](phase);
    }
    if (setters.hasOwnProperty("remote")) {
      setters["remote"](phase);
    }
  }

  function updateLocalList(action, vocab) {
    const acronym = vocab.ontology.acronym;

    if (action === "add") {
      var tempLocalVocabList = localVocabList.slice();
      tempLocalVocabList.push(vocab);
      setLocalVocabList(tempLocalVocabList);

    } else if (action === "remove") {
      var copy = acronymPhaseSettersObject;
      delete copy[acronym]["local"];
      setAcronymPhaseSettersObject(copy);
      setLocalVocabList(localVocabList.filter((vocab) => vocab.ontology.acronym != acronym));
    }
  }

  function determinePhase(acronym, released) {
    if (!acronymDateObject.hasOwnProperty(acronym)) {
      return Phase["Not Installed"];
    }
    const remoteReleaseDate = new Date(released);
    const localInstallDate = new Date(acronymDateObject[acronym]);
    return (remoteReleaseDate > localInstallDate ? Phase["Update Available"] : Phase["Latest"]);
  }

  if (localLoaded && remoteLoaded && !displayTables) {
    var tempAcronymPhaseObject = {};
    remoteVocabList.map((vocab) => {
      tempAcronymPhaseObject[vocab.ontology.acronym] = determinePhase(vocab.ontology.acronym, vocab.released)
    });
    setAcronymPhaseObject(tempAcronymPhaseObject);
    setDisplayTables(true);
  }

  return (
    <Grid container direction="column" spacing={4} justify="space-between">

      <Grid item>
        <Typography variant="h2">
          Installed
        </Typography>
      </Grid>

      <VocabularyDirectory 
        type="local"
        link={vocabLinks["local"]}
        vocabList={localVocabList}
        setVocabList={processLocalVocabList}
        acronymPhaseObject={acronymPhaseObject}
        displayTables={displayTables}
        updateLocalList={updateLocalList}
        addSetter={addSetter}
        setPhase={setPhase}
      />

      <Grid item>
        <Typography variant="h2">
          Find on <a href="https://www.bioontology.org/" target="_blank">BioPortal</a>
        </Typography>
      </Grid>
       
      <VocabularyDirectory 
        type="remote"
        link={
          vocabLinks["remote"]["base"] +
          vocabLinks["apikey"] +
          Object.keys(vocabLinks["remote"]["params"]).map(
            key => ("&" + key + "=" +
              (key === "include" ? 
                vocabLinks["remote"]["params"][key].join()
                :
                vocabLinks["remote"]["params"][key])
              )
            )
          .join("")
        } 
        vocabList={remoteVocabList}
        setVocabList={processRemoteVocabList}
        acronymPhaseObject={acronymPhaseObject}
        displayTables={displayTables}
        setPhase={setPhase}
        updateLocalList={updateLocalList}
        addSetter={addSetter}
      />

    </Grid>
  );
}

