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
  withStyles
} from "@material-ui/core";
import { Link } from 'react-router-dom';
import DescriptionIcon from '@material-ui/icons/Description';
import MoreHorizIcon from '@material-ui/icons/MoreHoriz';
import DeleteButton from "./DeleteButton.jsx";
import EditButton from "./EditButton.jsx";
import NewFormDialog from "./NewFormDialog.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

function FormView(props) {
  const { questionnaire, expanded, disableHeader, disableAvatar, topPagination, classes } = props;
  
  const [ title, setTitle ] = useState(typeof(props.title) != 'undefined' ? props.title : "Forms");
  const [ subtitle, setSubtitle ] = useState();
  const [ questionnairePath, setQuestionnairePath ] = useState();
  const [ qFetchSent, setQFetchStatus ] = useState(false);

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
    DeleteButton,
    EditButton
  ]

  const tabs = ["Completed", "Draft"];
  const urlSearchParams = new URLSearchParams(window.location.search);
  const activeTabParam = urlSearchParams.get('activeTab');
  let activeTabIndex = 0
  if (activeTabParam && tabs.indexOf(activeTabParam) > 0) {
    activeTabIndex = tabs.indexOf(activeTabParam);
  }
  const [ activeTab, setActiveTab ] = useState(activeTabIndex);

  let qFilter = '';

  if (questionnaire) {
    // Set the questionnaire filter for displayed forms
    qFilter = '&fieldname=questionnaire&fieldvalue=' + encodeURIComponent(questionnaire);
    // Also fetch the title and other info if we haven't yet
    if (!qFetchSent) {
      setQFetchStatus(true);
      fetch('/query?query=' + encodeURIComponent(`select * from [lfs:Questionnaire] as n WHERE n.'jcr:uuid'='${questionnaire}'`))
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
      {(!expanded || !disableHeader && !disableAvatar && (title || questionnaire)) &&
      <CardHeader
        avatar={!disableAvatar && <Avatar className={classes.formViewAvatar}><DescriptionIcon/></Avatar>}
        title={
          <>
            <Typography variant="h6">{title}</Typography>
            <Typography variant="subtitle1">{subtitle}</Typography>
          </>
        }
        action={
          !expanded &&
          <Tooltip title="Expand">
            <Link to={"/content.html/Forms" + "?activeTab=" + tabs[activeTab]}>
              <IconButton>
                <MoreHorizIcon/>
              </IconButton>
            </Link>
          </Tooltip>
        }
      />
      }
      <Tabs value={activeTab} onChange={(event, value) => setActiveTab(value)}>
        {tabs.map((value, index) => {
          return <Tab label={value}  key={"form-" + index} />;
        })}
      </Tabs>
      <Divider />
      <CardContent>
        <LiveTable
          columns={props.columns || columns}
          customUrl={`/Forms.paginate?descending=true${qFilter}${activeTab == tabs.indexOf("Draft") ? '&fieldname=statusFlags&fieldvalue=INCOMPLETE' : ''}`}
          defaultLimit={10}
          joinChildren="lfs:Answer"
          filters
          entryType={"Form"}
          actions={actions}
          disableTopPagination={!topPagination}
        />
      {expanded &&
        <div className={classes.mainPageAction}>
          <NewFormDialog presetPath={questionnairePath}>
            New form
          </NewFormDialog>
        </div>
      }
      </CardContent>
    </Card>
  );
}

export default withStyles(QuestionnaireStyle)(FormView);
