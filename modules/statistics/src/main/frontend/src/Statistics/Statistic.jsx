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
  CardHeader,
  Grid,
  withStyles
} from "@material-ui/core";
import palette from "google-palette";
import {
   BarChart, Bar, CartesianGrid, Line, LineChart, XAxis, YAxis, Tooltip, Label, Legend,
} from "recharts";
import statisticsStyle from "./statisticsStyle.jsx";

// A single statistic, displayed as a chart
function Statistic(props) {
  const { classes, theme, definition } = props;
  // Colours to be used before relying on the google palette
  const DEFAULT_PALETTE = [
    theme.palette.primary.main,
    theme.palette.secondary.main
  ];

  // Transform our input data from the statistics servlet into something recharts can understand
  // Note that keys is transformed in this process
  let expandData = (label, data, keys) => {
    if (typeof(data) === 'object') {
      // Objects represent data with split
      let result = {};
      for (const [key, value] of Object.entries(data)) {
        result[key] = value;
        keys[key] = 1;
      }
      return result;
    } else {
      // Integers represent a single point
      keys[label] = 1;
      return {[label]: data};
    }
  }

  // Transform the data into recharts' format
  let rechartsData = [];
  let allFields = {};
  for (const [key, value] of Object.entries(definition["data"])) {
    rechartsData.push({"x": key, ...expandData(definition["y-label"], value, allFields)});
  }

  let isBar = definition["type"] == "bar";
  let ChartType = isBar ? BarChart : LineChart;
  // Generate a palette via google-palette, and append the necessary # for a hex value
  let chartColours = DEFAULT_PALETTE.slice();
  let numKeys = Object.keys(allFields).length;
  if (numKeys > DEFAULT_PALETTE.length) {
    chartColours = chartColours.concat(palette("rainbow", numKeys-DEFAULT_PALETTE.length).map((col) => "#" + col));
  }

  return <Grid item md={12} lg={6}>
    <Card>
      <CardHeader
        title={definition["name"]}
        />
      <CardContent>
        <ChartType width={730} height={250} data={rechartsData}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="x">
            <Label value={definition["x-label"]} offset={0} position="insideBottom" />
          </XAxis>
          <YAxis allowDecimals={false}  label={{ value: definition["y-label"], angle: -90, position: 'insideLeft' }} />
          <Tooltip />
          <Legend />
          {Object.keys(allFields).map((field, idx) =>
            isBar ?
              <Bar dataKey={field} fill={chartColours[idx]} key={idx}/>
            :
              <Line dataKey={field} type="monotone" stroke={chartColours[idx]} key={idx} />
          )}
          </ChartType>
      </CardContent>
    </Card>
  </Grid>
}

export default withStyles(statisticsStyle, {withTheme: true})(Statistic);