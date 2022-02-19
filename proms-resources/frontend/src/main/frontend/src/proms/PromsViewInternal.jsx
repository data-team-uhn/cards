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
  const { color, title, avatar, query, columns, questionnaireId, filters, className } = props;

  const [ filtersJson, setFiltersJson ] = useState(filters);

  let dateFormat = filtersJson.date.dateFormat;
  let today = DateQuestionUtilities.getTodayDate(dateFormat);
  //today = DateQuestionUtilities.momentToString(today, filtersJson.date.type);
  let tomorrow = DateQuestionUtilities.getTomorrowDate(dateFormat);
  //tomorrow = DateQuestionUtilities.momentToString(tomorrow, filtersJson.date.type);

  const tabFilter = {
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
  };

  const tabs = Object.keys(tabFilter);

  const [ activeTab, setActiveTab ] = useState(1); // Today

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
          customUrl={`${query}${sortFilter}`}
          defaultLimit={10}
          questionnaire={questionnaireId}
          entryType={"Form"}
          disableTopPagination={true}
          filters
          filtersJsonString={filtersJsonString}
        />
      </CardContent>
    </Card>
  );
}

export default PromsViewInternal;
