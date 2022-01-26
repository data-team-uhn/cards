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
import DescriptionIcon from '@material-ui/icons/Description';
import LaunchIcon from '@material-ui/icons/Launch';

import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

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


function PromsView(props) {
  const { data, color, visitInfo } = props;

  const classes = useStyles(color)();

  const [ columns, setColumns ] = useState();
  const [ questionnaireId, setQuestionnaireId ] = useState();

  const [ title, setTitle ] = useState(props.title);
  const [ subtitle, setSubtitle ] = useState(props.subtitle);
  const [ questionnairePath, setQuestionnairePath ] = useState();
  const [ acronym, setAcronym ] = useState();
  const [ qFetchSent, setQFetchStatus ] = useState(false);

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

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const [ activeTab, setActiveTab ] = useState(1); // Today

  // At startup, load questionnaire
  useEffect(() => {
    if (data && !columns) {
      fetchWithReLogin(globalLoginDisplay, data + ".deep.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setColumns(JSON.parse(json["view"] || "[]"));
        setQuestionnaireId(json.questionnaire?.["jcr:uuid"] || "");
        setTitle(json.questionnaire?.["title"]);
        setSubtitle(json.questionnaire?.["description"]);
        setQuestionnairePath(json.questionnaire?.["@path"]);
        setAcronym(json.questionnaire?.["@name"]);
      });
    }
  }, [data]);

  let query = (
"select distinct dataForm.* " +
  "from " +
    "[cards:Subject] as visitSubject " +
    "inner join [cards:Form] as visitInformation on visitSubject.[jcr:uuid] = visitInformation.subject " +
      "inner join [cards:Answer] as visitDate on isdescendantnode(visitDate, visitInformation) " +
      "inner join [cards:Answer] as patientSubmitted on isdescendantnode(patientSubmitted, visitInformation) " +
    "inner join [cards:Form] as dataForm on visitSubject.[jcr:uuid] = dataForm.subject " +
  "where " +
    `visitInformation.questionnaire = '${visitInfo?.["jcr:uuid"]}' ` +
      `and visitDate.question = '${visitInfo?.time?.["jcr:uuid"]}' and ` + tabFilter[tabs[activeTab]].dateFilter + " " +
      `and patientSubmitted.question = '${visitInfo?.surveys_submitted?.["jcr:uuid"]}' and patientSubmitted.value = 1 ` +
    `and dataForm.questionnaire = '${questionnaireId}' ` +
  "order by visitDate.value " + tabFilter[tabs[activeTab]].order
  )

  return (
    <Card className={classes.promsView}>
      {title &&
      <CardHeader
        disableTypography
        avatar={<Avatar className={classes.promsViewAvatar}><Typography variant="caption">{acronym?.substring(0,4)}</Typography></Avatar>}
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
          filters
          questionnaire={questionnaireId}
          entryType={"Form"}
          disableTopPagination={true}
        />
      </CardContent>
    </Card>
  );
}

export default PromsView;
