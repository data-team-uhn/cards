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

import React, { useEffect, useState } from "react";
import PropTypes from "prop-types";
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from "@mui/material";

import ClearIcon from '@mui/icons-material/Clear';

let AnswerOptionSuggestions = (props) => {
  const {
    open,
    suggestions,
    existingOptions,
    onUpdate,
    onClose
  } = props;

  let [ selection, setSelection ] = useState(Object.assign({}, suggestions ?? {}));
  let [ invalidOptions, setInvalidOptions ] = useState();

  useEffect(()=>{
    if (!open || !existingOptions?.length) return;
    setInvalidOptions(existingOptions.filter(o => !suggestions[o.value]));
  }, [open, existingOptions, suggestions]);

  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Answer option suggestions</DialogTitle>
      <DialogContent>
        { invalidOptions?.length > 0 &&
          <Alert severity="error">
            Some answer options that have been specified for this question
            are not valid with the new settings and will be removed:
            <List dense>
            { invalidOptions.map( item => (
              <ListItem key={item.value}>
                <ListItemIcon><ClearIcon/></ListItemIcon>
                <ListItemText primary={`${item.value} = ${item.label}`} />
              </ListItem>
            ))}
            </List>
          </Alert>
        }
        The following answer options are suggested for this question
        based on the current question definition. Please select the
        options you would like to keep:
        <List dense>
        { Object.entries(suggestions).map( ([key, label]) => (
          <ListItemButton
            key={key}
            role="listitem"
            onClick={() => setSelection(oldSelection => ({
              ...oldSelection,
              [key]: (oldSelection[key] ? undefined : suggestions[key])
            }))}
          >
            <ListItemIcon>
              <Checkbox
                checked={!!selection[key]}
                tabIndex={-1}
                disableRipple
              />
            </ListItemIcon>
            <ListItemText
              primary={`${key} = ${label}`}
              secondary={existingOptions?.some(o => o.value == key) ? "Current answer option" : undefined}
            />
          </ListItemButton>
        ))}
        </List>
      </DialogContent>
      <DialogActions>
        <Button
          variant="outlined"
          onClick={onClose}
        >
          Ignore
        </Button>
        <Button
          variant="contained"
          onClick={() => {
            onUpdate?.(selection);
            onClose?.()
          }}
        >
          Update
        </Button>
      </DialogActions>
    </Dialog>
  )
}

AnswerOptionSuggestions.propTypes = {
  open: PropTypes.bool,
  suggestions: PropTypes.object.isRequired,
  existingOptions: PropTypes.array,
  onMerge: PropTypes.func,
  onReplace: PropTypes.func,
  onIgnore: PropTypes.func,
};

export default AnswerOptionSuggestions;
