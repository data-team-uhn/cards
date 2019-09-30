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
import PropTypes from 'prop-types';

import Answer from "./Answer";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";

const NAME_POS = 0;
const ID_POS = 1;
const IS_DEFAULT_POS = 2;

function MultipleChoice(props) {
  let { classes, ghostAnchor, max, defaults, input, textarea, ...rest } = props;
  const [selection, setSelection] = useState([["", ""]]);
  const [ghostName, setGhostName] = useState("&nbsp;");
  const [ghostValue, setGhostValue] = useState("&nbsp;");
  const [options, setOptions] = useState([]);
  const ghostSelected = ghostName === selection;
  const isRadio = max === 1;
  const disabled = selection.length >= max && !isRadio;

  // On startup, convert our defaults into a list of useable options
  useEffect( () => {
    let newOptions = defaults.map( (defaultOption) => {
      if (!("id" in defaultOption)) {
        console.log("Malformed default option: " + JSON.stringify(defaultOption));
        return ['', '', true];
      }
      let id = defaultOption["id"];
      let label = ("label" in defaultOption ? defaultOption["label"] : id);
      return ([id, label, true]); // id, label, default
    });
    setOptions(newOptions);
  }, [defaults]);

  let selectOption = (id, name) => {
    if (isRadio) {
      setSelection([[name, id]]);
      return;
    }

    // Do not add anything if we are at our maximum number of selections
    if (selection.length >= max) {
      return;
    }

    // Do not add duplicates
    if (options.some(element => {return element[ID_POS] === id})) {
      return;
    }

    let newSelection = selection.slice();
    newSelection.push([name, id]);
    setSelection(newSelection);
  }

  if (isRadio) {
    return (
      <Answer
        answers={selection}
        {...rest}
        >
        <RadioGroup
          aria-label="selection"
          name="selection"
          className={classes.selectionList}
          value={selection[0][ID_POS]}
        >
          {generateDefaultOptions(options, disabled, isRadio, selectOption)}
          {/* Ghost radio for the text input */}
          {
          input && <ListItem key={name} className={classes.selectionChild + " " + classes.ghostListItem}>
            <FormControlLabel
              control={
              <Radio
                onChange={() => {selectOption(ghostValue, ghostName);}}
                onClick={() => {ghostAnchor && ghostAnchor.select();}}
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
        {
          input && <div className={classes.searchWrapper}>
            <TextField
              className={classes.textField}
              onChange={(event) => {
                setGhostValue("custom-input");
                setGhostName(event.target.value);
                selectOption("custom-input", event.target.name)}}
              onFocus={() => {selectOption(ghostValue, ghostName)}}
            />
          </div>
        }
      </Answer>
    );
  } else {
    return (
      <Answer
        answers={selection}
        {...rest}
        >
        <List className={classes.selectionList}>
          {generateDefaultOptions(options, disabled, isRadio, selectOption)}
        </List>
      </Answer>
    )
  }
}

// Generate a list of options that are part of the default suggestions
function generateDefaultOptions(defaults, disabled, isRadio, onClick) {
  return defaults.map( (childData) => {
    return (
      <StyledResponseChild
        id={childData[ID_POS]}
        key={childData[ID_POS]}
        name={childData[NAME_POS]}
        disabled={disabled}
        onClick={onClick}
        isDefault={childData[IS_DEFAULT_POS]}
        isRadio={isRadio}
      ></StyledResponseChild>
    );
  });
}

var StyledResponseChild = withStyles(QuestionnaireStyle)(ResponseChild);

// One option (either a checkbox or radiobox as appropriate)
function ResponseChild(props) {
  const {classes, name, id, isDefault, onClick, disabled, isRadio} = props;
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
                      onChange={() => {setCheck(!checked); onClick(id, name, checked);}}
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
    </React.Fragment>
  );
}

MultipleChoice.propTypes = {
  classes: PropTypes.object.isRequired,
  title: PropTypes.string,
  subtitle: PropTypes.string,
  answers: PropTypes.array,
  max: PropTypes.number,
  defaults: PropTypes.array,
  input: PropTypes.bool,
  ghostAnchor: PropTypes.object
};

export default withStyles(QuestionnaireStyle)(MultipleChoice);
