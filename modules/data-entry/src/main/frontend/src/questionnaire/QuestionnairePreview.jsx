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

import React, { useEffect, useState } from "react";
import { useHistory } from 'react-router-dom';

import {
  CircularProgress,
  Grid
} from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import CloseIcon from "@mui/icons-material/Close";

import { FormProvider } from "./FormContext";
import { FormUpdateProvider } from "./FormUpdateContext";
import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";
import FormEntry, { ENTRY_TYPES } from "./FormEntry";
import FormPagination from "./FormPagination";
import MainActionButton from "../components/MainActionButton.jsx";
import { usePageNameWriterContext } from "../themePage/Page.jsx";

/**
 * Component that displays Questionnaire form preview.
 *
 * @param {object} data full Questionnaire data object
 */
function QuestionnairePreview (props) {
  let { classes, data, title } = props;

  let [ pages, setPages ] = useState(null);
  let [ contentOffsetTop, setContentOffsetTop ] = useState(props.contentOffset);
  let [ contentOffsetBottom, setContentOffsetBottom ] = useState(0);
  let paginationEnabled = !!data?.paginate;

  let pageNameWriter = usePageNameWriterContext();
  useEffect(() => {
    pageNameWriter(title);
  }, [title])

  useEffect(() => {
    setContentOffsetTop(props.contentOffset + (document?.getElementById('cards-resource-header')?.clientHeight || 0));
    paginationEnabled && setContentOffsetBottom(document?.getElementById('cards-resource-footer')?.clientHeight || 0);
  }, [pages])

  let history = useHistory();
  let close = () => {
    history.push(/((.*)\/Questionnaires)\/([^.]+)/.exec(location.pathname)[1]);
  }

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    return (
      <Grid container justifyContent="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  return (<div className={data?.hideAnswerInstructions ? classes.hideAnswerInstructions : null}>
    <Grid container {...FORM_ENTRY_CONTAINER_PROPS} >
      { /* Added dummy save functionality for mocking file and pedigree questions functionality. */ }
      <FormProvider additionalFormData={{
          ['/Save']: () => { return new Promise((resolve, reject) => {return;})},
          ['/URL']: data ? data["@path"] : '',
          ['/AllowResave']: () => {},
          ['/DisableUploads'] : true
          }}>
        <FormUpdateProvider>
        { pages &&
          Object.entries(data)
            .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
            .map(([key, entryDefinition]) => {
              let pageResult = pages[key];
              return <FormEntry
                key={key}
                entryDefinition={entryDefinition}
                path={"."}
                depth={0}
                existingAnswers={data}
                keyProp={key}
                classes={classes}
                onChange={()=>{}}
                visibleCallback={pageResult.callback}
                pageActive={pageResult.page.visible}
                isEdit={true}
                contentOffset={{top: contentOffsetTop, bottom: contentOffsetBottom}}
              />
            })
        }
        </FormUpdateProvider>
      </FormProvider>
      <Grid item xs={12} className={classes.formFooter} id="cards-resource-footer">
        <FormPagination
            variant={data?.paginationVariant}
            navMode={data?.paginationMode}
            enabled={paginationEnabled}
            questionnaireData={data}
            setPagesCallback={setPages}
            enableSave={false}
            onDone={close}
        />
      </Grid>
    </Grid>
    {!paginationEnabled &&
      <MainActionButton
        icon={<CloseIcon />}
        onClick={close}
        label="Close"
      />
    }
  </div>);
};

export default withStyles(QuestionnaireStyle)(QuestionnairePreview);
