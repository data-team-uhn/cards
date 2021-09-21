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

import React, { useState } from "react";

import { InputAdornment, TextField, Typography, withStyles } from "@material-ui/core";
import NumberFormat from 'react-number-format';

import PropTypes from "prop-types";

import Answer from "./Answer";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";
import MultipleChoice from "./MultipleChoice";

import AnswerComponentManager from "./AnswerComponentManager";

/** Conversion between the `dataType` setting in the question definition and the corresponding primary node type of the `Answer` node for that question. */
const DATA_TO_NODE_TYPE = {
  "long": "cards:LongAnswer",
  "double": "cards:DoubleAnswer",
  "decimal": "cards:DecimalAnswer",
};
/** Conversion between the `dataType` setting in the question definition and the corresponding value type for storing the value in the `Answer` node. */
const DATA_TO_VALUE_TYPE = {
  "long": "Long",
  "double": "Double",
  "decimal": "Decimal",
};

// Component that renders a multiple choice question, with optional number input.
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// Optional props:
//  minAnswers: Integer denoting minimum number of options that may be selected
//  maxAnswers: Integer denoting maximum number of options that may be selected
//  text: String containing the question to ask
//  defaults: Array of arrays, each with two values, a "label" which will be displayed to the user,
//            and a "value" denoting what will actually be stored
//  displayMode: Either "input", "textbox", or undefined denoting the type of
//             user input. Currently, only "input" is supported
//  maxValue: The maximum allowed input value
//  minValue: The minimum allowed input value
//  type: One of "integer" or "float" (default: "float")
//  errorText: String to display when the input is not valid (default: "invalid input")
//  isRange: Whether or not to display a range instead of a single value
//
// Sample usage:
// <NumberQuestion
//    text="Please enter the patient's age"
//    defaults={[
//      ["<18", -1]
//    ]}
//    maxAnswers={1}
//    minValue={18}
//    type="long"
//    errorText="Please enter an age above 18, or select the <18 option"
//    />
function NumberQuestion(props) {
  const { existingAnswer, errorText, isRange, classes, ...rest} = props;
  const { dataType, displayMode, minValue, maxValue } = {...props.questionDefinition, ...props};
  const answerNodeType = props.answerNodeType || DATA_TO_NODE_TYPE[dataType];
  const valueType = props.valueType || DATA_TO_VALUE_TYPE[dataType];
  const [error, setError] = useState(false);

  const initialValue = existingAnswer ? existingAnswer[1].value : undefined;

  // The following two are only used if a default is not given, as we switch to handling values here
  const [input, setInput] = useState(initialValue);
  const [endInput, setEndInput] = useState(undefined);

  // Callback function for our min/max
  let hasError = (text) => {
    let value = 0;

    if (typeof(text) === "undefined") {
      // The custom input has been unset
      return false;
    }

    if (dataType === "long") {
      // Test that it is an integer
      if (!/^[-+]?\d*$/.test(text)) {
        return true;
      }

      value = parseInt(text);
    } else {
      value = Number(text);

      // Reject whitespace and non-numbers
      if (/^\s*$/.test(text) || isNaN(value)) {
        return true;
      }
    }

    // Test that it is within our min/max (if they are defined)
    if ((typeof minValue !== 'undefined' && value < minValue) ||
      (typeof maxValue !== 'undefined' && value > maxValue)) {
      return true;
    }

    return false;
  }

  // Callback for a change of MultipleChoice input to check for errors on the input
  let findError = (text) => {
    text ? setError(hasError(text)) : setError(false);
  }

  // Callback for a range input to check for errors on our self-stated input
  let findRangeError = (inputToCheck, endInputToCheck) => {
    if (hasError(inputToCheck)) {
      setError(true);
      return;
    }

    // Also consider the end of the range (if applicable)
    if (isRange && (hasError(endInputToCheck) ||
      Number(inputToCheck) > Number(endInputToCheck))) {
        setError(true);
        return;
    }

    setError(false);
  }

  const answers = isRange ? [["start", input], ["end", endInput]] : [["start", input]];
  const textFieldProps = {
    min: minValue,
    max: maxValue,
    allowNegative: (typeof minValue === "undefined" || minValue < 0),
    decimalScale: dataType === "long" ? 0 : undefined
  };
  const muiInputProps = {
    inputComponent: NumberFormatCustom, // Used to override a TextField's type
    className: classes.textField
  };
  if (props.questionDefinition && props.questionDefinition.unitOfMeasurement) {
    muiInputProps.endAdornment = <InputAdornment position="end">{props.questionDefinition.unitOfMeasurement}</InputAdornment>;
  }

  let hasAnswerOptions = !!(props.defaults || Object.values(props.questionDefinition).some(value => value['jcr:primaryType'] == 'cards:AnswerOption'));

  return (
    <Question
      disableInstructions={hasAnswerOptions}
      currentAnswers={ typeof(input) != "undefined" && input != "" ? 1 : 0 }
      {...props}
      >
      {error && <Typography color='error'>{errorText}</Typography>}
      { isRange ?
        <>
        <TextField
          className={classes.textField + " " + classes.answerField}
          onChange={(event) => {
            findRangeError(event.target.value, endInput);
            setInput(event.target.value);
          }}
          inputProps={textFieldProps}
          value={input}
          InputProps={muiInputProps}
          />
          <Answer
            answers={answers}
            existingAnswer={existingAnswer}
            answerNodeType={answerNodeType}
            valueType={valueType}
            {...rest}
            />
          <span className={classes.mdash}>&mdash;</span>
          <TextField
            className={classes.textField}
            onChange={(event) => {
              findRangeError(input, event.target.value);
              setEndInput(event.target.value);
            }}
            inputProps={textFieldProps}
            value={endInput}
            InputProps={muiInputProps}
            />
        </>
        :
        <MultipleChoice
          answerNodeType={answerNodeType}
          valueType={valueType}
          input={displayMode === "input" || displayMode === "list+input"}
          textbox={displayMode === "textbox"}
          onUpdate={findError}
          additionalInputProps={textFieldProps}
          muiInputProps={muiInputProps}
          error={error}
          existingAnswer={existingAnswer}
          {...rest}
          />
      }
    </Question>);
}

