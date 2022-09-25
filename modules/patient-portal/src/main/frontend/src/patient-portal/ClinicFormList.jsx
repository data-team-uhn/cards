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
  const { color, title, avatar, query, dateField, columns, questionnaireId, enableTimeTabs, className } = props;

  let toMidnight = (date) => {
     date.setHours(0);
     date.setMinutes(0);
     date.setSeconds(0);
     date.setMilliseconds(0);
     return date;
  }

  let today = new Date(), tomorrow = new Date();
  tomorrow.setDate(today.getDate() + 1);
  today = toMidnight(today).toISOString();
  tomorrow = toMidnight(tomorrow).toISOString();

  const timeFilter = enableTimeTabs ? {
    "Past" : {
      dateFilter :  `${dateField}.value < '${today}' `,
      order : "desc"
    },
    "Today" : {
      dateFilter :  `${dateField}.value >= '${today}' and ${dateField}.value < '${tomorrow}' `,
      order: ""
    },
    "Upcoming" : {
      dateFilter :  `${dateField}.value >= '${tomorrow}' `,
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

  let finalQuery = query.replaceAll("__DATE_FILTER_PLACEHOLDER__", timeFilter[tabs[activeTab]].dateFilter)
                        .replaceAll("__SORT_ORDER_PLACEHOLDER__", timeFilter[tabs[activeTab]].order);

  const classes = useStyles(color)();

  return (
    <Card className={classes.clinicFormList + (className ? ` ${className}` : '')}>
      { title && <CardHeader
        disableTypography
        avatar={<Avatar className={classes.clinicFormListAvatar}>{avatar}</Avatar>}
        title={<Typography variant="overline" className={classes.clinicFormListTitle}>{title}</Typography>}
      /> }
      { enableTimeTabs &&
      <Tabs value={activeTab} onChange={(event, value) => setActiveTab(value)} indicatorColor="primary" textColor="inherit" >
        { tabs.map((value, index) => <Tab label={value}  key={"form-" + questionnaireId + index} />) }
      </Tabs> }
      <Divider />
      <CardContent>
        <LiveTable
          columns={columns}
          customUrl={'/query?query=' + encodeURIComponent(finalQuery)}
          defaultLimit={10}
          questionnaire={questionnaireId}
          entryType={"Form"}
          disableTopPagination={true}
        />
      </CardContent>
    </Card>
  );
}

export default ClinicFormList;
