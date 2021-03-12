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
import LiveTable from "./LiveTable.jsx";
import SubjectType from "../questionnaire/SubjectType.jsx";

import { Button, Card, CardContent, CardHeader, Fab, Tooltip, withStyles } from "@material-ui/core";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import CreateSubjectTypeDialog from "../questionnaire/NewSubjectTypeDialog.jsx";
import NewItemButton from "../components/NewItemButton.jsx";
import DeleteButton from "./DeleteButton.jsx";
import EditButton from "./EditButton.jsx";
import AddIcon from "@material-ui/icons/Add";

function SubjectTypes(props) {
  const { classes } = props;
  const [ newSubjectTypePopperOpen, setNewSubjectTypePopperOpen ] = useState(false);
  const [ updateData, setUpdateData ] = useState(false);
  const [ subjectData, setSubjectData ] = useState([]);
  const [ editSubject, setEditSubject ] = useState(null);
  const [ isEdit, setIsEdit ] = useState(false);
  const columns = [
    {
      "key": "label",
      "label": "Label",
      "format": "string",
      "link": "dashboard+field:@path",
    },
    {
      "key": "jcr:created",
      "label": "Created on",
      "format": "date:YYYY-MM-DD HH:mm",
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
    {
      "key": "lfs:defaultOrder",
      "label": "Default Order",
      "format": "string",
    },
  ]
  const actions = [
    DeleteButton,
    EditButton
  ]

  // When the subject data is changed, set the update data flag to false
  useEffect(() => {
    if (subjectData){
      setUpdateData(false);
    }
  }, [subjectData]);

  useEffect(() => {
    setIsEdit(false);
    setEditSubject(null);
    const entry = /SubjectTypes\/(.+)/.exec(location.pathname);
    // for edit mode show dialog to allow to modify only the default order
    if (entry && entry[1].endsWith('.edit')) {
      let path = "/" + entry[0].replace('.edit', '');
      let subject = subjectData.find(subject => subject["@path"] === path);
      setIsEdit(true);
      setEditSubject(subject);
      setNewSubjectTypePopperOpen(true);
    }
  }, [location.pathname]);

  let subjectPath = /SubjectTypes\/(.+)/.exec(location.pathname);
  if (subjectPath && !subjectPath[1].endsWith('.edit')) {
    return <SubjectType id={subjectPath[1]} classes={classes}/>;
  }

  return (
  <>
    <Card>
      <CardHeader
        title={
          <Button className={classes.cardHeaderButton}>
            Subject Types
          </Button>
        }
      />
      <CardContent>
        <LiveTable
          columns={columns}
          actions={actions}
          entryType={"Subject Type"}
          admin={true}
          disableTopPagination={true}
          defaultSort={true}
          updateData={updateData}
        />
      </CardContent>
    </Card>
    <NewItemButton
      title="New subject type"
      onClick={() => { setNewSubjectTypePopperOpen(true); }}
    />
    { newSubjectTypePopperOpen && <CreateSubjectTypeDialog
      onClose={() => { setNewSubjectTypePopperOpen(false);}}
      onSubmit={() => { setNewSubjectTypePopperOpen(false); setUpdateData(true);}}
      open={newSubjectTypePopperOpen}
      subjects={subjectData}
      isEdit={isEdit}
      editSubject={editSubject}
    /> }
  </>
  );
}

export default withStyles(QuestionnaireStyle)(SubjectTypes);