// Helper function to bridge react-number-format with @material-ui
export function NumberFormatCustom(props) {
  const { inputRef, onChange, ...other } = props;

  return (
    <NumberFormat
      {...other}
      getInputRef={inputRef}
      onValueChange={values => {
        onChange({
          target: {
            value: values.value,
          },
        });
      }}
    />
  );
}

NumberFormatCustom.propTypes = {
  inputRef: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
};

NumberQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    minAnswers: PropTypes.number,
    maxAnswers: PropTypes.number,
    minValue: PropTypes.number,
    maxValue: PropTypes.number,
    displayMode: PropTypes.oneOf([undefined, "input", "textbox", "list", "list+input"]),
  }).isRequired,
  text: PropTypes.string,
  minAnswers: PropTypes.number,
  maxAnswers: PropTypes.number,
  defaults: PropTypes.array,
  displayMode: PropTypes.oneOf([undefined, "input", "list", "list+input", "textbox"]),
  dataType: PropTypes.oneOf(['long', 'double', 'decimal']),
  minValue: PropTypes.number,
  maxValue: PropTypes.number,
  errorText: PropTypes.string,
  isRange: PropTypes.bool,
};

NumberQuestion.defaultProps = {
  errorText: "Invalid input",
  isRange: false
};

const StyledNumberQuestion = withStyles(QuestionnaireStyle)(NumberQuestion)
export default StyledNumberQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (["long", "double", "decimal"].includes(questionDefinition.dataType)) {
    return [StyledNumberQuestion, 50];
  }
});
