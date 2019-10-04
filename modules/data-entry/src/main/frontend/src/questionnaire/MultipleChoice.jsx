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

import React, { useState, useEffect } from 'react';

import { Checkbox, FormControlLabel, IconButton, List, ListItem, Radio, RadioGroup, TextField, Typography, withStyles } from "@material-ui/core";
import Close from "@material-ui/icons/Close";
import PropTypes from 'prop-types';

import Answer, {NAME_POS, ID_POS} from "./Answer";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

// Position used to read whether or not an option is a "default" suggestion (i.e. one provided by the questionnaire)
const IS_DEFAULT_POS = 2;
// Sentinel value used for the user-controlled input
const GHOST_SENTINEL = "custom-input";

function MultipleChoice(props) {
  let { classes, ghostAnchor, max, min, defaults, input, textbox, ...rest } = props;
  const [selection, setSelection] = useState([]);
  const [ghostName, setGhostName] = useState("&nbsp;");
  const [ghostValue, setGhostValue] = useState(GHOST_SENTINEL);
  const [options, setOptions] = useState([]);
  const ghostSelected = selection.some(element => {return element[ID_POS] === GHOST_SENTINEL;});
  const isRadio = max === 1;
  const disabled = selection.length >= max && !isRadio;
  let inputEl = null;

  // On startup, convert our defaults into a list of useable options
  useEffect( () => {
    let newOptions = defaults.map( (defaultOption) => {
      if (!("id" in defaultOption)) {
        console.log("Malformed default option: " + JSON.stringify(defaultOption));
        return ['', '', true];
      }
      let id = defaultOption["id"];
      let label = ("label" in defaultOption ? defaultOption["label"] : id);
      return ([label, id, true]); // label, id, default
    });
    setOptions(newOptions);
  }, [defaults]);

  let selectOption = (id, name, checked = false, removeSentinel = false) => {
    if (isRadio) {
      setSelection([[name, id]]);
      return;
    }

    // If the element was already checked, remove it instead
    if (checked) {
      return unselect(id, name);
    }

    // Do not add anything if we are at our maximum number of selections
    if (selection.length >= max && !removeSentinel) {
      return;
    }

    // Do not add duplicates
    if (selection.some(element => {return element[ID_POS] === id})) {
      return;
    }

    let newSelection = selection.slice();
    if (removeSentinel) {
      // Due to how React handles state, we need to do this in one step
      newSelection = newSelection.filter(
        (element) => {
          return (element[ID_POS] !== GHOST_SENTINEL);
        }
      );
    }
    newSelection.push([name, id]);
    setSelection(newSelection);
  }

  let unselect = (id, name) => {
    return setSelection(selection.filter(
      (element) => {
        return !(element[ID_POS] === id && element[NAME_POS] === name)
      }
    ));
  }

  let updateGhost = (id, name) => {
    // If we're a radio, just update with the new value
    if (isRadio) {
      setSelection([[name, id]]);
      return;
    }

    let ghostIndex = selection.findIndex(element => {return element[ID_POS] === GHOST_SENTINEL});
    let newSelection = selection.slice();
    // If the ghost is already selected, update it. Otherwise, append it.
    if (ghostIndex >= 0) {
      newSelection[ghostIndex] = [name, id];
    } else {
      newSelection.push([name, id]);
    }
    setSelection(newSelection);
  }

  // Add a non-default option
  let addOption = (id, name) => {
    let newOptions = options.slice();
    newOptions.push([name, id, false]);
    setOptions(newOptions);
  }

  // Remove a non-default option
  let removeOption = (id, name) => {
    setOptions(options.filter(
      (option) => {
        return !(option[ID_POS] === id && option[NAME_POS] === name)
      }
    ));
    unselect(id, name);
    return;
  }

  // Hold the input box for either multiple choice type
  let ghostInput = input && (<div className={classes.searchWrapper}>
      <TextField
        className={classes.textField}
        onChange={(event) => {
          setGhostName(event.target.value);
          updateGhost(GHOST_SENTINEL, event.target.value);
          onChange && onChange(event.target.value);
        }}
        onFocus={() => {max === 1 && selectOption(ghostValue, ghostName)}}
        inputProps={{
          onKeyDown: (event) => {
            if (event.key == 'Enter') {
              if (isRadio) {
                selectOption(ghostValue, ghostName);
              } else {
                // If we can select multiple, add this as a possible input
                addOption(ghostName, ghostName);
                selectOption(ghostName, ghostName, false, true);

                // Clear the ghost
                inputEl.value = "";
              }
            }
          }}
        }
        inputRef={ref => {inputEl = ref}}
      />
    </div>);

  const warning = selection.length < min && (<Typography color='error'>Please select at least {min} option{min > 1 && "s"}.</Typography>)

  if (isRadio) {
    return (
      <React.Fragment>
        {warning}
        <Answer
          answers={selection}
          {...rest}
          />
        <RadioGroup
          aria-label="selection"
          name="selection"
          className={classes.selectionList}
          value={selection.length > 0 && selection[0][ID_POS]}
        >
          {generateDefaultOptions(options, disabled, isRadio, selectOption, removeOption)}
          {/* Ghost radio for the text input */}
          {
          input && <ListItem key={ghostName} className={classes.selectionChild + " " + classes.ghostListItem}>
            <FormControlLabel
              control={
              <Radio
                onChange={() => {selectOption(ghostValue, ghostName);}}
                onClick={() => {inputEl && inputEl.select();}}
                disabled={!ghostSelected && disabled}
                className={classes.ghostRadiobox}
              />
              }
              label="&nbsp;"
              value={ghostValue}
              key={ghostValue}
              className={classes.ghostFormControl + " " + classes.childFormControl}
              classes={{
                label: classes.inputLabel
              }}
            />
          </ListItem>
          }
        </RadioGroup>
        {ghostInput}
      </React.Fragment>
    );
  } else {
    return (
      <React.Fragment>
        {warning}
        <Answer
          answers={selection}
          {...rest}
          />
        <List className={classes.checkboxList}>
          {generateDefaultOptions(options, disabled, isRadio, selectOption, removeOption)}
          {input && <ListItem key={ghostName} className={classes.selectionChild + " " + classes.ghostListItem}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={ghostSelected}
                    onChange={() => {selectOption(ghostValue, ghostName, ghostSelected);}}
                    onClick={() => {inputEl && inputEl.select();}}
                    disabled={!ghostSelected && disabled}
                    className={classes.ghostRadiobox}
                  />
                }
                label="&nbsp;"
                value={ghostValue}
                key={ghostValue}
                className={classes.ghostFormControl + " " + classes.childFormControl}
                classes={{
                  label: classes.inputLabel
                }}
              />
            </ListItem>
          }
        </List>
        {ghostInput}
      </React.Fragment>
    )
  }
}

