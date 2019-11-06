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
    borderLeftWidth: "0.6em",
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
  const [filterTable, setFilterTable] = React.useState(false);
  const [keywords, setKeywords] = React.useState("");
  const [loading, setLoading] = React.useState(false);

  const classes = useStyles();

  function search() {
    var errHappened = false;
    setLoading(true);

    fetch(vocabLinks["recommender"]["base"]+vocabLinks["apikey"]+"&input="+keywords.replace(" ", "%20"))
    .then((response) => {
      if (response.status >= 400) {
        errHappened = true;
      } else {
        return response;
      }
    })
    .then((response) => {
      if(!errHappened) {
        return response.json();
      }
    })
    .then((data) => {
      if(!errHappened) {
        extractList(data);
        props.setParentAcronymList(extractList(data));
        setFilterTable(true);
        props.setParentFilterTable(true);
      }
      setLoading(false);
    });
  }

  function clearSearch() {
    props.setParentFilterTable(false);
    props.setParentAcronymList([]);
    setKeywords("");
    setFilterTable(false);
  }

  function checkEnterKey(event) {
    if(event.keyCode == 13) {
      search();
    }
  }

  return(
    <React.Fragment>
      <Grid item>
        <Grid container alignItems="center" justify="space-between">
        
          <Grid item xs={12} sm={11}>
            <TextField
              fullWidth
              InputProps={{
                endAdornment: <InputAdornment position="end">
                                <IconButton onClick={search}>
                                  <SearchIcon/>
                                </IconButton>
                              </InputAdornment>
              }}
              label="Search by keywords"
              onChange={(event) => setKeywords(event.target.value)}
              onKeyDown={checkEnterKey}
              type="text"
              value={keywords}
              variant="outlined"
            />
            {loading && <LinearProgress/>}
          </Grid>

          <Grid item xs={12} sm={1}>
            <Button className={classes.clearButton} onClick={clearSearch} variant="contained" size="large" color="secondary">
              Clear
            </Button>
          </Grid>

        </Grid>
      </Grid>

      <Grid item>
        {(filterTable ?
        <React.Fragment> 
          <Typography variant="h3">Browse vocabularies matching </Typography>
          <Typography variant="h3" className={classes.keywords}>{keywords}</Typography>
        </React.Fragment> 
        : 
        <Typography variant="h3">Browse All</Typography>)}
      </Grid>
    </React.Fragment>
  );
}
