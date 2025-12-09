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
import { useState, useEffect } from "react";

import DescriptionIcon from '@mui/icons-material/Description';
import LaunchIcon from '@mui/icons-material/Launch';
import {
  Avatar,
  Card,
  CardContent,
  CardHeader,
  Divider,
  IconButton,
  LinearProgress,
  Tab,
  Tabs,
  Tooltip,
  Typography,
} from "@mui/material";
import { Link } from 'react-router';
import { withStyles } from 'tss-react/mui';

import DeleteButton from "./DeleteButton.jsx";
import EditButton from "./EditButton.jsx";
import LiveTable from "./LiveTable.jsx";
import NewFormDialog from "./NewFormDialog.jsx";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

function FormView(props) {
  const { extension, actionSwitches, questionnaire, expanded, disableHeader, disableAvatar, topPagination, classes } = props;

  const [ title, setTitle ] = useState(props.title);
  const [ subtitle, setSubtitle ] = useState(props.subtitle);
  const [ qFilter, setQFilter ] = useState();
  const [ filtersJsonString, setFiltersJsonString ] = useState(new URLSearchParams(window.location.hash.substring(1)).get("forms:filters"));

  const extensionURL = extension?.["cards:extensionURL"] || props.extensionURL || ""
  const baseURL = "../content.html" + (extensionURL ? "/" + extensionURL : "");

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
  const actions = {
    "edit": EditButton,
    "delete": DeleteButton   
  }
  const [ enabledActions, setEnabledActions ] = useState(actions);
  let isActionEnabled = (action) => (!!!actionSwitches || !!(actionSwitches[action]()));
  useEffect(() => {
    setEnabledActions(Object.entries(actions).filter(entry => isActionEnabled(entry[0])).map(entry => entry[1]));
  }, [actionSwitches])

  const tabFilter = {
    "Questionnaires" : '&includeallstatus=true',
    "Completed" : '',
    "Drafts" : '&fieldnames=statusFlags&fieldvalues=INCOMPLETE&fieldcomparators=%3D',
  };
  const tabs = Object.keys(tabFilter);

  const activeTabParam = new URLSearchParams(window.location.hash.substring(1)).get("forms:activeTab");
  let activeTabIndex = Math.max(tabs.indexOf(activeTabParam), 0);
  const [ activeTab, setActiveTab ] = useState(activeTabIndex);

  useEffect (() => {
    // If a questionnaire parameter is specified:
    if (questionnaire) {
      // Fetch the questionnaire info and update the title, subtitle and query filter
      fetch(`${questionnaire}.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then(qData => {
          setTitle(qData["title"]);
          setSubtitle(qData["description"]);
          setQFilter('&fieldnames=questionnaire&fieldcomparators=%3D&fieldvalues=' + encodeURIComponent(qData["jcr:uuid"]));
        })
        .catch(err => setQFilter(''));
    } else {
      // No filtering by questionnaire
      setQFilter('');
    }
  }, [questionnaire]);

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
          !expanded && isActionEnabled("expand") &&
          <Tooltip title="Expand">
            <Link to={baseURL + "/Forms#" + new URLSearchParams({ "forms:activeTab" : tabs?.[activeTab] || "", "forms:filters" : filtersJsonString || "" }).toString()} underline="hover">
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
        { typeof(qFilter) == "undefined" ? <LinearProgress /> :
          <LiveTable
            columns={props.columns || columns}
            customUrl={`/Forms.paginate?descending=true${qFilter}${tabFilter[tabs[activeTab]]}`}
            defaultLimit={10}
            filters
            questionnaire={questionnaire}
            entryType="Form"
            actions={enabledActions.length > 0 ? enabledActions : undefined}
            disableTopPagination={!topPagination}
            onFiltersChange={(str) => { setFiltersJsonString(str); }}
            filtersJsonString={filtersJsonString}
            extensionURL={extensionURL}
          />
        }
        { expanded && isActionEnabled("create") &&
        <NewFormDialog
          presetPath={questionnaire}
          withButton
          buttonTitle="New questionnaire"
          extensionURL={extensionURL}
        />
        }
      </CardContent>
    </Card>
  );
}

export default withStyles(FormView, QuestionnaireStyle);