// Generate a list of options that are part of the default suggestions
function generateDefaultOptions(defaults, disabled, isRadio, onClick, onDelete) {
  return defaults.map( (childData) => {
    return (
      <StyledResponseChild
        id={childData[ID_POS]}
        key={childData[ID_POS]}
        name={childData[NAME_POS]}
        disabled={disabled}
        onClick={onClick}
        onDelete={onDelete}
        isDefault={childData[IS_DEFAULT_POS]}
        isRadio={isRadio}
      ></StyledResponseChild>
    );
  });
}

var StyledResponseChild = withStyles(QuestionnaireStyle)(ResponseChild);

// One option (either a checkbox or radiobox as appropriate)
function ResponseChild(props) {
  const {classes, name, id, isDefault, onClick, disabled, isRadio, onDelete} = props;
  const [checked, setCheck] = useState(false);

  return (
    <React.Fragment>
      <ListItem key={name} className={classes.selectionChild}>
          { /* This is either a Checkbox if this is a default suggestion, or a delete button otherwise */
          isDefault ?
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
                      onChange={() => {onClick(id, name, checked); setCheck(!checked);}}
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
                onClick={() => {onDelete(id, name)}}
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
    </React.Fragment>
  );
}

MultipleChoice.propTypes = {
  classes: PropTypes.object.isRequired,
  text: PropTypes.string,
  description: PropTypes.string,
  answers: PropTypes.array,
  max: PropTypes.number,
  defaults: PropTypes.array,
  input: PropTypes.bool,
  ghostAnchor: PropTypes.object
};

export default withStyles(QuestionnaireStyle)(MultipleChoice);
