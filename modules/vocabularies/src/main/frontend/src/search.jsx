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

import { styled } from '@mui/material/styles';

import {
  Button,
  CircularProgress,
  Grid,
  IconButton,
  InputAdornment,
  LinearProgress,
  TextField,
  makeStyles
} from "@material-ui/core";

import SearchIcon from "@material-ui/icons/Search";
import CloseIcon from "@material-ui/icons/Close";

import fetchBioPortalApiKey from "./bioportalApiKey";
import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

const PREFIX = 'search';

const classes = {
  searchAdornmentWrapper: `${PREFIX}-searchAdornmentWrapper`,
  searchProgress: `${PREFIX}-searchProgress`
};

// TODO jss-to-styled codemod: The Fragment root was replaced by div. Change the tag if needed.
const Root = styled('div')((
  {
    theme
  }
) => ({
  [`& .${classes.searchAdornmentWrapper}`]: {
    marginRight: theme.spacing(-1),
    position: 'relative',
  },

  [`& .${classes.searchProgress}`]: {
    position: 'absolute',
    top: theme.spacing(0.5),
    left: theme.spacing(0.5),
    zIndex: 1,
  }
}));

const vocabLinks = require('./vocabularyLinks.json');

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
  const [keywords, setKeywords] = React.useState("");
  const [lastSearch, setLastSearch] = React.useState("");
  const [loading, setLoading] = React.useState(false);


  const globalLoginDisplay = useContext(GlobalLoginContext);

  // Search using the currently entered keywords
  function search() {
    // Prevent a race condition by disallowing searches while a search is underway
    if (!loading) {
      setError(false);
      setLoading(true);
      props.setLoading(true);
      setLastSearch(keywords);
      props.setAcronymFilterList([]);

      fetchBioPortalApiKey(globalLoginDisplay, (apiKey) => {
        // Then also make a request to recommender and update filtered list.
        let url = new URL(vocabLinks["recommender"]["base"]);
        url.searchParams.set("apikey", apiKey);
        url.searchParams.set("input", encodeURIComponent(keywords));
        fetchWithReLogin(globalLoginDisplay, url)
          .then((response) => (response.ok ? response.json() : Promise.reject(response)))
          .then((data) => {
            props.setAcronymFilterList(extractList(data));
            props.setParentFilterTable(true);
          })
          .catch(() => {
            setError(true);
          })
          .finally(() => {
            setLoading(false);
            props.setLoading(false);
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
    props.setAcronymFilterList([]);
    if (keywords !== "") {
      setKeywords("");
    }
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

  return (
    <Root>
      <Grid item>
        <TextField
          fullWidth
          helperText={(error ? "Request Failed" : "")}
          InputProps={{
            endAdornment: <InputAdornment position="end">
                            { keywords &&
                              <IconButton onClick={reset} size="small">
                                <CloseIcon/>
                              </IconButton>
                            }
                            <div className={classes.searchAdornmentWrapper}>
                              <IconButton onClick={keywords === "" ? reset: search} size="large">
                                <SearchIcon/>
                              </IconButton>
                              {loading && <CircularProgress className={classes.searchProgress} />}
                            </div>
                          </InputAdornment>
          }}
          label="Search BioPortal by keywords"
          helperText="Search BioPortal for vocabularies mentioning a specific concept, e.g. “Microcephaly”"
          onChange={(event) => setKeywords(event.target.value)}
          onKeyDown={handleSearchInput}
          type="text"
          value={keywords}
          variant="outlined"
        />
      </Grid>
    </Root>
  );
}
