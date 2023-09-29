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

import { Checkbox, Chip, FormControl, FormControlLabel, IconButton, List, ListItem, MenuItem, Radio, RadioGroup, Select, TextField, Typography } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import Close from "@mui/icons-material/Close";
import PropTypes from 'prop-types';

import Answer, {LABEL_POS, VALUE_POS, DESC_POS, IS_DEFAULT_OPTION_POS, IS_DEFAULT_ANSWER_POS} from "./Answer";
import { useFormUpdateReaderContext, useFormUpdateWriterContext } from "./FormUpdateContext";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import AnswerInstructions from "./AnswerInstructions.jsx";
import UserInputAssistant from "../components/UserInputAssistant.jsx";
import FormattedText from "../components/FormattedText.jsx";

// Sentinel value used for the user-controlled input
const GHOST_SENTINEL = "custom-input";

 /**
  * Component that displays a Multiple Choice question.
  *
  * @param {Object} existingAnswer form data that may include answers already submitted for this component
  * @param {bool} input if true, display a free-text single-line input after the predefined options; at most one of "input" or "textbox" may be true
  * @param {bool} textbox if true, display a free-text multi-line input after the predefined options; at most one of "input" or "textbox" may be true
  * @param {Component} customInput if true, display this after the predefined options; do not define this and input/textbox
  * @param {Object} customInputProps additional props to be given to the customInput element provided
  * @param {func} onUpdate Callback for when an input value is changed or an option is added, receives as argument the new value of the changed option
  * @param {func} onChange Callback for when an option is removed, receives as argument the value of the removed option
  * @param {Object} additionalInputProps additional props to be set on the input element
  * @param {Object} muiInputProps additional props to be forwarded to the MUI input element
  * @param {Object} naValue if provided, any answer with this value will de-select all other selected options, and will be unselected if any other option is selected
  * @param {Object} noneOfTheAboveValue if provided, any answer with this value will de-select all other pre-defined options, and will be unselected if any other option is selected
  * @param {bool} pageActive if true, this page will render graphical components. Otherwise, it will skip rendering (to save on performance)
  * @param {bool} error indicates if the current selection is in a state of error
  */
