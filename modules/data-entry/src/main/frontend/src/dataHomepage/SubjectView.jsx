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
import { useState, useMemo, useContext } from "react";

import AssignmentIndIcon from '@mui/icons-material/AssignmentInd';
import LaunchIcon from '@mui/icons-material/Launch';
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
} from "@mui/material";
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import DeleteWithRefreshButton from "./DeleteWithRefreshButton.jsx";
import LiveTable from "./LiveTable.jsx";
import NewItemButton from "../components/NewItemButton.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";
import { NewSubjectDialog } from "../questionnaire/SubjectSelector.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

const useStyles = makeStyles()(theme => ({
  subjectView: {
    "& .MuiTabs-indicator": {
      background: theme.palette.secondary.main,
    },
  },
  subjectViewAvatar: {
    background: theme.palette.secondary.main,
  },
}));

function SubjectView(props) {
  const {
    expanded,
    actionSwitches,
    disableHeader,
    disableAvatar,
    topPagination,
    extension,
    extensionURL,
  } = props;

  const { classes } = useStyles();
  const [ newSubjectPopperOpen, setNewSubjectPopperOpen ] = useState(false);
  const [ activeTab, setActiveTab ] = useState(0);
  const [ subjectTypes, setSubjectTypes] = useState([]);
  const [ tabsLoading, setTabsLoading ] = useState(null);
  const [ columns, setColumns ] = useState(props.columns || null);
  const [ filtersJsonString, setFiltersJsonString ] = useState(new URLSearchParams(window.location.hash.substring(1)).get("subjects:filters"));
  const hasSubjects = tabsLoading === false && subjectTypes.length > 0;

  const activeExtensionURL = extension?.["cards:extensionURL"] || extensionURL || "";
  const baseURL = "../content.html" + (activeExtensionURL ? "/" + activeExtensionURL : "");

  const activeTabParam = new URLSearchParams(window.location.hash.substring(1)).get("subjects:activeTab");

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
      "format": "date:yyyy-MM-dd HH:mm",
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
  ]
  const actions = {
    "delete": DeleteWithRefreshButton
  }

  let isActionEnabled = (action) => (!!!actionSwitches || !!(actionSwitches[action]()));
  const enabledActions = useMemo(() =>
    Object.entries(actions).filter(entry => isActionEnabled(entry[0])).map(entry => entry[1])
  , [actionSwitches]);

  let fetchSubjectTypes = () => {
    let url = new URL("/query", window.location.origin);
    url.searchParams.set("query", `SELECT * FROM [cards:SubjectType] as n order by n.'cards:defaultOrder' option (index tag cards)`);
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

  if (tabsLoading === null) {
    fetchSubjectTypes();
    setTabsLoading(true);
  } else if (tabsLoading === false && !subjectTypes?.length) {
    return null;
  }

  return (
    <Card className={classes.subjectView}>
      {!disableHeader &&
      <CardHeader
        avatar={!disableAvatar && <Avatar className={classes.subjectViewAvatar}><AssignmentIndIcon/></Avatar>}
        title={
          tabsLoading
            ? <CircularProgress/>
            : subjectTypes.length < 1 ?
              <></>
              : <Tabs value={activeTab} onChange={(event, value) => setActiveTab(value)} indicatorColor="primary" textColor="inherit" >
                {subjectTypes.map((subject, index) => {
                  return <Tab
                    label={
                      <Typography variant="h6">
                        {subject['subjectListLabel'] || subject['label'] || subject['@name']}
                      </Typography>
                    }
                    key={"subject-" + index}
                  />;
                })}
              </Tabs>
        }
        action={
          !expanded && isActionEnabled("expand") &&
          <Tooltip title="Expand">
            <Link to={baseURL + "/Subjects#" + new URLSearchParams({ "subjects:activeTab" : subjectTypes?.[activeTab]?.['@name'] || "", "subjects:filters" : filtersJsonString || "" }).toString()} underline="hover">
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
        {
          hasSubjects
            ? <LiveTable
              columns={columns || defaultColumns}
              customUrl={'/Subjects.paginate?fieldnames=type&fieldcomparators=%3D&fieldvalues='+ encodeURIComponent(subjectTypes[activeTab]["jcr:uuid"])}
              defaultLimit={10}
              entryType="Subject"
              actions={enabledActions.length > 0 ? enabledActions : undefined}
              disableTopPagination={!topPagination}
              filters
              onFiltersChange={(str) => setFiltersJsonString(str)}
              filtersJsonString={filtersJsonString}
              extensionURL={activeExtensionURL}
            />
            : <Typography sx={{ pl: 1 }}>No results</Typography>
        }
      </CardContent>
      {expanded && isActionEnabled("create") &&
      <>
        <NewItemButton
          onClick={() => setNewSubjectPopperOpen(true)}
        />
        <NewSubjectDialog
          onClose={() => setNewSubjectPopperOpen(false)}
          onSubmit={() => setNewSubjectPopperOpen(false)}
          open={newSubjectPopperOpen}
          extensionURL={activeExtensionURL}
        />
      </>
      }
    </Card>
  );
}

export default SubjectView;
