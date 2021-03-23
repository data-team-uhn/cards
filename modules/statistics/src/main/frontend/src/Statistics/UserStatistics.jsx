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
import {
   BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Label, Legend,
} from "recharts";
import statisticsStyle from "./statisticsStyle.jsx";

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
            setCurrentStatistic(currentStatistic => [...currentStatistic, JSON.stringify(statJson)]);
          })
          .catch(handleError);

      })
      .catch(handleError);
  }

  let fetchAll = (data) => {
    data.map((stat) => fetchStat(stat))
  }

  let expandData = (data) => {
    let result = [];
    for (const [key, value] of Object.entries(data)) {
      result = result.concat(new Array(value).fill(key));
    }
    return result;
  }

  return (
    <React.Fragment>
      <Grid container spacing={3}>
        {currentStatistic && currentStatistic.map((stat) => {
          console.log(stat);
          // Transform the data into something recharts can understand
          let rechartsData = [];
          let parsedStat = JSON.parse(stat);
          for (const [key, value] of Object.entries(parsedStat["data"])) {
            rechartsData.push({"x": key, [parsedStat["y-label"]]: expandData(value)});
          }
          return(
            <Grid item lg={12} xl={6} key={stat["name"]}>
              <Card>
                <CardContent>
                    <Grid container alignItems='flex-end' spacing={2}>
                      <Grid item xs={12}>
                        <BarChart width={730} height={250} data={rechartsData}>
                          <CartesianGrid strokeDasharray="3 3" />
                          <XAxis dataKey="x">
                            <Label value={parsedStat["x-label"]} offset={0} position="insideBottom" />
                          </XAxis>
                          <YAxis allowDecimals={false}/>
                          <Tooltip />
                          <Legend />
                          <Bar dataKey={parsedStat["y-label"]} fill="#8884d8" />
                        </BarChart>
                      </Grid>
                    </Grid>
                </CardContent>
              </Card>
            </Grid>
          )
        })}
      </Grid>
    </React.Fragment>
  );
}

export default withStyles(statisticsStyle)(UserStatistics);
