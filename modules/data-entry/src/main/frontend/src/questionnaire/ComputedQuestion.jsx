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
import PropTypes from 'prop-types';
import { InputAdornment, TextField, Typography } from "@material-ui/core";

import withStyles from '@material-ui/styles/withStyles';

import Answer from "./Answer";
import AnswerComponentManager from "./AnswerComponentManager";
import DateQuestionUtilities from "./DateQuestionUtilities";
import Question from "./Question";
import {Time} from "./TimeQuestion";
import FormattedText from "../components/FormattedText";
import QuestionnaireStyle from './QuestionnaireStyle';
import { useFormReaderContext } from "./FormContext";
import { MakeRequest } from "../vocabQuery/util.jsx";


// Component that renders a computed value as a question field
// Computed value is placed in a <input type="hidden"> tag for submission.
//
// Mandatory props:
// text: the question to be displayed
// expression: the javascript run to determine the value.
//   Values between the tags `@{` and `}` will be interpreted as input variables.
//   These tags will be removed and the value of the question named by the
//     input variable will be provided as a function argument.
//
// Other options are passed to the <question> widget
//
// Sample usage, given question_a and question_b are number questions:
//<ComputedQuestion
//  text="Result of of a divided by b"
//  expression="if (@{question_b} === 0) setError('Can not divide by 0'); return @{question_a}/@{question_b}"
//  />
let ComputedQuestion = (props) => {
  const { existingAnswer, classes, pageActive, questionDefinition, ...rest} = props;
  const { text, expression, unitOfMeasurement, dataType, displayMode, dateFormat, yesLabel, noLabel, unknownLabel } = {...props.questionDefinition, ...props};
  const [error, changeError] = useState(false);
  const [errorMessage, changeErrorMessage] = useState(false);

  let initialValue = existingAnswer?.[1].value || "";
  const [displayValue, changeDisplayValue] = useState(initialValue);
  const [baseValue, changeBaseValue] = useState(initialValue);
  const [answer, changeAnswer] = useState(initialValue === "" ? [] : [["value", initialValue]]);
  const [fieldType, changeFieldType] = useState("string")
  const [muiInputProps, changeMuiInputProps] = useState({});
  const [isFormatted, changeIsFormatted] = useState(false);

  const form = useFormReaderContext();
  const startTag = "@{";
  const endTag = "}";
  const defaultTag = ":-";
  const booleanDefaultLabels = {"0": "No", "1": "Yes", "-1": "Unknown"}

  let setError = (input) => {
    if (error !== input) changeError(input);
  }
  let setErrorMessage = (input) => {
    if (errorMessage !== input) changeErrorMessage(input);
  }

  let setValue = (input) => {
    if (input === baseValue) {
      //Do nothing
      return;
    }
    changeBaseValue(input);
    let newDisplayedValue = input;
    if (dataType === "boolean") {
      switch (input) {
        case 1:
          newDisplayedValue = typeof(yesLabel) === "undefined" ? booleanDefaultLabels["1"] : yesLabel;
          break;
        case 0:
          newDisplayedValue = typeof(noLabel) === "undefined" ? booleanDefaultLabels["0"] : noLabel;
          break;
        case -1:
          newDisplayedValue = typeof(unknownLabel) === "undefined" ? booleanDefaultLabels["-1"] : unknownLabel;
          break;
        default:
          // Default to blank
          break;
      }
    } else if (dataType === "date") {
      let dateType = DateQuestionUtilities.getDateType(dateFormat);
      if (dateType === DateQuestionUtilities.MONTH_DATE_TYPE) {
        newDisplayedValue = DateQuestionUtilities.formatDateAnswer(dateFormat, DateQuestionUtilities.stripTimeZone(newDisplayedValue));
      } else if (dateType === DateQuestionUtilities.DATETIME_TYPE || dateType === DateQuestionUtilities.FULL_DATE_TYPE) {
        newDisplayedValue = typeof(newDisplayedValue) === "string" && newDisplayedValue.length > 0
          ? DateQuestionUtilities.momentToString(DateQuestionUtilities.amendMoment(DateQuestionUtilities.stripTimeZone(newDisplayedValue || ""), dateFormat), dateType)
          : "";
      }
    } else if (dataType === "vocabulary") {
      var url = new URL("." + newDisplayedValue + ".info.json", window.location.origin);
      let showInfo = (status, data, params) => {
        if (status === null && data) {
          console.log("Setting value to " + data["label"]);
          changeDisplayValue(data["label"]);
        } else {
          setError(true);
          setErrorMessage(`Error searching vocabulary term. Using value as shown.`);
        }
      }
      MakeRequest(url, showInfo);
    }
    if (displayValue !== newDisplayedValue) {
      changeDisplayValue(newDisplayedValue);
      changeAnswer([["value", typeof(input) === "undefined" ? "" : input]]);
    }
  }

  let setFieldType = (value) => {
    if (fieldType !== value) {
      changeFieldType(value);
    }
  }

  let missingValue = false;
  let getQuestionValue = (name, form, defaultValue) => {
    let value;
    if (form[name] && form[name][0]) {
      value = form[name][0][1];
    }
    if (typeof(value) === "undefined" || value === "") {
      if (defaultValue != null) {
        value = defaultValue;
      } else {
        missingValue = true;
      }
    }
    if (!isNaN(Number(value))) {
      value = Number(value);
    }
    return value;
  }

  let parseExpressionInputs = (expr, form) => {
    let inputNames = [];
    let inputValues = [];
    let start = expr.indexOf(startTag);
    let end = expr.indexOf(endTag, start);
    while(start > -1 && end > -1) {
      let optionStart = expr.indexOf(defaultTag, start);
      let hasOption = (optionStart > -1 && optionStart < end);
      // Divide the text between the start and end tag the question name and a default value if provided
      let inputName;
      let defaultValue = null;
      if (hasOption) {
        inputName = expr.substring(start + startTag.length, optionStart);
        defaultValue = expr.substring(optionStart + defaultTag.length, end);
      } else {
        inputName = expr.substring(start + startTag.length, end);
      }
      if (!inputNames.includes(inputName)) {
        inputNames.push(inputName);
        inputValues.push(getQuestionValue(inputName, form, defaultValue));
      }
      // Remove the start and end tags as well as the default option if provided
      expr = [expr.substring(0, start), expr.substring(start + startTag.length, hasOption ? optionStart : end), expr.substring(end + endTag.length)].join('');
      start = expr.indexOf(startTag, hasOption ? optionStart : end - startTag.length);
      end = expr.indexOf(endTag, start);
    }
    return [inputNames, inputValues, expr];
  }

  let evaluateExpression = () => {
    let result;
    let expressionError = null;
    try {
      let parseResults = parseExpressionInputs(expression, form);
      let expressionArguments = ["form", "setError"].concat(parseResults[0]);
      result = new Function(expressionArguments, parseResults[2])
        (form, (errorMessage) => {expressionError = errorMessage}, ...parseResults[1]);
      if (missingValue || typeof(result) === "undefined" || (typeof(result) === "number" && isNaN(result))) {
        result = "";
      }
    }
    catch(err) {
      console.error(`Error encountered evaluating expression:\n${expression}\n`, err);
      result = "";
    }
    if (expressionError) {
      setError(true);
      setErrorMessage(`Error encountered evaluating expression:\n${expressionError}`);
      result = "";
    } else {
      setError(false);
    }

    setValue(typeof(result) === "undefined" ? "" : result);
  }

  useEffect(() => {
    if (unitOfMeasurement) {
      muiInputProps.endAdornment = <InputAdornment position="end">{unitOfMeasurement}</InputAdornment>;
    } else {
      delete muiInputProps.endAdornment;
    }
  }, [unitOfMeasurement])

  useEffect(() => {
    let formatted = (displayMode === "formatted" || displayMode === "summary");
    if (formatted !== isFormatted) {
      changeIsFormatted(formatted)
    };
  }, [displayMode])

  // Performance improvement? Only compute if inputs have changed
  evaluateExpression();

  let capitalizedDataType = dataType.substring(0, 1).toUpperCase() + dataType.substring(1);
  let answerType, answerNodeType = `cards:${capitalizedDataType}Answer`, newFieldType;
  switch (dataType) {
    case "boolean":
      answerType = "Long"; // Long, not Boolean
      break;
    case "date":
      newFieldType = DateQuestionUtilities.getFieldType(dateFormat);
      setFieldType(newFieldType);
      switch (newFieldType) {
        case "long":
          answerType = "Long";
          break;
        case "string":
          answerType = "Date";
          break;
        default:
          answerType = "Date";
          break;
      }
      break;
    case "long":
    case "double":
    case "decimal":
      answerType = capitalizedDataType;
      break;
    case "vocabulary":
      answerType = "String";
      break;
    case "time":
      answerType = "String";
      newFieldType = Time.timeQuestionFieldType(dateFormat);
      setFieldType(newFieldType);
      break;
    case "text":
      answerType = "String";
      break;
    case "computed": // Fallthrough default to string computed
    default:
      answerType = "String";
      answerNodeType = "cards:ComputedAnswer";
      break;
  }

  return (
    <Question
      defaultDisplayFormatter={isFormatted ? (label, idx) => <FormattedText>{label}</FormattedText> : undefined}
      currentAnswers={typeof(displayValue) !== "undefined" && displayValue !== "" ? 1 : 0}
      {...props}
      >
      {
        pageActive && <>
          {error && <Typography color='error'>{errorMessage}</Typography>}
          { isFormatted ? <FormattedText>
              {displayValue + (unitOfMeasurement ? (" " + unitOfMeasurement) : '')}
            </FormattedText>
          :
          <TextField
            variant="standard"
            type={fieldType}
            dateFormat={(fieldType === "date" || fieldType === "time" && dateFormat) || null}
            disabled={true}
            className={classes.textField + " " + classes.answerField}
            value={displayValue}
            InputProps={muiInputProps} />
          }
        </>
      }
      <Answer
        answers={answer}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType={answerNodeType}
        valueType={answerType}
        pageActive={pageActive}
        {...rest}
        />
    </Question>
  );
}

ComputedQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  questionDefinition: PropTypes.shape({
    text: PropTypes.string.isRequired,
    expression: PropTypes.string.isRequired,
    description: PropTypes.string,
    displayMode: PropTypes.oneOf(['input', 'formatted', 'hidden', 'summary']),
    unitOfMeasurement: PropTypes.string
  }).isRequired
};

const StyledComputedQuestion = withStyles(QuestionnaireStyle)(ComputedQuestion);
export default StyledComputedQuestion;

AnswerComponentManager.registerAnswerComponent((definition) => {
  if (definition.entryMode === "computed") {
    return [StyledComputedQuestion, 80];
  }
});
