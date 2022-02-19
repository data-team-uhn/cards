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

import {
  makeStyles,
} from "@material-ui/core";
import { Link } from 'react-router-dom';
import EventIcon from '@material-ui/icons/Event';

import PromsViewInternal from "./PromsViewInternal.jsx";

const useStyles = color => makeStyles(theme => ({
  visitView : {
    "&.MuiCard-root" : {
       border: "2px solid " + color,
    },
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

  let generateFilters = () => {
    let result = [];
    result.surveys = {
      comparator: "=",
      name: "surveys",
      title: visitInfo?.surveys?.["text"],
      type: visitInfo?.surveys?.["dataType"],
      uuid: visitInfo?.surveys?.["jcr:uuid"],
      value: surveysId
    };
    result.date = {
      comparator: "=",
      name: "time",
      title: visitInfo?.time?.["text"],
      type: visitInfo?.time?.["dataType"],
      uuid: visitInfo?.time?.["jcr:uuid"],
      value: '__DATE_FILTER_PLACEHOLDER__'
    };
    return result;
  }

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
           className={classes["status" + (!row.surveys_complete ? "Incomplete" : (!row.surveys_submitted ? "Unreviewed" : "Completed"))]}
           to={`/content.html${row.subject['@path']}`}>
           { !row.surveys_complete ? "Incomplete" : (!row.surveys_submitted ? "Pending submission" : "Completed") }
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
      query={`/Forms.paginate?fieldname=questionnaire&fieldvalue=${encodeURIComponent(visitInfo?.["jcr:uuid"])}&orderBy=${visitInfo?.time?.["jcr:uuid"]}`}
      filters={generateFilters()}
      questionnaireId={visitInfo?.["@path"]}
    />
  );
}

export default VisitView;
