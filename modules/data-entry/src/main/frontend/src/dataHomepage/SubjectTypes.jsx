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

import { Button, Card, CardContent, CardHeader, IconButton, Tooltip, withStyles } from "@material-ui/core";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import SubjectTypeDialog from "../questionnaire/SubjectTypeDialog.jsx";
import NewItemButton from "../components/NewItemButton.jsx";
import DeleteButton from "./DeleteButton.jsx";
import EditIcon from "@material-ui/icons/Edit";
import AddIcon from "@material-ui/icons/Add";

// Get a flat list of subject type parents as labels separated by " / "
function getTextHierarchy (path, subjects) {
  let names = path.replace("/SubjectTypes/", "").split("/");
  let hierarchy = "";
  for (let name of names) {
    let subject = subjects.find( item => item["@name"] === name );
    hierarchy = ( hierarchy ? hierarchy + " / " : "") + (subject?.label || name);
  }
  return hierarchy;
}

function EditSubjectTypeButton(props) {
  const { classes, onClick } = props;
  return(
    <Tooltip title={"Edit Subject Type"}>
      <IconButton className={classes.actionButton} onClick={onClick}>
        <EditIcon />
      </IconButton>
    </Tooltip>
  )
}

function SubjectTypes(props) {
  const { classes } = props;
  const [ newSubjectTypePopperOpen, setNewSubjectTypePopperOpen ] = useState(false);
  const [ updateData, setUpdateData ] = useState(false);
  const [ subjectData, setSubjectData ] = useState([]);
  const [ editSubject, setEditSubject ] = useState(null);
  const [ isEdit, setIsEdit ] = useState(false);
  const columns = [
    {
      "key": "",
      "label": "Subject Type",
      "format": (row) => (getTextHierarchy(row['@path'], subjectData)),
    },
    {
      "key": "",
      "label": "Subjects",
      "format": (row) => (<a href={"/content.html/Subjects#" + row['@name']} title={"Show subjects of type " + row.label} target="_blank">{row.subjectsNumber || 0}</a>),
    },
    {
      "key": "lfs:defaultOrder",
      "label": "Default Order",
      "format": "string",
    },
        {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
    {
      "key": "",
      "label": "Actions",
      "format": (row) => (<>
                            <DeleteButton
                              entryPath={row["@path"]}
                              entryName={row.label}
                              onComplete={() => { setUpdateData(true); }}
                              entryType={"Subject Type"}
                              buttonClass={classes.actionButton}
                              admin={true}
                            />
                            <EditSubjectTypeButton
                              onClick={() => {setIsEdit(true); setEditSubject(row); setNewSubjectTypePopperOpen(true);}}
                              classes={classes}
                            />
                          </>),
    },
  ]

  // When the subject data is changed, set the update data flag to false
  useEffect(() => {
    if (subjectData){
      setUpdateData(false);
    }
  }, [subjectData]);

  let onClose = () => {
    setNewSubjectTypePopperOpen(false);
    setIsEdit(false);
    setEditSubject(null);
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
          entryType={"Subject Type"}
          admin={true}
          disableTopPagination={true}
          updateData={updateData}
          onDataReceived={setSubjectData}
        />
      </CardContent>
    </Card>
    <NewItemButton
      title="New subject type"
      onClick={() => { setNewSubjectTypePopperOpen(true); }}
    />
    { newSubjectTypePopperOpen &&
        <SubjectTypeDialog
          onClose={() => { onClose(); }}
          onSubmit={() => { onClose(); setUpdateData(true); }}
          open={newSubjectTypePopperOpen}
          subjects={subjectData}
          isEdit={isEdit}
          editSubject={editSubject}
        />
     }
  </>
  );
}

export default withStyles(QuestionnaireStyle)(SubjectTypes);
