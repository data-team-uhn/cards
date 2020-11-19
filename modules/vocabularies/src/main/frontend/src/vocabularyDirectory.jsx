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
  makeStyles,
  Typography 
} from "@material-ui/core";

import VocabularyTable from "./vocabularyTable";

const Status = require("./statusCodes.json");

const useStyles = makeStyles(theme => ({
  root: {
    flexGrow: 1,
  },
  button: {
    margin: theme.spacing(1),
    textTransform: "none",
    color: "white",
    borderRadius: 3,
    border: 0
  }
}));

/*
  This function reformats the data from the local source into the format of data from the remote source. 
  This allows for the reusing of the same code to display a table for both the local and remote vocabularies.
*/
function reformat(data) {
  data.map((vocabulary) =>  {
      vocabulary.ontology = {
        acronym: vocabulary["identifier"],
        name: vocabulary["name"]
      };
      vocabulary.released = vocabulary["jcr:created"];
  });
  return data;
}

// Requests list of Vocabularies from Bioontology API. Currently only renders a table to display items if they are form the remote source
export default function VocabularyDirectory(props) {
  const [curStatus, setCurStatus] = React.useState(Status["Init"]);

  // Function that fetches list of Vocabularies from Bioontology API
  function getVocabList(url) {
    setCurStatus(Status["Loading"]);
    var badResponse = false;
    fetch(url)
    .then((response) => {
      var code = response.status;
      if (code >= 400) {
        badResponse = true;
        setCurStatus(Status["Error"]);
        return Promise.reject(response);
      }
      return response;
    })
    .then(response => response.json())
    .then(function(data) {
      props.resetTest("e");

      if (props.type === "remote") {
        props.setVocabList(data);
      } else if (props.type === "local") {
        props.setVocabList(reformat(data.rows));
      }
    })
    .catch(function(error) {
      // ERROR MODAL
      props.setErrorModal(true);
      setCurStatus(Status["Error"]);
      badResponse = true;
      props.setErrorModal(true);
    })
    .finally(function() {
      if (!badResponse) {
        setCurStatus(Status["Loaded"]);
      }
    });
  }

  if (curStatus == Status["Init"] && props.link !== "") {
    getVocabList(props.link);
  }

  // load vocab list when API key changes
  useEffect(() => {
    setCurStatus(Status["Init"]);
  }, [props.apiKey])

  const classes = useStyles();

  return(
    <React.Fragment>
    {(curStatus == Status["Loading"]) && (
      <Grid item className={classes.root}>
        <LinearProgress color={(props.type === "remote" ? "primary" : "secondary" )} />
      </Grid>
    )}
    {(curStatus == Status["Error"]) && (
      <React.Fragment>
        <Grid item>
          <Typography color="error">
            The list of Bioportal vocabularies is currently inaccessible
          </Typography>

          <Button variant="contained" color="primary" className={classes.button} onClick={getVocabList}>
            <Typography variant="button">Retry</Typography>
          </Button>
        </Grid>
      </React.Fragment>
     
    )}
    {(curStatus == Status["Loaded"] && props.displayTables) && (
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
