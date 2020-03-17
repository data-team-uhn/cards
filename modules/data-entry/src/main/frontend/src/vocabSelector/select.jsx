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
import { FormControlLabel, List, RadioGroup, Typography, withStyles, Radio } from "@material-ui/core";

import { MakeRequest, REST_URL } from "../vocabQuery/util.jsx";
import Answer from "../questionnaire/Answer.jsx";
import Thesaurus from "../vocabQuery/query.jsx";
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
  const {defaultSuggestions, existingAnswer, source, vocabularyFilter, max, selectionContainer, questionDefinition, classes, ...rest} = props;

  const [defaultListChildren, setDefaultListChildren] = useState([]);
  const [listChildren, setListChildren] = useState([]);
  const [selected, setSelected] = useState(0);
  const [radioName, setRadioName] = useState("");
  const [radioSelect, setRadioSelect] = useState("");
  const [radioValue, setRadioValue] = useState("&nbsp;");
  
  const disabled = max > 1 && selected >= max;
  const isRadio = max === 1;
  const reminderText = `Please select at most ${max} options.`;
  const selectedListChildren = listChildren.filter( (element) => element[IS_SELECTED_POS] );

  let thesaurusRef = null;

  let generateList = (disabled, isRadio) => {
    if (isRadio) {
      var ghostSelected = radioSelect === "&nbsp;";
      return (
        <RadioGroup
          aria-label="selection"
          name="selection"
          className={classes.selectionList}
          value={radioSelect}
          onChange={changeRadio}
        >
          {generateListChildren(disabled, isRadio)}
          {/* Ghost radio for the text input */}
          <FormControlLabel
            control={
            <Radio
              onChange={() => {setRadioSelect(radioValue)}}
              onClick={() => {thesaurusRef.anchorEl.select(); setRadioSelect(radioValue)}}
              disabled={!ghostSelected && disabled}
              className={classes.ghostRadiobox}
            />
            }
            label="&nbsp;"
            name={radioName}
            value={radioValue}
            className={classes.ghostFormControl + " " + classes.childFormControl}
            classes={{
              label: classes.inputLabel
            }}
          />
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
    setListChildren(defaultListChildren);
    setSelected(1);
    setRadioSelect(event.target.value);
  }

  let handleThesaurus = (id, name) => {
    var isRadio = max === 1;
    if (isRadio) {
      setRadioName(name);
      setRadioValue(id);
      setSelected(1);
      setRadioSelect(id);
    } else {
      addSelection(id, name);
    }
  }

  // Create a new child from the selection with parent
  let addSelection = (id, name) => {
    // Also do not add anything if we are at our maximum number of selections
    if (selected >= max && max > 1 ) {
      return;
    }

    // Also do not add duplicates
    if (listChildren.some(element => {return element[ID_POS] === id})) {
      return;
    }

    if (max == 1) {
      // If only 1 child is allowed, replace it instead of copying our array
      var newChildren = defaultListChildren.slice();
      newChildren.push([name, id, false, true]);
      setListChildren(newChildren);
      setSelected(1);
      setRadioSelect(name);
    } else {
      // As per React specs, we do not modify the state array directly, but slice and add
      var newChildren = listChildren.slice();
      newChildren.push([name, id, false, true]);
      setListChildren(newChildren);
      setSelected(selected + 1);
    }
  }

  let populateDefaults = () => {
    var newChildren = [];
    const hasExistingAnswers = existingAnswer && existingAnswer.length > 1 && existingAnswer[1].value;
    const existingAnswers = hasExistingAnswers && existingAnswer[1].value;
    for (var id in defaultSuggestions) {
      // If we are given a name, use it
      if (typeof defaultSuggestions[id] !== "undefined") {
        newChildren.push([defaultSuggestions[id], id, true, hasExistingAnswers && existingAnswers.includes(id)]);
        continue;
      }

      // Determine the name from our vocab
      var escapedId = id.replace(":", "");  // Vocabulary terms have no colons in their JCR node names
      var url = new URL(`./${source}/${escapedId}.json`, REST_URL);
      MakeRequest(url, (status, data) => addDefaultSuggestion(status, data, id));
    };

    // If any answers are existing (i.e. we are loading an old form), also populate these
    if (hasExistingAnswers) {
      Array.of(existingAnswer[1].value).flat().forEach( (id) => {
        // Do not add a pre-existing answer if it is a default
        if (id in defaultSuggestions) {
          return;
        }
        // Determine the name from our vocab
        var escapedId = id.replace(":", "");  // Vocabulary terms have no colons in their JCR node names
        var url = new URL(`./${source}/${escapedId}.json`, REST_URL);
        MakeRequest(url, (status, data) => addDefaultSuggestion(status, data, id, false));
      });
    }

    setListChildren(newChildren);
  }

  let addDefaultSuggestion = (status, data, id, isSuggestion) => {
    const hasExistingAnswers = existingAnswer && existingAnswer.length > 1 && existingAnswer[1].value;
    const existingAnswers = existingAnswer && existingAnswer[1].value;
    if (status === null) {
      // Use the name from the response (if available) or the ID if not
      var name = data["name"] || id;

      // Avoid the race condition by using updater functions
      var newChild = [name, id, isSuggestion, hasExistingAnswers && existingAnswers.includes(id)];
      setDefaultListChildren(oldDefaultListChildren => {var newList = oldDefaultListChildren.slice(); newList.push(newChild); return(newList);});
      setListChildren(oldListChildren => {var newList = oldListChildren.slice(); newList.push(newChild); return(newList);});
    } else {
      console.log("Error: Thesaurus lookup failed with code " + status);
    }
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
      if (wasSelected) {
        setSelected(oldSelected => oldSelected - 1);
      } else {
        setSelected(oldSelected => oldSelected + 1);
      }
      return;
    }

    var newChildren = listChildren.filter(element => element[ID_POS] != id);
    setListChildren(newChildren);
    setSelected(selected - 1);
  }

  // Populate defaults when we load for the first time
  useEffect(() => {populateDefaults()}, [JSON.stringify(defaultSuggestions)]);

  return (
    <React.Fragment>
      <Thesaurus
        onClick = {handleThesaurus}
        vocabularyFilter = {vocabularyFilter}
        vocabulary = {source}
        ref = {(ref) => {thesaurusRef = ref;}}
        disabled = {disabled}
        overrideText = {disabled ? reminderText : undefined }
        clearOnClick = {!isRadio}
        onInputFocus = {() => {setRadioSelect(radioValue);}}
        {...rest}
      >
        {max > 1 ?(<Typography>{reminderText}</Typography>) : ''}
        {
          // If we don't have an external container, add results here
          typeof selectionContainer === "undefined" && generateList(disabled, isRadio)
        }
      </Thesaurus>
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
    source: PropTypes.string.isRequired,
    max: PropTypes.number.isRequired,
    requiredAncestors: PropTypes.array,
    defaultSuggestions: PropTypes.object,
    searchDefault: PropTypes.string,
};

VocabularySelector.defaultProps = {
    title: "VocabularySelector",
    max: 0,
    searchDefault: 'Other (specify here)'
};

export default withStyles(SelectorStyle)(VocabularySelector);
