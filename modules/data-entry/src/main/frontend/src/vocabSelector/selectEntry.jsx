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
import React, { useState } from "react";
import PropTypes from "prop-types";
// @material-ui/core
import { Checkbox, FormControlLabel, IconButton, ListItem, withStyles, Typography, Radio } from "@material-ui/core"
import Close from "@material-ui/icons/Close"
import SelectorStyle from "./selectorStyle.jsx"

// Child selection element of a vocabulary term
//
// Required arguments:
//  name: Label of the term
//  id: Internal ID of the entry
//  onClick: Callback when the control for this term is clicked (generally to select it)
//  disabled: Whether or not this element is disabled
//  isRadio: Whether or not this element should have a radio (true) or a checkbox (false) beside it
//
// Optional arguments:
//  isPreselected: Whether or not this term is "preselected", i.e. non-deletable (default: false)
function VocabularyEntry(props) {
  const [checked, setChecked] = useState(false);
  const {classes, name, id, isPreselected, onClick, disabled, isRadio} = props;

  let toggleCheck = () => {
    setChecked(!checked);
  }

  return (
    <React.Fragment>
      <ListItem key={name} className={classes.selectionChild}>
          { /* This is either a Checkbox if this is a suggestion, or a button otherwise */
          isPreselected ?
            (
              <FormControlLabel
                control={
                  isRadio ?
                  (
                    <Radio
                      onChange={() => {onClick(id, name, checked);}}
                      disabled={!checked && disabled}
                      className={classes.checkbox}
                    />
                  ) :
                  (
                    <Checkbox
                      checked={checked}
                      onChange={() => {toggleCheck(); onClick(id, name, checked);}}
                      disabled={!checked && disabled}
                      className={classes.checkbox}
                    />
                  )
                }
                label={name}
                value={id}
                className={classes.childFormControl}
                classes={{
                  label: classes.inputLabel
                }}
              />
            ) : (
            <React.Fragment>
              <IconButton
                onClick={() => {onClick(id, name)}}
                className={classes.deleteButton}
                color="secondary"
                title="Delete"
              >
                <Close color="action" className={classes.deleteIcon}/>
              </IconButton>
              <div className={classes.inputLabel}>
                <Typography>
                  {name}
                </Typography>
              </div>
            </React.Fragment>
          )
          }
      </ListItem>
      {
        /* Add the hidden inputs if this is a user input selection (i.e. !isPreselected)
            or if this is a suggestion that is checked */
        (!isPreselected || checked) ?
        (
        <React.Fragment>
          <input type="hidden" name="name" value={name} />
          <input type="hidden" name="id" value={id} />
        </React.Fragment>
        ) : ""
      }
    </React.Fragment>
  );
};

VocabularyEntry.propTypes = {
  name: PropTypes.string.isRequired,
  id: PropTypes.string.isRequired,
  onClick: PropTypes.func.isRequired,
  disabled: PropTypes.bool,
  isRadio: PropTypes.bool,
  isPreselected: PropTypes.bool
}

VocabularyEntry.defaultProps = {
  isPreselected: false,
};

export default withStyles(SelectorStyle)(VocabularyEntry);
