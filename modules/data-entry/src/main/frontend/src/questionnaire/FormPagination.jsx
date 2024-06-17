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
  MobileStepper
} from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import PropTypes from "prop-types";
import { SECTION_TYPES, ENTRY_TYPES } from "./FormEntry";
import FormPageNavigation from "./FormPageNavigation";

import QuestionnaireStyle from "./QuestionnaireStyle";

class Page {
  constructor(visible, title, key) {
    this.visible = visible;
    this.canBeVisible = true;
    this.title = title;
    this.keys = [ key ];
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
  let { classes, enabled, variant, navMode, saveInProgress, lastSaveStatus, setPagesCallback, isPageCompleted, enableSave, onDone, doneLabel, doneIcon, questionnaireData, disableProgress, onPageChange } = props;

  let [ savedLastPage, setSavedLastPage ] = useState(false);
  let [ pendingSubmission, setPendingSubmission ] = useState(false);
  let [ pages, setPages ] = useState([]);
  let [ activePage, setActivePage ] = useState(0);
  let [ direction, setDirection ] = useState(1);
  let [ nextActivePage, setNextActivePage ] = useState();
  const DIRECTION_NEXT = 1, DIRECTION_PREV = -1;

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
  }, [questionnaireData, activePage, enabled]);

  let addPage = (entryDefinition) => {
    if (enabled) {
      let page;
      if (!SECTION_TYPES.includes(entryDefinition["jcr:primaryType"]) && previousEntryType && !SECTION_TYPES.includes(previousEntryType)) {
        page = pagesArray[pagesArray.length - 1];
        page.keys?.push(entryDefinition["@name"]);
        questionIndex++;
      } else {
        page = new Page(
          !enabled || activePage == pagesArray.length,
          entryDefinition.label || entryDefinition.text || "",
          entryDefinition["@name"]
        );
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
    initChangePage(DIRECTION_NEXT);
  }

  let handleBack = () => {
    initChangePage(DIRECTION_PREV);
  }

  let handleNavigateTo = (pageIndex) => {
    if (enableSave) {
      // If we must save the page before going, we make sure to not move to the new page
      // until the submission process is complete.
      setPendingSubmission(true);
      setNextActivePage(pageIndex);
      setDirection(undefined);
    } else {
      // If saving is not enabled, we can call handlePageChange directly
      pages[pageIndex]?.canBeVisible && activatePage(pageIndex);
    }
  }

  // Change the page in the given direction
  let initChangePage = (changeDirection) => {
    if (enableSave) {
      // If we must save the page before going, we make sure to not call handlePageChange
      // until the submission process is complete.
      setPendingSubmission(true);
      setDirection(changeDirection);
      setNextActivePage(undefined);
    } else {
      // If saving is not enabled, we can call handlePageChange directly
      // And call the onDone() if we're on the last page
      handlePageChange(changeDirection);
      if (activePage === lastValidPage() && changeDirection === DIRECTION_NEXT) {
        setSavedLastPage(true);
        onDone && onDone();
      }
    }
  }

  let handlePageChange = (overrideDirection) => {
    let change = (overrideDirection || direction);
    let nextPage = activePage;
    while ((change === DIRECTION_NEXT || nextPage >= 0) && (change === DIRECTION_PREV || nextPage < lastValidPage())) {
      nextPage += change;
      if (pages[nextPage].canBeVisible) break;
    }
    activatePage(nextPage);
  }

  let activatePage = (page) => {
    let nextPage = page ?? nextActivePage;
    // If the next page is not the one we're on already
    if (nextPage !== activePage) {
      window.scrollTo(0, 0);
      setActivePage(nextPage);
      onPageChange?.();
    }
  }

  useEffect(() => {
    if (!saveInProgress && pendingSubmission && !(disableProgress && (nextActivePage > activePage || direction === DIRECTION_NEXT))) {
      setPendingSubmission(false);
      if (activePage === lastValidPage() && direction === DIRECTION_NEXT) {
        setSavedLastPage(true);
        onDone && onDone();
      } else {
        setSavedLastPage(false);
        typeof(nextActivePage) != 'undefined' ? activatePage() : handlePageChange();
      }
    }
  }, [saveInProgress, pendingSubmission, disableProgress, nextActivePage, direction, activePage]);

  let saveButton =
    <Button
      startIcon={activePage === lastValidPage() ? doneIcon : undefined}
      type="submit"
      variant="contained"
      disabled={saveInProgress}
      className={classes.paginationButton}
      onClick={handleNext}
    >
      {
      ((lastValidPage() === 0 || activePage === lastValidPage()) && saveInProgress) ? 'Saving' :
      lastSaveStatus === false ? 'Save failed, log in and try again?' :
      activePage < lastValidPage() ? "Next" :
      !enableSave ? (doneLabel || "Close") :
      lastSaveStatus && savedLastPage ? 'Saved' :
      (doneLabel || 'Save')}
    </Button>

  let backButton = navMode == "only_next" ? undefined : (
    <Button
      type="submit"
      variant="outlined"
      // Don't disable until form submission started
      disabled={activePage === 0
        || saveInProgress
        || lastSaveStatus === false}
      className={classes.paginationButton}
      onClick={handleBack}
    >
      Back
    </Button>
  );

  let stepperClasses = [classes.formStepper];
  if (classes[navMode]) {
    stepperClasses.push(classes[navMode]);
  }
  stepperClasses = stepperClasses.join(' ');

  let progressAdjustment = (condition) => (condition && variant == "progress" ? 1 : 0);

  return (
    enabled
    ?
      variant == "navigable" && pages?.length > 0 ?
        <FormPageNavigation
          pages={pages}
          activePage={activePage}
          saveButton={saveButton}
          backButton={backButton}
          isPageCompleted={isPageCompleted}
          navigateTo={handleNavigateTo}
        />
      :
      lastValidPage() > 0
      ?
        <MobileStepper
          variant={variant}
          // Offset back bar 1 to create a "current page" region.
          // If the final page has been saved, progress the front bar to complete
          activeStep={activePage + progressAdjustment(lastSaveStatus && savedLastPage)}
          // Change the color of the back bar
          LinearProgressProps={{ 
              classes: {
                         bar2Buffer: classes.formStepperBufferBar,
                         dashed: classes.formStepperBackgroundBar
                       },
              variant: "buffer",
              valueBuffer: (activePage + 1) / (lastValidPage() + 1) * 100
          }}
          className={stepperClasses}
          // base 0 to base 1, plus 1 for the "current page" region when variant is "progress"
          steps={lastValidPage() + 1 + progressAdjustment(true)}
          nextButton={saveButton}
          backButton={backButton}
        />
      :
        saveButton
    : null
  );
};

FormPagination.propTypes = {
  enableSave: PropTypes.bool,
  enabled: PropTypes.bool,
  variant: PropTypes.oneOf(['progress', 'dots', 'text', "navigable"]),
  navMode: PropTypes.oneOf(['back_next', 'only_next']),
  questionnaireData: PropTypes.object.isRequired,
  setPagesCallback: PropTypes.func.isRequired,
  isPageCompleted: PropTypes.func.isRequired,
  lastSaveStatus: PropTypes.bool,
  saveInProgress: PropTypes.bool
};

FormPagination.defaultProps = {
  enableSave: true,
  enabled: true,
  variant: "progress",
  navMode: "back_next",
  saveInProgress: false,
  lastSaveStatus: true
};

export default withStyles(QuestionnaireStyle)(FormPagination);
