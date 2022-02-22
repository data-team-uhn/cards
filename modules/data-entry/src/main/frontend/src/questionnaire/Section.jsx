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
import { Button, Collapse, Dialog, DialogActions, DialogTitle, Grid, IconButton, Tooltip, Typography, withStyles } from "@material-ui/core";
import Add from "@material-ui/icons/Add";
import Delete from '@material-ui/icons/Delete';
import UnfoldLess from '@material-ui/icons/UnfoldLess';
import UnfoldMore from '@material-ui/icons/UnfoldMore';

import ConditionalComponentManager from "./ConditionalComponentManager";
import FormEntry, { ENTRY_TYPES } from "./FormEntry";
import { useFormReaderContext } from "./FormContext";
import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";

// FIXME In order for the conditionals to be registered, they need to be loaded, and the only way to do that at the moment is to explicitly invoke them here. Find a way to automatically load all conditional types, possibly using self-declaration in a node, like the assets, or even by filtering through assets.
import ConditionalGroup from "./ConditionalGroup";
import ConditionalSingle from "./ConditionalSingle";
import FormattedText from "../components/FormattedText.jsx";
import { v4 as uuidv4 } from 'uuid';

const ID_STATE_KEY = ":AccessCount";

// The heading levels that @material-ui supports
const MAX_HEADING_LEVEL = 6;
const MIN_HEADING_LEVEL = 4;

/**
 * Creates the title from the given section specification.
 * @param {String} label Label of the section
 * @param {int} idx Zero-indexed section number
 */
