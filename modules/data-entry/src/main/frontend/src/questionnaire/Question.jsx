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

import React, { useRef, useEffect, useState } from "react";
import PropTypes from "prop-types";
import { useLocation } from 'react-router-dom';

import { Card, CardHeader, CardContent, List, ListItem, Typography } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import QuestionnaireStyle from "./QuestionnaireStyle";
import AnswerInstructions from "./AnswerInstructions";
import FormattedText from "../components/FormattedText.jsx";

// GUI for displaying answers
function Question (props) {
  let { classes, children, questionDefinition, existingAnswer, isEdit, pageActive, preventDefaultView, defaultDisplayFormatter } = props;
  let { compact } = { ...questionDefinition };
  let { text, description, disableInstructions } = { ...questionDefinition, ...props }

  const [ doHighlight, setDoHighlight ] = useState();
  const [ anchor, setAnchor ] = useState();

  const location = useLocation();

  // if autofocus is needed and specified in the url
  useEffect(() => {
    setAnchor(decodeURIComponent(location.hash.substring(1)))
  }, [location]);
  useEffect(() => {
    if (anchor && questionDefinition) {
      if (questionDefinition.displayMode === "matrix") {
        setDoHighlight(Array.of(existingAnswer?.[1]["displayedValue"]).flat().filter(answer => anchor == answer[1].question["@path"]).length > 0);
      } else {
         setDoHighlight(anchor == questionDefinition["@path"]);
      }
    }
  }, [anchor, questionDefinition]);

  const questionRef = useRef();

  // create a ref to store the question container DOM element
  useEffect(() => {
    const timer = setTimeout(() => {
      questionRef?.current?.scrollIntoView({block: "center"});
    }, 500);
    return () => clearTimeout(timer);
  }, [questionRef]);

  let cardClasses = [classes.questionCard];
  if (doHighlight) {
    cardClasses.push(classes.focusedQuestionnaireItem);
  }
  if (questionDefinition.dataType == "phone") {
	cardClasses.push(classes.phoneAnswer);
  }

  let labels = existingAnswer?.[1].displayedValue;
  if (typeof(labels) == "undefined") {
    labels = existingAnswer?.[1].value;
  }
  // Always turn value into an array for convenience
  if (!Array.isArray(labels)) {
    if (typeof(labels) == "undefined" || labels === "") {
      labels = [];
    } else {
      labels = [labels];
    }
  }

  return (
    <Card
      id={questionDefinition["@path"]}
      variant="outlined"
      ref={doHighlight ? questionRef : undefined}
      className={cardClasses.join(" ")}
      >
      {
        // Note that we need to preserve the hierarchy in which we place children
        // so that pageActive changing does not cause children to lose state
        pageActive && <CardHeader
          disableTypography
          title={<FormattedText component="h6" variant="h6">{text}</FormattedText>}
          subheader={<FormattedText component="div" variant="caption" color="textSecondary">{description}</FormattedText>}
          />
      }
      <CardContent className={isEdit ? classes.editModeAnswers : classes.viewModeAnswers}>
        <div className={compact ? classes.compactLayout : null}>
          {
            pageActive && <>
              { !(isEdit && disableInstructions) &&
                <AnswerInstructions
                  {...questionDefinition}
                  {...props}
                />
              }
            </>
          }
          { !isEdit && !preventDefaultView ?
            ( labels ?
              <List>
                { labels.map( (item, idx) => {
                  return(
                    <ListItem key={existingAnswer[0] + idx}> {defaultDisplayFormatter ? defaultDisplayFormatter(item, idx) : item} </ListItem>
                  )})
                }
              </List>
              :
              ""
            )
            :
            children
          }
          { pageActive && !isEdit && existingAnswer?.[1]?.note &&
            <div className={classes.notesContainer}>
              <Typography variant="subtitle1">Notes</Typography>
              {existingAnswer[1].note}
            </div>
          }
        </div>
      </CardContent>
    </Card>
    )
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
