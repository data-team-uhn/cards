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
import React, { useState, useEffect, useContext }  from 'react';
import { v4 as uuidv4 } from 'uuid';

import {
  CircularProgress,
  Grid,
  List,
  ListItem,
  ListItemText,
  Typography
} from '@mui/material';
import { Link } from 'react-router-dom';

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

// Todo: make the component more scalable once the number of QuestionnaireSet a grows
// and fetching/displaying them needs pagination
function QuestionnaireSets(props) {
  const [ surveys, setSurveys ] = useState();
  const [ error, setError ] = useState("");

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // At startup, load available surveys
  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, "/query?query=" + encodeURIComponent("SELECT * FROM [cards:QuestionnaireSet]"))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setSurveys(json.rows || []);
      })
      .catch((response) => {
        setError(`Loading the available surveys failed with error code ${response.status}: ${response.statusText}`);
      });
  }, []);

  let content = null;

  if (error) {
    content = <Typography variant="subtitle1" color="error">{error}</Typography>;
  } else if (!surveys) {
    content = <CircularProgress />;
  } else {
    content = (
      <List>
        <ListItem key="title"><Typography variant="h6">Surveys</Typography></ListItem>
        { surveys.map((s, i) => (
          <ListItem key={i}><Link to={`/Proms.html/${s['@name']}`} underline="hover">{s.name}</Link></ListItem>
        ))}
      </List>
    );
  }

  return (
    <Grid container direction="column" alignItems="center" justifyContent="center">
      <Grid item xs={12}>
        {content}
      </Grid>
    </Grid>
  );
}

export default QuestionnaireSets;