function createTitle(label, idx, isRecurrent) {
  return (`${label}${isRecurrent ? (" #" + (idx+1)) : ""}`);
}

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
  const { classes, depth, existingAnswer, path, sectionDefinition, onChange, visibleCallback, pageActive, isEdit, isSummary, instanceId, contentOffset } = props;
  const isRecurrent = sectionDefinition['recurrent'];
  const { displayMode } = sectionDefinition;
  const [ focus, setFocus ] = useState(false);

  const headerVariant = "h5";
  const titleEl = sectionDefinition["label"] &&
    ((idx, uuid) =>
      <Typography variant={headerVariant} className={uuid == selectedUUID ? classes.highlightedTitle: undefined}>
        {createTitle(sectionDefinition["label"], idx, isRecurrent)}
      </Typography>
    );
  const descEl = sectionDefinition["description"] &&
    (idx =>
      <FormattedText variant="caption" display="block" color="textSecondary">
        {sectionDefinition["description"]}
      </FormattedText>
    );
  const hasHeader = titleEl || descEl;

  const [ instanceLabels, setInstanceLabels ] = useState(
    // If we already exist from existingAnswer, our labels are the first element
    existingAnswer?.length > 0 ? existingAnswer.map(element => element[0])
    // Otherwise, create a new UUID
    : [uuidv4()]);
  // Keep a list of UUIDs whose contents we need to remove
  const [ UUIDsToRemove, setUUIDsToRemove ] = useState([]);
  // Keep a list of UUIDs whose contents we should hide
  const [ labelsToHide, setLabelsToHide ] = useState({});
  const formContext = useFormReaderContext();
  const [ dialogOpen, setDialogOpen ] = useState(false);
  const [ selectedUUID, setSelectedUUID ] = useState();
  const [ uuid ] = useState(uuidv4());  // To keep our IDs separate from any other sections
  const [ removableAnswers, setRemovableAnswers ] = useState({[ID_STATE_KEY]: 1});

  // Determine if we have any conditionals in our definition that would cause us to be hidden
  const conditionIsMet = ConditionalComponentManager.evaluateCondition(
    sectionDefinition,
    formContext);

  // Determine if the section is flagged as incomplete
  const isFlagged = (existingAnswer?.[0]?.[1]?.statusFlags?.length > 0);

  // Determine if the section has any answers
  let detectAnswers = (answerSection) => {
    let result = false;
    Object.entries(answerSection || {}).forEach( ([key, item]) => {
      if (item.displayedValue || item.note || item.section && detectAnswers(item)) {
        result = true;
      }
    })
    return result;
  }

  let hasAnswers = isEdit || detectAnswers(existingAnswer[0]?.[1]);

  // Display the section in view mode if it has answers or is marked as incomplete.
  // Do not display summary questions outside of summary mode, or regular questions in summary mode.
  const isDisplayed = (isEdit && conditionIsMet || !isEdit && (hasAnswers || isFlagged))
    && (isSummary && "summary" === displayMode || !isSummary && displayMode !== "summary");

  if (visibleCallback) visibleCallback(conditionIsMet);

  let closeDialog = () => {
    setSelectedUUID(undefined);
    setDialogOpen(false);
  }

  const sectionEntries = Object.entries(sectionDefinition).filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']));

  function calculateDeletion() {
    let delList = [];
    let keySet = Object.keys(removableAnswers);
    for (let i = 0; i < keySet.length; i++) {
      let key = keySet[i];
      for (let j = 0; j < removableAnswers[key].length-1; j++) {
        delList.push(removableAnswers[key][j]);
      }
    }
    return delList;
  }

  const collapseClasses = [];
  collapseClasses.push(classes[displayMode + 'Section']);
  if (isEdit) {
    collapseClasses.push("cards-edit-section");
  }
  // Hide the section if it is conditioned to be hidden in edit mode
  // Or if we're in view mode and do not have any answers and the section is not marked as incomplete
  if (isEdit && !conditionIsMet || !isEdit && !hasAnswers && !isFlagged) {
    collapseClasses.push(classes.collapsedSection);
  }
  if (hasHeader) {
    collapseClasses.push(classes.collapseWrapper);
  }
  // Don't hide for undefined or null values
  if (pageActive === false) {
    collapseClasses.push(classes.hiddenSection);
  }

  let sectionPosition = {};
  if (displayMode == 'header') {
    sectionPosition.top = contentOffset.top;
  } else if (displayMode == 'footer') {
    sectionPosition.bottom = contentOffset.bottom;
  }

  // mountOnEnter and unmountOnExit force the inputs and children to be outside of the DOM during form submission
  // if it is not currently visible
  return useCallback(
  <React.Fragment>
    {/* if conditional is true, the collapse component is rendered and displayed.
        else, the corresponding input tag to the conditional section is deleted  */}
    { isDisplayed
      ? (<Collapse
      in={isDisplayed}
      component={Grid}
      item
      mountOnEnter
      unmountOnExit
      className={collapseClasses.join(" ")}
      style={sectionPosition}
      >
      {instanceLabels.map( (uuid, idx) => {
          const sectionPath = path + "/" + uuid;
          const existingSectionAnswer = existingAnswer?.find((answer) => answer[0] == uuid)?.[1];
          const hiddenSection = conditionIsMet && labelsToHide[uuid];
          return <div
            key={uuid}
            className={"recurrentSectionInstance " + classes.recurrentSectionInstance}
            >
            <input type="hidden" name={`${sectionPath}/jcr:primaryType`} value={"cards:AnswerSection"}></input>
            <input type="hidden" name={`${sectionPath}/section`} value={sectionDefinition['jcr:uuid']}></input>
            <input type="hidden" name={`${sectionPath}/section@TypeHint`} value="Reference"></input>

            <Grid
              container
              className={
                (isRecurrent ? classes.recurrentSection : undefined)
              }
              {...FORM_ENTRY_CONTAINER_PROPS}
              >
              {/* Section header */
                (hasHeader || isRecurrent) &&
                  <Grid item className={classes.sectionHeader + " " + (isRecurrent ? classes.recurrentHeader : "")}>
                    {/* Delete this entry and expand this entry button */}
                    {isEdit && isRecurrent &&
                      <Tooltip title="Delete section" aria-label="Delete section" >
                        <IconButton
                          autoFocus={focus && (idx == instanceLabels.length - 1)}
                          color="default"
                          className={classes.entryActionIcon}
                          onClick={() => {
                            setDialogOpen(true);
                            setSelectedUUID(uuid);
                          }}
                          >
                          <Delete fontSize="small" />
                        </IconButton>
                      </Tooltip>}
                    {pageActive !== true  &&
                      <Tooltip title="Expand section" aria-label="Expand section" >
                        <IconButton
                          color="default"
                          className={classes.entryActionIcon}
                          onClick={() => {
                            setLabelsToHide((toHide) => ({...toHide, [uuid]: !hiddenSection}));
                          }}
                          >
                          {hiddenSection ?
                            <UnfoldMore fontSize="small" />
                            : <UnfoldLess fontSize="small" />
                          }
                        </IconButton>
                      </Tooltip>
                    }

                    {/* Title & description */}
                    {titleEl && titleEl(idx, uuid)}
                    {descEl && descEl(idx)}
                  </Grid>
              }
              <Collapse
                mountOnEnter
                unmountOnExit
                in={!hiddenSection}
                component={Grid}
                className={(uuid == selectedUUID ? classes.highlightedSection : undefined)}
                item
                >
                <Grid container {...FORM_ENTRY_CONTAINER_PROPS}>
                  {/* Section contents are strange if this isn't a direct child of the above grid, so we wrap another container*/
                    sectionEntries.map(([key, definition]) =>
                      <FormEntry
                        instanceId={instanceId + "-" + idx}
                        key={key}
                        entryDefinition={definition}
                        path={sectionPath}
                        depth={depth+1}
                        existingAnswers={existingSectionAnswer}
                        keyProp={key}
                        classes={classes}
                        onChange={onChange}
                        isEdit={isEdit}
                        isSummary={isSummary}
                        contentOffset={contentOffset}
                        pageActive={pageActive}
                        sectionAnswersState={removableAnswers}
                        onAddedAnswerPath={(newAnswers) => {
                          newAnswers[ID_STATE_KEY] = newAnswers[ID_STATE_KEY] + 1;
                          setRemovableAnswers(newAnswers);
                        }}>
                      </FormEntry>)
                  }
                  {
                    calculateDeletion().map((delPath) =>
                      <input type="hidden" name={`${delPath}@Delete`} value="0" key={delPath}></input>
                  )}
                </Grid>
              </Collapse>
            </Grid>
          </div>
          })
        }
        {isEdit && isRecurrent &&
        <Grid item className="addSectionContainer">
          <Button
            size="small"
            variant="contained"
            color="default"
            className={classes.addSectionButton}
            onClick={() => {
              setInstanceLabels((oldLabels) => [...oldLabels, uuidv4()]);
              setFocus(true);
            }}
            >
            <Add fontSize="small" /> {sectionDefinition["label"]}
          </Button>
        </Grid>}
        {/* Remove any cards:AnswerSections that we have created by using an @Delete suffix */
          UUIDsToRemove.map((uuid) =>
            <input type="hidden" name={`${path + "/" + uuid}@Delete`} value="0" key={uuid}></input>
        )}
      </Collapse>)
      : instanceLabels.map((uuid) =>
        <input type="hidden" name={`${path + "/" + uuid}@Delete`} value="0" key={uuid}></input>
      )
      }
    <Dialog
      open={dialogOpen}
      onClose={closeDialog}
      aria-labelledby={uuid + "-delete-dialog-title"}
      >
      <DialogTitle id={uuid + "-delete-dialog-title"}>Are you sure you want to delete this section?</DialogTitle>
      <DialogActions>
        <Button onClick={closeDialog} color="primary">
          Cancel
        </Button>
        <Button
          onClick={() => {
            closeDialog();
            setInstanceLabels((oldLabels) => oldLabels.filter((label) => label != selectedUUID));
            setUUIDsToRemove((old_uuids_to_remove) => [...old_uuids_to_remove, selectedUUID]);
          }}
          color="primary"
          >
          Delete
        </Button>
      </DialogActions>
    </Dialog>
    </React.Fragment>
    , [conditionIsMet, instanceLabels, labelsToHide, dialogOpen, removableAnswers[ID_STATE_KEY], pageActive]);
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
