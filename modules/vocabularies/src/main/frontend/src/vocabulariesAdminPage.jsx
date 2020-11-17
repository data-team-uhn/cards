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
  Button,
  Grid,
  TextField,
  Typography
} from "@material-ui/core";

import React, {useEffect} from "react";

import VocabularyDirectory from "./vocabularyDirectory";

import OwlInstaller from "./owlInstaller";

import fetchBioPortalApiKey from "./bioportalApiKey";

const Phase = require("./phaseCodes.json");
const vocabLinks = require("./vocabularyLinks.json");

// Generates a URL to the vocabulary listing page
function generateRemoteLink(apiKey) {
  if (apiKey === null) {
    // never returned an incomplete URL without a valid key
    return "";
  }
  let url = new URL(vocabLinks["remote"]["base"]);
  url.searchParams.set("apikey", apiKey);
  Object.keys(vocabLinks["remote"]["params"]).forEach(
    (key) => {
      (key === "include" ? 
        url.searchParams.set(key, vocabLinks["remote"]["params"][key].join())
        :
        url.searchParams.set(key, vocabLinks["remote"]["params"][key])
      )
    }
  )
  return url.toString();
}

export default function VocabulariesAdminPage() {
  const [remoteVocabList, setRemoteVocabList] = React.useState([]);
  const [localVocabList, setLocalVocabList] = React.useState([]);
  const [customApiKey, setCustomApiKey] = React.useState(null);
  const [displayChangeKey, setDisplayChangeKey] = React.useState(false);
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
  /*
    Initially the key will be fetched from a script service.
  */
  const [bioPortalApiKey, setBioPortalApiKey] = React.useState(null);
  // check if api key is from node
  const [isFromNode, setIsFromNode] = React.useState(false);


  const localLink = '/query?query=' + encodeURIComponent(`select * from [lfs:Vocabulary]`);

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

  // useEffect - fetch new list when there is a new key.
  useEffect(() => {
    //TODO: figure out how to reload list of ontologies - this did not work
    // setRemoteLoaded(false);

    /* If the BioPortal API key cannot be loaded, assume the remote (empty)
      * data has been loaded.
      */
    fetchBioPortalApiKey(setBioPortalApiKey, setIsFromNode, () => {
        setRemoteLoaded(true);
        console.error("Can't fetch bioPortal API key");
    });
  }, [bioPortalApiKey])

  function addNewKey() {
    setBioPortalApiKey(customApiKey);

    // if (targetExists) {
    // } else {
      // If the question/section doesn't exist, create it

    console.log(isFromNode); // if true, node exists already ? doesn't work
    // TODO: how to check if node exists already?

    const URL = `/libs/lfs/conf/BioportalApiKey`
    var request_data = new FormData();
    request_data.append('key', customApiKey);
    fetch(URL, { method: 'POST', body: request_data })
      .then((response) => response.ok ? console.log("saved to node") : Promise.reject(response))
      .catch(() => {
        console.error("error creating node")
        }
      )

    // }

  }

  // TODO: error handling - parent callback for error in vocabulary directory

  return (
    <Grid container direction="column" spacing={4} justify="space-between">

      <Grid item>
        <Typography variant="h2">
          Installed
        </Typography>
      </Grid>

      {/* TODO: if error in vocabulary directory (fetching w/ key) --> parent handler 
      (display error in modal)
      Could not access Bioportal services. The API Key <the key> appears to be invalid.
      RETRY key does not work? 
      */}
      <VocabularyDirectory 
        type="local"
        link={localLink}
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
          Install from local file
        </Typography>
      </Grid>

      <OwlInstaller updateLocalList={updateLocalList}/>

      <Grid item>
        <Typography variant="h2">
          Find on <a href="https://bioportal.bioontology.org/" target="_blank">BioPortal</a>
        </Typography>
      </Grid>

      {bioPortalApiKey === null
        ? (
          <Grid>
          <Grid item>
            <Typography>Your system does not have a Bioportal API Key configured</Typography>
            <Typography>Without an API key, you cannot access Bioportal services such as listing and installing vocabularies.</Typography>
          </Grid>
          <Grid item>
            <Button color="primary" href="https://bioportal.bioontology.org/help#Getting_an_API_key">Get API Key</Button>
          </Grid>
          <Grid item>
            <TextField
                 variant="outlined"
                 onChange={(evt) => {setCustomApiKey(evt.target.value)}}
                 value={customApiKey}
                 name="customApiKey"
                 label="Enter your Bioportal API key:"
                 fullWidth={true}
            />
            <Button color="primary" onClick={() => {addNewKey()}}>Submit</Button> 
          </Grid>
          </Grid>
        )
        : (
          <Grid>
          {displayChangeKey
            ? (
              <Grid item>
                <TextField
                  variant="outlined"
                  onChange={(evt) => {setCustomApiKey(evt.target.value)}}
                  // below: value should have previous key already filled in
                  value={customApiKey}
                  name="customApiKey"
                  label="Enter the new Bioportal API key:"
                  fullWidth={true}
                />
                {/* TODO: update button should have same functionality as above submit button */}
                <Button onClick={() => {setDisplayChangeKey(false); addNewKey();}} color="primary">Update</Button>
                <Button onClick={() => {setDisplayChangeKey(false)}} color="primary">Cancel</Button>
              </Grid>
            )
            : (
              <Grid item>
                <Typography>{isFromNode ? "" : "Default"} API Key: {bioPortalApiKey}</Typography>
                <Button onClick={() => {setDisplayChangeKey(true)}} color="primary">Change</Button> 
              </Grid>
            )
            }
          </Grid>
        )
      }
       
      <VocabularyDirectory 
        type="remote"
        link={generateRemoteLink(bioPortalApiKey)}
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
