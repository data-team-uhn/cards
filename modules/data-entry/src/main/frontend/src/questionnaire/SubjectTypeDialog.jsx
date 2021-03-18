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

import { Button, Grid, Dialog, DialogTitle, DialogActions, DialogContent, InputLabel, MenuItem, TextField, Typography, Select, withStyles } from "@material-ui/core";

import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

function SubjectTypeDialog(props) {
  const { open, onClose, onSubmit, subjects, isEdit, editSubject, classes } = props;
  const initialParent = editSubject?.["parents"]?.["@path"] || "/SubjectTypes";
  let subjectTypes = !isEdit ? subjects : subjects.filter(item => item["jcr:uuid"] != editSubject["jcr:uuid"]);

  const [ newName, setNewName ] = useState("");
  const [ parentSubject, setParentSubject ] = useState(initialParent);
  const [ order, setOrder ] = useState(editSubject && editSubject["lfs:defaultOrder"] ? editSubject["lfs:defaultOrder"] : 0);
  const [ error, setError ] = useState(null);
  const [ isDuplicateName, setIsDuplicateName ] = useState(false);

  let validateName = (name) => {
    setIsDuplicateName(false);
    for (var i in subjects) {
      if (subjects[i].label === name) {
        setIsDuplicateName(true);
        return;
      }
    }
  }

  let handleSubjectType = () => {
    setError("");
    let formData = new FormData();

    if (!isEdit) {
      let formInfo = {};
      formInfo["jcr:primaryType"] = "lfs:SubjectType";
      formInfo["label"] = newName;
      formInfo["lfs:defaultOrder"] = order;

      formData.append(':contentType', 'json');
      formData.append(':operation', 'import');
      formData.append(':nameHint', newName);
      formData.append(':content', JSON.stringify(formInfo));
    } else {
      if (editSubject["lfs:defaultOrder"] == order) {
        moveSubjectType();
        return;
      } else {
        // Update the order first
        formData.append("lfs:defaultOrder", order);
      }
    }

    fetch(isEdit ? editSubject["@path"] : parentSubject, {
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

    fetch(editSubject["@path"], {
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
    setNewName("");
    setParentSubject("");
    setOrder(0);
    setIsDuplicateName(false);
    onClose();
  }

  return (
    <Dialog
      maxWidth="xs"
      open={open}
      onClose={onClose}
    >
      <DialogTitle>{isEdit ? "Modify " + editSubject.label : "Create New Subject Type"}</DialogTitle>
      <DialogContent>
        <Grid container justify="flex-start" alignItems="center" spacing={2}>
          <Grid item xs={4}>
            <Typography>Name</Typography>
          </Grid>
          <Grid item xs={8}>
            <TextField
                fullWidth
                disabled={isEdit}
                value={isEdit ? editSubject.label : newName}
                id="name"
                name="name"
                onChange={(event) => { setNewName(event.target.value); setError(""); validateName(event.target.value); }}
                autoFocus
                error={isDuplicateName}
                helperText={isDuplicateName ? "This name already exists" : "Required*"}
            />
          </Grid>
          { (isEdit || subjectTypes && subjectTypes.length > 0) &&
            <>
              <Grid item xs={4}>
                <Typography>Parent</Typography>
              </Grid>
              <Grid item xs={8}>
                <Select
                  disabled={isEdit && editSubject["@referenced"]}
                  labelId="parent"
                  label="optional"
                  value={parentSubject}
                  onChange={(event) => setParentSubject(event.target.value)}
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
              </Grid>
            </>
          }
          <Grid item xs={4}>
            <Typography>Order</Typography>
          </Grid>
          <Grid item xs={8}>
            <TextField
              type="number"
              inputProps={{min: 0}}
              value={order}
              onChange={(event) => setOrder(event.target.value)}
            />
          </Grid>
        </Grid>
        {error && <Typography color='error'>{error}</Typography>}
      </DialogContent>
      <DialogActions className={classes.dialogActions}>
        <Button
          disabled={!isEdit && (!newName || isDuplicateName) || isEdit && (editSubject["lfs:defaultOrder"] == order && initialParent == parentSubject)}
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
