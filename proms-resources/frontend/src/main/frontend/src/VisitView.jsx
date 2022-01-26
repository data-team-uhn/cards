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
import React, { useState, useEffect, useContext } from "react";

import questionnaireStyle from "./questionnaire/QuestionnaireStyle.jsx";
import LiveTable from "./dataHomepage/LiveTable.jsx";

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
  makeStyles,
  withStyles
} from "@material-ui/core";
import { Link } from 'react-router-dom';
import EventIcon from '@material-ui/icons/Event';

const useStyles = color => makeStyles(theme => ({
  promsView : {
    border: "2px solid " + color,
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
  statusIncomplete : {
    color: theme.palette.error.main,
  },
  statusUnreviewed : {
    color: theme.palette.warning.main,
  },
  statusCompleted : {
    color: theme.palette.success.main,
  },
}));


function VisitView(props) {
  const { surveysId, color, visitInfo } = props;

  const classes = useStyles(color)();

  const [ title, setTitle ] = useState("Appointments");

  let toMidnight = (date) => {
     date.setHours(0);
     date.setMinutes(0);
     date.setSeconds(0);
     return date;
  }

  let today = new Date(), tomorrow = new Date();
  tomorrow.setDate(today.getDate() + 1);
  today = toMidnight(today).toISOString();
  tomorrow = toMidnight(tomorrow).toISOString();

  const tabFilter = {
    "Past" : {
      dateFilter :  `visitDate.value < '${today}' `,
      order : "desc"
    },
    "Today" : {
      dateFilter :  `visitDate.value >= '${today}' and visitDate.value < '${tomorrow}' `,
      order: ""
    },
    "Upcoming" : {
      dateFilter :  `visitDate.value >= '${tomorrow}' `,
      order: ""
    },
  };

  const tabs = Object.keys(tabFilter);

  const [ activeTab, setActiveTab ] = useState(1); // Today

  let query = (
"select distinct visitInformation.* " +
  "from " +
    "[cards:Form] as visitInformation " +
      "inner join [cards:Answer] as visitSurveys on isdescendantnode(visitSurveys, visitInformation) " +
      "inner join [cards:Answer] as visitDate on isdescendantnode(visitDate, visitInformation) " +
  "where " +
    `visitInformation.questionnaire = '${visitInfo?.["jcr:uuid"]}' ` +
      `and visitDate.question = '${visitInfo?.time?.["jcr:uuid"]}' and ` + tabFilter[tabs[activeTab]].dateFilter + " " +
      `and visitSurveys.question = '${visitInfo?.surveys?.["jcr:uuid"]}' and visitSurveys.value = '${surveysId}' ` +
  "order by visitDate.value " + tabFilter[tabs[activeTab]].order
)

  let columns = [
    {
      "key": "last_name",
      "label": "Last name",
      "format": "string"
    },
    {
      "key": "first_name",
      "label": "First name",
      "format": "string"
    },
    {
      "key": "mrn",
      "label": "MRN",
      "format": "string"
    },
    {
      "key": "time",
      "label": "Visit time",
      "format": "date:YYYY-MM-DD hh:mm"
    },
    {
      "key" : "status",
      "label" : "Status",
      "format" : (row) => (
         <Link
           className={classes["status" + (!row.surveys_complete ? "Incomplete" : (!row.surveys_submitted ? "Unreviewed" : "Completed"))]}
           to={`/content.html/${row.subject['@path']}`}>
           { !row.surveys_complete ? "Incomplete" : (!row.surveys_submitted ? "Pending submission" : "Completed") }
         </Link>
      )
    }
  ];


  return (
    <Card className={classes.promsView}>
      {title &&
      <CardHeader
        disableTypography
        avatar={<Avatar className={classes.promsViewAvatar}><EventIcon /></Avatar>}
        title={<Typography variant="overline" className={classes.promsViewTitle}>{title}</Typography>}
      />
      }
      <Tabs value={activeTab} onChange={(event, value) => setActiveTab(value)}>
            { tabs.map((value, index) => {
              return <Tab label={value}  key={"form-" + index} />;
            })}
      </Tabs>
      <Divider />
      <CardContent>
        <LiveTable
          columns={columns}
          customUrl={'/query?query=' + encodeURIComponent(query)}
          defaultLimit={10}
          questionnaire={visitInfo?.["jcr:uuid"]}
          entryType={"Form"}
          disableTopPagination={true}
        />
      </CardContent>
    </Card>
  );
}

export default VisitView;
