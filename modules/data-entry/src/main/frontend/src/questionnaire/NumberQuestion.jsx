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

import React, { forwardRef, useState } from "react";

import {
  InputAdornment,
  Slider,
  TextField,
  Typography
} from "@mui/material";
import { makeStyles, withStyles } from 'tss-react/mui';

import { NumericFormat } from 'react-number-format';

import PropTypes from "prop-types";
import { checkPropTypes } from "../propTypes";

import Answer from "./Answer";
import AnswerInstructions from "./AnswerInstructions";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";
import MultipleChoice from "./MultipleChoice";
import FormattedText from "../components/FormattedText";

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

const useSliderStyles = makeStyles()(theme => ({
  verticalSliderContainer: {
    display: "flex",
    flexDirection: "column-reverse",
    alignItems: "center",
    width: "fit-content",
    "& > .MuiTypography-root:first-of-type" : {
      marginTop: theme.spacing(1.5),
    },
    "& > .MuiTypography-root:last-child" : {
      marginBottom: theme.spacing(1.5),
    },
    "& .MuiSlider-root" : {
      marginLeft: theme.spacing(4),
      marginRight: theme.spacing(4),
      "& .MuiSlider-valueLabel" : {
        background: theme.palette.secondary.main,
      },
    },
  },
  horizontalSliderContainer: {
    display: "flex",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    "& > .MuiTypography-root:first-of-type" : {
      marginRight: theme.spacing(1.5),
      textAlign: "right",
    },
    "& > .MuiTypography-root:last-child" : {
      marginLeft: theme.spacing(1.5),
    },
    "& .MuiSlider-root" : {
      maxWidth: "700px",
      marginTop: theme.spacing(2.5),
      "& .MuiSlider-valueLabel" : {
        background: theme.palette.secondary.main,
      },
    },
  },
}));

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
//  displayMode: Either "input", "list", "list+input", "slider", or undefined denoting the type of
//             user input. If nothing is specified or if displayMode is "slider" but the conditions
//             are not met (minValue or maxValue missing), "input" is used by default.
//  maxValue: The maximum allowed input value
//  minValue: The minimum allowed input value
//  dataType: One of "integer" or "float" (default: "float")
//  errorText: String to display when the input is not valid (default: "")
//  isRange: Whether or not to display a range instead of a single value
//  sliderStep: The increment between selectable slider values
//  sliderMarkStep: The increment between marked & labeled slider values
//  sliderOrientation: Either "horizontal" or "vertical": The orientation of the slider's bar
//
// Sample usage:
// <NumberQuestion
//    text="Please enter the patient's age"
//    defaults={[
//      ["<18", -1]
//    ]}
//    maxAnswers={1}
//    minValue={18}
//    dataType="long"
//    errorText="Please enter an age above 18, or select the <18 option"
//    />
function NumberQuestion(props) {
  checkPropTypes(NumberQuestion, props);
  const { existingAnswer, errorText = "", classes, pageActive, disableValueInstructions, ...rest} = props;
  const {
    dataType,
    displayMode,
    minAnswers,
    disableNegativeInput,
    minValue,
    maxValue,
    disableMinMaxValueEnforcement,
    messageForValuesOutsideMinMax,
    isRange,
    unitOfMeasurement,
    sliderStep,
    sliderMarkStep,
    sliderOrientation = "horizontal",
    minValueLabel,
    maxValueLabel,
    decimalScale
  } = {...props.questionDefinition, ...props};

  const answerNodeType = props.answerNodeType || DATA_TO_NODE_TYPE[dataType];
  const valueType = props.valueType || DATA_TO_VALUE_TYPE[dataType];
  const [ minMaxError, setMinMaxError ] = useState(false);
  const [ rangeError, setRangeError ] = useState(false);

  const initialValue = Array.from(existingAnswer?.[1]?.value || []);

  // The following two are only used for range answers
  const [lowerLimit, setLowerLimit] = useState(initialValue[0]);
  const [upperLimit, setUpperLimit] = useState(initialValue[1]);

  // The following is only used for non-range sliders.
  // Default to an empty string, which results in a "no data"
  // selection as close to 0 as possible within the valid range
  const [sliderValue, setSliderValue] = useState(existingAnswer?.[1]?.value);

  // The following is only used for ranged sliders.
  // Setting a default of "" leads to an error, unlike the non-range case.
  // Instead, default to a "no data" selection of both min and max being the lowest allowed value.
  const sliderValues = [typeof(lowerLimit) === "undefined" ? minValue : Number(lowerLimit), typeof(upperLimit) === "undefined" ? minValue : Number(upperLimit)]

  const isSlider = displayMode === "slider" && typeof minValue !== 'undefined' && typeof maxValue !== 'undefined';
  const isRangeSelected = isRange && typeof(lowerLimit) != 'undefined' && !isNaN(+lowerLimit) && typeof(upperLimit) != 'undefined' && !isNaN(+upperLimit);
  const isSingleSliderSelected = isSlider && typeof(sliderValue) != 'undefined' && !isNaN(+sliderValue);


  // Marks at the minimum and maximum, as well as user specified intervals if provided
  let sliderMarks = [{value: minValue, label: minValue}, {value: maxValue, label: maxValue}];
  if (typeof(sliderMarkStep) !== "undefined") {
    let i = minValue + sliderMarkStep;
    while (i <= maxValue - sliderMarkStep) {
      sliderMarks.push({value: i, label: i});
      i += sliderMarkStep;
    }
  }

  // Load slider-specific style
  const sliderClasses = useSliderStyles();
  // Adjust the height of a vertical slider based on the slider's marks
  const customStyle = isSlider && sliderOrientation === "vertical" ?
    { height: Math.max(100, sliderMarks.length*30) + "px" } : undefined

  // Callback function for our min/max
  let getMinMaxValueError = (text) => {
    let value = 0;
    if (typeof(text) === "undefined" || text === "") {
      // The custom input has been unset
      return null;
    }

    if (dataType === "long") {
      // Test that it is an integer
      if (!/^[-+]?\d*$/.test(text)) {
        return `The value${isRange ? 's' : ''} must be whole number${isRange ? 's' : ''}`;
      }

      value = parseInt(text);
    } else {
      value = Number(text);

      // Reject whitespace and non-numbers
      if (/^\s*$/.test(text) || isNaN(value)) {
        return `The value${isRange ? 's' : ''} must be numeric`;
      }
    }

    // Test that it is within our min/max (if they are defined)
    if ((typeof minValue !== 'undefined' && (isRange ? lowerLimit : value) < minValue) &&
        (typeof maxValue !== 'undefined' && (isRange ? upperLimit : value) > maxValue)) {
      return `The value${isRange ? 's' : ''} must be between ${minValue} and ${maxValue}`;
    }
    if (typeof minValue !== 'undefined' && value < minValue) {
      return `The value${isRange ? 's' : ''} must be greater than ${minValue}`;
    }
    if (typeof maxValue !== 'undefined' && value > maxValue) {
      return `The value${isRange ? 's' : ''} must be lower than ${maxValue}`;
    }

    return null;
  }

  React.useEffect(() => {
    if (!isRange) return;
    // Check for invalid range limits
    setMinMaxError(
      getMinMaxValueError(lowerLimit) ||
      getMinMaxValueError(upperLimit)
    );
    setRangeError(
       typeof(lowerLimit) == 'undefined' && typeof(upperLimit) != 'undefined' ||
       (Number(lowerLimit) > Number(upperLimit))
    );
  }, [lowerLimit, upperLimit]);

  React.useEffect(() => {
    if (isRange) return;
    setMinMaxError(
      getMinMaxValueError(sliderValue)
    );
  }, [sliderValue]);

  const answers = [];
  // Only save ranges that have both limits specified
  if (isRangeSelected) {
    answers.push(["lower", lowerLimit]);
    answers.push(["upper", upperLimit]);
  } else if (isSingleSliderSelected) {
    answers.push(["value", sliderValue]);
  }

  const textFieldProps = {
    min: minValue,
    max: maxValue,
    allowNegative: (typeof minValue === "undefined" || minValue < 0 || disableMinMaxValueEnforcement) && !disableNegativeInput,
    decimalScale: dataType === "long" ? 0 : (decimalScale ?? undefined)
  };
  const muiInputProps = {
    inputComponent: NumberFormatCustom, // Used to override a TextField's type
    className: classes.textField
  };
  if (unitOfMeasurement) {
    muiInputProps.endAdornment = <InputAdornment position="end"><FormattedText>{unitOfMeasurement}</FormattedText></InputAdornment>;
  }

  let hasAnswerOptions = !!(props.defaults || Object.values(props.questionDefinition).some(value => value['jcr:primaryType'] == 'cards:AnswerOption'));

  // Generate message about accepted min/maxValues
  // Don't show instructions if the the range is not defined or if
  // the ui already prevents users from entering out of range values:
  // * minValue  = 0
  // * displayMode = slider
  let minMaxMessage = "";
  if ((typeof minValue !== "undefined" || typeof maxValue !== "undefined") && !isSlider && !disableValueInstructions) {
    if (disableMinMaxValueEnforcement && typeof messageForValuesOutsideMinMax != "undefined") {
      minMaxMessage = messageForValuesOutsideMinMax;
    } else {
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
  }

  // Range error message
  let rangeErrorMessage = "The range is invalid: the lower limit must be less than or equal to the upper limit";

  let rangeDisplayFormatter = function(label, idx) {
    if (idx != 1) return '';
    return (
      <div>
        <FormattedText color={!disableMinMaxValueEnforcement && pageActive && (minMaxError || rangeError) ? "error" : ""}>
          { `${initialValue?.[0]} &mdash; ${label}` }
        </FormattedText>
        { (disableMinMaxValueEnforcement && typeof messageForValuesOutsideMinMax != "undefined") ?
          <Typography component="div" color="textSecondary" variant="caption">
            { messageForValuesOutsideMinMax }
          </Typography>
        : (pageActive && (minMaxError || rangeError)) &&
          <Typography component="div" color="error" variant="caption">
            { rangeError ? rangeErrorMessage : minMaxError }
          </Typography>
        }
      </div>
    );
  }

  let markdownFormatter = function(label, idx) {
    return (
      <div>
        <FormattedText color={!disableMinMaxValueEnforcement && pageActive && minMaxError ? "error" : ""}>
          { label }
        </FormattedText>
        { (disableMinMaxValueEnforcement && typeof messageForValuesOutsideMinMax != "undefined") ?
          <Typography component="div" color="textSecondary" variant="caption">
            { messageForValuesOutsideMinMax }
          </Typography>
        : (pageActive && minMaxError) &&
          <Typography component="div" color="error" variant="caption">
            { minMaxError }
          </Typography>
        }
      </div>
    );
  }

  let setValue = function(fn, value) {
    if (value != null && value != "") {
      let number = Number(value);
      if (dataType === "long" && !isNaN(number)) {
        value = Math.round(number);
      }
    }
    fn(String(value));
  }

  let makeSlider = (options) => {
    return (
      <div className={sliderClasses.classes[`${sliderOrientation}SliderContainer`]}>
      { minValueLabel &&
        <Typography variant="caption" color="textSecondary">{minValueLabel}</Typography>
      }
        <Slider
          style={customStyle}
          color="secondary"
          orientation={sliderOrientation}
          min={minValue}
          max={maxValue}
          step={sliderStep}
          marks={sliderMarks}
          valueLabelDisplay={options.valueLabelDisplay}
          value={options.value}
          onChange={options.onChange}
        />
      { maxValueLabel &&
          <Typography variant="caption" color="textSecondary">{maxValueLabel}</Typography>
      }
      </div>
    );
  }

  return (
    <Question
      defaultDisplayFormatter={isRange ? rangeDisplayFormatter : markdownFormatter }
      disableInstructions
      {...props}
      >
      { pageActive && (minMaxError || rangeError) && errorText &&
        <Typography
          component="p"
          color="error"
          className="cards-answerInstructions"
          variant="caption"
        >
          { errorText }
        </Typography>
      }
      { pageActive && minMaxMessage && !disableMinMaxValueEnforcement &&
        <Typography
          component="p"
          color={minMaxError ? 'error' : 'textSecondary'}
          className="cards-answerInstructions"
          variant="caption"
        >
          { minMaxMessage }
        </Typography>
      }
      { isRange ?
        pageActive && <>
        <AnswerInstructions
          minAnswers={Math.min(1, minAnswers)}
          maxAnswers={0}
          currentAnswers={typeof(lowerLimit) != 'undefined' && typeof(upperLimit) != 'undefined' ? 1 : 0}
          {...props}
          />
        { rangeError &&
          <Typography
            component="p"
            color="error"
            className="cards-answerInstructions"
            variant="caption"
          >
          { rangeErrorMessage }
          </Typography>
        }
        { pageActive && (isSlider ?
            makeSlider({
              valueLabelDisplay: (isRangeSelected ? "on" : "off"),
              value: sliderValues,
              onChange: (event, value) => { setValue(setLowerLimit, value[0]); setValue(setUpperLimit, value[1]); }
            })
          :
          <div className={classes.range}>
            <TextField
              variant="standard"
              helperText="Lower limit"
              value={lowerLimit}
              error={rangeError || !!minMaxError}
              placeholder={typeof minValue != "undefined" ? `${minValue}` : ""}
              onChange={event => setValue(setLowerLimit, event.target.value)}
              slotProps={{
                input: muiInputProps,
                htmlInput: textFieldProps,
                inputLabel: {
                  shrink: true,
                },
              }}
              />
            <span className="separator">&mdash;</span>
            <TextField
              variant="standard"
              helperText="Upper limit"
              value={upperLimit}
              error={rangeError || !!minMaxError}
              placeholder={typeof maxValue != "undefined" ? `${maxValue}` : ""}
              onChange={event => setValue(setUpperLimit, event.target.value)}
              slotProps={{
                input: muiInputProps,
                htmlInput: textFieldProps,
                inputLabel: {
                  shrink: true,
                },
              }}
              />
          </div>)
        }
        <Answer
          answers={answers}
          existingAnswer={existingAnswer}
          answerNodeType={answerNodeType}
          valueType={valueType}
          pageActive={pageActive}
          {...rest}
          />
        </>
        :
        <>
        { isSlider ?
          (pageActive && <>
          <AnswerInstructions
            minAnswers={Math.min(1, minAnswers)}
            maxAnswers={0}
            currentAnswers={isSingleSliderSelected ?  1 : 0}
            {...props}
          />
          { makeSlider({
              valueLabelDisplay: (isSingleSliderSelected ? "on" : "off"),
              value: isNaN(Number(sliderValue)) ? minValue : Number(sliderValue),
              onChange: (event, value) => { setValue(setSliderValue, value); }
            })
          }
          <Answer
            answers={answers}
            existingAnswer={existingAnswer}
            answerNodeType={answerNodeType}
            valueType={valueType}
            pageActive={pageActive}
            {...rest}
            />
          </>)
          :
          <MultipleChoice
            answerNodeType={answerNodeType}
            valueType={valueType}
            input={displayMode === "input" || displayMode === "list+input"}
            textbox={displayMode === "textbox"}
            onUpdate={text => setMinMaxError(getMinMaxValueError(text))}
            additionalInputProps={textFieldProps}
            muiInputProps={muiInputProps}
            error={!disableMinMaxValueEnforcement && minMaxError}
            existingAnswer={existingAnswer}
            pageActive={pageActive}
            validate={disableMinMaxValueEnforcement ? value => !getMinMaxValueError(value) : undefined}
            validationErrorText={minMaxMessage}
            softValidation={disableMinMaxValueEnforcement}
            {...rest}
            />
        }
        </>
      }
    </Question>);
}

// Helper function to bridge react-number-format with @material-ui
export const NumberFormatCustom = forwardRef((props, ref) => {
  checkPropTypes(NumberFormatCustom, props);
  const { onChange, ...other } = props;

  return (
    <NumericFormat
      {...other}
      getInputRef={ref}
      onValueChange={values => {
        onChange({
          target: {
            value: values.value,
          },
        });
      }}
    />
  );
});

NumberFormatCustom.propTypes = {
  onChange: PropTypes.func.isRequired
};

NumberQuestion.propTypes = {
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    minAnswers: PropTypes.number,
    maxAnswers: PropTypes.number,
    minValue: PropTypes.number,
    maxValue: PropTypes.number,
    displayMode: PropTypes.oneOf([undefined, "input", "list", "list+input", "slider"]),
  }).isRequired,
  text: PropTypes.string,
  minAnswers: PropTypes.number,
  maxAnswers: PropTypes.number,
  defaults: PropTypes.array,
  displayMode: PropTypes.oneOf([undefined, "input", "list", "list+input", "slider"]),
  dataType: PropTypes.oneOf(['long', 'double', 'decimal']),
  minValue: PropTypes.number,
  maxValue: PropTypes.number,
  errorText: PropTypes.string,
  isRange: PropTypes.bool,
};

const StyledNumberQuestion = withStyles(NumberQuestion, QuestionnaireStyle)
export default StyledNumberQuestion;

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (["long", "double", "decimal"].includes(questionDefinition.dataType)) {
    return [StyledNumberQuestion, 50];
  }
});
