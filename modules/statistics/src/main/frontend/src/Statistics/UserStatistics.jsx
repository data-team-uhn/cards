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
import {
  Card,
  CardContent,
  Grid,
  withStyles,
  Typography
} from "@material-ui/core";
import statisticsStyle from "./statisticsStyle.jsx";
import Statistic from "./Statistic.jsx";

// Dashboard of all of the statistics viewable by the user
function UserStatistics(props) {
  const { classes } = props;
  let [ currentStatistic, setCurrentStatistic ] = useState([]);
  let [initialized, setInitialized] = useState(false);
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  // Obtain information about the Statistics available to the user
  let initialize = () => {
    setInitialized(true);

    // Fetch the statistics
    fetch("/query?query=select * from [cards:Statistic]")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((response) => {
        if (response.totalrows == 0) {
          setError("No statistics have been added yet.");
        }
        fetchAll(response["rows"]);
      })
      .catch(handleError);
  }

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response.statusText ? response.statusText : response.toString());
  };

  // If no forms can be obtained, we do not want to keep on re-obtaining statistics
  if (!initialized) {
    initialize();
  }

  // If an error was returned, report the error
  if (error) {
    return (
      <Card>
        <CardContent>
          <Typography>{error}</Typography>
        </CardContent>
      </Card>
    );
  }

  let fetchStat = (stat) => {
    // for each existing statistic, get full.json
    // pathnames will be sent in request body, that's why full.json is used
    fetch(`/Statistics/${stat['@name']}.full.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((fullJson) => {
        const urlBase = "/Statistics.query";
        let url = new URL(urlBase, window.location.origin);
        let requestData = {
          'name': fullJson.name,
          'x-label': fullJson.xVar['@path'],
          'y-label': fullJson.yVar['@path']
        }
        if (fullJson.splitVar) {
          requestData['splitVar'] = fullJson.splitVar['@path']
        }

        fetch( url, { method: 'POST', body: JSON.stringify(requestData) })
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then((statJson) => {
            // Also include the definition of the chart type
            statJson["type"] = fullJson["type"];
            console.log(statJson);
            setCurrentStatistic(currentStatistic => [...currentStatistic, statJson]);
          })
          .catch(handleError);

      })
      .catch(handleError);
  }

  let fetchAll = (data) => {
    data.map((stat) => fetchStat(stat))
  }

  return (
    <React.Fragment>
      <Grid container spacing={3}>
        {currentStatistic && currentStatistic.map((stat, i) => {
          return <Statistic definition={stat} key={i} />
        })}
      </Grid>
    </React.Fragment>
  );
}

export default withStyles(statisticsStyle)(UserStatistics);
