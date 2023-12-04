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
import { useTheme } from '@mui/material/styles';
import Tooltip from "@mui/material/Tooltip";

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
  let { classes, errorText, existingAnswer, questionName, questionDefinition, pageActive, ...rest } = props;
  let { variant, maxAnswers } = {...props.questionDefinition, ...props};
  const [ error, setError ] = useState(false);
  const [ map, setMap ] = useState(null);
  const [ initialized, setInitialized ] = useState(false);

  const [ currentWidth, setCurrentWidth] = useState(0);
  const [ isAreaHovered, setAreaHovered ] = useState(false);
  const [ tooltipTitle, setTooltipTitle ] = useState("");

  const [ imageMapper, setImageMapper ] = useState(<></>);
  const [ selectionDisplay, setSelectionDisplay ] = useState(<></>);

  const mapperRef = useRef(null);
  const questionRef = useRef(null);

  let initialSelection =
    // If there's no existing answer, there's no initial selection
    (!existingAnswer || existingAnswer[1].value === undefined) ? [] :
    // The value can either be a single value or an array of values; force it into an array
    Array.of(existingAnswer[1].value).flat()
    // Only the internal values are stored, turn them into pairs of [label, value] by using their displayedValue
    .map((item, index) => [Array.of(existingAnswer[1].displayedValue).flat()[index], item]);

  const [ selection, setSelection ] = useState(initialSelection);

  // Image Mapper does support tracking it's own set of highlighted areas.
  // However, there is no way to initialize self tracked highlights with pre-highlighted areas.
  // As a result, we manually highlight the selected areas.
  useEffect(() => {
    if (map ) {
      map.forEach((mapEntry => {
        // Check if this area is selected.
        let found = false;
        for (let i = 0; i < selection.length; i++) {
          if (selection[i][VALUE_POS] === mapEntry.value) {
            // Area has been selected, highlight it.
            mapEntry.preFillColor = getSelectedColor(mapEntry);
            found = true;
            break;
          }
        }
        if (!found) {
          mapEntry.preFillColor = getUnselectedColor(mapEntry);
        }
      }))
    }
  }, [selection, map])

  const theme = useTheme();

  let getUnselectedColor = (area) => getColor(area, "unselectedColor", null)
  let getSelectedColor = (area) => getColor(area, "selectedColor", theme.palette.primary.main + "66")
  let getHoverColor = (area) => getColor(area, "hoverColor", "#55555555");
  let getStrokeColor = (area) => getColor(area, "strokeColor", "black");

  let getColor = (area, property, fallback) => {
    let result = fallback;
    // Color Priority Order:
    // question with override > variant with override > map area specific > question > variant > default
    if (questionDefinition?.overrideMapColors && questionDefinition[property]) {
      result = questionDefinition[property];
    } else if (variant?.overrideMapColors && variant?.[property]) {
      result = variant[property];
    } else if (area?.[property]) {
      result = area[property];
    } else if (questionDefinition[property]) {
      result = questionDefinition[property];
    } else if (variant?.[property]) {
      result = variant[property]
    }

    return result;
  }

  // Perform any initialization that is required once the map has loaded.
  useEffect(() => {
    if(!initialized && map) {
      setSelection(oldSelection => {
        let newSelection = oldSelection.slice();

        map.forEach(mapEntry => {
          mapEntry.fillColor = getHoverColor(mapEntry);
          mapEntry.strokeColor = getStrokeColor(mapEntry);

          // Check if any selections match the map entry
          newSelection.forEach(selectionValue => {
            if (mapEntry.value === selectionValue[LABEL_POS] && mapEntry.title) {
              // Load the appropriate display values from the map.
              selectionValue[LABEL_POS] = mapEntry.title;
            }
          })
        })
        return newSelection;
      })

      setInitialized(true);
    }
  }, [map])

  // List out the selected areas in text
  useEffect(() => {
    setSelectionDisplay(
      <ul style={{float: "left"}}>
        {selection.map(selection => {
          return <li key={selection[VALUE_POS]}>{selection[LABEL_POS]}</li>
        })}
      </ul>);
  }, [selection])

  // When the question's variant changes, load the relevant data.
  useEffect(() => {
    if (variant) {
      let unparsedMap = variant.map?.["jcr:content"]?.["jcr:data"];
      setMap(unparsedMap ? JSON.parse(unparsedMap) : null);
    } else {
      setMap(null);
    }
  }, [variant])

  // When an area is clicked, update the selection associated with that area.
  let onAreaClicked = (area, index) => {
    let clickedEntry = [area.title, area.value];

    setSelection(oldSelection => {
      if (maxAnswers == 1) {
        // Single select: Just set selection to the most recent value
        return [clickedEntry];
      } else {
        // Multi select: toggle the values' selection state
        let newSelection = oldSelection.slice();
        let entryIndex = -1;
        newSelection.forEach((selectionValue, selectionIndex) => {
          if (selectionValue[VALUE_POS] === clickedEntry[VALUE_POS]) {
            entryIndex = selectionIndex;
          }
        })
        if(entryIndex == -1) {
          newSelection.push(clickedEntry);
        } else {
          newSelection.splice(entryIndex, 1);
        }
        return newSelection;
      }
    })
  }

  // Track when the user's cursor is over one of the areas
  let onMouseEnter = (area, index, event) => {
    setAreaHovered(true);
    setTooltipTitle(area.title);
  }

  let onMouseLeave = (area, index, event) => {
    setAreaHovered(false);
  }

  // Recreate the image mapper whenever needed.
  useEffect(()=> {
    setImageMapper(
      initialized && map ?
        <ImageMapper
          src={variant?.image}
          map={{"name": props.questionDefinition["@name"], "areas": map}}
          onClick={onAreaClicked}
          onMouseEnter={onMouseEnter}
          onMouseLeave={onMouseLeave}
          containerRef={mapperRef}
          responsive={true}
          parentWidth={(variant?.maxWidth == null || variant?.maxWidth > currentWidth)
            ? currentWidth : variant?.maxWidth}
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
      <div ref={questionRef}>
        <Tooltip title={tooltipTitle} open={isAreaHovered} followCursor>
          <div className={isAreaHovered ? classes.imageMapperHovered : null} style={{position: 'relative', float:"left"}}>
            {imageMapper}
          </div>
        </Tooltip>
        {selectionDisplay}
        <div style={{clear: "both"}}></div>
      </div>
      <Answer
        answers={selection}
        existingAnswer={existingAnswer}
        questionName={questionName}
        questionDefinition={props.questionDefinition}
        isMultivalued={maxAnswers!==1}
        answerNodeType="cards:SelectableAreaAnswer"
        pageActive={pageActive}
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
