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
import PropTypes from 'prop-types';

import { Table, TableHead, TableBody, TableRow, TableCell } from "@material-ui/core";
import { Checkbox, Grid, Radio, Typography, withStyles } from "@material-ui/core";

import Answer, {LABEL_POS, VALUE_POS, DESC_POS, IS_DEFAULT_OPTION_POS, IS_DEFAULT_ANSWER_POS} from "./Answer";
import AnswerInstructions from "./AnswerInstructions";
import Question from "./Question";
import QuestionnaireStyle from './QuestionnaireStyle';

// Component that renders a matrix type of question.
//
// Sample usage:
//
// <MatrixQuestion
//   sectionDefinition={{
//     ...
//   }}
//   />
// existingAnswer array of sub-question answers

let QuestionnaireMatrix = (props) => {
  const { sectionDefinition, existingSectionAnswer, existingAnswers, path, isEdit, classes, ...rest} = props;
  const { maxAnswers, minAnswers } = {...sectionDefinition, ...props};

  // Use existing existingAnswer, Otherwise, create a new UUID
  const sectionPath = path + "/" + ( existingSectionAnswer ? existingSectionAnswer[0] || uuidv4() : "");
  const isRadio = maxAnswers === 1;
  const ControlElement = isRadio ? Radio : Checkbox;
  const valueType = sectionDefinition.dataType.charAt(0).toUpperCase() + sectionDefinition.dataType.slice(1);

  const subquestions = Object.entries(sectionDefinition)
      .filter(([key, value]) => value['jcr:primaryType'] == 'cards:Question');

  const defaults = Object.values(sectionDefinition)
    // Keep only answer options
    // FIXME Must deal with nested options, do this recursively
    .filter(value => value['jcr:primaryType'] == 'cards:AnswerOption')
    // Sort by default order
    .sort((option1, option2) => (option1.defaultOrder - option2.defaultOrder))
    // Only extract the labels, internal values and description from the node
    .map(value => [value.label || value.value, value.value, true, value.description, value.isDefault]);

  // Locate an option value referring to the "none of the above", if it exists
  const naOption = Object.values(sectionDefinition).find((value) => value['notApplicable'])?.["value"];

  let initialSelection = {};
  existingAnswers?.filter(answer => answer[1]["displayedValue"])
    // The value can either be a single value or an array of values; force it into an array
    .map(answer => { initialSelection[answer[0]] = Array.of(answer[1].value).flat()
								        .map( (item, index) => [Array.of(answer[1].displayedValue).flat()[index], item] );
				   });

  // When opening a form, if there is no existingAnswer but there are AnswerOptions specified as default values,
  // display those options as selected and ensure they get saved unless modified by the user, by adding them to initialSelection
  if (!existingAnswers) {
    let defaultSelection = defaults.filter(item => item[IS_DEFAULT_ANSWER_POS])
       // If there are more default values than the specified maxAnswers, only take into account the first maxAnswers default values.
      .slice(0, maxAnswers || defaults.length)
      .map(item => [item[LABEL_POS], item[VALUE_POS]]);

    subquestions.map(subquestion => { initialSelection[subquestion[0]] = defaultSelection; });
  }


  const [selection, setSelection] = useState(initialSelection);

  let selectOption = (id, option) => {
    let checked = selection[id]?.find(item => item[VALUE_POS] == option[VALUE_POS]);

    setSelection( old => {
      let newSelection = old;
      
      // Selecting a radio button or naOption option will select only that option
      if (isRadio || naOption == option[VALUE_POS]) {
        newSelection[id] = [option[LABEL_POS], option[VALUE_POS]];
        return newSelection;
      }
      
      let answer = newSelection[id] || [];

      // If the element was already checked, remove it instead
      if (checked) {
        newSelection[id] = answer.filter(item => {return !(String(item[VALUE_POS]) === String(option[VALUE_POS]))});
        return newSelection;
      }

      // Do not add anything if we are at our maximum number of selections
      if (maxAnswers > 0 && answer.length >= maxAnswers) {
        return old;
      }

      // Do not add duplicates
      if (answer.some(element => {return String(element[VALUE_POS]) === String(option[VALUE_POS])})) {
        return old;
      }

      // unselect naOption
      newSelection[id] = answer.filter(item => !naOption || item[VALUE_POS] != naOption);
      newSelection[id].push([option[LABEL_POS], option[VALUE_POS]]);
      return newSelection;
    }
    );
  }

  return (
    <Question
      questionDefinition={sectionDefinition}
      answers={existingAnswers}
      {...props}
      disableInstructions
      defaultDisplayFormatter={(subquestion, idx) => 
        <Grid container alignItems='flex-start' spacing={2} direction="row">
          <Grid item xs={6}>
            <Typography variant="subtitle2">{subquestion[1].text}:</Typography>
          </Grid>
          <Grid item xs={6}>
            {subquestion[1]["displayedValue"]}
          </Grid>
        </Grid>
      }
    >
      <Table>
        <TableHead>
          <TableRow>
          { [["",""]].concat(defaults).map( (option, index) => (
                <TableCell
                  key={index}
                  className={classes.tableCell}
                >
                  {option[LABEL_POS]}
                </TableCell>
              ) )
          }
          </TableRow>
        </TableHead>
        <TableBody>
          { subquestions.map( (question, i) => (
            <TableRow key={question[0] + i}>
              { [["",""]].concat(defaults).map( (option, index) => (
                <TableCell key={question[0] + i + index} className={classes.tableCell}>
                  { index == 0
                    ? <>
                    <Typography>{question[1].text}</Typography>
                    <AnswerInstructions
                      currentAnswers={selection[question[0]].length}
                      {...sectionDefinition}
                      {...props}
                     /> </>
                    :
                    <ControlElement
                      checked={selection[question[0]]?.find(item => item[VALUE_POS] == option[VALUE_POS])}
                      onChange={() => {selectOption(question[0], option);}}
                      className={classes.checkbox}
                    />
                  }
                </TableCell>
              )) }
            </TableRow>
          )) }
        </TableBody>
      </Table>
      <input type="hidden" name={`${sectionPath}/jcr:primaryType`} value={"cards:AnswerSection"}></input>
      <input type="hidden" name={`${sectionPath}/section`} value={sectionDefinition['jcr:uuid']}></input>
      <input type="hidden" name={`${sectionPath}/section@TypeHint`} value="Reference"></input>
      { subquestions.map(question => 
	      <Answer
	        key={question[1]["jcr:uuid"]}
	        answers={selection[question[0]]}
	        questionDefinition={question[1]}
	        existingAnswer={question[1]}
	        answerNodeType={question[1]["jcr:primaryType"]}
	        valueType={valueType}
	        isMultivalued={maxAnswers != 1}
	        questionName={question[1]["jcr:uuid"]}
	        {...rest}
	      />
      )}
    </Question>
  )
}

export default withStyles(QuestionnaireStyle)(QuestionnaireMatrix);
