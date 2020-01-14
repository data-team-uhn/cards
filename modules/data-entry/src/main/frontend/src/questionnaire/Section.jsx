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
import { Grid, withStyles } from "@material-ui/core";

import AnswerComponentManager from "./AnswerComponentManager";
import QuestionnaireStyle from "./QuestionnaireStyle";

/// Component that consists of a few sections, and optionally has some criteria to its display
function Section(props) {
  const { classes, existingAnswer, sectionDefinition } = props;
  const [ error, setError ] = useState();

  // I need to move the AnswerComponentManager renderer outside of Form so it can be accessed here instead of this

  /**
   * Method responsible for displaying a question from the questionnaire, along with its answer(s).
   *
   * @param {Object} questionDefinition the question definition JSON
   * @param {string} key the node name of the question definition JCR node
   * @returns a React component that renders the question
   */
  let displayQuestion = (questionDefinition, key) => {
    const existingQuestionAnswer = existingAnswer && Object.entries(existingAnswer)
      .find(([key, value]) => value["sling:resourceSuperType"] == "lfs/Answer"
        && value["question"]["jcr:uuid"] === questionDefinition["jcr:uuid"]);
    // This variable must start with an upper case letter so that React treats it as a component
    const QuestionDisplay = AnswerComponentManager.getAnswerComponent(questionDefinition);
    return <QuestionDisplay key={key} questionDefinition={questionDefinition} existingAnswer={existingQuestionAnswer} />;
  };

  /**
   * Method responsible for displaying a section from the questionnaire, along with its answer(s).
   * TODO: Somehow pass the conditional state upwards from here
   *
   * @param {Object} sectionDefinition the section definition JSON
   * @param {string} key the node name of the section definition JCR node
   * @returns a React component that renders the section
   */
  let displaySection = (sectionDefinition, key) => {
    // Parse through SectionLink nodes to find our Section node
    // TODO: What if a sectionRef loop is present?
    while (sectionDefinition && sectionDefinition["sling:resourceType"] == "sectionLink") {
      sectionDefinition = sectionDefinition["ref"];
    }
    const existingQuestionAnswer = existingAnswer && Object.entries(existingAnswer)
      .find(([key, value]) => value["sling:resourceType"] == "lfs/AnswerSection"
        && value["question"]["jcr:uuid"] === sectionDefinition["jcr:uuid"]);
    return <Section key={key} sectionDefinition={sectionDefinition} existingAnswer={existingQuestionAnswer} />;
  }

  // TODO: As before, I'm writing something that's basically an if statement
  // this should instead be via a componentManager
  // This code is likely reuseable for the Section component as well
  let display = (definition, key) => {
    if (definition["jcr:primaryType"] == "lfs:Question") {
      return displayQuestion(definition, key);
    } else if (definition["jcr:primaryType"] == "lfs:Section") {
      return displaySection(definition, key);
    }
  }

  return (
    <React.Fragment>
    {
      Object.entries(sectionDefinition)
        .filter(([key, value]) => value['jcr:primaryType'] == 'lfs:Question' || value['jcr:primaryType'] == 'lfs:Section')
        .map(([key, definition]) => <Grid item key={key}>{display(definition, key)}</Grid>)
    }
    </React.Fragment>
  );
}

export default withStyles(QuestionnaireStyle)(Section);