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

import Answer, {LABEL_POS, VALUE_POS} from "./Answer";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

// Position used to read whether or not an option is a "default" suggestion (i.e. one provided by the questionnaire)
const IS_DEFAULT_POS = 2;
// Sentinel value used for the user-controlled input
const GHOST_SENTINEL = "custom-input";

function MultipleChoice(props) {
  let { classes, existingAnswer, ghostAnchor, input, textbox, onChange, additionalInputProps, muiInputProps, error, ...rest } = props;
  let { maxAnswers, minAnswers } = {...props.questionDefinition, ...props};
  let defaults = props.defaults || Object.values(props.questionDefinition)
    // Keep only answer options
    // FIXME Must deal with nested options, do this recursively
    .filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption')
    // Only extract the labels and internal values from the node
    .map(value => [value.label, value.value, true]);
  let initialSelection =
    // If there's no existing answer, there's no initial selection
    (!existingAnswer || existingAnswer[1].value === undefined) ? [] :
    // The value can either be a single value or an array of values; force it into an array
    Array.of(existingAnswer[1].value).flat()
    // Only the internal values are stored, turn them into pairs of [label, value]
    // Values that are not predefined come from a custom input, and custom inputs use a special value
    .map(answer => (defaults.find(e => e[1] === String(answer)) || [String(answer), GHOST_SENTINEL]));
  const [selection, setSelection] = useState(initialSelection);
  // FIXME This doesn't work with multiple values
  const [ghostName, setGhostName] = useState(existingAnswer && existingAnswer[1].value || '');
  const [ghostValue, setGhostValue] = useState(GHOST_SENTINEL);
  const [options, setOptions] = useState(defaults);
  const ghostSelected = selection.some(element => {return element[VALUE_POS] === GHOST_SENTINEL;});
  const isRadio = maxAnswers === 1 && options.length > 0;
  const isBare = options.length === 0;
  const disabled = selection.length >= maxAnswers && !isRadio;
  let inputEl = null;

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
    if (selection.length >= maxAnswers && !removeSentinel) {
      return;
    }

    // Do not add duplicates
    if (selection.some(element => {return element[VALUE_POS] === id})) {
      return;
    }

    let newSelection = selection.slice();
    if (removeSentinel) {
      // Due to how React handles state, we need to do this in one step
      newSelection = newSelection.filter(
        (element) => {
          return (element[VALUE_POS] !== GHOST_SENTINEL);
        }
      );
    }
    newSelection.push([name, id]);
    setSelection(newSelection);
  }

  let unselect = (id, name) => {
    return setSelection(selection.filter(
      (element) => {
        return !(element[VALUE_POS] === id && element[LABEL_POS] === name)
      }
    ));
  }

  let updateGhost = (id, name) => {
    // If we're a radio, just update with the new value
    if (isRadio) {
      setSelection([[name, id]]);
      return;
    }

    let ghostIndex = selection.findIndex(element => {return element[VALUE_POS] === GHOST_SENTINEL});
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
        return !(option[VALUE_POS] === id && option[LABEL_POS] === name)
      }
    ));
    unselect(id, name);
    return;
  }

  // Hold the input box for either multiple choice type
  let ghostInput = (input || textbox) && (<div className={classes.searchWrapper}>
      <TextField
        className={classes.textField}
        onChange={(event) => {
          setGhostName(event.target.value);
          updateGhost(GHOST_SENTINEL, event.target.value);
          onChange && onChange(event.target.value);
        }}
        onFocus={() => {maxAnswers === 1 && selectOption(ghostValue, ghostName)}}
        inputProps={Object.assign({
          onKeyDown: (event) => {
            if (event.key == 'Enter') {
              if (isRadio) {
                selectOption(ghostValue, ghostName);
              } else if (maxAnswers !== 1 && !error) {
                // If we can select multiple and are not in error, add this as a possible input
                addOption(ghostName, ghostName);
                selectOption(ghostName, ghostName, false, true);

                // Clear the ghost
                setGhostName("");
              }
            }
          }
        }, additionalInputProps)
        }
        value={ghostName}
        multiline={textbox}
        InputProps={muiInputProps}
        inputRef={ref => {inputEl = ref}}
      />
    </div>);

  let selectNonGhostOption = (...args) => {
    // Clear the ghost input
    onChange && onChange(ghostSelected && !isRadio ? ghostName : undefined);
    selectOption(...args);
  }

  const warning = selection.length < minAnswers && (<Typography color='error'>Please select at least {minAnswers} option{minAnswers > 1 && "s"}.</Typography>)

  const answers = selection.map(item => item[VALUE_POS] === GHOST_SENTINEL ? [item[LABEL_POS], item[LABEL_POS]] : item);

  if (isBare) {
    return(
      <React.Fragment>
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          {...rest}
          />
        {ghostInput}
      </React.Fragment>
    )
  } else if (isRadio) {
    return (
      <React.Fragment>
        {warning}
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          {...rest}
          />
        <RadioGroup
          aria-label="selection"
          name="selection"
          className={classes.selectionList}
          value={selection.length > 0 && selection[0][VALUE_POS]}
        >
          {generateDefaultOptions(options, disabled, isRadio, selectNonGhostOption, removeOption)}
          {/* Ghost radio for the text input */}
          {
          (input || textbox) && <ListItem key={ghostName} className={classes.selectionChild + " " + classes.ghostListItem}>
            <FormControlLabel
              control={
              <Radio
                onChange={() => {
                  selectOption(ghostValue, ghostName);
                  onChange && onChange(ghostSelected ? undefined : ghostName);
                }}
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
          answers={answers}
          existingAnswer={existingAnswer}
          {...rest}
          />
        <List className={classes.checkboxList}>
          {generateDefaultOptions(options, disabled, isRadio, selectNonGhostOption, removeOption)}
          {(input || textbox) && <ListItem key={ghostName} className={classes.selectionChild + " " + classes.ghostListItem}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={ghostSelected}
                    onChange={() => {
                      selectOption(ghostValue, ghostName, ghostSelected);
                      onChange && onChange(ghostSelected ? undefined : ghostName);
                    }}
                    onClick={() => {inputEl && inputEl.select();}}
                    disabled={!ghostSelected && disabled}
                    className={classes.ghostRadiobox}
                  />
                }
                label=""
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
        id={childData[VALUE_POS]}
        key={childData[VALUE_POS]}
        name={childData[LABEL_POS]}
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
  maxAnswers: PropTypes.number,
  defaults: PropTypes.array,
  input: PropTypes.bool,
  ghostAnchor: PropTypes.object,
  additionalInputProps: PropTypes.object,
  muiInputProps: PropTypes.object,
  error: PropTypes.bool
};

export default withStyles(QuestionnaireStyle)(MultipleChoice);
