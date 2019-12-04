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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  makeStyles,
  Tooltip,
  Typography,
  Zoom
} from "@material-ui/core";

import CloseIcon from "@material-ui/icons/Close";

import VocabularyAction from "./vocabularyAction";

const useStyles = makeStyles(theme => ({
  about: {
    background: "#007bff",
    "&:hover": {
      background: "#2361b8"
    }
  },
  button: {
    margin: theme.spacing(1),
    textTransform: "none",
    color: "white",
    borderRadius: 3,
    border: 0
  },
  closeButton: {
    position: "absolute",
    right: theme.spacing(1),
    top: theme.spacing(1),
    marginLeft: theme.spacing(5),
    color: theme.palette.grey[500],
  },
  dialogTitle: {
    marginRight: theme.spacing(5)
  },
  root: {
    margin: 0,
    padding: theme.spacing(2),
  }
}));


export default function VocabularyDetails(props) {
  const [displayPopup, setDisplayPopup] = React.useState(false);
  const handleOpen = () => {setDisplayPopup(true);}
  const handleClose = () => {setDisplayPopup(false);}

  const classes = useStyles();

  return(
    <React.Fragment>

      <Tooltip title="About this vocabulary" TransitionComponent={Zoom}>
        <Button onClick={handleOpen} variant="contained" className={classes.button + " " + classes.about} >About</Button>
      </Tooltip>

      <Dialog onClose={handleClose} open={displayPopup}>

        <DialogTitle disableTypography onClose={handleClose} className={classes.root}>
          <Typography variant="h4" className={classes.dialogTitle}>{props.acronym}</Typography>
          <IconButton className={classes.closeButton} onClick={handleClose}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>

        <DialogContent dividers>
          <Typography variant="h6">{props.name}</Typography>
          <Typography variant="body1">{props.description}</Typography>
        </DialogContent>

        <DialogActions>
          <VocabularyAction acronym={props.acronym} install={props.install} uninstall={props.uninstall} phase={props.phase} />
        </DialogActions>

      </Dialog>

    </React.Fragment>
    );
}
