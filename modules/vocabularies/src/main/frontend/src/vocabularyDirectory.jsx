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

import React from "react";

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
  Object.filter = (obj, predicate) => Object.keys(obj)
                                     .filter( key => predicate(obj[key]) )
                                     .reduce( (res, key) => (res[key] = obj[key], res), {} );
  var filtered = Object.filter(data, (val) => ((typeof val) == "object") && 
                                     (val.hasOwnProperty("jcr:primaryType")) && 
                                     (val["jcr:primaryType"] === "lfs:Vocabulary"));
  // Filtered contains all properties of type object that have jcr:primaryType = lfs:Vocabulary
  var vocabularies = [];
  Object.keys(filtered).map((key) => vocabularies.push({
    ontology: {
      acronym: key,
      name: filtered[key]["name"]
    },
    released: filtered[key]["version"],
    description: filtered[key]["description"]
  }));
  return vocabularies;
}

// Requests list of Vocabularies from Bioontology API. Currently only renders a table to display items if they are form the remote source
export default function VocabularyDirectory(props) {
  const [curStatus, setCurStatus] = React.useState(Status["Init"]);

  // Function that fetches list of Vocabularies from Bioontology API
  function getVocabList() {
    setCurStatus(Status["Loading"]);
    var badResponse = false;
    fetch(props.link)
    .then((response) => {
      var code = response.status;
      if (code >= 400) {
        badResponse = true;
        setCurStatus(Status["Error"]);
      }
      return response;
    })
    .then((response) => (badResponse ? {} : response.json()))
    .then(function(data) {
      if (!badResponse) {
        if (props.type === "remote") {
          props.setVocabList(data);
        } else if (props.type === "local") {
          props.setVocabList(reformat(data));
        }
      }
    })
    .catch(function(error) {
      setCurStatus(Status["Error"]);
      badResponse = true;
    })
    .finally(function() {
      if (!badResponse) {
        setCurStatus(Status["Loaded"]);
      }
    });
  }

  if (curStatus == Status["Init"]) {
    getVocabList();
  }

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
    {(curStatus == Status["Loaded"] && !(typeof props.optimisedDateList === "undefined") && props.type === "remote") && (
      <VocabularyTable
        remoteVocabList={props.remoteVocabList}
        optimisedDateList={props.optimisedDateList} 
      />
    )}
    </React.Fragment>
  );
}
