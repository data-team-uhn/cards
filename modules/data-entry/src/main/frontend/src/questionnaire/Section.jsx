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

import React, { useCallback, useState } from "react";
import PropTypes from "prop-types";
import { Button, Collapse, Grid, Typography, withStyles } from "@material-ui/core";
import Add from "@material-ui/icons/Add";
import Close from '@material-ui/icons/Close';
import uuidv4 from "uuid/v4";

import ConditionalComponentManager from "./ConditionalComponentManager";
import FormEntry, { ENTRY_TYPES } from "./FormEntry";
import { useFormReaderContext } from "./FormContext";
import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";

// FIXME In order for the conditionals to be registered, they need to be loaded, and the only way to do that at the moment is to explicitly invoke them here. Find a way to automatically load all conditional types, possibly using self-declaration in a node, like the assets, or even by filtering through assets.
import ConditionalGroup from "./ConditionalGroup";
import ConditionalSingle from "./ConditionalSingle";

// The heading levels that @material-ui supports
const MAX_HEADING_LEVEL = 6;
const MIN_HEADING_LEVEL = 3;

/**
 * Component responsible for displaying a (sub)section of a questionnaire, consisting of a title, a description,
 * a list of questions and/or subsections, and may have some conditions to its display.
 *
 * @example
 * <Section depth={1} path="." sectionDefinition={{label: "Section"}} />
 *
 * @param {int} depth the section nesting depth
 * @param {Object} existingAnswer form data that may include answers already submitted for this component
 * @param {string} path the path to the parent of the question
 * @param {Object} sectionDefinition the section definition JSON
 */
function Section(props) {
  const { classes, depth, existingAnswer, path, sectionDefinition } = props;
  const [ instanceLabels, setInstanceLabels ] = useState(
    // If we already exist from existingAnswer, our labels are the first element
    existingAnswer ? existingAnswer.map(element => element[0])
    // Otherwise, create a new UUID
    : [uuidv4()]);
  const headerVariant = (depth > MAX_HEADING_LEVEL - MIN_HEADING_LEVEL ? "body1" : ("h" + (depth+MIN_HEADING_LEVEL)));
  const formContext = useFormReaderContext();

  const titleEl = sectionDefinition["label"] && <Typography variant={headerVariant}>{sectionDefinition["label"]} </Typography>;
  const descEl = sectionDefinition["description"] && <Typography variant="caption" color="textSecondary">{sectionDefinition["description"]} </Typography>
  const hasHeader = titleEl || descEl;
  const isRecurrent = sectionDefinition['recurrent'];

  // Determine if we have any conditionals in our definition that would cause us to be hidden
  const displayed = ConditionalComponentManager.evaluateCondition(
    sectionDefinition,
    formContext);

  // mountOnEnter and unmountOnExit force the inputs and children to be outside of the DOM during form submission
  // if it is not currently visible
  return useCallback(<Collapse
    in={displayed}
    component={Grid}
    item
    mountOnEnter
    unmountOnExit
    className={(hasHeader ? classes.labeledSection : "") + " " + (displayed ? "" : classes.collapsedSection)}
    >
    {instanceLabels.map( (uuid, idx) => {
        const sectionPath = path + "/" + uuid;
        const existingSectionAnswer = existingAnswer?.find((answer) => answer[0] == uuid)?.[1];
        return <Grid item className={hasHeader && idx === 0 && classes.labeledSection} key={uuid}>
          <input type="hidden" name={`${sectionPath}/jcr:primaryType`} value={"lfs:AnswerSection"}></input>
          <input type="hidden" name={`${sectionPath}/section`} value={sectionDefinition['jcr:uuid']}></input>
          <input type="hidden" name={`${sectionPath}/section@TypeHint`} value="Reference"></input>

          <Grid container {...FORM_ENTRY_CONTAINER_PROPS}>
            {hasHeader &&
              <Grid item className={classes.sectionHeader}>
                {titleEl}
                {descEl}
              </Grid>
            }
            {<Grid item className={classes.sectionHeader}>{sectionPath}</Grid>}
            {Object.entries(sectionDefinition)
              .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
              .map(([key, definition]) => FormEntry(definition, sectionPath, depth+1, existingSectionAnswer, key))
            }
          </Grid>
        </Grid>
        })
      }
      {isRecurrent &&
      <Grid item>
        <Button
          size="small"
          variant="contained"
          color="default"
          className={classes.addSectionButton}
          onClick={() => {
            console.log(uuidv4());
            setInstanceLabels((oldLabels) => [...oldLabels, uuidv4()]);
          }}
          >
          <Add fontSize="small" />
        </Button>
      </Grid>}
  </Collapse>, [displayed]);
}

Section.propTypes = {
  classes: PropTypes.object.isRequired,
  depth: PropTypes.number.isRequired,
  existingAnswer: PropTypes.array,
  path: PropTypes.string.isRequired,
  sectionDefinition: PropTypes.shape({
    label: PropTypes.string,
    description: PropTypes.string,
  }).isRequired,
}

export default withStyles(QuestionnaireStyle)(Section);
