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
import LiveTable from "../dataHomepage/LiveTable.jsx";
import DateQuestionUtilities from "../questionnaire/DateQuestionUtilities";

import {
  Avatar,
  Card,
  CardContent,
  CardHeader,
  Divider,
  Tab,
  Tabs,
  Typography
} from "@mui/material";


import makeStyles from '@mui/styles/makeStyles';


const useStyles = color => makeStyles(theme => ({
  clinicFormList : {
    border: "1px solid " + theme.palette.divider,
    "& .MuiTab-root": {
      paddingBottom: theme.spacing(1),
      textTransform: "none",
      fontWeight: "300",
    },
    "& .MuiTab-root.Mui-selected" : {
      color: color,
    },
    "& .MuiTabs-indicator": {
      background: color,
    },
  },
  clinicFormListAvatar: {
    background: color,
  },
  clinicFormListTitle: {
    fontWeight: "600",
    fontSize: "90%",
  },
}));


function ClinicFormList (props) {
  const { color, title, avatar, query, columns, questionnairePath, filters, enableTimeTabs, className } = props;

  const [ filtersJson, setFiltersJson ] = useState(filters);

  let dateFormat = filtersJson.date.dateFormat;
  let today = DateQuestionUtilities.getTodayDate(dateFormat);
  let tomorrow = DateQuestionUtilities.getTomorrowDate(dateFormat);

  const timeFilter = enableTimeTabs ? {
    "Past" : {
      comparator: "<",
      dateFilter: today,
      order: "&descending=true"
    },
    "Today" : {
      comparator: [">=", "<"],
      dateFilter: [today, tomorrow],
      order: ""
    },
    "Upcoming" : {
      comparator: ">=",
      dateFilter: tomorrow,
      order: ""
    },
  } : {
    "All" : {
      dateFilter :  `${dateField}.value IS NOT NULL`,
      order: "desc"
    },
  };

  const tabs = Object.keys(timeFilter);

  const [ activeTab, setActiveTab ] = useState(enableTimeTabs ? 1 : 0); // Today if time tabs enabled

  let sortFilter = tabFilter[tabs[activeTab]].order;
  let filtersJsonString = "";
  // need to have 2 filters for Today date filtering
  if (tabs[activeTab] == "Today") {
    filtersJson.date.comparator = tabFilter[tabs[activeTab]].comparator[0];
    filtersJson.date.value = tabFilter[tabs[activeTab]].dateFilter[0];
    filtersJson.enddate = Object.assign({}, filtersJson.date);
    filtersJson.enddate.comparator = tabFilter[tabs[activeTab]].comparator[1];
    filtersJson.enddate.value = tabFilter[tabs[activeTab]].dateFilter[1];
    filtersJsonString = window.btoa(JSON.stringify(Object.values(filtersJson)));
  } else {
    delete filtersJson.enddate;
    filtersJson.date.comparator = tabFilter[tabs[activeTab]].comparator;
    filtersJson.date.value = tabFilter[tabs[activeTab]].dateFilter;
    filtersJsonString = window.btoa(JSON.stringify(Object.values(filtersJson)));
  }

  const classes = useStyles(color)();

  return (
    <Card className={classes.clinicFormList + (className ? ` ${className}` : '')}>
      { title && <CardHeader
        disableTypography
        avatar={<Avatar className={classes.clinicFormListAvatar}>{avatar}</Avatar>}
        title={<Typography variant="overline" className={classes.clinicFormListTitle}>{title}</Typography>}
      /> }
      { enableTimeTabs &&
      <Tabs value={activeTab} onChange={(event, value) => setActiveTab(value)}>
        { tabs.map((value, index) => <Tab label={value}  key={"form-" + questionnairePath + index} />) }
      </Tabs>}
      <Divider />
      <CardContent>
        <LiveTable
          columns={columns}
          customUrl={`${query}${sortFilter}`}
          defaultLimit={10}
          questionnaire={questionnairePath}
          entryType={"Form"}
          disableTopPagination={true}
          filters
          filtersJsonString={filtersJsonString}
        />
      </CardContent>
    </Card>
  );
}

export default ClinicFormList;
