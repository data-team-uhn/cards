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
import LiveTable from "./LiveTable.jsx";
import Questionnaire from "../questionnaire/Questionnaire.jsx";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle";
import NewQuestionnaireDialog from "../questionnaireEditor/NewQuestionnaireDialog.jsx";
import DeleteQuestionnaireDialog from "../questionnaireEditor/DeleteQuestionnaireDialog.jsx";
import { Button, Card, CardHeader, CardContent, Typography, withStyles } from "@material-ui/core";
import DeleteButton from "./DeleteButton.jsx";

function Questionnaires(props) {
  let [ openDialog, setOpenDialog ] = useState(false);
  let [ dataToDelete, setDataToDelete ] = useState(false);
  let [ error, setError ] = useState();
  let [ deletionCount, setDeletionCount ] = useState(0); // Used to force reupdates to the livetable after deletion
  const { classes } = props;
  const entry = /Questionnaires\/(.+)/.exec(location.pathname);

  let deleteQuestionnaire = (questionnaire) => {
    setDataToDelete(questionnaire);
    setOpenDialog(true);
  }

  // Increment the numebr of times we have deleted something, forcing the LiveTable to update
  let updateDeletionCount = () => {
    setDeletionCount((old) => (old+1));
  }

  if (entry) {
    return <Questionnaire id={entry[1]} key={location.pathname}/>;
  }

  let columns = [
    {
      "key": "title",
      "label": "Title",
      "format": "string",
      "link": "dashboard+path",
      "admin": true,
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
    {
      "key": "jcr:created",
      "label": "Created on",
      "format": "date:YYYY-MM-DD HH:mm",
    },
    {
      "key":"actions",
      "label":"Actions",
      "admin": true,
      // Align the actions to the end
      "props": {
        "style": {
          "textAlign": "end"
        }
      }
    }
  ]
  const actions = [
    DeleteButton
  ]

  return (
    <React.Fragment>
      <Card>
       <CardHeader
        title={
          <Button className={classes.cardHeaderButton}>
            Questionnaires
          </Button>
        }
        action={
          <NewQuestionnaireDialog presetpath={true}>
          </NewQuestionnaireDialog>
        }
        classes={{
          action: classes.newFormButtonHeader
        }}
        />
        <CardContent>
          <LiveTable
            columns={columns}
            delete={deleteQuestionnaire}
            updateData={deletionCount}
            actions={actions}
            entryType={"Questionnaire"}
            />
          { error &&
            <Typography color="error" variant="h3">
              {error}
            </Typography>
          }
        </CardContent>
      </Card>
      <DeleteQuestionnaireDialog
        open={openDialog}
        onClose={() => {setOpenDialog(false);}}
        onDelete={updateDeletionCount}
        data={dataToDelete}
        onError={setError}
        />
    </React.Fragment>
  );
}

export default withStyles(QuestionnaireStyle)(Questionnaires);
