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

import { useMemo, useState, useEffect } from "react";

import {
  InputAdornment,
  Slider,
  Stack,
  TextField,
  Typography
} from "@mui/material";
import PropTypes from "prop-types";
import { NumericFormat } from 'react-number-format';
import { makeStyles } from 'tss-react/mui';

import { checkPropTypes } from "../propTypes";
import Answer from "./Answer";
import AnswerComponentManager from "./AnswerComponentManager";
import AnswerInstructions from "./AnswerInstructions";
import { useFormReaderContext } from "./FormContext";
import MultipleChoice from "./MultipleChoice";
import questionEditorHints from './NumberQuestion-editor-hints.json';
import questionEditorConfig from './NumberQuestion-editor.json';
import Question from "./Question";
import FormattedText from "../components/FormattedText";
import { registerQuestionEditorConfig } from "../questionnaireEditor/QuestionModelManager";

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
const INTEGER_VALUE_PATTERN = /^[-+]?\d*$/;
const WHITESPACE_ONLY_PATTERN = /^\s*$/;
const isValidNumber = (input) => input !== "" && !Number.isNaN(Number(input));

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
//  displayMode: Either "input", "list", "list+input", "select", "slider", or undefined denoting the type of
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
  const { existingAnswer, errorText = "", pageActive, disableValueInstructions, ...rest } = props;
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
  } = { ...props.questionDefinition, ...props };

  const answerNodeType = props.answerNodeType || DATA_TO_NODE_TYPE[dataType];
  const valueType = props.valueType || DATA_TO_VALUE_TYPE[dataType];
  const formContext = useFormReaderContext();
  const handleFormDataChange = formContext?.['/OnFormDataChanged'];
  const isListSelectOrSlider = useMemo(() => ["list", "select", "slider"].includes(displayMode), [displayMode]);
  const isMultiValue = Array.isArray(existingAnswer?.[1]?.value);
  const displayedValue = existingAnswer?.[1]?.displayedValue;

  const rawDefaultValue = props.questionDefinition.defaultValue;
  // Keep only the numeric default value(s); a multivalued question may provide a comma-separated list, and any
  // value that cannot be parsed as a number is discarded.
  const numericDefaultValues = (rawDefaultValue == null || String(rawDefaultValue) === "")
    ? []
    : String(rawDefaultValue).split(",")
      .map(value => value.trim())
      .filter(value => value !== "" && !isNaN(Number(value)))
      .map(value => Number(value));
  // A single numeric default for this question's own slider and range inputs.
  const defaultValue = numericDefaultValues.length ? numericDefaultValues[0] : null;

  const [ minMaxError, setMinMaxError ] = useState(false);
  const [ minMaxErrorObject, setMinMaxErrorObject ] = useState({});

  const initialValue = Array.from(existingAnswer?.[1]?.value || numericDefaultValues);

  // The following two are only used for range answers
  const [lowerRangeValue, setLowerRangeValue] = useState(isRange ? initialValue[0] : undefined);
  const [upperRangeValue, setUpperRangeValue] = useState(isRange ? initialValue[1] : undefined);
  const [ rangeError, setRangeError ] = useState(false);
  const isRangeSelected = isRange && isValidNumber(lowerRangeValue) && isValidNumber(upperRangeValue);

  // The following is only used for non-range sliders.
  // Default to an empty string, which results in a "no data"
  // selection as close to 0 as possible within the valid range
  const isSlider = displayMode === "slider" && typeof minValue !== 'undefined' && typeof maxValue !== 'undefined';
  const [sliderValue, setSliderValue] = useState(isSlider ? (existingAnswer?.[1]?.value || defaultValue) : undefined);
  // Load slider-specific style
  const sliderClasses = useSliderStyles();
  // Adjust the height of a vertical slider based on the slider's marks
  const customSliderStyle = isSlider && sliderOrientation === "vertical" ?
    { height: Math.max(100, sliderMarks.length*30) + "px" } : undefined;
  const isSingleSliderSelected = isSlider && isValidNumber(sliderValue);

  const pluralSuffix = isRange ? "s" : "";

  // Marks at the minimum and maximum, as well as user specified intervals if provided
  const sliderMarks = useMemo(() => {
    const marks = [{ value: minValue, label: minValue }, { value: maxValue, label: maxValue }];
    if (typeof(sliderMarkStep) !== "undefined") {
      let i = minValue + sliderMarkStep;
      while (i <= maxValue - sliderMarkStep) {
        marks.push({ value: i, label: i });
        i += sliderMarkStep;
      }
    }
    return marks;
  }, [maxValue, minValue, sliderMarkStep]);

  const getValidationErrorMessage = (text) => {
    if (typeof(text) === "undefined" || text === "" || Array.isArray(text)) {
      // The custom input has been unset or is an array
      return null;
    }

    let value;
    if (dataType === "long") {
      // Test that it is an integer
      if (!INTEGER_VALUE_PATTERN.test(text)) {
        return `The value${pluralSuffix} must be whole number${pluralSuffix}`;
      }

      value = Number.parseInt(text, 10);
    } else {
      value = Number(text);

      // Reject whitespace and non-numbers
      if (WHITESPACE_ONLY_PATTERN.test(text) || Number.isNaN(value)) {
        return `The value${pluralSuffix} must be numeric`;
      }
    }

    // Test that it is within our min/max (if they are defined), can happen only if isRange
    if (isRange && typeof minValue !== 'undefined' && lowerRangeValue < minValue &&
                   typeof maxValue !== 'undefined' && upperRangeValue > maxValue) {
      return `The values must be between ${minValue} and ${maxValue}`;
    }

    // individual out of range error can happen if range or not
    if (typeof minValue !== 'undefined' && value < minValue) {
      return `The value${pluralSuffix} must be greater than ${minValue}`;
    }
    if (typeof maxValue !== 'undefined' && value > maxValue) {
      return `The value${pluralSuffix} must be lower than ${maxValue}`;
    }

    return null;
  };

  useEffect(() => {
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

  useEffect(() => {
    if (isRange || Array.isArray(sliderValue)) return;
    setMinMaxError(
      getMinMaxValueError(sliderValue)
    );
  }, [sliderValue]);

  const answers = [];
  // Only save ranges that have both limits specified
  if (isRangeSelected) {
    answers.push(["lower", lowerRangeValue]);
    answers.push(["upper", upperRangeValue]);
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
  };
  if (unitOfMeasurement) {
    muiInputProps.endAdornment = <InputAdornment position="end"><FormattedText>{unitOfMeasurement}</FormattedText></InputAdornment>;
  }

  // Generate message about accepted min/maxValues
  // Don't show instructions if the the range is not defined or if
  // the ui already prevents users from entering out of range values:
  // * minValue  = 0
  // * displayMode = slider
  let minMaxMessage = "";
  let hasAnswerOptions = !!(props.defaults || Object.values(props.questionDefinition).some(value => value['jcr:primaryType'] == 'cards:AnswerOption'));
  if ((typeof minValue !== "undefined" || typeof maxValue !== "undefined") && !isSlider && !disableValueInstructions) {
    if (typeof messageForValuesOutsideMinMax !== "undefined") {
      minMaxMessage = messageForValuesOutsideMinMax;
    } else {
      const rangeMessage = typeof minValue !== "undefined" && typeof maxValue !== "undefined"
        ? `between ${minValue} and ${maxValue}`
        : typeof minValue !== "undefined"
          ? `of at least ${minValue}`
          : `of at most ${maxValue}`;
      minMaxMessage = `Please enter values ${rangeMessage}${hasAnswerOptions ? " or select one of the options" : ""}`;
    }
  }

  // Range error message
  let rangeErrorMessage = "The range is invalid: the lower limit must be less than or equal to the upper limit";
  let rangeDisplayFormatter = function(label, idx) {
    if (idx != 1) return '';
    return (
      <div>
        <FormattedText color={!disableMinMaxValueEnforcement && pageActive && (minMaxError || rangeError) ? "error" : ""}>
          { `${lowerRangeValue} &mdash; ${label}` }
        </FormattedText>
        { (typeof messageForValuesOutsideMinMax != "undefined" && minMaxError) ?
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
    const errorMessage = isMultiValue ? minMaxErrorObject[label] : minMaxError;
    return (
      <div>
        <FormattedText color={!disableMinMaxValueEnforcement && pageActive && errorMessage ? "error" : ""}>
          { label }
        </FormattedText>
        { (typeof messageForValuesOutsideMinMax != "undefined" && errorMessage) ?
          <Typography component="div" color="textSecondary" variant="caption">
            { messageForValuesOutsideMinMax }
          </Typography>
          : (pageActive && errorMessage) &&
          <Typography component="div" color="error" variant="caption">
            { errorMessage }
          </Typography>
        }
      </div>
    );
  }

  let setValue = function(fn, value) {
    if (value != null && value !== "") {
      let number = Number(value);
      if (dataType === "long" && !Number.isNaN(number)) {
        value = Math.round(number);
      }
    }
    fn(String(value));
  }

  const makeSlider = (options) => {
    return (
      <div className={sliderClasses.classes[`${sliderOrientation}SliderContainer`]}>
        { minValueLabel &&
        <Typography variant="caption" color="textSecondary">{minValueLabel}</Typography>
        }
        <Slider
          style={customSliderStyle}
          color="secondary"
          orientation={sliderOrientation}
          min={minValue}
          max={maxValue}
          step={sliderStep}
          marks={sliderMarks}
          valueLabelDisplay={options.valueLabelDisplay}
          value={options.value}
          onChange={(event, value) => {
            options.onChange(event, value);
            handleFormDataChange?.();
          }}
        />
        { maxValueLabel &&
          <Typography variant="caption" color="textSecondary">{maxValueLabel}</Typography>
        }
      </div>
    );
  };

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
            currentAnswers={typeof(lowerRangeValue) !== 'undefined' && typeof(upperRangeValue) !== 'undefined' ? 1 : 0}
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
              value: typeof(lowerRangeValue) === "undefined" ? [minValue, maxValue] : [Number(lowerRangeValue), Number(upperRangeValue)],
              onChange: (event, value) => {
                setValue(setLowerRangeValue, value[0]);
                setValue(setUpperRangeValue, value[1]);
                handleFormDataChange?.();
              }
            })
            :
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              className="cards-answerRange"
              sx={{ '& > .MuiTextField-root': { maxWidth: "110px" } }}
            >
              <TextField
                variant="standard"
                helperText="Lower limit"
                value={lowerRangeValue}
                error={rangeError || !!minMaxError}
                placeholder={typeof minValue !== "undefined" ? `${minValue}` : ""}
                onChange={event => {
                  setValue(setLowerRangeValue, event.target.value);
                  handleFormDataChange?.();
                }}
                slotProps={{
                  input: muiInputProps,
                  htmlInput: textFieldProps,
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
              <span>&mdash;</span>
              <TextField
                variant="standard"
                helperText="Upper limit"
                value={upperRangeValue}
                error={rangeError || !!minMaxError}
                placeholder={typeof maxValue !== "undefined" ? `${maxValue}` : ""}
                onChange={event => {
                  setValue(setUpperRangeValue, event.target.value);
                  handleFormDataChange?.();
                }}
                slotProps={{
                  input: muiInputProps,
                  htmlInput: textFieldProps,
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
            </Stack>)
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
                value: Number.isNaN(Number(sliderValue)) ? minValue : Number(sliderValue),
                onChange: (event, value) => setValue(setSliderValue, value)
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
              onUpdate={text => setMinMaxError(getValidationErrorMessage(text))}
              additionalInputProps={textFieldProps}
              muiInputProps={muiInputProps}
              error={!disableMinMaxValueEnforcement && !!minMaxError}
              existingAnswer={existingAnswer}
              pageActive={pageActive}
              validate={disableMinMaxValueEnforcement ? value => !getValidationErrorMessage(value) : undefined}
              validationErrorText={minMaxMessage}
              softValidation={disableMinMaxValueEnforcement}
              defaultValue={numericDefaultValues.join(",") || undefined}
              {...rest}
            />
          }
        </>
      }
    </Question>);
}

// Helper function to bridge react-number-format with @material-ui
export const NumberFormatCustom = (props, ref) => {
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
};
NumberFormatCustom.displayName = 'NumberFormatCustom';

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
    displayMode: PropTypes.oneOf([undefined, "input", "list", "list+input", "slider", "select"]),
  }).isRequired,
  text: PropTypes.string,
  minAnswers: PropTypes.number,
  maxAnswers: PropTypes.number,
  defaults: PropTypes.array,
  displayMode: PropTypes.oneOf([undefined, "input", "list", "list+input", "slider", "select"]),
  dataType: PropTypes.oneOf(['long', 'double', 'decimal']),
  minValue: PropTypes.number,
  maxValue: PropTypes.number,
  errorText: PropTypes.string,
  isRange: PropTypes.bool,
};

export default NumberQuestion;

// Contribute the "long", "decimal" and "double" dataTypes to the question editor.
registerQuestionEditorConfig(questionEditorConfig, {
  hints: questionEditorHints,
  order: { long: 300, decimal: 400, double: 500 }
});

AnswerComponentManager.registerAnswerComponent((questionDefinition) => {
  if (["long", "double", "decimal"].includes(questionDefinition.dataType)) {
    return [NumberQuestion, 50];
  }
});
