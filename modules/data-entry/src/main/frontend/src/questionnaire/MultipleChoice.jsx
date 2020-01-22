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
    .map(value => [value.label || value.value, value.value, true]);
  const isBare = defaults.length === 0 && maxAnswers === 1;
  const isRadio = defaults.length > 0 && maxAnswers === 1;
  let initialSelection =
    // If there's no existing answer, there's no initial selection
    (!existingAnswer || existingAnswer[1].value === undefined) ? [] :
    // The value can either be a single value or an array of values; force it into an array
    Array.of(existingAnswer[1].value).flat()
    // Only the internal values are stored, turn them into pairs of [label, value]
    // Values that are not predefined come from a custom input, and custom inputs use either the same name as their answer (multiple inputs)
    // or the the special ghost sentinel value
    .map(answer => (defaults.find(e => e[1] === String(answer)) || [String(answer), (isBare || isRadio) ? GHOST_SENTINEL : String(answer)]));
  const all_options =
    // If the question is a radio, just display the defaults as duplicates
    isRadio ? defaults.slice() :
    // Otherwise, display as options the union of all defaults + existing answers, without duplicates
    defaults.slice().concat(initialSelection.filter( (selectedAnswer) => defaults.indexOf(selectedAnswer) < 0));
  const [selection, setSelection] = useState(initialSelection);
  const [ghostName, setGhostName] = useState((isBare || (isRadio && defaults.indexOf(initialSelection[0]) < 0)) && existingAnswer && existingAnswer[1].value || '');
  const [ghostValue, setGhostValue] = useState(GHOST_SENTINEL);
  const [options, setOptions] = useState(all_options);
  const ghostSelected = selection.some(element => {return element[VALUE_POS] === GHOST_SENTINEL;});
  const disabled = maxAnswers > 0 && selection.length >= maxAnswers && !isRadio;
  let inputEl = null;

  let selectOption = (id, name, checked = false) => {
    if (isRadio) {
      let defaultOption = defaults.filter((option) => {return option[VALUE_POS] === name || option[LABEL_POS] === name})[0];
      if (defaultOption) {
        setSelection([[defaultOption[LABEL_POS], defaultOption[VALUE_POS]]]);
        // Selected the matching value, we no longer need the input
        return true;
      } else {
        setSelection([[name, id]]);
        // Don't clear the input, we're still using it:
        return false;
      }
    }

    // If the element was already checked, remove it instead
    if (checked) {
      return unselect(id, name);
    }

    // Do not add anything if we are at our maximum number of selections
    if (maxAnswers > 0 && selection.length >= maxAnswers) {
      return;
    }

    // Do not add duplicates
    if (selection.some(element => {return element[VALUE_POS] === id || element[LABEL_POS] === name})) {
      return;
    }

    let newSelection = selection.slice();

    // Check if any of the predefined options matches the user input. If yes, select it instead of adding a new entry
    let defaultOption = defaults.filter((option) => {
      return (option[VALUE_POS] === id || option[LABEL_POS] === name)
    })[0];
    if (defaultOption) {
      newSelection.push([defaultOption[LABEL_POS], defaultOption[VALUE_POS]]);
    } else {
      // Otherwise, add a new entry
      newSelection.push([name, id]);
    }
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
    isRadio && setSelection([[name, id]]);
  }

  // Add a non-default option
  // Returns whether an option was added (true) or a matching option already existed (false)
  let addOption = (id, name) => {
    if ( !options.some((option) => {return option[VALUE_POS] === id || option[LABEL_POS] === name}) &&
        !defaults.some((option) => {return option[VALUE_POS] === id || option[LABEL_POS] === name})) {
      let newOptions = options.slice();
      newOptions.push([name, id, false]);
      setOptions(newOptions);
    }
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

  let acceptEnteredOption = () => {
    if (isRadio) {
      selectOption(ghostValue, ghostName) && setGhostName("");
      inputEl && inputEl.blur();
    } else if (maxAnswers !== 1 && !error && ghostName !== "") {
      // If we can select multiple and are not in error, add this option (if not alreday available) and ensure it's selected
      addOption(ghostName, ghostName);
      selectOption(ghostName, ghostName);
      // Clear the ghost
      setGhostName("");
    }
  }

  // Hold the input box for either multiple choice type
  let ghostInput = (input || textbox) && (<div className={isBare ? classes.bareAnswer : classes.searchWrapper}>
      <TextField
        helperText={maxAnswers !== 1 && "Press ENTER to add a new line"}
        className={classes.textField}
        onChange={(event) => {
          setGhostName(event.target.value);
          updateGhost(GHOST_SENTINEL, event.target.value);
          onChange && onChange(event.target.value);
        }}
        onFocus={() => {maxAnswers === 1 && selectOption(ghostValue, ghostName)}}
        onBlur={acceptEnteredOption}
        inputProps={Object.assign({
          onKeyDown: (event) => {
            if (event.key == 'Enter') {
              // We need to stop the event so that it doesn't trigger a form submission
              event.preventDefault();
              event.stopPropagation();
              acceptEnteredOption();
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

  const warning = selection.length < minAnswers && (
    <Typography color={error ? 'error' : 'textSecondary'} className={classes.warningTypography}>
      Please select at least {minAnswers} option{minAnswers > 1 && "s"}.
    </Typography>
    );

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
          {generateDefaultOptions(options, selection, disabled, isRadio, selectNonGhostOption, removeOption)}
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
        {warning}
      </React.Fragment>
    );
  } else {
    return (
      <React.Fragment>
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          {...rest}
          />
        <List className={classes.checkboxList}>
          {generateDefaultOptions(options, selection, disabled, isRadio, selectNonGhostOption, removeOption)}
        </List>
        {ghostInput}
        {warning}
      </React.Fragment>
    )
  }
}

// Generate a list of options that are part of the default suggestions
function generateDefaultOptions(defaults, selection, disabled, isRadio, onClick, onDelete) {
  return defaults.map( (childData) => {
    return (
      <StyledResponseChild
        id={childData[VALUE_POS]}
        key={"value-"+childData[VALUE_POS]}
        name={childData[LABEL_POS]}
        checked={selection.some((sel) => {return (sel[LABEL_POS] === childData[LABEL_POS] || sel[VALUE_POS] === childData[VALUE_POS])})}
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
  const {classes, checked, name, id, isDefault, onClick, disabled, isRadio, onDelete} = props;

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
                      onChange={() => {onClick(id, name, checked)}}
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
