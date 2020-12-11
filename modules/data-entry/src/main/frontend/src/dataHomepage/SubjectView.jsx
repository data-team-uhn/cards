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

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

import {
  Avatar,
  Card,
  CardContent,
  CardHeader,
  CircularProgress,
  Divider,
  IconButton,
  Tab,
  Tabs,
  Tooltip,
  Typography,
  withStyles
} from "@material-ui/core";
import { Link } from 'react-router-dom';
import AssignmentIndIcon from '@material-ui/icons/AssignmentInd';
import MoreHorizIcon from '@material-ui/icons/MoreHoriz';
import DeleteButton from "./DeleteButton.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

function SubjectView(props) {
  const { classes } = props;
  const [ activeTab, setActiveTab ] = useState(0);
  const [ subjectTypes, setSubjectTypes] = useState([])
  const [ tabsLoading, setTabsLoading ] = useState(null);
  const hasSubjects = tabsLoading === false && subjectTypes.length > 0;

  // Column configuration for the LiveTables
  const columns = [
    {
      "key": "@name",
      "label": "Identifier",
      "format": getEntityIdentifier,
      "link": "dashboard+path",
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
  ]
  const actions = [
    DeleteButton
  ]

  let fetchSubjectTypes = () => {
    let url = new URL("/query", window.location.origin);
    url.searchParams.set("query", `SELECT * FROM [lfs:SubjectType] as n order by n.'lfs:defaultOrder'`);
    // TODO: Handle pagination?
    url.searchParams.set("limit", 10);
    url.searchParams.set("offset", 0);
    return fetch(url)
      .then(response => response.json())
      .then(result => {
        setSubjectTypes(result.rows);
        setTabsLoading(false);
      })
  }

  if (tabsLoading == null) {
    fetchSubjectTypes();
    setTabsLoading(true);
  }

  let path;
  if (hasSubjects) {
    path = subjectTypes[activeTab]['@path'];
  }

  return (
    <Card className={classes.subjectView}>
      <CardHeader
        avatar={<Avatar className={classes.subjectViewAvatar}><AssignmentIndIcon/></Avatar>}
        title={<Typography variant="h6">Subjects</Typography>}
        action={
          <Tooltip title="Expand">
            <Link to={"/content.html/Subjects"}>
              <IconButton>
                <MoreHorizIcon/>
              </IconButton>
            </Link>
          </Tooltip>
        }
      />
      {
        tabsLoading
          ? <CircularProgress/>
          : <Tabs value={activeTab} onChange={(event, value) => setActiveTab(value)}>
              {subjectTypes.map((subject, index) => {
                return <Tab label={subject['label'] || subject['@name']} key={"subject-" + index}/>;
              })}
            </Tabs>
      }
      <Divider />
      <CardContent>
      {
        hasSubjects
          ? <LiveTable
              columns={columns}
              customUrl={'/Subjects.paginate?fieldname=type&fieldvalue='+ encodeURIComponent(subjectTypes[activeTab]["jcr:uuid"])}
              defaultLimit={10}
              entryType={"Subject"}
              actions={actions}
              disableTopPagination
            />
          : <Typography>No results</Typography>
      }
      </CardContent>
    </Card>
  );
}

export default withStyles(QuestionnaireStyle)(SubjectView);
