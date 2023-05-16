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
  Divider,
  IconButton,
  Tab,
  Tabs,
  Tooltip,
  Typography,
} from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import { Link } from 'react-router-dom';
import DescriptionIcon from '@mui/icons-material/Description';
import LaunchIcon from '@mui/icons-material/Launch';
import DeleteButton from "./DeleteButton.jsx";
import EditButton from "./EditButton.jsx";
import NewFormDialog from "./NewFormDialog.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

function FormView(props) {
  const { questionnaire, expanded, disableHeader, disableAvatar, topPagination, classes } = props;
  
  const [ title, setTitle ] = useState(props.title);
  const [ subtitle, setSubtitle ] = useState(props.subtitle);
  const [ questionnairePath, setQuestionnairePath ] = useState();
  const [ qFetchSent, setQFetchStatus ] = useState(false);
  const [ filtersJsonString, setFiltersJsonString ] = useState(new URLSearchParams(window.location.hash.substring(1)).get("forms:filters"));

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
      "format": "date:yyyy-MM-dd HH:mm",
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
  ]
  const actions = [
    DeleteButton,
    EditButton
  ]

  const tabFilter = {
    "Questionnaires" : '&includeallstatus=true',
    "Completed" : '',
    "Drafts" : '&fieldname=statusFlags&fieldvalue=INCOMPLETE',
  };
  const tabs = Object.keys(tabFilter);

  const activeTabParam = new URLSearchParams(window.location.hash.substring(1)).get("forms:activeTab");
  let activeTabIndex = Math.max(tabs.indexOf(activeTabParam), 0);
  const [ activeTab, setActiveTab ] = useState(activeTabIndex);

  let qFilter = '';

  if (questionnaire) {
    // Set the questionnaire filter for displayed forms
    qFilter = '&fieldname=questionnaire&fieldvalue=' + encodeURIComponent(questionnaire);
    // Also fetch the title and other info if we haven't yet
    if (!qFetchSent) {
      setQFetchStatus(true);
      fetch('/query?query=' + encodeURIComponent(`select * from [cards:Questionnaire] as n WHERE n.'jcr:uuid'='${questionnaire}'`))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let qData = json["rows"][0];
        if (qData) {
          setTitle(qData["title"]);
          setSubtitle(qData["description"]);
          setQuestionnairePath(qData["@path"]);
        }
      });
    }
  }

  return (
    <Card className={classes.formView}>
      {title &&
      <CardHeader
        title={
          <>
            {title && <Typography variant="h4">{title}</Typography>}
            {subtitle && <Typography variant="subtitle1">{subtitle}</Typography>}
          </>
        }
      />
      }
      {(!expanded || !disableHeader && !disableAvatar) &&
      <CardHeader
        avatar={!disableAvatar && <Avatar className={classes.formViewAvatar}><DescriptionIcon/></Avatar>}
        title={
          <>
            <Tabs value={activeTab} onChange={(event, value) => setActiveTab(value)} indicatorColor="primary" textColor="inherit" >
            { tabs.map((value, index) => {
              return <Tab label={<Typography variant="h6">{value}</Typography>}  key={"form-" + index} />;
            })}
            </Tabs>
          </>
        }
        action={
          !expanded &&
          <Tooltip title="Expand">
            <Link to={"/content.html/Forms#" + new URLSearchParams({"forms:activeTab" : tabs?.[activeTab] || "", "forms:filters" : filtersJsonString || ""}).toString()} underline="hover">
              <IconButton size="large">
                <LaunchIcon/>
              </IconButton>
            </Link>
          </Tooltip>
        }
      />
      }
      <Divider />
      <CardContent>
        <LiveTable
          columns={props.columns || columns}
          customUrl={`/Forms.paginate?descending=true${qFilter}${tabFilter[tabs[activeTab]]}`}
          defaultLimit={10}
          filters
          questionnaire={questionnaire}
          entryType="Form"
          actions={actions}
          disableTopPagination={!topPagination}
          onFiltersChange={(str) => { setFiltersJsonString(str); }}
          filtersJsonString={filtersJsonString}
        />
      {expanded &&
        <NewFormDialog presetPath={questionnairePath}>
          New questionnaire
        </NewFormDialog>
      }
      </CardContent>
    </Card>
  );
}

export default withStyles(QuestionnaireStyle)(FormView);
