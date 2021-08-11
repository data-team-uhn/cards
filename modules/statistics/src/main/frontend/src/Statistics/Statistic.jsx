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
  withStyles
} from "@material-ui/core";
import {
   BarChart, Bar, CartesianGrid, Line, LineChart, XAxis, YAxis, Tooltip, Label, Legend,
} from "recharts";
import statisticsStyle from "./statisticsStyle.jsx";

// A single statistic, displayed as a chart
function Statistic(props) {
  const { classes, definition } = props;

  // Transform our input data from the statistics servlet into something recharts can understand
  let expandData = (data) => {
    if (typeof(data) === 'object') {
      // Objects represent data with split
      let result = [];
      for (const [key, value] of Object.entries(data)) {
        result = result.concat(new Array(value).fill(key));
      }
      return result;
    } else {
      // Integers represent a single point
      return data;
    }
  }

  // Transform the data into recharts' format
  let rechartsData = [];
  for (const [key, value] of Object.entries(definition["data"])) {
    rechartsData.push({"x": key, [definition["y-label"]]: expandData(value)});
  }

  let ChartType = definition["type"] == "bar" ? BarChart : LineChart;
  let bar = definition["type"] == "bar" ? <Bar dataKey={definition["y-label"]} fill="#8884d8" /> : <Line dataKey={definition["y-label"]} type="monotone" stroke="#8884d8" />

  return <Card>
    <CardContent>
        <Grid container alignItems='flex-end' spacing={2}>
          <Grid item xs={12}>
            <ChartType width={730} height={250} data={rechartsData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="x">
                <Label value={definition["x-label"]} offset={0} position="insideBottom" />
              </XAxis>
              <YAxis allowDecimals={false} />
              <Tooltip />
              <Legend />
              {bar}
            </ChartType>
          </Grid>
        </Grid>
    </CardContent>
  </Card>
}

export default withStyles(statisticsStyle)(Statistic);