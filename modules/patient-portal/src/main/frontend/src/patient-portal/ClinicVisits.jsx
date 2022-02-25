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
  const { clinicId, color, visitInfo, enableTimeTabs, eventsLabel, dashboardConfig } = props;

  const classes = useStyles(color)();

  let filters = {
    surveys : {
      comparator: "=",
      name: "surveys",
      title: visitInfo?.surveys?.["text"],
      type: visitInfo?.surveys?.["dataType"],
      uuid: visitInfo?.surveys?.["jcr:uuid"],
      value: surveysId,
      hidden: true
    },
    status : {
      comparator: "<>",
      name: "status",
      title: visitInfo?.status?.["text"],
      type: visitInfo?.status?.["dataType"],
      uuid: visitInfo?.status?.["jcr:uuid"],
      value: "cancelled",
      hidden: true
    },
    date : {
      comparator: "=",
      name: "time",
      title: visitInfo?.time?.["text"],
      type: visitInfo?.time?.["dataType"],
      uuid: visitInfo?.time?.["jcr:uuid"],
      dateFormat: "yyyy-MM-dd",
      value: '__DATE_FILTER_PLACEHOLDER__',
      hidden: true
    }
  };

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
      enableTimeTabs={dashboardConfig?.enableTimeTabs}
      query={`/Forms.paginate?fieldname=questionnaire&fieldvalue=${encodeURIComponent(visitInfo?.["jcr:uuid"])}&orderBy=${visitInfo?.time?.["jcr:uuid"]}`}
      filters={filters}
      questionnairePath={visitInfo?.["@path"]}
    />
  );
}

export default ClinicVisits;
