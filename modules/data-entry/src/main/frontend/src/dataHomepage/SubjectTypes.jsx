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

import { Box } from "@mui/material";
import { Link } from 'react-router';

import DeleteButton from "./DeleteButton.jsx";
import EditButton from "./EditButton.jsx";
import AdminResourceListing from "../adminDashboard/AdminResourceListing.jsx";
import SubjectTypeDialog from "../questionnaire/SubjectTypeDialog.jsx";

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

function SubjectTypes(props) {
  const [ dialogOpen, setDialogOpen ] = useState(false);
  const [ updateData, setUpdateData ] = useState(0);
  const [ subjectTypeData, setSubjectTypeData ] = useState([]);
  const [ currentSubjectType, setCurrentSubjectType ] = useState(null);
  const [ isEdit, setIsEdit ] = useState(false);

  const entryType = "Subject Type";
  const columns = [
    {
      header: "Subject type",
      size: 100,
      accessorFn: (row) => getTextHierarchy(row['@path'], subjectTypeData),
      Cell: ({ row }) => (getTextHierarchy(row.original['@path'], subjectTypeData)),
    },
    {
      header: "Subject list label",
      accessorKey: 'subjectListLabel',
      size: 80,
    },
    {
      header: "Number of subjects",
      size: 20,
      accessorFn: (row) => row.instanceCount || 0,
      Cell: ({ row }) => (row.original.instanceCount ? <Link to={"../content.html/Subjects#" + row.original['@name']} title={"Show subjects of type " + row.original.label} underline="hover">{row.original.instanceCount}</Link> : "0"),
    },
    {
      header: "Order",
      accessorKey: 'cards:defaultOrder',
      size: 20,
    },
    {
      header: "Id pattern",
      accessorKey: 'idPattern',
      enableSorting: false,
    },
    {
      header: "Id pattern hint",
      accessorKey: 'idPatternHint',
      enableSorting: false,
    },
  ]

  let onClose = () => {
    setDialogOpen(false);
    setIsEdit(false);
    setCurrentSubjectType();
  }

  // If an entity was successfully added or deleted, trigger the table add new data, if passed on, or refresh
  let dialogSuccess = (newData) => {
    if (newData) {
      let addedEvent = new CustomEvent('MaterialTableAppend', {
        bubbles: true,
        cancelable: true,
        detail: newData
      });
      document.dispatchEvent(addedEvent);
    } else {
      setUpdateData((old) => (old+1));
    }
  }

  let makeActions = ({ row }) => (
            <Box sx={{ display: 'flex', flexWrap: 'nowrap', float: 'right'}}>
              <EditButton
                entryType={entryType}
                onClick={() => {setIsEdit(true); setCurrentSubjectType(row.original); setDialogOpen(true);}}
              />
              <DeleteButton
                entryPath={row.original["@path"]}
                entryName={row.original.label}
                onComplete={dialogSuccess}
                entryType={entryType}
              />
            </Box>
        )

  let customFilterFn = (row, id, filterValue) => {
    let path = row.original['@path'] || "";
    let label = row.original.subjectListLabel || "";
    return path.toLowerCase().includes(filterValue.toLowerCase()) || label.toLowerCase().includes(filterValue.toLowerCase());
  }

  return (
  <>
    <AdminResourceListing
      title="Subject Types"
      buttonProps={{
        title: "New subject type",
        onClick: () => setDialogOpen(true)
      }}
      columns={columns}
      tableActions={makeActions}
      entryType="SubjectType"
      disableTopPagination={true}
      updateData={updateData}
      onDataReceived={setSubjectTypeData}
      resourceSelectors=".instanceCount"
      customFilter={customFilterFn}
    />

    <SubjectTypeDialog
      open={dialogOpen}
      onClose={onClose}
      onSuccess={dialogSuccess}
      data={subjectTypeData}
      isEdit={isEdit}
      currentSubjectType={currentSubjectType}
    />
  </>
  );
}

export default SubjectTypes;
