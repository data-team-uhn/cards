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

import React, { useState, useEffect, useRef } from "react";

import { Typography } from "@mui/material";
import withStyles from '@mui/styles/withStyles';

import ImageMapper from "react-img-mapper"
import PropTypes from "prop-types";

import Answer, { LABEL_POS, VALUE_POS } from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";
import AnswerComponentManager from "./AnswerComponentManager";

// Component that renders a multiple choice question, with optional text input.
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional arguments:
//  maxAnswers: Integer denoting maximum number of areas that may be selected
//  minAnswers: Integer denoting minimum number of areas that may be selected
//  text: String containing the question to ask
//  variant: The path to the variant which defines the image and area map
//  maxWidth: The max width that the image should be displayed in
//
// sample usage:
// <SelectableAreasQuestion
//    text="Select areas of the body"
//    variant="/libs/cards/dataEntry/SelectableArea/FullBody"
//    />
function SelectableAreaQuestion(props) {
  let { errorText, existingAnswer, questionName, ...rest } = props;
  let { variant, maxAnswers } = {...props.questionDefinition, ...props};
  const [ error, setError ] = useState(false);
  const [ map, setMap ] = useState(null);
  const [ imageUrl, setImageUrl ] = useState(null);
  const [ imageMapper, setImageMapper ] = useState([]);
  const [ selectionDisplay, setSelectionDisplay ] = useState(<></>);
  const [ initialized, setInitialized ] = useState(false);
  const [ maxWidth, setMaxWidth ] = useState(null);
  const [ currentWidth, setCurrentWidth] = useState(0);
  const [ strokeColor, setStrokeColor ] = useState(null);
  const [ highlightColor, setHighlightColor ] = useState(null);

  const mapperRef = useRef(null);
  const questionRef = useRef(null);

  let initialSelection =
    // If there's no existing answer, there's no initial selection
    (!existingAnswer || existingAnswer[1].value === undefined) ? [] :
    // The value can either be a single value or an array of values; force it into an array
    Array.of(existingAnswer[1].value).flat()
    // Only the internal values are stored, turn them into pairs of [label, value] by using their displayedValue
    .map((item, index) => [Array.of(existingAnswer[1].displayedValue).flat()[index], item]);


  const [ selections, setSelections ] = useState(initialSelection);

  // Image Mapper does support tracking it's own set of highlighted areas.
  // However, there is no way to initialize self tracked highlights with pre-highlighted areas.
  // As a result, we manually highlight the selected areas.
  let updateMapWithSelections = () => {
    map.forEach((mapEntry => {
      // Check if this area is selected.
      let found = false;
      for (let i = 0; i < selections.length; i++) {
        if (selections[i][VALUE_POS] === mapEntry.value) {
          // Area has been selected, highlight it.
          mapEntry.preFillColor = highlightColor ? highlightColor : mapEntry.fillColor;
          found = true;
          break;
        }
      }
      if (!found) {
        // Area has not been selected, clear any highlight.
        delete mapEntry.preFillColor;
      }
    }))
  }

  // As the displayed values are based on an extension, the backend doesn't know what display values go with what values.
  // Once the map has loaded, initialize the selections to set the approriate display values.
  useEffect(() => {
    if(!initialized && map) {
      selections.forEach(selection =>{
        map.forEach(mapEntry => {
          if (mapEntry.value === selection[LABEL_POS] && mapEntry.title) {
            selection[LABEL_POS] = mapEntry.title;
          }
        });
      })

      // Update both the map highlights and the displayed list of selections
      updateMapWithSelections();
      updateSelectionsDisplay();
      setInitialized(true);
    }
  }, [initialized, map])

  // List out the selected areas in text
  let updateSelectionsDisplay = () => {
    setSelectionDisplay(
      <ul>
        {selections.map(selection => {
          return <li key={selection[VALUE_POS]}>{selection[LABEL_POS]}</li>
        })}
      </ul>);
  }

  // When the question's variant changes, load the relevant data.
  useEffect(() => {
    if (variant) {
      let unparsedMap = variant.map?.["jcr:content"]?.["jcr:data"];
      setMap(unparsedMap ? JSON.parse(unparsedMap) : null);
      setImageUrl(variant.image);
      setMaxWidth(variant.maxWidth);
      setStrokeColor(variant.strokeColor);
      setHighlightColor(variant.highlightColor);
    } else {
      resetQuestionData();
    }
  }, [variant])

  // Clear out any data associated with a specific question
  let resetQuestionData = () => {
    setImageUrl(null);
    setMap(null);
    setSelections(initialSelection);
    setMaxWidth(null);
  }

  // When an area is clicked, update the selection associated with that area.
  let onAreaClicked = (area, index) => {
    let clickedEntry = [area.title, area.value];

    if (maxAnswers == 1) {
      // Single select: Just set selection to the most recent value
      selections.pop();
      selections.push(clickedEntry);
    } else {
      // Multi select: toggle the values' selection state
      let entryIndex = -1;
      selections.forEach((selectionValue, selectionIndex) => {
        if (selectionValue[VALUE_POS] === clickedEntry[VALUE_POS]) {
          entryIndex = selectionIndex;
        }
      })
      if(entryIndex == -1) {
        selections.push(clickedEntry);
      } else {
        selections.splice(entryIndex, 1);
      }
    }

    // Update the displayed highlights and list
    updateMapWithSelections();
    updateSelectionsDisplay();
  }

  // Recreate the image mapper whenever needed.
  useEffect(()=> {
    setImageMapper(
      initialized && map ? 
        <ImageMapper
          src={imageUrl}
          map={{"name": props.questionDefinition["@name"], "areas": map}}
          onClick={onAreaClicked}
          containerRef={mapperRef}
          responsive={true}
          parentWidth={(maxWidth == null || maxWidth > currentWidth) ? currentWidth : maxWidth}
          strokeColor={strokeColor ? strokeColor : null}
          />
        : <></>
      )
  }, [map, initialized, currentWidth])

  // Track the current width of the question in order to ensure the image isn't too wide
  useEffect(() => {
    if (questionRef.current) {
      const observer = new ResizeObserver(entries => {
        setCurrentWidth(entries[0].contentRect.width)
      });
      observer.observe(questionRef.current);
      return () => questionRef.current && observer.unobserve(questionRef.current);
    }
  })

  return (
    <Question
      {...props}
      >
      {error && <Typography color='error'>{errorText}</Typography>}
      <div ref={questionRef} style={{width: '100%'}}>
        {imageMapper}
      </div>
      {selectionDisplay}
      <Answer
        answers={selections}
        existingAnswer={existingAnswer}
        questionName={questionName}
        isMultivalued={maxAnswers!==1}
        answerNodeType="cards:SelectableAreaAnswer"
        {...rest}
        />
    </Question>);
}

SelectableAreaQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    minAnswers: PropTypes.number,
    maxAnswers: PropTypes.number,
    variant: PropTypes.object,
  }).isRequired,
  text: PropTypes.string,
  minAnswers: PropTypes.number,
  maxAnswers: PropTypes.number,
  defaults: PropTypes.array,
  errorText: PropTypes.string
};

const StyledSelectableAreaQuestion = withStyles(QuestionnaireStyle)(SelectableAreaQuestion)
export default StyledSelectableAreaQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (questionDefinition.dataType === "selectableArea") {
    return [StyledSelectableAreaQuestion, 50];
  }
});
