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

import {
  Button,
  Step,
  StepLabel,
  Stepper,
  withStyles,
} from "@material-ui/core";

import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";

/**
 * Component that displays an page of a Form.
 */
function FormPagination (props) {
  let { classes, pages, activePage, saveInProgress, lastSaveStatus, handlePageChange } = props;

  let [ savedLastPage, setSavedLastPage ] = useState(false);
  let [ pendingSubmission, setPendingSubmission ] = useState(false);

  let handleNext = () => {
    if (pages.length === 0 || activePage === pages.length - 1) {
      setSavedLastPage(true);
    } else {
      setSavedLastPage(false);
    }
    setPendingSubmission(true);
    handlePageChange(activePage + 1);
  }

  let handleBack = () => {
    setPendingSubmission(true);
    handlePageChange(activePage - 1);
  }

  if (saveInProgress && pendingSubmission) {
    setPendingSubmission(false);
  }

  return (
    pages ?
      <React.Fragment>
        <Stepper activeStep={activePage} className={classes.formStepper} alternativeLabel>
          {pages.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>
        <Button
          type="submit"
          variant="contained"
          color="primary"
          disabled={saveInProgress}
          className={classes.paginationButton}
          onClick={handleNext}
        >
          {/* {activePage === pages.length -1 ? "Save" : "Next"} */}
          {saveInProgress ? 'Saving' :
          lastSaveStatus === false ? 'Save failed, log in and try again?' :
          activePage < pages.length -1 ? "Next" :
          lastSaveStatus && savedLastPage ? 'Saved' :
          'Save'}
        </Button>
        {
          pages.length > 1
            ? <Button
                type="submit"
                disabled={(activePage === 0 && !pendingSubmission) /* Don't disable until form submission started */
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
      </React.Fragment>
    :
      null
  );
};

export default withStyles(QuestionnaireStyle)(FormPagination);
