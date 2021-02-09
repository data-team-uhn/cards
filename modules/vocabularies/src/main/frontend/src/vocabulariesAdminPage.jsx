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
  Dialog,
  DialogContent,
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
  const [customApiKey, setCustomApiKey] = React.useState('');
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

  // function to create / edit node
  function addNewKey() {
    const URL = `/libs/lfs/conf/BioportalApiKey`
    var request_data = new FormData();
    request_data.append('key', customApiKey);
    fetch(URL, { method: 'POST', body: request_data })
      .then((response) => response.ok ? response : Promise.reject(response))
      .then((data) => {
          setBioPortalApiKey(customApiKey);
          console.log("saved to node");
        })
      .catch((error) => {
        console.error("error creating node: " + error)
        }
      )
  }

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
    var tempAcronymPhaseObject = {};
    vocabList.map((vocab) => {
      tempAcronymPhaseObject[vocab.ontology.acronym] = determinePhase(vocab.ontology.acronym, vocab.released)
    });
    setAcronymPhaseObject(tempAcronymPhaseObject);
    setDisplayTables(true);
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

  function handleErrorModal(isError) {
    // show modal
    setError(isError);
  }

  useEffect(() => {
    /* If the BioPortal API key cannot be loaded, assume the remote (empty)
      * data has been loaded.
      */
    fetchBioPortalApiKey( (apiKey) => {
        setBioPortalApiKey(apiKey);
        setCustomApiKey(apiKey);
        setRemoteLoaded(true);
        setDisplayChangeKey(false);
      }, () => {
        setRemoteLoaded(true);
        setDisplayChangeKey(true);
    });
  }, [bioPortalApiKey])

  return (
    <Grid container direction="column" spacing={4} justify="space-between">
      <Grid item>
        <Typography variant="h6">
          Installed
        </Typography>
      </Grid>
      { localVocabList.length == 0 &&
          <Grid item>
            <Typography color="textSecondary">No local vocabularies are installed yet.</Typography>
          </Grid>
       }
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
        apiKey={bioPortalApiKey}
      />

      <Grid item>
        <Typography variant="h6">
          Install from local file
        </Typography>
      </Grid>
      <Grid item>
        <OwlInstaller updateLocalList={updateLocalList}/>
      </Grid>

      <Grid item>
        <Typography variant="h6">
          Find on <a href="https://bioportal.bioontology.org/" target="_blank">BioPortal</a>
        </Typography>
      </Grid>

      { !bioPortalApiKey &&
         <Grid item>
           <Typography>Your system does not have a <a href="https://bioportal.bioontology.org/help#Getting_an_API_key" target="_blank">Bioportal API Key</a> configured.</Typography>
           <Typography>Without an API key, you cannot access Bioportal services such as listing and installing vocabularies.</Typography>
         </Grid>
      }

      <Grid item>
        <Grid container
          direction="row"
          alignItems="center"
          spacing={1}
        >
          <Grid item xs={6}>
            <TextField
              InputProps={{
                readOnly: !displayChangeKey,
              }}
              variant={displayChangeKey ? "outlined" : "filled" }
              onChange={(evt) => {setCustomApiKey(evt.target.value)}}
              value={customApiKey}
              name="customApiKey"
              label={ displayChangeKey ? "Enter new Bioportal API key:" : "Bioportal API key:" }
              fullWidth={true}
            />
          </Grid>
          <Grid item>
            { !displayChangeKey && bioPortalApiKey &&
              <Button variant="contained" color="primary" onClick={() => {setDisplayChangeKey(true);}}>Change</Button>
            }
          </Grid>
          <Grid item>
            { displayChangeKey &&
              <Button color="primary" disabled={!customApiKey} variant="contained" onClick={() => {addNewKey();}}>Submit</Button>
            }
          </Grid>
          <Grid item>
            { displayChangeKey && bioPortalApiKey &&
              <Button color="default" variant="contained" onClick={() => {setDisplayChangeKey(false);}}>Cancel</Button>
            }
          </Grid>
        </Grid>
      </Grid>

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
        apiKey={bioPortalApiKey}
      />
    </Grid>
  );
}
