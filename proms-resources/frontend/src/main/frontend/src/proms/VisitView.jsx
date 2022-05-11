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

import { styled } from '@mui/material/styles';

import {
  makeStyles,
} from "@material-ui/core";
import { Link } from 'react-router-dom';
import EventIcon from '@material-ui/icons/Event';

import PromsViewInternal from "./PromsViewInternal.jsx";

const PREFIX = 'VisitView';

const classes = {
  visitView: `${PREFIX}-visitView`,
  statusUnassigned: `${PREFIX}-statusUnassigned`,
  statusIncomplete: `${PREFIX}-statusIncomplete`,
  statusUnreviewed: `${PREFIX}-statusUnreviewed`,
  statusCompleted: `${PREFIX}-statusCompleted`
};

const StyledPromsViewInternal
 = styled(PromsViewInternal
)((
  {
    theme
  }
) => ({
  [`& .${classes.visitView}`]: {
    "&.MuiCard-root" : {
       border: "2px solid " + color,
    },
  },

  [`& .${classes.statusUnassigned}`]: {
    color: theme.palette.text.secondary,
  },

  [`& .${classes.statusIncomplete}`]: {
    color: theme.palette.error.main,
  },

  [`& .${classes.statusUnreviewed}`]: {
    color: theme.palette.warning.main,
  },

  [`& .${classes.statusCompleted}`]: {
    color: theme.palette.success.main,
  }
}));


function VisitView(props) {
  const { surveysId, color, visitInfo } = props;



  let query = (
"select distinct visitInformation.* " +
  "from " +
    "[cards:Form] as visitInformation " +
      "inner join [cards:TextAnswer] as visitSurveys on isdescendantnode(visitSurveys, visitInformation) " +
      "inner join [cards:DateAnswer] as visitDate on isdescendantnode(visitDate, visitInformation) " +
      "inner join [cards:TextAnswer] as visitStatus on isdescendantnode(visitStatus, visitInformation) " +
  "where " +
    `visitInformation.questionnaire = '${visitInfo?.["jcr:uuid"]}' ` +
      `and visitDate.question = '${visitInfo?.time?.["jcr:uuid"]}' and __DATE_FILTER_PLACEHOLDER__ ` +
      `and visitSurveys.question = '${visitInfo?.surveys?.["jcr:uuid"]}' and visitSurveys.value = '${surveysId}' ` +
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
      "label": "Visit time",
      "format": "date:YYYY-MM-DD hh:mm"
    },
    {
      "key" : "status",
      "label" : "Survey completion",
      "format" : (row) => (
         <Link
           className={classes["status" + (!row.has_surveys ? "Unassigned" : (!row.surveys_complete ? "Incomplete" : (!row.surveys_submitted ? "Unreviewed" : "Completed")))]}
           to={`/content.html${row.subject['@path']}`}>
           { row.email_unsubscribed ? "Unsubscribed" : (!row.has_surveys ? "No surveys assigned" : (!row.surveys_complete ? "Incomplete" : (!row.surveys_submitted ? "Pending submission" : "Completed"))) }
         </Link>
      )
    }
  ];


  return (
    <PromsViewInternal
      className={classes.visitView}
      color={color}
      avatar={<EventIcon />}
      title="Appointments"
      columns={columns}
      query={query}
      dateField="visitDate"
      questionnaireId={visitInfo?.["jcr:uuid"]}
    />
  );
}

export default VisitView;
