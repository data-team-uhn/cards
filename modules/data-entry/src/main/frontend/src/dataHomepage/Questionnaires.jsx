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
import ExportButton from "./ExportButton.jsx";
import AdminResourceListing from "../adminDashboard/AdminResourceListing.jsx";
import DateTimeUtilities from "../components/DateTimeUtilities.jsx";
import FormattedText from "../components/FormattedText.jsx";
import NewQuestionnaireDialog from "../questionnaireEditor/NewQuestionnaireDialog.jsx";

function Questionnaires(props) {
  const [ questionnairesData, setQuestionnairesData ] = useState([]);
  const [ dialogOpen, setDialogOpen ] = useState(false);
  const [ updateData, setUpdateData ] = useState(0);

  const entryType = "Questionnaire";

  let columns = [
    {
      header: "Title",
      accessorKey: "title",
      Cell: ({ row }) => (<Link to={"../content.html/admin" + row.original["@path"]} underline="hover">{row.original.title}</Link>),
    },
    {
      header: "Created on",
      accessorKey: "jcr:created",
      Cell: ({ row }) => DateTimeUtilities.formatDateAnswer("yyyy-MM-dd HH:mm", row.original["jcr:created"]),
      sortingFn: 'datetime',
      size: 20,
    },
    {
      accessorKey: "description",
      header: "Description",
      enableSorting: false,
      Cell: ({ row }) => <FormattedText variant="caption">{row.original.description}</FormattedText>,
    },
  ]

  let makeActions = ({ row }) => {
    return (
            <Box sx={{ display: 'flex', flexWrap: 'nowrap', float: 'right'}}>
              <EditButton
                entryType={entryType}
                entryPath={row.original["@path"]}
                extensionURL="admin"
              />
              <ExportButton
                entryPath={row.original["@path"]}
                entryName={row.original.title}
                entryType={entryType}
                size="medium"
              />
              <DeleteButton
                entryPath={row.original["@path"]}
                entryName={row.original.title}
                onComplete={dialogSuccess}
                entryType={entryType}
              />
            </Box>
          )
  }

  let customFilterFn = (row, id, filterValue) => {
    let title = row.original.title || "";
    let description = row.original.description || "";
    return title.toLowerCase().includes(filterValue.toLowerCase()) || description.toLowerCase().includes(filterValue.toLowerCase());
  }

  let dialogClose = () => {
    setDialogOpen(false);
  }

  // If an entity was successfully added or deleted, trigger the table fetch
  let dialogSuccess = () => {
    setUpdateData((old) => (old+1));
  }

  return (
    <>
      <AdminResourceListing
        title="Questionnaires"
        buttonProps={{
          title: "New questionnaire",
          onClick: () => setDialogOpen(true)
        }}
        columns={columns}
        tableActions={makeActions}
        entryType={entryType}
        updateData={updateData}
        onDataReceived={setQuestionnairesData}
        customFilter={customFilterFn}
      />
      <NewQuestionnaireDialog
        open={dialogOpen}
        onClose={dialogClose}
        questionnaires={questionnairesData}
      />
    </>
  );
}

export default Questionnaires;
