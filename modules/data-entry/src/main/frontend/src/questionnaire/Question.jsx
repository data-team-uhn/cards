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

import React from "react";
import PropTypes from "prop-types";

import { Card, CardHeader, CardContent, List, ListItem, Typography, withStyles } from "@material-ui/core";

import QuestionnaireStyle from "./QuestionnaireStyle";
import AnswerInstructions from "./AnswerInstructions";
import FormattedText from "../components/FormattedText.jsx";

// GUI for displaying answers
function Question (props) {
  let { classes, children, questionDefinition, existingAnswer, answers, isEdit, preventDefaultView, defaultDisplayFormatter } = props;
  let { text, label, compact, description, disableInstructions } = { ...questionDefinition, ...props }

  let existingAnswers = answers || (existingAnswer ? Array.of(existingAnswer.[1]["displayedValue"]).flat() : null);

  return (
    <Card
      variant="outlined"
      className={classes.questionCard}
      >
      <CardHeader
        title={text || label}
        titleTypographyProps={{ variant: 'h6' }}
        subheader={<FormattedText variant="caption">{description}</FormattedText>}
        subheaderTypographyProps={{ component: "div" }}
        />
      <CardContent className={isEdit ? classes.editModeAnswers : classes.viewModeAnswers}>
        <div className={compact ? classes.compactLayout : null}>
        { !(isEdit && disableInstructions) &&
          <AnswerInstructions
             {...questionDefinition}
             {...props}
          />
        }
        { !isEdit && !preventDefaultView ?
          ( existingAnswers ?
            <List>
              { existingAnswers.map( (item, idx) => {
                return(
                  <ListItem key={item}> {defaultDisplayFormatter ? defaultDisplayFormatter(item, idx) : item} </ListItem>
                )})
              }
            </List>
            :
            ""
          )
          :
          children
        }
        { !isEdit && existingAnswer?.[1]?.note &&
          <div className={classes.notesContainer}>
            <Typography variant="subtitle1">Notes</Typography>
            {existingAnswer[1].note}
          </div>
        }
        </div>
      </CardContent>
    </Card>
    );
}

Question.propTypes = {
    classes: PropTypes.object.isRequired,
    text: PropTypes.string,
    description: PropTypes.string,
    disableInstructions: PropTypes.bool,
};

Question.defaultProps = {
    disableInstructions: false,
};

export default withStyles(QuestionnaireStyle)(Question);
