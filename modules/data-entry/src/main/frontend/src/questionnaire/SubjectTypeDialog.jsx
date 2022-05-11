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

import React, { useState } from "react";

import {
  Button,
  Grid,
  Dialog,
  DialogTitle,
  DialogActions,
  DialogContent,
  InputLabel,
  MenuItem,
  TextField,
  Typography,
  Select,
  FormHelperText,
} from "@material-ui/core";

import withStyles from '@material-ui/styles/withStyles';

import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

function SubjectTypeDialog(props) {
  const { open, onClose, onSubmit, data, isEdit, currentSubjectType, classes } = props;
  const initialParent = currentSubjectType?.["@path"].replace("/" + currentSubjectType["@name"], "") || "/SubjectTypes";
  const subjectTypes = !isEdit ? data : data.filter(item => item["jcr:uuid"] != currentSubjectType["jcr:uuid"]);

  const [ label, setLabel ] = useState(isEdit ? currentSubjectType.label : "");
  const [ parentSubject, setParentSubject ] = useState(initialParent);
  const [ order, setOrder ] = useState(currentSubjectType && currentSubjectType["cards:defaultOrder"] ? currentSubjectType["cards:defaultOrder"] : 0);
  const [ subjectListLabel, setSubjectListLabel ] = useState(currentSubjectType?.subjectListLabel || "");
  const [ error, setError ] = useState(null);
  const [ isDuplicateLabel, setIsDuplicateLabel ] = useState(false);

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

  let handleSubjectType = () => {
    setError("");
    let formData = new FormData();

    if (!isEdit) {
      let formInfo = {};
      formInfo["jcr:primaryType"] = "cards:SubjectType";
      formInfo["label"] = label;
      formInfo["cards:defaultOrder"] = order;
      formInfo["subjectListLabel"] = subjectListLabel;

      formData.append(':contentType', 'json');
      formData.append(':operation', 'import');
      formData.append(':nameHint', label);
      formData.append(':content', JSON.stringify(formInfo));
    } else {
      // if nothing changed - just move the node
      if (currentSubjectType["cards:defaultOrder"] == order && currentSubjectType["label"] === label && currentSubjectType["subjectListLabel"] === subjectListLabel) {
        moveSubjectType();
        return;
      } else {
        // Update all the changes first
        formData.append("cards:defaultOrder", order);
        formData.append("label", label);
        formData.append("subjectListLabel", subjectListLabel);
      }
    }

    fetch(isEdit ? currentSubjectType["@path"] : parentSubject, {
        method: 'POST',
        body: formData
    })
    .then((response) => {
        if (!response.ok) {
          setError(response.statusText);
          return;
        }

        // If parent changed we need to move the node
        if (isEdit && initialParent != parentSubject) {
          moveSubjectType();
        } else {
          onSubmit();
        }
    });
  }

  let moveSubjectType = () => {
    let formData = new FormData();
    formData.append(':operation', 'move');
    formData.append(':dest', !parentSubject.endsWith("/") ? parentSubject + "/" : parentSubject);
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

        onSubmit();
    });
  }

  let close = () => {
    setError("");
    setLabel("");
    setParentSubject("");
    setOrder(0);
    setSubjectListLabel("")
    setIsDuplicateLabel(false);
    onClose();
  }

  return (
    <Dialog
      maxWidth="xs"
      open={open}
      onClose={onClose}
    >
      <DialogTitle>{isEdit ? "Modify " + currentSubjectType.label : "Create New Subject Type"}</DialogTitle>
      <DialogContent>
        <Grid container justify="flex-start" alignItems="center" spacing={2}>
          <Grid item xs={4}>
            <Typography>Label</Typography>
          </Grid>
          <Grid item xs={8}>
            <TextField
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
                  disabled={isEdit && currentSubjectType.instanceCount != undefined && currentSubjectType.instanceCount > 0}
                  labelId="parent"
                  label="optional"
                  value={parentSubject}
                  onChange={(event) => { setParentSubject(event.target.value); setError(""); }}
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
              fullWidth
              type="text"
              value={subjectListLabel}
              onChange={(event) => { setSubjectListLabel(event.target.value); setError(""); }}
            />
          </Grid>
        </Grid>
        {error && <Typography color='error'>{error}</Typography>}
      </DialogContent>
      <DialogActions className={classes.dialogActions}>
        <Button
          disabled={!isEdit && (!label || isDuplicateLabel)
                  || isEdit && (currentSubjectType["cards:defaultOrder"] == order &&
                                initialParent == parentSubject &&
                                currentSubjectType["label"] == label &&
                                currentSubjectType["subjectListLabel"] == subjectListLabel)
          }
          color="primary"
          variant="contained"
          size="small"
          onClick={(event) => { event.preventDefault(); handleSubjectType(); }}
         >
          { isEdit ? "Save" : "Create" }
        </Button>
        <Button variant="contained" size="small" onClick={close}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

export default withStyles(QuestionnaireStyle)(SubjectTypeDialog);
