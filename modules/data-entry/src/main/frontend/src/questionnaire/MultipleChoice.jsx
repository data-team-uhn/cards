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

import { Checkbox, FormControlLabel, IconButton, List, ListItem, MenuItem, Radio, RadioGroup, Select, TextField, Typography, withStyles } from "@material-ui/core";
import Close from "@material-ui/icons/Close";
import PropTypes from 'prop-types';

import Answer, {LABEL_POS, VALUE_POS} from "./Answer";
import { useFormUpdateReaderContext, useFormUpdateWriterContext } from "./FormUpdateContext";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

// Position used to read whether or not an option is a "default" suggestion (i.e. one provided by the questionnaire)
const IS_DEFAULT_POS = 2;
// Sentinel value used for the user-controlled input
const GHOST_SENTINEL = "custom-input";

 /**
  * Component that displays a Multiple Choice question.
  *
  * @param {Object} existingAnswer form data that may include answers already submitted for this component
  * @param {bool} input if true, display a free-text single-line input after the predefined options; at most one of "input" or "textbox" may be true
  * @param {bool} textbox if true, display a free-text multi-line input after the predefined options; at most one of "input" or "textbox" may be true
  * @param {func} onUpdate Callback for when an input value is changed or an option is added, receives as argument the new value of the changed option
  * @param {func} onChange Callback for when an option is removed, receives as argument the value of the removed option
  * @param {Object} additionalInputProps additional props to be set on the input element
  * @param {Object} muiInputProps additional props to be forwarded to the MUI input element
  * @param {Object} naValue if provided, any answer with this value will de-select all other selected options, and will be unselected if any other option is selected
  * @param {Object} noneOfTheAboveValue if provided, any answer with this value will de-select all other pre-defined options, and will be unselected if any other option is selected
  * @param {bool} error indicates if the current selection is in a state of error
  */
