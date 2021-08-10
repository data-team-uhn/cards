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

import React, { useState, useEffect } from "react";

import {
  Button,
  MobileStepper,
  withStyles,
} from "@material-ui/core";

import PropTypes from "prop-types";
import { QUESTION_TYPES, ENTRY_TYPES } from "./FormEntry";
import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";

class Page {
  constructor(visible) {
    this.visible = visible;
    this.canBeVisible = true;
  }
  conditionalVisible = [];

  addConditionalVisible(visible, index) {
    this.conditionalVisible[index] = visible;
    this.canBeVisible = this.conditionalVisible.length === 0 || this.conditionalVisible.includes(true);
  }
}

/**
 * Component that displays a page of a Form.
 */
function FormPagination (props) {
  let { classes, saveInProgress, lastSaveStatus, setPagesCallback, paginationEnabled, enableSave, questionnaireData } = props;

  let [ savedLastPage, setSavedLastPage ] = useState(false);
  let [ pendingSubmission, setPendingSubmission ] = useState(false);
  let [ pages, setPages ] = useState([]);
  let [ activePage, setActivePage ] = useState(0);

  let previousEntryType;
  let questionIndex = 0;
  let pagesResults = {};
  let pagesArray = [];

  useEffect(() => {
    setPagesCallback(null);
    Object.entries(questionnaireData)
            .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
            .map(([key, entryDefinition]) => {
              let pageResult = addPage(entryDefinition);
              pagesResults[key] = pageResult;
            });
    setPages(pagesArray);
    setPagesCallback(pagesResults);
  }, [questionnaireData, activePage]);

  let addPage = (entryDefinition) => {
    if (paginationEnabled) {
      let page;
      if (QUESTION_TYPES.includes(entryDefinition["jcr:primaryType"]) && previousEntryType && QUESTION_TYPES.includes(previousEntryType)) {
        page = pagesArray[pagesArray.length - 1];
        questionIndex++;
      } else {
        page = new Page(!paginationEnabled || activePage == pagesArray.length);
        pagesArray.push(page);
        questionIndex = 0;
      }
      previousEntryType = entryDefinition["jcr:primaryType"];

      return {
        page: page,
        callback: (visible) => {page.addConditionalVisible(visible, questionIndex);}
      }
    } else {
      if (pagesArray.length === 0) {
        pagesArray.push(new Page(true));
      }
      return {page: pagesArray[0], callback: ()=>{}}
    }
  }

  let lastValidPage = () => {
    let result = pages.length - 1;
    while (result > 0 && !pages[result].canBeVisible) {
      result--;
    }
    return result;
  }

  let handleNext = () => {
    setPendingSubmission(true);
    if (activePage === lastValidPage()) {
      setSavedLastPage(true);
    } else {
      setSavedLastPage(false);
      handlePageChange("next");
    }
  }

  let handleBack = () => {
    setPendingSubmission(true);
    if (activePage > 0) {
      handlePageChange("back");
    }
  }

  if (saveInProgress && pendingSubmission) {
    setPendingSubmission(false);
  }

  let handlePageChange = (direction) => {
    let change = (direction === "next" ? 1 : -1);
    let nextPage = activePage;
    while ((change === 1 || nextPage > 0) && (change === -1 || nextPage < lastValidPage())) {
      nextPage += change;
      if (pages[nextPage].canBeVisible) break;
    }
    if (nextPage !== activePage) {
      window.scrollTo(0, 0);
    }
    setActivePage(nextPage);
  }

  let saveButton =
    <Button
      type="submit"
      variant="contained"
      color="primary"
      disabled={saveInProgress || activePage === lastValidPage()}
      className={classes.paginationButton}
      onClick={handleNext}
    >
      {!enableSave ? "Next" :
      ((lastValidPage() === 0 || activePage === lastValidPage()) && saveInProgress) ? 'Saving' :
      lastSaveStatus === false ? 'Save failed, log in and try again?' :
      activePage < lastValidPage() ? "Next" :
      lastSaveStatus && savedLastPage ? 'Saved' :
      'Save'}
    </Button>

  let stepper = (isBack) =>
    <MobileStepper
      variant="progress"
      // Offset back bar 1 to create a "current page" region.
      // If the final page has been saved, progress the front bar to complete
      activeStep={activePage + (isBack ? 1 : (lastSaveStatus && savedLastPage ? 1 : 0))}
      // Change the color of the back bar
      LinearProgressProps={isBack ? {classes: {barColorPrimary: classes.formStepperTopBar}}: null}
      // Hide the backround of the front bar to segment of back bar
      className={`${classes.formStepper} ${isBack ? classes.formStepperTop : classes.formStepperBottom}`}
      classes={isBack ? null : {progress:classes.formStepperBottomBackground}}
      // base 0 to base 1, plus 1 for the "current page" region
      steps={lastValidPage() + 2}
      nextButton={saveButton}
      backButton={
        lastValidPage() > 0
          ? <Button
              type="submit"
              // Don't disable until form submission started
              disabled={(activePage === 0 && !pendingSubmission)
                || saveInProgress
                || lastSaveStatus === false}
              onClick={handleBack}
              className={classes.paginationButton}
              color="primary"
            >
              Back
            </Button>
          : null
      }
    />

  return (
    paginationEnabled ?
    lastValidPage() > 0 ?
      <React.Fragment>
        {/* Back bar to show a different colored current page section*/}
        {stepper(true)}
        {/* Front bar to color completed pages differently from current page */}
        {stepper(false)}
      </React.Fragment>
    :
      saveButton
    : null
  );
};

FormPagination.propTypes = {
  enableSave: PropTypes.bool,
  paginationEnabled: PropTypes.bool,
  questionnaireData: PropTypes.object.isRequired,
  setPagesCallback: PropTypes.func.isRequired,
  lastSaveStatus: PropTypes.bool,
  saveInProgress: PropTypes.bool
};

FormPagination.defaultProps = {
  enableSave: true,
  paginationEnabled: true,
  saveInProgress: false,
  lastSaveStatus: true
};

export default withStyles(QuestionnaireStyle)(FormPagination);
