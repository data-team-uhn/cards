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

import { IconButton, Tooltip } from "@mui/material";
import { Link } from 'react-router-dom';
import SubjectTypeDialog from "../questionnaire/SubjectTypeDialog.jsx";
import AdminResourceListing from "../adminDashboard/AdminResourceListing.jsx";
import DeleteButton from "./DeleteButton.jsx";
import EditIcon from "@mui/icons-material/Edit";

// Get a flat list of subject type parents as labels separated by " / "
function getTextHierarchy (path, subjectTypes) {
  let names = path.replace("/SubjectTypes/", "").split("/");
  let hierarchy = "";
  for (let name of names) {
    let subjectType = subjectTypes.find( item => item["@name"] === name );
    hierarchy = ( hierarchy ? hierarchy + " / " : "") + (subjectType?.label || name);
  }
  return hierarchy;
}

function EditSubjectTypeButton(props) {
  const { onClick } = props;
  return(
    <Tooltip title={"Edit Subject Type"}>
      <IconButton onClick={onClick} size="large">
        <EditIcon />
      </IconButton>
    </Tooltip>
  )
}

function SubjectTypes(props) {
  const [ dialogOpen, setDialogOpen ] = useState(false);
  const [ updateData, setUpdateData ] = useState(false);
  const [ subjectTypeData, setSubjectTypeData ] = useState([]);
  const [ currentSubjectType, setCurrentSubjectType ] = useState(null);
  const [ isEdit, setIsEdit ] = useState(false);
  const columns = [
    {
      "key": "",
      "label": "Subject Type",
      "format": (row) => (getTextHierarchy(row['@path'], subjectTypeData)),
    },
    {
      "key": "subjectListLabel",
      "label": "Subject list label",
      "format": "string",
    },
    {
      "key": "",
      "label": "Subjects",
      "format": (row) => (row.instanceCount ? <Link to={"/content.html/Subjects#" + row['@name']} title={"Show subjects of type " + row.label} underline="hover">{row.instanceCount}</Link> : "0"),
    },
    {
      "key": "cards:defaultOrder",
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
      "type": "actions",
      "format": (row) => (<>
                            <DeleteButton
                              entryPath={row["@path"]}
                              entryName={row.label}
                              onComplete={() => { setUpdateData(true); }}
                              entryType={"Subject Type"}
                              admin={true}
                            />
                            <EditSubjectTypeButton
                              onClick={() => {setIsEdit(true); setCurrentSubjectType(row); setDialogOpen(true);}}
                            />
                          </>),
    },
  ]

  // When the subject data is changed, set the update data flag to false
  useEffect(() => {
    if (subjectTypeData){
      setUpdateData(false);
    }
  }, [subjectTypeData]);

  let onClose = () => {
    setDialogOpen(false);
    setIsEdit(false);
    setCurrentSubjectType(null);
  }

  return (
  <>
    <AdminResourceListing
      title="Subject Types"
      buttonProps={{
        title: "New subject type",
        onClick: () => setDialogOpen(true)
      }}
      resourceSelectors=".instanceCount"
      columns={columns}
      entryType={"Subject Type"}
      admin={true}
      disableTopPagination={true}
      updateData={updateData}
      onDataReceived={setSubjectTypeData}
    />
    { dialogOpen &&
        <SubjectTypeDialog
          onClose={() => { onClose(); }}
          onSubmit={() => { onClose(); setUpdateData(true); }}
          open={dialogOpen}
          data={subjectTypeData}
          isEdit={isEdit}
          currentSubjectType={currentSubjectType}
        />
     }
  </>
  );
}

export default SubjectTypes;
