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
import AnswerInstructions from "./AnswerInstructions";
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
//  errorText: String to display when the input is not valid (default: "")
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
  const { existingAnswer, errorText, classes, ...rest} = props;
  const { dataType, displayMode, minAnswers, minValue, maxValue, isRange } = {...props.questionDefinition, ...props};
  const answerNodeType = props.answerNodeType || DATA_TO_NODE_TYPE[dataType];
  const valueType = props.valueType || DATA_TO_VALUE_TYPE[dataType];
  const [ minMaxError, setMinMaxError ] = useState(false);
  const [ rangeError, setRangeError ] = useState(false);

  const initialValue = Array.from(existingAnswer?.[1]?.value || []);

  // The following two are only used for range answers
  const [lowerLimit, setLowerLimit] = useState(initialValue[0]);
  const [upperLimit, setUpperLimit] = useState(initialValue[1]);

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
    setMinMaxError(text && hasError(text));
  }

  React.useEffect(() => {
    if (!isRange) return;
    // Check for invalid range limits
    setMinMaxError(
      lowerLimit && hasError(lowerLimit) ||
      upperLimit && hasError(upperLimit)
    );
    setRangeError(
       !lowerLimit && upperLimit ||
       (Number(lowerLimit) > Number(upperLimit))
    );
  }, [lowerLimit, upperLimit]);

  const answers = [];
  // Only save ranges that have both limits specified
  if (isRange && lowerLimit && !isNaN(+lowerLimit) && upperLimit && !isNaN(+upperLimit)) {
    answers.push(["lower", lowerLimit]);
    answers.push(["upper", upperLimit]);
  }

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

  // Generate message about accepted min/maxValues
  let minMaxMessage = "";
  if (typeof minValue !== "undefined" || typeof maxValue !== "undefined") {
    minMaxMessage = "Please enter values ";
    if (typeof minValue !== "undefined" && typeof maxValue !== "undefined") {
      minMaxMessage = `${minMaxMessage} between ${minValue} and ${maxValue}`;
    } else if (typeof minValue !== "undefined") {
      minMaxMessage = `${minMaxMessage} of at least ${minValue}`;
    } else {
      minMaxMessage = `${minMaxMessage} of at most ${maxValue}`;
    }
    if (hasAnswerOptions) {
      minMaxMessage = `${minMaxMessage} or select one of the options`;
    }
  }

  // Range error message
  let rangeErrorMessage = "The range is invalid: the lower limit must be less than or equal to the upper limit";

  let rangeDisplayFormatter = function(label, idx) {
    if (idx > 0 || !(initialValue?.length)) return '';
    let limits = initialValue.slice(0, 2);
    // In case of invalid data (only one limit of the range is available)
    if (limits.length == 1) {
      limits.push("");
    }
    return limits.join(' - ');
  }

  return (
    <Question
      defaultDisplayFormatter={isRange? rangeDisplayFormatter : undefined}
      compact={isRange}
      disableInstructions
      {...props}
      >
      { (minMaxError || rangeError) && errorText &&
        <Typography
          component="p"
          color="error"
          className={classes.answerInstructions}
          variant="caption"
        >
          { errorText }
        </Typography>
      }
      { minMaxMessage &&
        <Typography
          component="p"
          color={minMaxError ? 'error' : 'textSecondary'}
          className={classes.answerInstructions}
          variant="caption"
        >
          { minMaxMessage }
        </Typography>
      }
      { isRange ?
        <>
        <AnswerInstructions
          minAnswers={Math.min(1, minAnswers)}
          maxAnswers={0}
          currentAnswers={lowerLimit && upperLimit ? 1 : 0}
          />
        { rangeError &&
          <Typography
            component="p"
            color="error"
            className={classes.answerInstructions}
            variant="caption"
          >
          { rangeErrorMessage }
          </Typography>
        }
        <div className={classes.range}>
          <TextField
            helperText="Lower limit"
            value={lowerLimit}
            placeholder={typeof minValue != "undefined" ? `${minValue}` : ""}
            onChange={event => setLowerLimit(event.target.value)}
            inputProps={textFieldProps}
            InputProps={Object.assign({shrink: "true"}, muiInputProps)}
            />
          <span className="separator">&mdash;</span>
          <TextField
            helperText="Upper limit"
            value={upperLimit}
            placeholder={typeof maxValue != "undefined" ? `${maxValue}` : ""}
            onChange={event => setUpperLimit(event.target.value)}
            inputProps={textFieldProps}
            InputProps={Object.assign({shrink: "true"}, muiInputProps)}
            />
        </div>
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          answerNodeType={answerNodeType}
          valueType={valueType}
          {...rest}
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
          error={minMaxError}
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
  errorText: "",
};

const StyledNumberQuestion = withStyles(QuestionnaireStyle)(NumberQuestion)
export default StyledNumberQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (["long", "double", "decimal"].includes(questionDefinition.dataType)) {
    return [StyledNumberQuestion, 50];
  }
});
