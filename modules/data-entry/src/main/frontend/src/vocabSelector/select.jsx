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
// @material-ui/core
import { FormControlLabel, List, ListItem, RadioGroup, Typography, withStyles, Radio } from "@material-ui/core";

import { MakeRequest } from "../vocabQuery/util.jsx";
import Answer, { VALUE_POS } from "../questionnaire/Answer.jsx";
import VocabularyQuery from "../vocabQuery/query.jsx";
import SelectorStyle from "./selectorStyle.jsx";
import VocabularyEntry from "./selectEntry.jsx";
import SelectionResults from "./selectionResults.jsx";
import NCRNote from "../questionnaire/NCRNote.jsx";

// Enumeration for how to store the output
const NAME_POS = 0;
const ID_POS = 1;
const IS_PRESELECT_POS = 2;
const IS_SELECTED_POS = 3;

// Component that renders a full screen dialog, to browse related terms of an input
// term.
//
// Required arguments:
//  title: Title of the selector question
//  source: Vocabulary source to use
//  max: Maximum number of selectable options
//  requiredAncestors: For vocabularies that support ancestries, supplies a required ancestor element. This
//    can be used to restrict answers to be e.g. descendents of skin conditions
//  defaultSuggestions: Default suggestions
//  searchDefault: Default text to place in search bar before user input
// Other arguments are passed onto contained Thesaurus element
function VocabularySelector(props) {
  const {defaultSuggestions, existingAnswer, source, vocabularyFilter, max, selectionContainer, questionDefinition, searchDefault, classes, ...rest} = props;
  const {selectionUpdated} = props;

  const [listChildren, setListChildren] = useState([]);
  const [selectedListChildren, setSelectedListChildren] = useState([]);
  const [selected, setSelected] = useState(0);
  const [radioName, setRadioName] = useState("");
  const [radioSelect, setRadioSelect] = useState("");
  const [radioValue, setRadioValue] = useState("&nbsp;");
  const [disabled, setDisabled] = useState(false);

  const isRadio = max === 1;
  const hasDefaultOptions = (Object.keys(defaultSuggestions || {}).length > 0);

  // Populate option list when we load for the first time
  useEffect(() => {
    populateOptions();
  }, []);

  // Update selection based on the flags in listed options
  useEffect(() => {
    setSelectedListChildren(listChildren.filter( (element) => element[IS_SELECTED_POS] ));
  }, [listChildren]);

  // Update the number of selected options based on the selection
  // If single select, also update the user option radio button selection, name and value
  useEffect(() => {
    setSelected(selectedListChildren?.length || 0);
    setDisabled(max > 1 && selected >= max);
    if (isRadio) {
      let selectedOption = selectedListChildren?.[0];
      if (selectedOption) {
        setRadioSelect(selectedOption[ID_POS]);
        if (!selectedOption[IS_PRESELECT_POS]) {
          setRadioValue(selectedOption[ID_POS]);
          setRadioName(selectedOption[NAME_POS]);
        }
      }
    }
  }, [selectedListChildren]);

  // Update disabled status based on number of selected options
  useEffect(() => {
    setDisabled(max > 1 && selected >= max);
    selectionUpdated && selectionUpdated(selected);
  }, [selected]);

  let thesaurusRef = null;

  let generateList = (disabled, isRadio) => {
    if (isRadio) {
      return (
        <RadioGroup
          aria-label="selection"
          name="selection"
          className={classes.selectionList}
          value={radioSelect}
          onChange={changeRadio}
        >
        <List className={classes.selectionList}>
          {generateListChildren(disabled, isRadio)}
          {/* Ghost radio for the text input */}
          <ListItem className={classes.ghostListItem}>
          <FormControlLabel
            control={
            <Radio
              onClick={() => {thesaurusRef.anchorEl.select();}}
              className={classes.ghostRadiobox}
            />
            }
            label="&nbsp;"
            name={radioName}
            value={radioValue}
            className={hasDefaultOptions ? classes.ghostFormControl : classes.hiddenGhostFormControl}
            classes={{
              label: classes.inputLabel
            }}
          />
          </ListItem>
        </List>
        </RadioGroup>
      );
    } else {
      return (
        <List className={classes.selectionList}>
          {generateListChildren(disabled, isRadio)}
        </List>
      )
    }
  }

  /* Since we need to enable/disable children on the fly, we also generate them
      on the fly */
  let generateListChildren = (disabled, isRadio) => {
    return listChildren.map( (childData) => {
      return (
        <VocabularyEntry
          id={childData[ID_POS]}
          key={childData[ID_POS]}
          name={childData[NAME_POS]}
          onClick={removeSelection}
          disabled={disabled}
          isPreselected={childData[IS_PRESELECT_POS]}
          currentlySelected={childData[IS_SELECTED_POS]}
          isRadio={isRadio}
        ></VocabularyEntry>
      );
    });
  }

  // Handle the user clicking on a radio button
  let changeRadio = (event) => {
    addSelection(event.target.value);
  }

  // Select a specific value
  let addSelection = (id, name) => {
    // Do not add anything if we are at our maximum number of selections
    if (selected >= max && max > 1 ) {
      return old;
    }

    // Prevent closures from mucking up logic by placing everything in an updater function
    setListChildren((oldChildren) => {
      // Do not add duplicates. If the option already exists, select it
      var newChildren = oldChildren.map(element => {
        // select if it was previously selected, and is not sigle select, or is added now
        element[IS_SELECTED_POS] = element[IS_SELECTED_POS] && !isRadio || (element[ID_POS] == id);
        return element;
      });

      // If the option already existed, there's nothing else to do
      if (newChildren.some(element => {return element[ID_POS] === id})) {
        return newChildren;
      }

      if (max == 1) {
        // If only 1 child is allowed, replace it instead of copying our array
        newChildren = newChildren.filter(childData => childData[IS_PRESELECT_POS] == true);
      }
      newChildren.push([name, id, false, true]);
      return newChildren;
    });
  }

  let populateOptions = () => {
    var newChildren = [];
    const hasExistingAnswers = existingAnswer && existingAnswer.length > 1 && existingAnswer[VALUE_POS].value;
    // The existing value, if present, can either be a single value or an array of values; force it into an array
    const existingAnswers = hasExistingAnswers && Array.of(existingAnswer[VALUE_POS].value).flat();

    for (var id in defaultSuggestions) {
      // If we are given a name, use it
      if (typeof defaultSuggestions[id] !== "undefined") {
        newChildren.push([defaultSuggestions[id], id, true, hasExistingAnswers && existingAnswers.includes(id)]);
        continue;
      }

      // Determine the name from our vocab
      var escapedId = id.replace(":", "");  // Vocabulary terms have no colons in their JCR node names
      var url = new URL(`${id}.json`, window.location.origin);
      MakeRequest(url, (status, data) => addOption(status, data, id, true));
    };

    // If any answers are existing (i.e. we are loading an old form), also populate these
    if (hasExistingAnswers) {
      Array.of(existingAnswer[VALUE_POS].value).flat().forEach( (id) => {
        // Do not add a pre-existing answer if it is a default
        if (id in defaultSuggestions) {
          return;
        }
        // Determine the name from our vocab
        var escapedId = id.replace(":", "");  // Vocabulary terms have no colons in their JCR node names
        var url = new URL(`${id}.json`, window.location.origin);
        MakeRequest(url, (status, data) => addOption(status, data, id, false));
      });
    }

    setListChildren(newChildren);
  }

  let addOption = (status, data, id, isSuggestion) => {
    const hasExistingAnswers = existingAnswer && existingAnswer.length > 1 && existingAnswer[VALUE_POS].value;
    const existingAnswers = existingAnswer && existingAnswer[VALUE_POS].value;
    var name;
    if (status === null) {
      // Use the name from the response (if available) or the ID if not
      name = data["label"] || id;
    } else {
      console.log("Error: Thesaurus lookup failed with code " + status);

      // Fallback by using the ID
      name = id;
    }

    // Avoid the race condition by using updater functions
    var newChild = [name, id, isSuggestion, hasExistingAnswers && existingAnswers.includes(id)];
    setListChildren(oldListChildren => {var newList = oldListChildren.slice(); newList.push(newChild); return(newList);});
  }

  let removeSelection = (id, name, wasSelected=false) => {
    // Do not remove this element if it is in our default suggestions
    // Instead, just update the number of items selected
    if (typeof defaultSuggestions !== "undefined" && id in defaultSuggestions) {
      setListChildren(
        (oldChildren) => {
          return oldChildren.slice().map( (childData) => {
            if (childData[ID_POS] === id && childData[NAME_POS] === name) {
              childData[IS_SELECTED_POS] = !wasSelected;
            }
            return(childData);
          })
        }
      );
      return;
    }

    var newChildren = listChildren.filter(element => element[ID_POS] != id);
    setListChildren(newChildren);
  }

  return (
    <React.Fragment>
      <VocabularyQuery
        onClick = {addSelection}
        questionDefinition = {questionDefinition}
        vocabularyFilter = {vocabularyFilter}
        vocabularies = {source}
        ref = {(ref) => {thesaurusRef = ref;}}
        disabled = {disabled}
        clearOnClick = {!isRadio}
        searchDefault = {searchDefault || (hasDefaultOptions ? "Other (please specify)" : "")}
        defaultValue = {isRadio && radioSelect == radioValue ? radioName : undefined}
        onInputFocus = {() => {if (isRadio && radioSelect != radioValue) {addSelection(radioValue, radioName);}}}
        isNested = {isRadio && hasDefaultOptions}
        {...rest}
      >
        {
          // If we don't have an external container, add results here
          typeof selectionContainer === "undefined" && generateList(disabled, isRadio)
        }
      </VocabularyQuery>
      {
        // If we have an external container, open a portal there
        typeof selectionContainer !== "undefined" &&
        (
          <SelectionResults
            root = {selectionContainer}
            >
            {generateList(disabled, isRadio)}
          </SelectionResults>
        )
      }
      {/* Generate the hidden answer array */}
      <Answer
        answers={selectedListChildren}
        answerNodeType={'lfs:VocabularyAnswer'}
        questionDefinition={questionDefinition}
        existingAnswer={existingAnswer}
        noteComponent={NCRNote}
        noteProps={{
          vocabulary: source,
          onAddSuggestion: addSelection
        }}
        {...rest}
      />
    </React.Fragment>
  );
}

VocabularySelector.propTypes = {
    classes: PropTypes.object.isRequired,
    title: PropTypes.string,
    source: PropTypes.array.isRequired,
    max: PropTypes.number.isRequired,
    requiredAncestors: PropTypes.array,
    defaultSuggestions: PropTypes.object,
    searchDefault: PropTypes.string,
};

VocabularySelector.defaultProps = {
    title: "VocabularySelector",
    max: 0,
    searchDefault: ''
};

export default withStyles(SelectorStyle)(VocabularySelector);
