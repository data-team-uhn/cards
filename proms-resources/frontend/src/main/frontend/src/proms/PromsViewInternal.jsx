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
  Typography,
  makeStyles,
} from "@material-ui/core";


const useStyles = color => makeStyles(theme => ({
  promsView : {
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
  promsViewAvatar: {
    background: color,
  },
  promsViewTitle: {
    fontWeight: "600",
    fontSize: "90%",
  },
}));


function PromsViewInternal (props) {
  const { color, title, avatar, query, dateField, columns, questionnaireId, className } = props;

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

  const tabFilter = {
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
  };

  const tabs = Object.keys(tabFilter);

  const [ activeTab, setActiveTab ] = useState(1); // Today

  let finalQuery = query.replaceAll("__DATE_FILTER_PLACEHOLDER__", tabFilter[tabs[activeTab]].dateFilter)
                        .replaceAll("__SORT_ORDER_PLACEHOLDER__", tabFilter[tabs[activeTab]].order);

  const classes = useStyles(color)();

  return (
    <Card className={classes.promsView + (className ? ` ${className}` : '')}>
      { title && <CardHeader
        disableTypography
        avatar={<Avatar className={classes.promsViewAvatar}>{avatar}</Avatar>}
        title={<Typography variant="overline" className={classes.promsViewTitle}>{title}</Typography>}
      /> }
      <Tabs value={activeTab} onChange={(event, value) => setActiveTab(value)}>
        { tabs.map((value, index) => <Tab label={value}  key={"form-" + questionnaireId + index} />) }
      </Tabs>
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

export default PromsViewInternal;
