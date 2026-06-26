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

import { useState, useEffect, useRef } from "react";

import { Alert, Checkbox, FormControlLabel, Typography } from "@mui/material";
import { useTheme, alpha } from '@mui/material/styles';
import Tooltip from "@mui/material/Tooltip";
import PropTypes from "prop-types";

import { checkPropTypes } from "../propTypes";
import Answer, { LABEL_POS, VALUE_POS } from "./Answer";
import { useFormReaderContext } from "./FormContext";
import Question from "./Question";
import questionEditorHints from './SelectableAreaQuestion-editor-hints.json';
import questionEditorConfig from './SelectableAreaQuestion-editor.json';
import FormattedText from "../components/FormattedText.jsx";

// Component that renders an image with clickable areas based on the available
// areas defined by the image and the options defined in the question. Selected
// options will be highlighted and listed in text format.
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional arguments:
//  maxAnswers: Integer denoting maximum number of areas that may be selected
//  minAnswers: Integer denoting minimum number of areas that may be selected
//  text: String containing the question to ask
//  variant: The path to the variant which defines the image and area map
//  variant.maxWidth: The max width that the image should be displayed in
//
// sample usage:
// <SelectableAreasQuestion
//    text="Select areas of the body"
//    variant="/libs/cards/dataEntry/SelectableArea/FullBody"
//    />
function SelectableAreaQuestion(props) {
  checkPropTypes(SelectableAreaQuestion, props);
  let { existingAnswer, questionName, questionDefinition, pageActive, isEdit, ...rest } = props;
  let { variant, maxAnswers } = { ...props.questionDefinition, ...props };

  const [ map, setMap ] = useState(null);
  const [ initialized, setInitialized ] = useState(false);

  const [ currentWidth, setCurrentWidth] = useState(0);
  const [ tooltipTitle, setTooltipTitle ] = useState("");
  const [ hoveredIndex, setHoveredIndex ] = useState(-1);

  const [ imageMap, setImageMap ] = useState(<></>);
  const [ selectionDisplay, setSelectionDisplay ] = useState(<></>);

  const [ notApplicableOption, setNotApplicableOption ] = useState(null);
  const [ notApplicableChecked, setNotApplicableChecked ] = useState(false);

  const questionRef = useRef(null);

  const formContext = useFormReaderContext();
  const handleFormDataChange = formContext?.['/OnFormDataChanged'];

  const defaultValue = questionDefinition.defaultValue;
  // The valid values for this question are its answer options, as [label, value] pairs.
  const answerOptions = Object.values(questionDefinition)
    .filter(value => value?.['jcr:primaryType'] === 'cards:AnswerOption')
    .map(answerOption => [answerOption.label || answerOption.value, answerOption.value]);

  // Parse the configured default value(s). A multivalued question (maxAnswers !== 1) accepts a comma-separated
  // list of distinct values; a single-valued question takes the value as-is. Only values matching an answer
  // option are kept (others are discarded), capped at maxAnswers.
  let defaultValueList = [];
  if (defaultValue != null && String(defaultValue) !== "") {
    defaultValueList = maxAnswers === 1
      ? [String(defaultValue)]
      : Array.from(new Set(String(defaultValue).split(",").map(value => value.trim()).filter(Boolean)));
  }
  const defaultSelection = defaultValueList
    .map(value => answerOptions.find(option => String(option[VALUE_POS]) === String(value)))
    .filter(Boolean)
    .slice(0, maxAnswers || undefined);

  let initialSelection =
    // If there's no existing answer, there's no initial selection
    (!existingAnswer || existingAnswer[1].value === undefined)
      ?
      defaultSelection
      :
    // The value can either be a single value or an array of values; force it into an array
      Array.of(existingAnswer[1].value).flat()
      // Only the internal values are stored, turn them into pairs of [label, value] by using their displayedValue
        .map((item, index) => [Array.of(existingAnswer[1].displayedValue).flat()[index], item]);

  const [ selection, setSelection ] = useState(initialSelection);

  let isAreaSelected = (area) => selection.filter(selectedArea => selectedArea[VALUE_POS] == area.value).length > 0;

  const theme = useTheme();

  let getUnselectedColor = (area) => getColor(area, "unselectedColor", "transparent");
  let getSelectedColor = (area) => getColor(area, "selectedColor", alpha(theme.palette.primary.main, 0.35));
  let getHoverColor = (area) => isAreaSelected(area) ? getSelectedHoverColor(area) : getUnselectedHoverColor(area);
  let getUnselectedHoverColor = (area) => getColor(area, "hoverColor", alpha(theme.palette.action.active, 0.4));
  let getSelectedHoverColor = (area) => getColor(area, "selectedHoverColor", alpha(theme.palette.action.active, 0.6));
  let getStrokeColor = (area) => getColor(area, "strokeColor", theme.palette.divider);

  let getColor = (area, property, fallback) => {
    let result = fallback;
    // Color Priority Order:
    // Question with override > Variant with override > Map area specific > Question > Variant > Default
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

  let updateAreaColor = (area) => {
    area.fill = (area.isHovered ? getHoverColor(area)
      : (isAreaSelected(area) ? getSelectedColor(area) : getUnselectedColor(area)));
  }

  // Perform any initialization that is required once the map has loaded.
  if(!initialized && variant?.map) {
    let unparsedMap = variant.map?.["jcr:content"]?.["jcr:data"];
    let inputMap = unparsedMap ? JSON.parse(unparsedMap) : null;

    let options = {};
    Object.entries(questionDefinition)
      // Grab all AnswerOptions for this question
      .filter( (childNode) => {
        return childNode[1]['jcr:primaryType'] && childNode[1]['jcr:primaryType'] === 'cards:AnswerOption'
      })
      // Set up a value to label map based on these AnswerOptions
      .forEach( (answerOption) => {
        if (answerOption[1].notApplicable) {
          // If there is an existing Answer that matches a Not Applicable AnswerOption,
          // set up the question state to match
          setNotApplicableOption(answerOption[1]);
          if (selection.length == 1 && selection[0][VALUE_POS] == answerOption[1].value) {
            setSelection([]);
            setNotApplicableChecked(true);
          }
        }
        let value = answerOption[1]['value'];
        let label = answerOption[1]['label'] || value;
        options[value] = label;
      });

    // Set up the map of selectable areas to match the options for this question.
    // This allows for questions to only provide a subset of areas supported by the variant.
    let outputMap = [];
    inputMap.forEach(mapEntry => {
      if (mapEntry.value in options) {
        mapEntry.title = options[mapEntry.value];
        mapEntry.fill = getUnselectedColor(mapEntry);
        mapEntry.stroke = getStrokeColor(mapEntry);
        mapEntry.hover = getHoverColor(mapEntry);
        outputMap.push(mapEntry);
      }
    });

    setMap(outputMap);
    if (outputMap) {
      setInitialized(true);
    }
  }

  // Update the map to match the current selected values
  useEffect(() => {
    if (map) {
      setMap(
        map.map((mapEntry => {
          let entry = mapEntry;
          updateAreaColor(entry);
          return entry;
        }))
      );
    }
  }, [selection]);

  // List out the selected areas in text
  useEffect(() => {
    setSelectionDisplay(
      <ul style={{ float: "left" }}>
        {selection.map(selection => {
          return <li key={selection[VALUE_POS]}>{selection[LABEL_POS]}</li>
        })}
      </ul>);
  }, [selection]);

  // When an area is clicked, update the selection associated with that area.
  let onAreaClicked = (area) => {
    // Ignore any user input in view mode
    if (!isEdit) {
      return;
    }

    let clickedEntry = [area.title, area.value];

    setNotApplicableChecked(false);
    setSelection(oldSelection => {
      if (maxAnswers == 1) {
        // Single select
        if (selection.length == 1 && selection[0][VALUE_POS] === clickedEntry[VALUE_POS]) {
          // Unselect the selection if the user clicked it again
          return [];
        } else {
          // Just set selection to the most recent value
          return [clickedEntry];
        }
      } else {
        // Multi select: toggle the values' selection state
        let newSelection = oldSelection.slice();
        // Check if the clicked area has been selected
        let entryIndex = -1;
        newSelection.forEach((selectionValue, selectionIndex) => {
          if (selectionValue[VALUE_POS] === clickedEntry[VALUE_POS]) {
            entryIndex = selectionIndex;
          }
        });
        // Toggle the clicked areas selection state
        if(entryIndex == -1) {
          newSelection.push(clickedEntry);
        } else {
          newSelection.splice(entryIndex, 1);
        }
        return newSelection;
      }
    });
    handleFormDataChange?.();
  }

  let onNotApplicableClicked = () => {
    setSelection([]);
    setNotApplicableChecked(!notApplicableChecked);
  }

  // Track when the user's cursor is over one of the areas
  let onMouseEnter = (mapEntry, index) => {
    // Only give the user hover feedback if:
    // - it is edit mode
    // - or if it is view mode and the user is hovering a previously selected area
    if (isEdit || isAreaSelected(mapEntry))
    {
      mapEntry.isHovered = true;
      updateAreaColor(mapEntry);
      setTooltipTitle(mapEntry.title);
      setHoveredIndex(index);
    }
  }

  let onMouseLeave = (mapEntry) => {
    mapEntry.isHovered = false;
    updateAreaColor(mapEntry);
    setHoveredIndex(-1);
  }

  // Create the SVG of possible areas
  useEffect(()=> {
    // Calculate the desired width of the SVG container
    let width = (variant?.maxWidth == null || variant?.maxWidth > currentWidth)
      ? currentWidth
      : variant?.maxWidth;

    // Determine how the base image and child elements should be scaled
    let scale = width / variant?.imageWidth;
    let height = variant?.imageHeight * scale;

    let viewBox = variant?.viewBox || null;
    if (viewBox) {
      // Viewbox is a set of 4 space seperated numbers "<min-x> <min-y> <width> <height>".
      // These numbers must be scaled by the same factor as other coordinates then recombined.
      viewBox = viewBox.split(" ").map(value => Number(value) * scale).join(" ");
    }

    setImageMap(
      initialized && map ?
        <svg
          width={width}
          height={height}
          viewBox={viewBox}>
          <image
            href={variant?.image}
            width={width}/>
          <>
            {map.map((mapEntry, index) => {
              if (mapEntry.shape == "rect") {
                return <rect
                  key={index}

                  x={mapEntry.coords[0] * scale}
                  y={mapEntry.coords[1] * scale}
                  width={(mapEntry.coords[2] - mapEntry.coords[0]) * scale}
                  height={(mapEntry.coords[3] - mapEntry.coords[1]) * scale}

                  fill={mapEntry.fill}
                  stroke={mapEntry.strokeColor}

                  onMouseEnter={()=>{onMouseEnter(mapEntry, index)}}
                  onMouseLeave={()=>{onMouseLeave(mapEntry)}}
                  onClick={()=>{onAreaClicked(mapEntry)}}
                />
              } else if (mapEntry.shape == "poly") {
                // Create the coordinate string in the format "x1,y1 x2,y2..."
                let i = 1;
                let coordinateString = "";
                while (i < mapEntry.coords.length) {
                  coordinateString += (mapEntry.coords[i-1] * scale) + "," + (mapEntry.coords[i] * scale) + " ";
                  i += 2;
                }
                return <polygon
                  key={index}

                  points={coordinateString}

                  fill={mapEntry.fill}
                  stroke={mapEntry.strokeColor}

                  onMouseEnter={()=>{onMouseEnter(mapEntry, index)}}
                  onMouseLeave={()=>{onMouseLeave(mapEntry)}}
                  onClick={()=>{onAreaClicked(mapEntry)}}/>
              } else return <></>
            })}
          </>
        </svg>
        : <></>
    );
  }, [map, initialized, currentWidth, hoveredIndex]);

  // Track the current width of the question in order to ensure the image isn't too wide
  useEffect(() => {
    if (questionRef.current) {
      const observer = new ResizeObserver(entries => {
        setCurrentWidth(entries[0].contentRect.width);
      });
      observer.observe(questionRef.current);
      return () => questionRef.current && observer.unobserve(questionRef.current);
    }
  }, []);

  return (
    <Question
      currentAnswers={notApplicableChecked ? 1 : selection.length}
      {...props}
      preventDefaultView
    >
      {notApplicableOption != null ?
        (isEdit ?
          <>
            <FormControlLabel
              control={
                <Checkbox
                  checked={notApplicableChecked}
                  onChange={() => onNotApplicableClicked()}
                  sx={{ my: -2, mx: 0 }}
                  color="secondary"
                />}
              label={notApplicableOption.label || notApplicableOption.value}
              value={notApplicableOption.value}
            />
            <FormattedText sx={{ pl: 4 }} variant="caption" color="textSecondary">
              {notApplicableOption.help}
            </FormattedText>
          </>
          : notApplicableChecked ?
            <Typography>{notApplicableOption.label || notApplicableOption.value}</Typography>
            : <></>
        ) : <></>
      }
      {
        (isEdit || notApplicableOption == null || !notApplicableChecked) ?
          <div ref={questionRef}>
            <Tooltip title={tooltipTitle} open={hoveredIndex >= 0} followCursor>
              <div style={{ position: 'relative', float:"left", cursor: (hoveredIndex >= 0 && isEdit ? "pointer" : "auto") }}>
                {variant ?
                  imageMap
                  :
                  <Alert severity="warning">
                    The selectable areas cannot be displayed due to incorrect configuration:
                    &quot;variant&quot; is not defined or invalid
                  </Alert>
                }
              </div>
            </Tooltip>
            {selectionDisplay}
            <div style={{ clear: "both" }}></div>
          </div>
          : <></>
      }
      { isEdit && <Answer
        answers={notApplicableChecked ?
          [[notApplicableOption.label || notApplicableOption.value, notApplicableOption.value]] : selection}
        existingAnswer={existingAnswer}
        questionName={questionName}
        questionDefinition={props.questionDefinition}
        isMultivalued={maxAnswers!==1}
        answerNodeType="cards:SelectableAreaAnswer"
        pageActive={pageActive}
        {...rest}
      />
      }
    </Question>);
}

SelectableAreaQuestion.propTypes = {
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    minAnswers: PropTypes.number,
    maxAnswers: PropTypes.number,
    variant: PropTypes.object,
  }).isRequired,
  text: PropTypes.string,
  minAnswers: PropTypes.number,
  maxAnswers: PropTypes.number
};

SelectableAreaQuestion.canProcess = (questionDefinition) => {
  if (questionDefinition.dataType === "selectableArea") {
    return [SelectableAreaQuestion, 50];
  }
};

SelectableAreaQuestion.questionEditorConfig = questionEditorConfig;
SelectableAreaQuestion.questionEditorHints = questionEditorHints;

export default SelectableAreaQuestion;
