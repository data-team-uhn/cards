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
  Typography,
} from "@material-ui/core";

import React from "react";

import VocabularyDirectory from './vocabularyDirectory';

const vocabLinks = require('./vocabularyLinks.json');

export default function VocabulariesAdminPage() {
  const [remoteVocabList, setRemoteVocabList] = React.useState({});

  /*
    The following object will map Acronym -> Release Date for a vocabulary. 
    This allows for efficiently figuring out whether an installed vocabulary is up to date 
  */
  const [optimisedDateList, setOptimisedDateList] = React.useState({});

  function processLocalVocabList(vocabList) {
    var acronymDateObject = {};
    vocabList.map((vocab) => {
      acronymDateObject[vocab.ontology.acronym] = vocab.released;
    });
    setOptimisedDateList(acronymDateObject);
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
        setVocabList={processLocalVocabList}
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
                vocabLinks["remote"]["params"][key])))
          .join("")
        } 
        setVocabList={setRemoteVocabList}
        remoteVocabList={remoteVocabList}
        optimisedDateList={optimisedDateList}
      />

    </Grid>
  );
}
