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

import { useState, useEffect } from "react";

import Add from "@mui/icons-material/Add";
import UnfoldLess from '@mui/icons-material/UnfoldLess';
import UnfoldMore from '@mui/icons-material/UnfoldMore';
import { Button, Collapse, Grid, IconButton, Tooltip } from "@mui/material";
import PropTypes from "prop-types";
import removeMd from 'remove-markdown';
import { withStyles } from 'tss-react/mui';
import { v4 as uuidv4 } from 'uuid';

import ConditionalComponentManager from "./ConditionalComponentManager";
// FIXME In order for the conditionals to be registered, they need to be loaded, and the only way to do that at the
// moment is to explicitly invoke them here. Find a way to automatically load all conditional types, possibly using
// self-declaration in a node, like the assets, or even by filtering through assets.
/* eslint-disable unused-imports/no-unused-imports, no-unused-vars */
import ConditionalGroup from "./ConditionalGroup";
import ConditionalSingle from "./ConditionalSingle";
/* eslint-enable unused-imports/no-unused-imports, no-unused-vars */
import { useFormReaderContext, useFormWriterContext } from "./FormContext";
import FormEntry, { ENTRY_TYPES } from "./FormEntry";
import { hasWarningFlags } from "./FormUtilities";
import { FORM_ENTRY_CONTAINER_PROPS } from "./questionnaireConstants.jsx";
import sectionStyles from "./sectionStyles.jsx";
import FormattedText from "../components/FormattedText.jsx";
import DeleteButton from "../dataHomepage/DeleteButton";
import { checkPropTypes } from "../propTypes";


const ID_STATE_KEY = ":AccessCount";

/**
 * Creates the title from the given section specification.
 * @param {String} label Label of the section
 * @param {int} idx Zero-indexed section number
 */