function MultipleChoice(props) {
  let { classes, existingAnswer, input, textbox, onUpdate, onChange, additionalInputProps, muiInputProps, naValue, noneOfTheAboveValue, error, questionName, ...rest } = props;
  let { maxAnswers, minAnswers, displayMode } = {...props.questionDefinition, ...props};
  let defaults = props.defaults || Object.values(props.questionDefinition)
    // Keep only answer options
    // FIXME Must deal with nested options, do this recursively
    .filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption')
    // Only extract the labels and internal values from the node
    .map(value => [value.label || value.value, value.value, true]);
  // Locate an option referring to the "none of the above", if it exists
  let naOption = naValue || Object.values(props.questionDefinition)
    .find((value) => value['notApplicable'])?.["value"];
  let noneOfTheAboveOption = noneOfTheAboveValue || Object.values(props.questionDefinition)
    .find((value) => value['noneOfTheAbove'])?.["value"];
  const isBare = defaults.length === 0 && maxAnswers === 1;
  const isRadio = defaults.length > 0 && maxAnswers === 1;
  const isSelect = displayMode === "select";
  let initialSelection =
    // If there's no existing answer, there's no initial selection
    (!existingAnswer || existingAnswer[1].value === undefined) ? [] :
    // The value can either be a single value or an array of values; force it into an array
    Array.of(existingAnswer[1].value).flat()
    // Only the internal values are stored, turn them into pairs of [label, value]
    // Values that are not predefined come from a custom input, and custom inputs use either the same name as their answer (multiple inputs)
    // or the the special ghost sentinel value
    .map(answer => (defaults.find(e => e[1] === String(answer)) || [String(answer), (isBare || isRadio) ? GHOST_SENTINEL : String(answer)]));
  let all_options =
    // If the question is a radio, just display the defaults as duplicates
    isRadio ? defaults.slice() :
    // Otherwise, display as options the union of all defaults + existing answers, without duplicates
    defaults.slice().concat(initialSelection.filter( (selectedAnswer) => defaults.indexOf(selectedAnswer) < 0));

  // If the field allows for multiple inputs (eg. maxAnswers !== 1),
  // No user input (aka. an empty input) takes the place of an empty string
  if (maxAnswers !== 1) {
    initialSelection = initialSelection || ["", ""];
    all_options.concat(["", ""]);
  }
  const [selection, setSelection] = useState(initialSelection);
  const [options, setOptions] = useState(all_options);
  const [ghostName, setGhostName] = useState((isBare || (isRadio && defaults.indexOf(initialSelection[0]) < 0)) && existingAnswer && existingAnswer[1].value || '');
  const [ghostValue, setGhostValue] = useState(GHOST_SENTINEL);
  const ghostSelected = selection.some(element => {return element[VALUE_POS] === GHOST_SENTINEL;});
  const disabled = maxAnswers > 0 && selection.length >= maxAnswers && !isRadio && !ghostSelected;
  let inputEl = null;

  let selectOption = (id, name, checked = false) => {
    // Selecting a radio button option will select only that option
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

    // If the naOption is selected, all other elements are deselected and user-input options are cleared
    if (naOption == id) {
      setSelection([[name, id]]);
      // Clear any user-input options
      setOptions(all_options);
      // OK to clear input, since they're removing everything else
      return false;
    } else if (noneOfTheAboveOption == id) {
      // If the noneOfTheAboveOption is selected, other elements are deselected but user-input options remain
      setSelection((old) => {
        // Only keep options that are user-input
        let defaultOptions = defaults.filter(option => option[IS_DEFAULT_POS]).map((option) => option[VALUE_POS]);
        let newSelection = old.slice().filter((option) => !defaultOptions.includes(option[VALUE_POS]));
        newSelection.push([name, id]);
        console.log(newSelection);
        return newSelection;
      });
      // OK to clear input (we more-or-less should never get here via a user-entered input)
      // The only instance where the user input something but we trigger a "none of the above" is when they type
      // the value corresponding to the "none of the above", which should clear the input anyway
      return false;
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

    // If we're inserting a new entry, we should never add the empty tracker
    newSelection = newSelection.filter((option) => {
      return (option[VALUE_POS] !== "" && option[LABEL_POS] !== "")
        // And if we've gotten here and there's an "na" option, we remove it from the selection
        && (!naOption || option[VALUE_POS] != naOption)
        // The same goes for a "none of the above" option
        && (!noneOfTheAboveOption || option[VALUE_POS] != noneOfTheAboveOption)
    });

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
    return setSelection( (old) => {
      let newSelection = old.slice().filter(
        (element) => {
          return !(element[VALUE_POS] === id && element[LABEL_POS] === name)
        });
      // Insert the empty string if nothing currently exists
      if (newSelection.length == 0) {
        return [["", ""]];
      }
      return newSelection;
    }
    );
  }

  let updateGhost = (id, name) => {
    // If at most one answer is allowed, just update with the new value
    (maxAnswers === 1) && setSelection([[name, id]]);
  }

  // Add a non-default option
  // Returns whether an option was added (true) or a matching option already existed (false)
  let addOption = (id, name) => {
    if ( !options.some((option) => {return option[VALUE_POS] === id || option[LABEL_POS] === name}) &&
        !defaults.some((option) => {return option[VALUE_POS] === id || option[LABEL_POS] === name})) {
      setOptions((oldOptions) => {
        let newOptions = oldOptions.slice();
        newOptions.push([name, id, false]);
        return newOptions
      });
    }
  }

  // Remove a non-default option
  let removeOption = (id, name) => {
    onChange && onChange(id); // will trigger callback in Form.jsx
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
      // If we can select multiple and are not in error, add this option (if not already available) and ensure it's selected
      addOption(ghostName, ghostName);
      selectOption(ghostName, ghostName);
      // Clear the ghost
      setGhostName("");
    }
  }

  // Listen for update commands from other components
  // Note that this code must appear after the definition of selectOption, or else it'll
  // run into undefined command issues
  let reader = useFormUpdateReaderContext();
  let writer = useFormUpdateWriterContext();
  let updatedOptions = reader[questionName];
  useEffect(() => {
    if (!updatedOptions) {
      return;
    }

    // Update our options with everything added in the update command
    updatedOptions.forEach((option) => {
      if (isRadio) {
        selectOption(option, option);
      } else if (maxAnswers !== 1) {
        // TODO: We need to perform error validation on the updated field
        addOption(option, option);
        selectOption(option, option);
      } else {
        setGhostName(option);
        updateGhost(GHOST_SENTINEL, option);
        onUpdate && onUpdate(option);
      }
    });

    // Remove written data so we don't somehow double-add details
    writer((old) => {
      let newData = {...old};
      delete newData[questionName];
      return newData;
    })
  }, [updatedOptions])

  // Hold the input box for either multiple choice type
  let ghostInput = (input || textbox) && (<div className={isBare ? classes.bareAnswer : classes.searchWrapper}>
      <TextField
        helperText={maxAnswers !== 1 && "Press ENTER to add a new option"}
        className={classes.textField + (maxAnswers === 1 && (!defaults || defaults.length == 0) ? (' ' + classes.answerField) : '')}
        onChange={(event) => {
          setGhostName(event.target.value);
          updateGhost(GHOST_SENTINEL, event.target.value);
          onUpdate && onUpdate(event.target.value);
        }}
        disabled={disabled}
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
    onUpdate && onUpdate(ghostSelected && !isRadio ? ghostName : undefined);
    selectOption(...args);
  }

  // display error if minimum is not met, display 'at least' if there is no maximum or if max is greater than min
  const warning = selection.length < minAnswers && (
    <Typography color={error ? 'error' : 'textSecondary'} className={classes.warningTypography}>
      Please select {maxAnswers !== minAnswers && "at least"} {minAnswers} option{minAnswers > 1 && "s"}.
    </Typography>
    );

  // Remove the ["", ""] unless there are only zero or one answer items
  var answers = selection.map(item => item[VALUE_POS] === GHOST_SENTINEL ? [item[LABEL_POS], item[LABEL_POS]] : item);
  answers = ((answers.length < 2) ? answers : answers.filter(item => item[LABEL_POS] !== ''));

  if (isSelect) {
    return (
      <React.Fragment>
        <Select
          value={selection?.[0]?.[0] || ''}
          className={classes.textField + ' ' + classes.answerField}
          onChange={(event) => {
            setSelection([[event.target.value, event.target.value]]);
          }
        }>
        {defaults.map(function([name, key], index) {
            return <MenuItem value={key} key={key}>{name}</MenuItem>;
        })}
        </Select>
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          questionName={questionName}
          {...rest}
          />
      </React.Fragment>
    )
  } else if (isBare) {
    return(
      <React.Fragment>
        {ghostInput}
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          questionName={questionName}
          {...rest}
          />
      </React.Fragment>
    )
  } else if (isRadio) {
    return (
      <React.Fragment>
        <RadioGroup
          aria-label="selection"
          name="selection"
          className={classes.selectionList}
          value={selection.length > 0 && selection[0][VALUE_POS]}
        >
          <List className={classes.optionsList}>
          {generateDefaultOptions(options, selection, disabled, isRadio, selectNonGhostOption, removeOption)}
          {/* Ghost radio for the text input */}
          {
          (input || textbox) && <ListItem key={ghostName} className={classes.selectionChild + " " + classes.ghostListItem}>
            <FormControlLabel
              control={
              <Radio
                onChange={() => {
                  selectOption(ghostValue, ghostName);
                  onUpdate && onUpdate(ghostSelected ? undefined : ghostName);
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
          </List>
        </RadioGroup>
        {ghostInput}
        {warning}
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          questionName={questionName}
          {...rest}
          />
      </React.Fragment>
    );
  } else {
    return (
      <React.Fragment>
        <List className={classes.optionsList}>
          {generateDefaultOptions(options, selection, disabled, isRadio, selectNonGhostOption, removeOption)}
        </List>
        {ghostInput}
        {warning}
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          questionName={questionName}
          {...rest}
          />
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
            ) : ((name !== "") && (
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
          ))
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
  additionalInputProps: PropTypes.object,
  muiInputProps: PropTypes.object,
  error: PropTypes.bool
};

export default withStyles(QuestionnaireStyle)(MultipleChoice);
