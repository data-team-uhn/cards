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
  MobileStepper,
  withStyles,
} from "@material-ui/core";

import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";

/**
 * Component that displays an page of a Form.
 */
function FormPagination (props) {
  let { classes, lastPage, activePage, saveInProgress, lastSaveStatus, handlePageChange } = props;

  let [ savedLastPage, setSavedLastPage ] = useState(false);
  let [ pendingSubmission, setPendingSubmission ] = useState(false);

  let handleNext = () => {
    setPendingSubmission(true);
    if (activePage === lastPage()) {
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

  let saveButton =
    <Button
      type="submit"
      variant="contained"
      color="primary"
      disabled={saveInProgress}
      className={classes.paginationButton}
      onClick={handleNext}
    >
      {((lastPage() === 0 || activePage === lastPage()) && saveInProgress) ? 'Saving' :
      lastSaveStatus === false ? 'Save failed, log in and try again?' :
      activePage < lastPage() ? "Next" :
      lastSaveStatus && savedLastPage ? 'Saved' :
      'Save'}
    </Button>

  return (
    lastPage() > 0 ?
      <React.Fragment>
        <MobileStepper
          activeStep={activePage + 1}
          className={classes.formStepper}
          variant="progress"
          steps={lastPage() + 3}
          nextButton={saveButton}
          backButton={
            lastPage() > 0
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
        />
        {
        }
      </React.Fragment>
    :
      saveButton
  );
};

export default withStyles(QuestionnaireStyle)(FormPagination);
