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

import React, { useContext } from "react";

import {
  Button,
  Grid,
  IconButton,
  InputAdornment,
  LinearProgress,
  makeStyles,
  TextField,
  Typography
} from "@material-ui/core";

import SearchIcon from "@material-ui/icons/Search";

import fetchBioPortalApiKey from "./bioportalApiKey";
import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

const vocabLinks = require('./vocabularyLinks.json');

const useStyles = makeStyles(theme => ({
  clearButton: {
    margin: theme.spacing(1),
    textTransform: "none",
    color: "white",
    borderRadius: 3,
    border: 0,
    fontSize: "1.25em"
  },
  keywords: {
    paddingLeft: "0.5em",
    fontStyle: "italic",
    borderLeftStyle: "solid",
    borderLeftColor: "#DCDCDC",
    borderLeftWidth: "0.5em",
    fontSize: "2em"
  }
}));

function extractList(data) {
  var acronymList = [];
  if(Array.isArray(data)) {
    data.map((result) => {
      result.ontologies.map((ontology) => {
        acronymList.push(ontology.acronym);
      });
    });
  }
  return acronymList;
}

export default function Search(props) {
  const [error, setError] = React.useState(false);
  const [filterTable, setFilterTable] = React.useState(false);
  const [keywords, setKeywords] = React.useState("");
  const [lastSearch, setLastSearch] = React.useState("");
  const [loading, setLoading] = React.useState(false);

  const classes = useStyles();
  const globalLoginDisplay = useContext(GlobalLoginContext);

  // Search using the currently entered keywords
  function search() {
    // Prevent a race condition by disallowing searches while a search is underway
    if (!loading) {
      setError(false);
      setLoading(true);
      setLastSearch(keywords);
      // First check if any of the keywords match a vocabulary name or acronym

      // Process keywords of search into a list of lower case words
      let keywordsList = keywords.split(" ").map(keyword => keyword.toLowerCase());

      // Filter the list for vocabularies that meet either of 2 criteria
      let acronymList = props.vocabList.filter(vocab => (
        keywordsList.some(word => word.includes(
             // (1) Any of the search keywords contains the vocabulary's acronym
             vocab.acronym.toLowerCase()) ||
             // (2) Vocab name contains any of the search keywords as a part of it
             vocab.name.toLowerCase().includes(word))
       // Finally return only the acronyms of the vocabularies that meet above criteria as a list
       )).map(vocab => vocab.acronym);

      setFilterTable(true);
      props.setParentAcronymList(acronymList);
      props.setParentFilterTable(true);

      fetchBioPortalApiKey(globalLoginDisplay, (apiKey) => {
        // Then also make a request to recommender and update filtered list.
        let url = new URL(vocabLinks["recommender"]["base"]);
        url.searchParams.set("apikey", apiKey);
        url.searchParams.set("input", encodeURIComponent(keywords));
        fetchWithReLogin(globalLoginDisplay, url)
          .then((response) => (response.ok ? response.json() : Promise.reject(response)))
          .then((data) => {
            props.concatParentAcronymList(extractList(data));
            setFilterTable(true);
            props.setParentFilterTable(true);
          })
          .catch(() => {
            setError(true);
          })
          .finally(() => {
            setLoading(false);
          });
        },
        () => {
            setError(true);
        }
      );
    }
  }

  // Clear the search
  function reset() {
    props.setParentFilterTable(false);
    props.setParentAcronymList([]);
    if (keywords !== "") {
      setKeywords("");
    }
    setFilterTable(false);
  }

  // Checks if return is pressed, and searches/resets the search
  function handleSearchInput(event) {
    if(event.keyCode == 13) {
      if (keywords === "") {
        reset();
      } else {
        search();
      }
    }
  }

  return(
    <React.Fragment>
      <Grid item>
        <Grid container alignItems="center" justify="space-between">
          <Grid item xs={12} sm={11}>
            <TextField
              fullWidth
              helperText={(error ? "Request Failed" : "")}
              InputProps={{
                endAdornment: <InputAdornment position="end">
                                <IconButton onClick={keywords === "" ? reset: search}>
                                  <SearchIcon/>
                                </IconButton>
                              </InputAdornment>
              }}
              label="Search by keywords"
              onChange={(event) => setKeywords(event.target.value)}
              onKeyDown={handleSearchInput}
              type="text"
              value={keywords}
              variant="outlined"
            />
            {loading && <LinearProgress/>}
          </Grid>

          <Grid item xs={12} sm={1}>
            <Button className={classes.clearButton} onClick={reset} variant="contained" size="large" color="secondary">
              Clear
            </Button>
          </Grid>

        </Grid>
      </Grid>
    </React.Fragment>
  );
}
