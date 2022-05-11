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
import { styled } from '@mui/material/styles';
import {
  Typography,
  makeStyles,
} from "@material-ui/core";
import { DateTime } from "luxon";

const PREFIX = 'PrintHeader';

const classes = {
  container: `${PREFIX}-container`
};

const Root = styled('div')((
  {
    theme
  }
) => ({
  [`&.${classes.container}`]: {
    display: "flex",
    justifyContent: "space-between",
    borderBottom: "1px solid " + theme.palette.divider,
    marginBottom: theme.spacing(3),
    paddingBottom: theme.spacing(2),
    "& .MuiTypography-root" : {
      display: "block",
    },
    "& > *:last-child .MuiTypography-root" : {
      textAlign: "right",
    }
  }
}));

function PrintHeader (props) {
  const { resourceData } = props;



  const hasData = (
    resourceData?.last_name ||
    resourceData?.first_name ||
    resourceData?.date_of_birth ||
    resourceData?.mrn ||
    resourceData?.time
  );

  return hasData ?
  <Root className={classes.container}>
    <div>
      {(resourceData.last_name || resourceData.first_name) && <Typography variant="overline">{[resourceData.last_name || '-', resourceData.first_name || '-'].join(", ")}</Typography>}
      {resourceData.date_of_birth && <Typography variant="overline">DOB: {DateTime.fromISO(resourceData.date_of_birth).toLocaleString(DateTime.DATE_MED)}</Typography>}
    </div>
    <div>
      {resourceData.mrn && <Typography variant="overline">MRN: {resourceData.mrn}</Typography>}
      {resourceData.time && <Typography variant="overline">Appt: {DateTime.fromISO(resourceData.time).toLocaleString(DateTime.DATETIME_MED)}</Typography>}
    </div>
  </Root>
  : null;
}

export default PrintHeader;
