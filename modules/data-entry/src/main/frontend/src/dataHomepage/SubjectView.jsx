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
import React, { useState, useContext } from "react";
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
  Fab,
  Tab,
  Tabs,
  Tooltip,
  Typography,
  withStyles
} from "@material-ui/core";
import { Link } from 'react-router-dom';
import AssignmentIndIcon from '@material-ui/icons/AssignmentInd';
import LaunchIcon from '@material-ui/icons/Launch';
import AddIcon from '@material-ui/icons/Add';
import DeleteButton from "./DeleteButton.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import NewItemButton from "../components/NewItemButton.jsx";
import { NewSubjectDialog } from "../questionnaire/SubjectSelector.jsx";

function SubjectView(props) {
  const { expanded, disableHeader, disableAvatar, topPagination, classes } = props;
  const [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(false);
  const [ activeTab, setActiveTab ] = useState(0);
  const [ subjectTypes, setSubjectTypes] = useState([])
  const [ tabsLoading, setTabsLoading ] = useState(null);
  const [ columns, setColumns ] = React.useState(props.columns || null);
  const hasSubjects = tabsLoading === false && subjectTypes.length > 0;

  const activeTabParam = location.hash.substr(1);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // Default column configuration for the LiveTables to be used from User Dashboard
  const defaultColumns = [
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
    url.searchParams.set("query", `SELECT * FROM [cards:SubjectType] as n order by n.'cards:defaultOrder'`);
    return fetchWithReLogin(globalLoginDisplay, url)
      .then(response => response.json())
      .then(result => {
        let optionTypes = Array.from(result.rows);
        if (columns && optionTypes.length <= 1) {
          let result = columns.slice();
          result.splice(1, 2);
          setColumns(result);
        }
        setSubjectTypes(optionTypes);
        setTabsLoading(false);

        if (activeTabParam && result.rows.length > 0) {
          let activeTabIndex = result.rows.indexOf(result.rows.find(element => element["@name"] === activeTabParam));
          activeTabIndex > 0 && setActiveTab(activeTabIndex);
        }
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
      {!disableHeader &&
      <CardHeader
        avatar={!disableAvatar && <Avatar className={classes.subjectViewAvatar}><AssignmentIndIcon/></Avatar>}
        title={<Typography variant="h6">Subjects</Typography>}
        action={
          !expanded &&
          <Tooltip title="Expand">
            <Link to={"/content.html/Subjects#" + ( subjectTypes.length > 0 ? subjectTypes[activeTab]['@name'] : '' )}>
              <IconButton>
                <LaunchIcon/>
              </IconButton>
            </Link>
          </Tooltip>
        }
      />
      }
      {
        tabsLoading
          ? <CircularProgress/>
          : subjectTypes.length <= 1 ?
          <></>
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
              columns={columns || defaultColumns}
              customUrl={'/Subjects.paginate?fieldname=type&fieldvalue='+ encodeURIComponent(subjectTypes[activeTab]["jcr:uuid"])}
              defaultLimit={10}
              entryType={"Subject"}
              actions={actions}
              disableTopPagination={!topPagination}
            />
          : <Typography>No results</Typography>
      }
      </CardContent>
      {expanded &&
      <>
        <NewItemButton
           title="New subject"
           onClick={() => {setNewSubjectPopperOpen(true)}}
        />
        <NewSubjectDialog
          onClose={() => { setNewSubjectPopperOpen(false);}}
          onSubmit={() => { setNewSubjectPopperOpen(false);}}
          openNewSubject={true}
          open={newSubjectPopperOpen}
        />
      </>
      }
    </Card>
  );
}

export default withStyles(QuestionnaireStyle)(SubjectView);
