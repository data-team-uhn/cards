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

import React, {useEffect} from "react";

import { 
  Button,
  Grid,
  LinearProgress,
  Typography
} from "@material-ui/core";

import VocabularyTable from "./vocabularyTable";

const Status = require("./statusCodes.json");

/*
  This function reformats the vocab data for more light unified representation.
*/
function reformat(data, type) {
  let vocabs = [];
  data.map(vocab =>  vocabs.push({
          status: vocab.status,
          acronym: type == "remote" ? vocab.ontology.acronym : vocab.identifier,
          name: type == "remote" ? vocab.ontology.name : vocab.name,
          source: vocab.source,
          description: vocab.description,
          released: type == "remote" ? vocab.released : vocab["jcr:created"],
          version: vocab.version
   }));
   return vocabs;
}

// Requests list of Vocabularies from Bioontology API. Currently only renders a table to display items if they are form the remote source
export default function VocabularyDirectory(props) {
  const [curStatus, setCurStatus] = React.useState(Status["Init"]);

  // Function that fetches list of Vocabularies from Bioontology API
  function getVocabList() {
    setCurStatus(Status["Loading"]);
    var badResponse = false;
    fetch(props.link)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then(function(data) {

      if (props.type === "remote") {
        props.setVocabList(reformat(data, props.type));
      } else if (props.type === "local") {
        props.setVocabList(reformat(data.rows, props.type));
      }
    })
    .catch((error) => {
      setCurStatus(Status["Error"]);
      badResponse = true;
    })
    .finally(() =>{
      if (!badResponse) {
        setCurStatus(Status["Loaded"]);
      }
    });
  }

  useEffect(() => {
    !props.loaded && getVocabList();
  }, [props.loaded, props.link])

  return(
    <React.Fragment>
    {(curStatus == Status["Loading"]) && (
      <Grid item>
        <LinearProgress color={(props.type === "remote" ? "primary" : "secondary" )} />
      </Grid>
    )}
    {(curStatus == Status["Error"]) && (
      <React.Fragment>
        <Grid item>
          <Typography color="error">
            The list of Bioportal vocabularies is currently inaccessible.
          </Typography>
          { props.apiKey && <Typography color="error">
            Could not access Bioportal services. The API Key {props.apiKey} appears to be invalid.
          </Typography>}
        </Grid>
        <Grid item>
          <Button variant="contained" color="primary" onClick={getVocabList}>
            <Typography variant="button">Retry</Typography>
          </Button>
        </Grid>
      </React.Fragment>
    )}
    {(curStatus == Status["Loaded"] && props.loaded) && (
      <VocabularyTable
        type={props.type}
        vocabList={props.vocabList}
        acronymPhaseObject={props.acronymPhaseObject}
        updateLocalList={props.updateLocalList}
        setPhase={props.setPhase}
        addSetter={props.addSetter}
      />
    )}
    </React.Fragment>
  );
}