function createTitle(label, idx, isRecurrent) {
  return (`${label || ""}${isRecurrent ? (" #" + (idx+1)) : ""}`);
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
  checkPropTypes(Section, props);
  const {
    classes,
    depth,
    existingAnswer,
    path,
    sectionDefinition,
    onChange,
    visibleCallback,
    pageActive,
    isEdit,
    isSummary,
    instanceId,
    contentOffset,
    gridProps
  } = props;
  const isRecurrent = sectionDefinition['recurrent'];
  const isCompact = sectionDefinition['compact'];
  const { displayMode } = sectionDefinition;

  const headerVariant = "h5";
  const titleEl = sectionDefinition["label"] &&
    (idx =>
      <FormattedText component={headerVariant} variant={headerVariant}>
        {createTitle(sectionDefinition["label"], idx, isRecurrent)}
      </FormattedText>
    );
  const descEl = sectionDefinition["description"] &&
    (() =>
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
  const changeFormContext = useFormWriterContext();
  const [ selectedUUID, setSelectedUUID ] = useState();
  const [ removableAnswers, setRemovableAnswers ] = useState({ [ID_STATE_KEY]: 1 });
  const [ answersToDelete, setAnswersToDelete ] = useState([]);

  // Determine if we have any conditionals in our definition that would cause us to be hidden
  const conditionIsMet = ConditionalComponentManager.evaluateCondition(
    sectionDefinition,
    formContext);

  // When the section no longer meets its condition, mark all previous answers as deletable.
  useEffect(() => {
    if (!conditionIsMet) {
      let delList = [];
      let keySet = Object.keys(removableAnswers);
      for (let i = 0; i < keySet.length; i++) {
        let key = keySet[i];
        for (let j = 0; j < removableAnswers[key].length; j++) {
          delList.push(removableAnswers[key][j]);
        }
      }
      // Append the new list of answers to delete to the previous one
      setAnswersToDelete((oldValue) => oldValue.concat(delList));
      // Reset the list of existing answers
      setRemovableAnswers({ [ID_STATE_KEY]: 1 });

      changeFormContext((oldContext) => {
        // Remove the section answers from the context
        let newData = { ...oldContext };
        keySet.forEach(key => { delete newData[key] });
        return newData;
      });
    }
  }, [conditionIsMet]);

  // Determine if the section is flagged as incomplete
  const isFlagged = hasWarningFlags(existingAnswer?.[0]);

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

  // Determine if the section has any other content that should be displayed
  let detectOtherContent = (entryDefinition) => (
    Object.values(entryDefinition)
      .filter(childEntry => ENTRY_TYPES.includes(childEntry['jcr:primaryType']))
      .some(childEntry => ["view", "any"].includes(childEntry.formMode) || detectOtherContent(childEntry))
  )

  let hasContent = isEdit || detectOtherContent(sectionDefinition);

  // Display the section in view mode if it has answers or is marked as incomplete.
  // Do not display summary questions outside of summary mode, or regular questions in summary mode.
  const isDisplayed = (isEdit && conditionIsMet || !isEdit && (isFlagged || hasAnswers || hasContent))
    && (isSummary && "summary" === displayMode || !isSummary && displayMode !== "summary");

  if (visibleCallback) visibleCallback(conditionIsMet);

  const sectionEntries = Object.entries(sectionDefinition).filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']));

  const collapseClasses = [];
  collapseClasses.push(classes[displayMode + 'Section']);
  if (isEdit) {
    collapseClasses.push("cards-edit-section");
  }
  // Hide the section if it is conditioned to be hidden in edit mode
  // Or if we're in view mode and do not have any answers or other content, and the section is not marked as incomplete
  if (!isDisplayed) {
    collapseClasses.push(classes.collapsedSection);
  }
  // Don't hide for undefined or null values
  if (pageActive === false) {
    collapseClasses.push("cards-hidden");
  }

  let sectionPosition = {};
  if (displayMode == 'header') {
    sectionPosition.top = contentOffset.top;
  } else if (displayMode == 'footer') {
    sectionPosition.bottom = contentOffset.bottom;
  }

  // mountOnEnter and unmountOnExit force the inputs and children to be outside of the DOM during form submission
  // if it is not currently visible
  return (
    <>
      {/* if conditional is true, the collapse component is rendered and displayed.
        else, the corresponding input tag to the conditional section is deleted  */}
      { isDisplayed
        ? (<Collapse
          in={isDisplayed}
          component={Grid}
          {...gridProps}
          mountOnEnter
          unmountOnExit
          className={collapseClasses.join(" ")}
          style={sectionPosition}
        >
          {instanceLabels.map( (uuid, idx) => {
            const sectionPath = path + "/" + uuid;
            const existingSectionAnswer = existingAnswer?.find((answer) => answer[0] == uuid)?.[1];
            const hiddenSection = conditionIsMet && labelsToHide[uuid];
            const classNames = [];
            if (isRecurrent) classNames.push(classes.recurrentSectionInstance);
            if (uuid == selectedUUID) classNames.push(classes.highlightedSection);
            return <div
              key={uuid}
              className={classNames.join(" ")}
            >
              <input type="hidden" name={`${sectionPath}/jcr:primaryType`} value={"cards:AnswerSection"} />
              <input type="hidden" name={`${sectionPath}/section`} value={sectionDefinition['jcr:uuid']} />
              <input type="hidden" name={`${sectionPath}/section@TypeHint`} value="Reference" />

              <Grid
                container
                {...FORM_ENTRY_CONTAINER_PROPS}
              >
                {/* Section header */
                  (hasHeader || isRecurrent) &&
                  <Grid className={classes.sectionHeader}>
                    {/* Delete this entry and expand this entry button */}
                    {isEdit && isRecurrent &&
                      <DeleteButton
                        size="large"
                        className={classes.entryActionIcon}
                        entryName={createTitle(sectionDefinition.label, idx, isRecurrent)}
                        entryType="section"
                        onClick={() => {
                          setSelectedUUID(uuid);
                        }}
                        onClose={() => {
                          setSelectedUUID(undefined);
                        }}
                        onComplete={() => {
                          setInstanceLabels((oldLabels) => oldLabels.filter((label) => label != selectedUUID));
                          setUUIDsToRemove((old_uuids_to_remove) => [...old_uuids_to_remove, selectedUUID]);
                        }}
                      />
                    }
                    {pageActive !== true  &&
                      <Tooltip title="Expand section" aria-label="Expand section" >
                        <IconButton
                          className={classes.entryActionIcon}
                          onClick={() => {
                            setLabelsToHide((toHide) => ({ ...toHide, [uuid]: !hiddenSection }));
                          }}
                          size="large"
                        >
                          {hiddenSection ?
                            <UnfoldMore fontSize="small" />
                            : <UnfoldLess fontSize="small" />
                          }
                        </IconButton>
                      </Tooltip>
                    }

                    {/* Title & description */}
                    {titleEl?.(idx)}
                    {descEl?.()}
                  </Grid>
                }
                <Collapse
                  mountOnEnter
                  unmountOnExit
                  in={!hiddenSection}
                  component={Grid}
                >
                  <Grid container
                    {...FORM_ENTRY_CONTAINER_PROPS}
                    className={
                      isCompact && sectionEntries.length > 1 ?
                        [classes.horizontalSection, "cards-horizontal-section"].join(' ')
                        : undefined
                    }
                  >
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
                          onChange={onChange}
                          isEdit={isEdit}
                          isSummary={isSummary}
                          contentOffset={contentOffset}
                          gridProps={isCompact && sectionEntries.length > 1 ?
                            { size : { xs: 12, sm: 12, md: 6, lg: (sectionEntries.length == 2 ? 6 : 4) } } : undefined}
                          pageActive={pageActive}
                          sectionAnswersState={removableAnswers}
                          onAddedAnswerPath={(newAnswers) => {
                            newAnswers[ID_STATE_KEY] = newAnswers[ID_STATE_KEY] + 1;
                            setRemovableAnswers(newAnswers);
                          }}>
                        </FormEntry>)
                    }
                    {
                      answersToDelete.map((delPath) =>
                        <input type="hidden" name={`${delPath}@Delete`} value="0" key={delPath} />
                      )}
                  </Grid>
                </Collapse>
              </Grid>
            </div>
          })
          }
          {isEdit && isRecurrent &&
        <Grid sx={{ bgcolor: "action.hover", px: 1, py: 2 }}>
          <Button
            size="small"
            variant="contained"
            color="success"
            startIcon={<Add />}
            onClick={() => {
              setInstanceLabels((oldLabels) => [...oldLabels, uuidv4()]);
            }}
          >
            {removeMd(sectionDefinition["label"])}
          </Button>
        </Grid>}
          {/* Remove any cards:AnswerSections that we have created by using an @Delete suffix */
            UUIDsToRemove.map((uuid) =>
              <input type="hidden" name={`${path + "/" + uuid}@Delete`} value="0" key={uuid} />
            )}
        </Collapse>)
        : instanceLabels.map((uuid) =>
          <input type="hidden" name={`${path + "/" + uuid}@Delete`} value="0" key={uuid} />
        )
      }
    </>
  );
}

Section.propTypes = {
  depth: PropTypes.number.isRequired,
  existingAnswer: PropTypes.array,
  path: PropTypes.string.isRequired,
  sectionDefinition: PropTypes.shape({
    label: PropTypes.string,
    description: PropTypes.string,
  }).isRequired,
}

export default withStyles(Section, sectionStyles);
