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

import { useState, useEffect, useRef } from "react";

import BuildIcon from '@mui/icons-material/Build';
import {
  Alert,
  AppBar,
} from '@mui/material';

export default function DowntimeWarning(props) {
  // The the configuration values specified by the Administration
  const [ enabled, setEnabled ] = useState(false);
  const [ fromDate, setFromDate ] = useState();
  const [ toDate, setToDate ] = useState();
  // Error message set when fetching the data from the server fails
  const [ error, setError ] = useState();
  const appBarRef = useRef(null);

  // Load the configurations only once, upon initialization
  useEffect(() => {
    fetch("/apps/cards/config/DowntimeWarning.deep.json")
      .then((response) => response.json())
      .then((json) => {
        if (!json.enabled) {
          return;
        }
        setEnabled(json.enabled == 'true');
        if (json.fromDate) {
          let date = new Date(json.fromDate);
          !isNaN(date.getTime()) && setFromDate(date.toDateString() + " " + date.toLocaleTimeString().replace(":00 ", " "));
        }
        if (json.toDate) {
          let date = new Date(json.toDate);
          !isNaN(date.getTime()) && setToDate(date.toDateString() + " " + date.toLocaleTimeString().replace(":00 ", " "));

          // Check if the downtime period ended
          if (new Date() > date) {
            setEnabled(false);
          }
        }
      })
      .catch((error) => {
        setError(error.statusText ? error.statusText : error);
      });
  }, []);

  // Report the height on mount and re-report whenever the banner appears, disappears or changes.
  useEffect(() => {
    props.onRender?.(appBarRef.current);
  }, [props.onRender, enabled, fromDate, toDate]);

  if (!enabled || !fromDate || !toDate) {
    return null;
  }

  return (
    <AppBar position="fixed" style={props.style} ref={appBarRef}>
      { error &&
        <Alert variant="filled" square severity="error" sx={{ justifyContent: "center" }}>{error}</Alert>
      }
      <Alert variant="filled" square severity="info" icon={<BuildIcon/>} sx={{ justifyContent: "center" }}>
        Scheduled Maintenance: {fromDate} - {toDate}
      </Alert>
    </AppBar>
  );
}
