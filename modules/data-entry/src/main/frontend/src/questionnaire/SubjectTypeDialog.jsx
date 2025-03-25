/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/

import React, { useState, useEffect } from "react";

import { Button, Grid, Dialog, DialogTitle, DialogActions, DialogContent, MenuItem, TextField, Typography, Select, FormHelperText } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

function SubjectTypeDialog(props) {
  const { open, onClose, onSuccess, data, isEdit, currentSubjectType, classes } = props;
  const initialParent = currentSubjectType?.["@path"].replace("/" + currentSubjectType["@name"], "") || "/SubjectTypes";
  const subjectTypes = !isEdit ? data : data.filter(item => item["jcr:uuid"] != currentSubjectType["jcr:uuid"]);

  const [ label, setLabel ] = useState("");
  const [ parent, setParent ] = useState(initialParent);
  const [ order, setOrder ] = useState(0);
  const [ subjectListLabel, setSubjectListLabel ] = useState("");
  const [ idPattern, setIdPattern ] = useState("");
  const [ idPatternHint, setIdPatternHint ] = useState("");

  const [ error, setError ] = useState(null);
  const [ isDuplicateLabel, setIsDuplicateLabel ] = useState(false);
  const [ isInvalidRegexp, setIsInvalidRegexp ] = useState(false);

  let validateLabel = (name) => {
    setError("");
    setIsDuplicateLabel(false);
    for (var i in subjectTypes) {
      if (subjectTypes[i].label === name && (!isEdit || currentSubjectType["label"] != name)) {
        setIsDuplicateLabel(true);
        return;
      }
    }
  }

  let validateRegexp = (pattern) => {
    setIsInvalidRegexp(false);
    try {
      new RegExp(pattern);
    } catch(e) {
      setIsInvalidRegexp(true);
    }
  }

  useEffect(() => {
    if (isEdit && currentSubjectType) {
      setLabel(currentSubjectType.label);
      setParent(initialParent);
      setOrder(currentSubjectType["cards:defaultOrder"] || 0);
      setSubjectListLabel(currentSubjectType?.subjectListLabel || "");
      setIdPattern(currentSubjectType?.idPattern || "");
      setIdPatternHint(currentSubjectType?.idPatternHint || "");
    }
  }, [currentSubjectType]);

  let handleSubjectType = () => {
    setError("");
    let formData = new FormData();

    let formInfo = {};
    formInfo["jcr:primaryType"] = "cards:SubjectType";
    formInfo["label"] = label;
    formInfo["cards:defaultOrder"] = order;
    formInfo["subjectListLabel"] = subjectListLabel;
    formInfo["idPattern"] = idPattern;
    formInfo["idPatternHint"] = idPatternHint;

    if (!isEdit) {
      formData.append(':contentType', 'json');
      formData.append(':operation', 'import');
      formData.append(':nameHint', label);
      formData.append(':content', JSON.stringify(formInfo));
    } else {
      if (currentSubjectType["cards:defaultOrder"] == order &&
          currentSubjectType["label"] === label &&
          currentSubjectType["subjectListLabel"] === subjectListLabel &&
          currentSubjectType["idPattern"] === idPattern &&
          currentSubjectType["idPatternHint"] === idPatternHint) {
        // if nothing changed except parent - just move the node
        if (initialParent != parent) {
          moveSubjectType();
          return;
        } else {
          close();
          return;
        }
      } else {
        // Update all the changes first
        formData.append("cards:defaultOrder", order);
        formData.append("label", label);
        formData.append("subjectListLabel", subjectListLabel);
        formData.append("idPattern", idPattern);
        formData.append("idPatternHint", idPatternHint);
      }
    }

    fetch(isEdit ? currentSubjectType["@path"] : parent, {
        method: 'POST',
        body: formData
    })
    .then((response) => {
        if (!response.ok) {
          setError(response.statusText);
          return;
        }

        // If parent changed we need to move the node
        if (isEdit && initialParent != parent) {
          moveSubjectType();
        } else {
          if (!isEdit) {
            formInfo["@name"] = label;
            formInfo["@path"] = parent + "/" + label;
            onSuccess(formInfo);
          } else {
            onSuccess();
          }
          close();
        }
    });
  }

  let moveSubjectType = () => {
    let formData = new FormData();
    formData.append(':operation', 'move');
    formData.append(':dest', !parent.endsWith("/") ? parent + "/" : parent);
    formData.append(':replace', true);

    fetch(currentSubjectType["@path"], {
        method: 'POST',
        body: formData
    })
    .then((response) => {
        if (!response.ok) {
          setError(response.statusText);
          return;
        }

        onSuccess();
        close();
    });
  }

  let close = () => {
    setError("");
    setLabel("");
    setParent("/SubjectTypes");
    setOrder(0);
    setSubjectListLabel("");
    setIdPattern("");
    setIdPatternHint("");
    setIsDuplicateLabel(false);
    onClose();
  }

  return (
    <Dialog
      maxWidth="sm"
      open={open}
      onClose={close}
    >
      <DialogTitle>{isEdit ? "Modify " + currentSubjectType.label : "Create New Subject Type"}</DialogTitle>
      <DialogContent>
        <Grid container justifyContent="flex-start" alignItems="center" spacing={2}>
          <Grid item xs={4}>
            <Typography>Label</Typography>
          </Grid>
          <Grid item xs={8}>
            <TextField
              variant="standard"
              fullWidth
              value={label}
              id="label"
              name="label"
              onChange={(event) => { setLabel(event.target.value); validateLabel(event.target.value); }}
              autoFocus
              error={isDuplicateLabel}
              helperText={isDuplicateLabel ? "This label already exists" : "Required*"}
            />
          </Grid>
          { (isEdit || subjectTypes && subjectTypes.length > 0) &&
            <>
              <Grid item xs={4}>
                <Typography>Parent</Typography>
              </Grid>
              <Grid item xs={8}>
                <Select
                  variant="standard"
                  disabled={isEdit && currentSubjectType.instanceCount != undefined && currentSubjectType.instanceCount > 0}
                  labelId="parent"
                  label="optional"
                  value={parent}
                  onChange={(event) => { setParent(event.target.value); setError(""); }}
                  displayEmpty
                >
                  <MenuItem key="none" value="/SubjectTypes">
                    <em>None</em>
                  </MenuItem>
                  { subjectTypes.map((option) =>
                      <MenuItem key={option["jcr:uuid"]} value={option["@path"]}>
                        {option.label}
                      </MenuItem>
                    )
                  }
                </Select>
                <FormHelperText>{isEdit && currentSubjectType.instanceCount > 0 && "There are already subjects of this type. The parent can no longer be changed"}</FormHelperText>
              </Grid>
            </>
          }
          <Grid item xs={4}>
            <Typography>Order</Typography>
          </Grid>
          <Grid item xs={8}>
            <TextField
              variant="standard"
              fullWidth
              type="number"
              inputProps={{min: 0}}
              value={order}
              onChange={(event) => { setOrder(event.target.value); setError(""); }}
            />
          </Grid>
          <Grid item xs={4}>
            <Typography>Subject list label</Typography>
          </Grid>
          <Grid item xs={8}>
            <TextField
              variant="standard"
              fullWidth
              type="text"
              value={subjectListLabel}
              onChange={(event) => { setSubjectListLabel(event.target.value); }}
            />
          </Grid>
          <Grid item xs={4}>
            <Typography>Subject Id Pattern</Typography>
          </Grid>
          <Grid item xs={8}>
            <TextField
              variant="standard"
              fullWidth
              type="text"
              value={idPattern}
              error={isInvalidRegexp}
              helperText={isInvalidRegexp ? "Invalid regex pattern" : ""}
              onChange={(event) => { setIdPattern(event.target.value); validateRegexp(event.target.value); }}
            />
            <FormHelperText>
              {isEdit && currentSubjectType.instanceCount > 0 && "There are already subjects of this type. Changing the pattern may cause inconsistencies with the existing subject identifiers."}
            </FormHelperText>
          </Grid>
          <Grid item xs={4}>
            <Typography>Subject Id Pattern Hint</Typography>
          </Grid>
          <Grid item xs={8}>
            <TextField
              variant="standard"
              fullWidth
              type="text"
              value={idPatternHint}
              onChange={(event) => { setIdPatternHint(event.target.value); }}
            />
          </Grid>
        </Grid>
        {error && <Typography color='error'>{error}</Typography>}
      </DialogContent>
      <DialogActions className={classes.dialogActions}>
        <Button variant="outlined" onClick={close}>Cancel</Button>
        <Button
          disabled={!isEdit && (!label || isDuplicateLabel)
                  || isEdit && (currentSubjectType["cards:defaultOrder"] == order &&
                                initialParent == parent &&
                                currentSubjectType["label"] == label &&
                                currentSubjectType["subjectListLabel"] == subjectListLabel &&
                                currentSubjectType?.["idPattern"] == idPattern &&
                                currentSubjectType?.["idPatternHint"] == idPatternHint
                                )
          }
          color="primary"
          variant="contained"
          onClick={(event) => { event.preventDefault(); handleSubjectType(); }}
         >
          { isEdit ? "Save" : "Create" }
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default withStyles(QuestionnaireStyle)(SubjectTypeDialog);
