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
import React from "react";

import makeStyles from '@mui/styles/makeStyles';
import { Link } from 'react-router-dom';
import EventIcon from '@mui/icons-material/Event';

import ClinicFormList from "./ClinicFormList.jsx";

const useStyles = color => makeStyles(theme => ({
  visitList : {
    "&.MuiCard-root" : {
       border: "2px solid " + color,
    },
  },
  statusUnassigned : {
    color: theme.palette.text.secondary,
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


function ClinicVisits(props) {
  const { clinicId, color, visitInfo, dashboardConfig } = props;

  const classes = useStyles(color)();

  let query = (
"select distinct visitInformation.* " +
  "from " +
    "[cards:Form] as visitInformation " +
      "inner join [cards:ResourceAnswer] as visitClinic on visitClinic.form = visitInformation.[jcr:uuid] " +
      "inner join [cards:DateAnswer] as visitDate on visitDate.form = visitInformation.[jcr:uuid] " +
      "inner join [cards:TextAnswer] as visitStatus on visitStatus.form = visitInformation.[jcr:uuid] " +
  "where " +
    `visitInformation.questionnaire = '${visitInfo?.["jcr:uuid"]}' ` +
      `and visitDate.question = '${visitInfo?.time?.["jcr:uuid"]}' and __DATE_FILTER_PLACEHOLDER__ ` +
      `and visitClinic.question = '${visitInfo?.clinic?.["jcr:uuid"]}' and visitClinic.value = '/Survey/ClinicMapping/${clinicId}' ` +
      `and visitStatus.question = '${visitInfo?.status?.["jcr:uuid"]}' and visitStatus.value <> 'cancelled' and visitStatus.value <> 'entered-in-error' ` +
  "order by visitDate.value __SORT_ORDER_PLACEHOLDER__"
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
      "label": dashboardConfig?.eventTimeLabel,
      "format": "date:yyyy-MM-dd HH:mm"
    },
    {
      "key": "email_sent",
      "label": "Email sent",
      "format" : (row) => {
         let email_date = row.reminder2_sent || row.reminder1_sent || row.invitation_sent;
         let label = row.reminder2_sent || row.reminder1_sent ? "(reminder)" : row.invitation_sent ? "(initial)" : "";
         return email_date ? `${email_date.substring(0, 10)} ${label}` : "N/A";
      }
    },
    {
      "key" : "status",
      "label" : "Survey completion",
      "format" : (row) => (
         <Link
           className={classes["status" + (!row.has_surveys ? "Unassigned" : (!row.surveys_complete ? "Incomplete" : (!row.surveys_submitted ? "Unreviewed" : "Completed")))]}
           to={`/content.html${row.subject['@path']}`}
           underline="hover">
           { row.email_unsubscribed ? "Unsubscribed" : (!row.has_surveys ? "No surveys assigned" : (!row.surveys_complete ? "Incomplete" : (!row.surveys_submitted ? "Pending submission" : "Completed"))) }
         </Link>
      )
    }
  ];


  return (
    <ClinicFormList
      className={classes.visitList}
      color={color}
      avatar={<EventIcon />}
      title={dashboardConfig?.eventsLabel}
      columns={columns}
      query={query}
      dateField="visitDate"
      questionnaireId={visitInfo?.["jcr:uuid"]}
      enableTimeTabs={dashboardConfig?.enableTimeTabs}
    />
  );
}

export default ClinicVisits;