function MultipleChoice(props) {
  let { classes, customInput, customInputProps, existingAnswer, input, textbox, onUpdate, onChange, additionalInputProps, muiInputProps, naValue, noneOfTheAboveValue, error, questionName, ...rest } = props;
  let { maxAnswers, displayMode, enableSeparatorDetection } = {...props.questionDefinition, ...props};
  let { validate, validationErrorText, liveValidation } = {...props.questionDefinition, ...props};
  // pageActive should be passed to the Answer component, so we make sure to include it in the `rest` variable above
  let { instanceId, pageActive } = props;

  let defaults = props.defaults || Object.values(props.questionDefinition)
    // Keep only answer options
    // FIXME Must deal with nested options, do this recursively
    .filter(value => value['jcr:primaryType'] == 'cards:AnswerOption')
    // Sort by default order
    .sort((option1, option2) => (option1.defaultOrder - option2.defaultOrder))
    // Only extract the labels, internal values and description from the node
    .map(value => [value.label || value.value, value.value, true, value.description, value.isDefault]);
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
    // Only the internal values are stored, turn them into pairs of [label, value] by using their displayedValue
    .map((item, index) => [Array.of(existingAnswer[1].displayedValue).flat()[index], item]);
  // When opening a form, if there is no existingAnswer but there are AnswerOptions specified as default values,
  // display those options as selected and ensure they get saved unless modified by the user, by adding them to initialSelection
  if (!existingAnswer) {
    initialSelection = defaults.filter(item => item[IS_DEFAULT_ANSWER_POS])
       // If there are more default values than the specified maxAnswers, only take into account the first maxAnswers default values.
      .slice(0, maxAnswers || defaults.length)
      .map(item => [item[LABEL_POS], item[VALUE_POS]]);
  }
  let default_values = defaults.map((thisDefault) => thisDefault[VALUE_POS]);
  let all_options =
    // If the question is a radio, just display the defaults as duplicates
    isRadio ? defaults.slice() :
    // Otherwise, display as options the union of all defaults + existing answers, without duplicates
    defaults.slice().concat(initialSelection.filter( (selectedAnswer) => default_values.indexOf(String(selectedAnswer[VALUE_POS])) < 0));

  // If the field allows for multiple inputs (eg. maxAnswers !== 1),
  // No user input (aka. an empty input) takes the place of an empty string
  if (maxAnswers !== 1) {
    initialSelection = initialSelection || ["", ""];
    all_options.concat(["", ""]);
  }
  const [selection, setSelection] = useState(initialSelection);
  const [options, setOptions] = useState(all_options);

  // If this is a bare input or radio input, we need to pre-populate the blank input with the custom answer (if available)
  let inputPrefill = (isBare || (isRadio && default_values.indexOf(String(initialSelection[0]?.[VALUE_POS])) < 0)) && existingAnswer?.[1] || '';
  const [ghostName, setGhostName] = useState(inputPrefill?.displayedValue);
  const [ghostValue, setGhostValue] = useState(inputPrefill?.value || GHOST_SENTINEL);
  const ghostSelected = selection.some(element => {return String(element[VALUE_POS]) === ghostValue || element[LABEL_POS] === ghostName});
  const disabled = maxAnswers > 1 && selection.length >= maxAnswers;
  let inputEl = null;
  const [separatorDetectionEnabled, setSeparatorDetectionEnabled] = useState(enableSeparatorDetection);
  const [separatorDetected, setSeparatorDetected] = useState(false);
  const [assistantAnchor, setAssistantAnchor] = useState(null);
  const [tmpGhostSelection, setTmpGhostSelection] = useState(null);
  const [inputError, setInputError] = useState();

  useEffect(() => {
    // If liveValidation is on, run validation on every change of the input
    // Otherwise, validate once when inputError is undefined and
    // revalidate at every text update only if inputError is true
    // (meaning that a validation error was detected when attempting to accept the input)
    if (validate) {
      (liveValidation || inputError !== false) && setInputError(!validate(ghostName));
    }
  }, [ghostName, inputError]);

  let selectOption = (id, name, checked = false) => {
    if (!(isRadio || isBare) && !checked && naOption == id) {
      setOptions(defaults);
    }

    setSelection( old => {
      // Selecting a radio button option will select only that option
      if (isRadio || isBare) {
        let defaultOption = defaults.filter((option) => {return String(option[VALUE_POS]) === name || option[LABEL_POS] === name})[0];
        if (defaultOption) {
          // Selected the matching value, we no longer need the input
          return [[defaultOption[LABEL_POS], defaultOption[VALUE_POS]]];
        } else {
          // Don't clear the input, we're still using it:
          return [[name, id]];
        }
      }

      // If the element was already checked, remove it instead
      if (checked) {
        return unselect(old, id);
      }

      // If the naOption is selected, all other elements are deselected and user-input options are cleared
      if (naOption == id) {
        // Clear any user-input options
        // OK to clear input, since they're removing everything else
        return [[name, id]];
      } else if (noneOfTheAboveOption == id) {
        // If the noneOfTheAboveOption is selected, other elements are deselected but user-input options remain
        // Only keep options that are user-input
        let defaultOptionValues = defaults.filter(option => option[IS_DEFAULT_OPTION_POS]).map((option) => String(option[VALUE_POS]));
        let newSelection = old.filter((option) => !defaultOptionValues.includes(String(option[VALUE_POS])));
        newSelection.push([name, id]);
        return newSelection;
      }

      // Do not add anything if we are at our maximum number of selections
      if (maxAnswers > 0 && old.length >= maxAnswers) {
        return old;
      }

      // Do not add duplicates
      if (old.some(element => {return String(element[VALUE_POS]) === id})) {
        return old;
      }

      let newSelection = old.filter((option) => {
        return (option[VALUE_POS] !== "" && option[LABEL_POS] !== "")
          // And if we've gotten here and there's an "na" option, we remove it from the selection
          && (!naOption || option[VALUE_POS] != naOption)
          // The same goes for a "none of the above" option
          && (!noneOfTheAboveOption || option[VALUE_POS] != noneOfTheAboveOption)
      });

      // Check if any of the predefined options matches the user input. If yes, select it instead of adding a new entry
      let defaultOption = defaults.filter((option) => {
        return (String(option[VALUE_POS]) === id || option[LABEL_POS] === name)
      })[0];
      if (defaultOption) {
        newSelection.push([defaultOption[LABEL_POS], defaultOption[VALUE_POS]]);
      } else {
        // Otherwise, add a new entry
        newSelection.push([name, id]);
      }
      return newSelection;
    }
    );
  }

  let unselect = (old, id) => {
    let newSelection = old.filter(
      (element) => {
        return !(String(element[VALUE_POS]) === String(id))
      });

    // Insert the empty string if nothing currently exists
    if (newSelection.length == 0) {
      return [["", ""]];
    }

    return newSelection;
  }

  let updateGhost = (id, name) => {
    // If at most one answer is allowed, just update with the new value
    (maxAnswers === 1) && setSelection([[name, id]]);
  }

  // Add a non-default option
  // Returns whether an option was added (true) or a matching option already existed (false)
  let addOption = (id, name) => {
    setOptions((oldOptions) => {
      if ( !oldOptions.some((option) => {return option[VALUE_POS] === id}) &&
        !defaults.some((option) => {return option[VALUE_POS] === id})) {
        let newOptions = oldOptions.slice();
        newOptions.push([name, id, false]);
        return newOptions;
      }
      return oldOptions;
    });
  }

  // Remove a non-default option
  let removeOption = (id, name) => {
    onChange && onChange(id); // will trigger callback in Form.jsx
    setOptions( (old) => {
      return old.filter(
        (option) => {
          return !(option[VALUE_POS] === id) || option[IS_DEFAULT_OPTION_POS]
        });
    });
    setSelection((old) => unselect(old, id));
  }

  let acceptEnteredOption = (enforceValidation) => {
    // If a validation method is provided and liveValidation is off, validate before proceeding
    if (!liveValidation) {
      if (typeof(validate) == 'function') {
        let validationError = !validate(ghostName);
        if (validationError) {
          setInputError(true);
          // If validation is enforced and the input is invalid, do not accept it
          if (enforceValidation) return;
        }
      }
    } else {
      // If validation is enforced and the input is invalid, do not accept it
      if (inputError && enforceValidation) return;
    }

    let labelToAccept = ghostName || (ghostValue == GHOST_SENTINEL ? "" : ghostValue);
    let valToAccept = (ghostValue == GHOST_SENTINEL ? labelToAccept : ghostValue);
    acceptOption(valToAccept, labelToAccept);
  }

  let acceptOption = (valToAccept, labelToAccept) => {
    if (isRadio || isBare) {
      selectOption(valToAccept, labelToAccept) && setGhostName("");
      inputEl && inputEl.blur();
    } else if (maxAnswers !== 1 && !error && valToAccept !== "") {
      // If we can select multiple and are not in error, add this option (if not already available) and ensure it's selected
      addOption(valToAccept, labelToAccept);
      selectOption(valToAccept, labelToAccept);
      // Clear the ghost
      setGhostName("");
      setGhostValue(GHOST_SENTINEL);
      checkForSeparators(null);
    }
  }

  // Check if the user entered any characters that are separators: ",", ";"
  // If yes, we will show an information bubble about entering each option separately
  let checkForSeparators = (input) => {
    let hasSeparators = separatorDetectionEnabled && !!(input?.value?.match(/[,;]/));
    setSeparatorDetected(hasSeparators);
    setAssistantAnchor(hasSeparators ? input : null);
    setTmpGhostSelection(hasSeparators ? [input.value, input.value] : null);
  }

  // Split the input by the separators and add each component as a different entry
  let splitInput = (input) => {
    let entries = input?.value?.split(/\s*[,;]\s*/);
    // Remove empty strings, duplicates, and entries that are already selected
    entries = entries?.filter((item, index) => (item != "" && entries.indexOf(item) === index && !selection.find(option => option[VALUE_POS] == item))) || [];
    // Add and select remaining entries
    if (entries.length > 0) {
      let newSelection = selection.slice();
      entries.forEach((item) => {
        addOption(item, item)
        newSelection.push([item, item])
      });
      setSelection(newSelection);
    }
    // Clear the input
    setGhostName("");
    checkForSeparators(null);
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
        updateGhost(ghostValue, option);
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

  let ghostUpdateEvent = (event) => {
    setGhostName(event.target.value);
    setGhostValue(event.target.value);
    updateGhost(event.target.value, event.target.value);
    checkForSeparators(event.target);
    onUpdate && onUpdate(event.target.value);
  }

  // Certain classes that implement MultipleChoice might add options of their own
  // e.g. The vocab selector and the NCRNote component.
  // In those cases, we want to ensure the same behaviour as if they had entered
  // in a ghosts input normally
  let acceptOptionFromWidget = (value, label) => {
    // If we are bare or a radio, the selected option should become the value
    // unless it is one of the defaults
    let isDefault = defaults.filter((option) => {
      return (option[VALUE_POS] === value || option[LABEL_POS] === label)
    })[0];
    if ((isBare || isRadio) && !isDefault) {
      setGhostName(label);
      setGhostValue(value);
    } else {
      // In all other cases, we want to clear the ghost value
      setGhostValue(GHOST_SENTINEL);
    }
    updateGhost(value, label);
    acceptOption(value, label);
    onUpdate && onUpdate(value);
  }

  // Hold the input box for either multiple choice type
  let CustomInput = customInput;
  let ghostInput = (input || textbox || customInput) && (<div className={isBare ? classes.bareAnswer : classes.searchWrapper}>
      {
        customInput ?
          <CustomInput
            initialSelection={selection.filter(option => option[VALUE_POS])}
            onRemoveOption={removeOption}
            onClick={acceptOptionFromWidget}
            onChange = {ghostUpdateEvent}
            value={ghostSelected ? ghostName : undefined}
            disabled={disabled}
            {...customInputProps}
            />
        :
          <TextField
            variant="standard"
            error={inputError}
            helperText={inputError ? validationErrorText : maxAnswers !== 1 && "Press ENTER to add a new option"}
            className={classes.textField + (isRadio ? (' ' + classes.nestedInput) : '')}
            onChange={ghostUpdateEvent}
            disabled={disabled}
            onFocus={() => {maxAnswers === 1 && ghostName && selectOption(ghostValue, ghostName)}}
            onBlur={separatorDetected ? ()=>{} : () => acceptEnteredOption()}
            inputProps={Object.assign({
              onKeyDown: (event) => {
                if (event.key == 'Enter') {
                  // We need to stop the event so that it doesn't trigger a form submission
                  event.preventDefault();
                  event.stopPropagation();
                  acceptEnteredOption(true);
                }
              },
              tabIndex: isRadio ? -1 : undefined
            }, additionalInputProps)
            }
            value={ghostName || ''}
            multiline={textbox}
            InputProps={muiInputProps}
            inputRef={ref => {inputEl = ref}}
            />
      }
      { maxAnswers !== 1 && separatorDetectionEnabled &&
        <UserInputAssistant
          title="Separator detected"
          anchorEl={assistantAnchor}
          actionLabel="Separate and add"
          onAction={() => {splitInput(assistantAnchor)}}
          onIgnore={() => {setSeparatorDetectionEnabled(false); assistantAnchor?.focus(); checkForSeparators(null);}}
          onClickAway={(event) => {
            (document.activeElement != assistantAnchor) && acceptEnteredOption();
            checkForSeparators(null);
          }}
          >
          Using separators such as comma or semicolon will not create separate entries.
          If you wish to enter multiple values, press ENTER to add each one.
        </UserInputAssistant>
      }
    </div>);

  let selectNonGhostOption = (...args) => {
    // Clear the ghost input
    onUpdate && onUpdate(ghostSelected && !isRadio ? ghostName : undefined);
    selectOption(...args);
  }

  // Remove the ["", ""] unless there are only zero or one answer items
  var answers = selection.map(item => item[VALUE_POS] === GHOST_SENTINEL ? [item[LABEL_POS], item[LABEL_POS]] : item);
  answers = ((answers.length < 2) ? answers : answers.filter(item => item[LABEL_POS] !== ''));

  // When counting current answers for proper highlighting of answer instructions to the user, exclude the empty one
  let currentAnswers = answers.filter(item => item[VALUE_POS] !== '').length;
  const instructions = <AnswerInstructions currentAnswers={currentAnswers} {...props.questionDefinition} {...props} />;

  // Temporarily append the input content to answers to avoid data loss while separator detection is active and preventing
  //  the contents of the ghost input from being added as selection
  tmpGhostSelection?.[VALUE_POS] && !answers.find(item => item[VALUE_POS] == tmpGhostSelection[VALUE_POS]) && answers.push(tmpGhostSelection);

  if (isSelect) {
    return (
      <React.Fragment>
        {
          pageActive && <FormControl sx={{width: 300}}>
            {instructions}
            <Select
              variant="standard"
              multiple={maxAnswers != 1}
              value={
                maxAnswers == 1
                ? (selection?.[0]?.[VALUE_POS] || '')
                : (selection?.map(s => s[VALUE_POS]) || [])
              }
              className={classes.textField}
              onChange={(event) => {
                if (maxAnswers == 1) {
                  setSelection([[event.target.value, event.target.value]]);
                } else {
                  setSelection(Array.of(event.target.value || []).flat().map(v => [v,v]));
                }
              }}
              renderValue={
                maxAnswers == 1 ? undefined
                : (value) => (<div className={classes.selectMultiValues}>
                  { value.map((v, i) => (
                    <Chip key={v + i} label={defaults.find(e => e[VALUE_POS] == v)?.[LABEL_POS] || v}/>
                  ))}
                </div>)
              }
            >
            {defaults.map(function([name, key], index) {
                return <MenuItem value={key} key={key}>{name}</MenuItem>;
            })}
            </Select>
          </FormControl>
        }
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          questionName={questionName}
          onAddSuggestion={acceptOptionFromWidget}
          {...rest}
          />
      </React.Fragment>
    )
  } else if (isBare) {
    return(
      <React.Fragment>
        {
          pageActive && <>
            {instructions}
            {ghostInput}
          </>
        }
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          questionName={questionName}
          onAddSuggestion={acceptOptionFromWidget}
          {...rest}
          />
      </React.Fragment>
    )
  } else if (isRadio) {
    return (
      <React.Fragment>
        {
          pageActive && <>
            {instructions}
            <RadioGroup
              aria-label="selection"
              name={props.questionDefinition['jcr:uuid'] + (instanceId || '')}
              className={classes.selectionList}
              value={selection.length > 0 && String(selection[0][VALUE_POS])}
            >
              <List className={classes.optionsList}>
              {generateDefaultOptions(options, selection, disabled, isRadio, selectNonGhostOption, removeOption, validate, validationErrorText)}
              {/* Ghost radio for the text input */}
              {
              ghostInput && <ListItem className={classes.ghostListItem}>
                <FormControlLabel
                  control={
                  <Radio
                    color="secondary"
                    checked={ghostSelected}
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
                {ghostInput}
              </ListItem>
              }
              </List>
            </RadioGroup>
          </>
        }
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          questionName={questionName}
          onAddSuggestion={acceptOptionFromWidget}
          {...rest}
          />
      </React.Fragment>
    );
  } else {
    return (
      <React.Fragment>
        {
          pageActive && <>
            {instructions}
            <List className={classes.optionsList}>
              {generateDefaultOptions(options, selection, disabled, isRadio, selectNonGhostOption, removeOption, validate, validationErrorText)}
              {ghostInput && <ListItem>{ghostInput}</ListItem>}
            </List>
          </>
        }
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          questionName={questionName}
          onAddSuggestion={acceptOptionFromWidget}
          {...rest}
          />
      </React.Fragment>
    )
  }
}

// Generate a list of options that are part of the default suggestions
function generateDefaultOptions(defaults, selection, disabled, isRadio, onClick, onDelete, validate, validationErrorText) {
  return defaults.map( (childData) => {
    let isInvalid = !childData[IS_DEFAULT_OPTION_POS] && !(validate?.(childData[LABEL_POS]) ?? true);
    return (
      <StyledResponseChild
        id={childData[VALUE_POS]}
        key={"value-"+childData[VALUE_POS]}
        name={childData[LABEL_POS]}
        checked={selection.some((sel) => {return (sel[LABEL_POS] === childData[LABEL_POS] || String(sel[VALUE_POS]) === childData[VALUE_POS])})}
        disabled={disabled}
        onClick={onClick}
        onDelete={onDelete}
        isDefaultOption={childData[IS_DEFAULT_OPTION_POS]}
        isRadio={isRadio}
        isInvalid={isInvalid}
        description={childData[DESC_POS] || isInvalid ? validationErrorText : ""}
      ></StyledResponseChild>
    );
  });
}

var StyledResponseChild = withStyles(QuestionnaireStyle)(ResponseChild);

// One option (either a checkbox or radiobox as appropriate)
function ResponseChild(props) {
  const {classes, checked, name, id, isDefaultOption, onClick, disabled, isRadio, isInvalid, onDelete, description} = props;

  return (
    <React.Fragment>
      <ListItem key={name} className={classes.selectionChild} onClick={evt => {evt.preventDefault(); onClick(id, name, checked);}}>
          { /* This is either a Checkbox/Radiobox if this is a default suggestion, or a delete button otherwise */
          isDefaultOption ?
            (<>
              <FormControlLabel
                control={
                  isRadio ?
                  (
                    <Radio
                      color="secondary"
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
                      color="secondary"
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
              <FormattedText className={classes.selectionDescription} variant="caption" color="textSecondary">
                {description}
              </FormattedText>
             </>
            ) : ((name !== "") && (
            <React.Fragment>
              <IconButton
                onClick={() => {onDelete(id, name)}}
                className={classes.deleteButton}
                color="secondary"
                title="Delete"
                size="large"
              >
                <Close color="action" className={classes.deleteIcon}/>
              </IconButton>
              <div className={classes.inputLabel}>
                <Typography color={isInvalid ? "error" : ""}>
                  {name}
                </Typography>
              </div>
              { description &&
                <FormattedText className={classes.selectionDescription} variant="caption" color={isInvalid ? "error" : "textSecondary"}>
                  {description}
                </FormattedText>
              }
            </React.Fragment>
          ))
          }
      </ListItem>
    </React.Fragment>
  );
}

MultipleChoice.propTypes = {
  classes: PropTypes.object.isRequired,
  defaults: PropTypes.array,
  input: PropTypes.bool,
  additionalInputProps: PropTypes.object,
  muiInputProps: PropTypes.object,
  error: PropTypes.bool
};

export default withStyles(QuestionnaireStyle)(MultipleChoice);
