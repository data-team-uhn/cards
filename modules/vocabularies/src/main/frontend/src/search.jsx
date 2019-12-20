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
  IconButton,
  InputAdornment,
  LinearProgress,
  makeStyles,
  TextField,
  Typography
} from "@material-ui/core";

import SearchIcon from "@material-ui/icons/Search";

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
        // (1) Any of the search keywords is the vocabulary's acronym
        keywordsList.includes(vocab.ontology.acronym.toLowerCase())
        ||
        // (2) There is an intersection of the set of name words and the set of search keywords
        // The name words are also processed into a list of lower case words
        vocab.ontology.name.split(" ")
          .map(S => S.toLowerCase())
          .some(nameWord => keywordsList.includes(nameWord))
      // Finally return only the acronyms of the vocabularies that meet above criteria as a list
      )).map(vocab => vocab.ontology.acronym);

      if (acronymList.length > 0) {
        setFilterTable(true);
        props.setParentFilterTable(true);
        props.setParentAcronymList(acronymList);
      }

      // Then also make a request to recommender and update filtered list.
      let url = new URL(vocabLinks["recommender"]["base"]);
      url.searchParams.set("apikey", vocabLinks["apikey"]);
      url.searchParams.set("input", encodeURIComponent(keywords));
      fetch(url)
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

      <Grid item>
        {(filterTable ?
        <React.Fragment> 
          <Typography variant="h3">Browse vocabularies matching </Typography>
          <Typography variant="h3" className={classes.keywords}>{lastSearch}</Typography>
        </React.Fragment> 
        : 
        <Typography variant="h3">Browse All</Typography>)}
      </Grid>
    </React.Fragment>
  );
}
