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

import React, { useState, useContext } from "react";

import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  CircularProgress,
  ListItemText,
  Typography,
  Tooltip
} from "@mui/material";

import makeStyles from '@mui/styles/makeStyles';

import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

const Phase = require("./phaseCodes.json");

const useStyles = makeStyles(theme => ({
  vocabularyAction: {
    margin: theme.spacing(1),
    textTransform: "none"
  },
  buttonProgress: {
    top: "50%",
    left: "50%",
    position: "absolute",
    marginTop: -12,
    marginLeft: -12,
    textTransform: "none"
  },
  install: {
    background: theme.palette.success.main,
    color: theme.palette.success.contrastText,
    "&:hover": {
      background: theme.palette.success.dark
    }
  },
  uninstall: {
    background: theme.palette.error.main,
    color: theme.palette.error.contrastText,
    "&:hover": {
      background: theme.palette.error.dark
    }
  },
  installingColor: {
    color: theme.palette.success.main
  },
  uninstallingColor: {
    color: theme.palette.error.main
  },
  update: {
    background: theme.palette.warning.main,
    color: theme.palette.warning.contrastText,
    "&:hover": {
      background: theme.palette.warning.dark
    }
  },
  wrapper: {
    position: "relative",
    display: "inline-block"
  }
}));

export default function VocabularyAction(props) {
  const { install, uninstall, phase, vocabulary, exit } = props;
  const classes = useStyles();
  const [displayPopup, setDisplayPopup] = React.useState(false);
  const [linkedQuestions, setLinkedQuestions] = useState([]);
  const [questionnaires, setQuestionnaires] = useState([]);
  const handleOpen = () => {fetchQuestionnaires();}
  const handleClose = () => {setDisplayPopup(false);}
  const handleUninstall = () => {setDisplayPopup(false); uninstall();}

  const globalLoginDisplay = useContext(GlobalLoginContext);

  let fetchQuestionnaires = () => {
    if (questionnaires.length === 0) {
      // Send a fetch request to determine the questionnaires available
      const query = `select n.* from [cards:Questionnaire] as n inner join [cards:Question] as q on isdescendantnode(q, n) where q.sourceVocabularies = '${vocabulary.acronym}'`;
      fetchWithReLogin(globalLoginDisplay, `/query?query=${encodeURIComponent(query)}&limit=100`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {
          setQuestionnaires(json["rows"]);
          fetchData(json["rows"]);
        })
        .catch(function(err) {
          console.log("Something went wrong: " + err);
          // We were unable to fetch any questionnaire data possibly due to timeout in user authentication
          // thus, we have to abort uninstalling process
          return;
        });
    } else {
      fetchData(questionnaires);
    }
  };

  let fetchData = (questionnairesData) => {
    if (questionnairesData.length == 0) {
      setDisplayPopup(true);
    } else {
      setLinkedQuestions([]);
      let aggregatedQuestions = [];
      let i = 0;
      questionnairesData.forEach( (questionnaire) => {
        fetchWithReLogin(globalLoginDisplay, `${questionnaire["@path"]}.deep.json`)
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then((data) => {
            aggregatedQuestions = aggregatedQuestions.concat(getVocabularyQuestions(data, questionnaire.title));
          })
          .finally(() => {
            if (++i == questionnairesData.length) {
              setLinkedQuestions(aggregatedQuestions);
              setDisplayPopup(true);
            }
          });
      });
    }
  };

  let getVocabularyQuestions = (data, title) => {
    let vocQuestions = [];

    data && Object.entries(data)
      .forEach( ([key, value]) => {
        if (value["jcr:primaryType"] == "cards:Question" && value['dataType'] == 'vocabulary' && value['sourceVocabularies'].includes(vocabulary.acronym)) {
          value.questionnaireName = title;
          vocQuestions.push(value);
        }
        if (value["jcr:primaryType"] == "cards:Section") {
            vocQuestions = vocQuestions.concat(getVocabularyQuestions(value, title));
        }
      });

      return vocQuestions;
  }

  return(
    <React.Fragment>
    {(phase == Phase["Not Installed"]) && (
      <Tooltip title="Install this vocabulary">
        <Button onClick={install} variant="contained" className={classes.vocabularyAction + " " + classes.install}>Install</Button>
      </Tooltip>
    )}
    {(phase == Phase["Installing"]) && (
      <span className={classes.wrapper}>
        <Button disabled variant="contained" className={classes.vocabularyAction}>Installing</Button>
        <CircularProgress size={24} className={classes.buttonProgress + " " + classes.installingColor} />
      </span>
    )}
    {(phase == Phase["Update Available"]) && (
      <React.Fragment>
        <Tooltip title="Update this vocabulary">
          <Button onClick={install} variant="contained" className={classes.vocabularyAction + " " + classes.update}>Update</Button>
        </Tooltip>
        <Tooltip title="Remove this vocabulary">
          <Button onClick={uninstall} variant="contained" className={classes.vocabularyAction + " " + classes.uninstall}>Uninstall</Button>
        </Tooltip>
      </React.Fragment>
    )}
    {(phase == Phase["Uninstalling"]) && (
      <span className={classes.wrapper}>
        <Button disabled variant="contained" className={classes.vocabularyAction}>Uninstalling</Button>
        <CircularProgress size={24} className={classes.buttonProgress + " " + classes.uninstallingColor} />
      </span>
    )}
    {(phase == Phase["Latest"]) && (
      <Tooltip title="Remove this vocabulary">
        <Button onClick={handleOpen} variant="contained" className={classes.vocabularyAction + " " + classes.uninstall}>Uninstall</Button>
      </Tooltip>
    )}
    {exit && (
      <Tooltip title="Close">
        <Button onClick={exit} variant="outlined" className={classes.vocabularyAction}>Close</Button>
      </Tooltip>
    )}
    <Dialog onClose={handleClose} open={displayPopup}>

      <DialogTitle>
        {vocabulary.name} ({vocabulary.acronym})
      </DialogTitle>

      <DialogContent dividers>
        {(linkedQuestions.length > 0) && (
          <span className={classes.wrapper}>
          <Typography variant="body1">The following variables are linked to this vocabulary:</Typography>
          <ul>
            {linkedQuestions.map((question, index) => {
              return (
                <li key={index}>
                  <ListItemText primary={question.text + " (" + question.questionnaireName + ")"}>
                  </ListItemText>
                </li>
              );
            })}
          </ul>
          </span>
        )}
        {(linkedQuestions.length == 0) && (
          <Typography variant="body1">No variables are linked to this vocabulary.</Typography>
        )}

        <Typography variant="body1">Uninstalling this vocabulary may result in data not being properly standardized. Proceed?</Typography>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleUninstall} variant="contained" color="primary" className={classes.vocabularyAction + " " + classes.uninstall}>Uninstall</Button>
        <Button onClick={handleClose} variant="outlined" className={classes.vocabularyAction}>Cancel</Button>
      </DialogActions>

    </Dialog>
    </React.Fragment>
  );
}
