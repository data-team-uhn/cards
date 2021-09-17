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
  Avatar,
  Card,
  CardContent,
  CardHeader,
  Grid,
  Typography,
  withStyles
} from "@material-ui/core";
import BarChartIcon from '@material-ui/icons/BarChart';
import LineChartIcon from '@material-ui/icons/ShowChart';
import { deepPurple, indigo } from '@material-ui/core/colors';

import moment from "moment";
import palette from "google-palette";
import {
   BarChart, Bar, CartesianGrid, Line, LineChart, Label, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis
} from "recharts";

import statisticsStyle from "./statisticsStyle.jsx";
import DateQuestionUtilities from "../questionnaire/DateQuestionUtilities";

// A single statistic, displayed as a chart
function Statistic(props) {
  const { classes, theme, definition } = props;
  // Colours to be used before relying on the google palette
  const DEFAULT_PALETTE = [
    theme.palette.primary.main,
  ];

  // Format dates into a human readable format
  let formatIfDate = (label, dateQuestionDef) => {
    if (dateQuestionDef["dataType"] != "date") {
      return label;
    }

    let dateFormat = dateQuestionDef["dateFormat"] || "yyyy-MM-dd";
    return DateQuestionUtilities.amendMoment(label, dateFormat).format(moment.HTML5_FMT.DATE);
  }

  // Transform our input data from the statistics servlet into something recharts can understand
  // Note that keys is transformed in this process
  let isSplit = false;
  let expandData = (label, data, keys) => {
    if (typeof(data) === 'object') {
      // Objects represent data with split
      let result = {};
      for (const [key, value] of Object.entries(data)) {
        result[key] = value;
        keys[key] = 1;
      }
      isSplit = true;
      return result;
    } else {
      // Integers represent a single point
      keys[label] = 1;
      return {[label]: data};
    }
  }

  // Transform the data into recharts' format
  let rechartsData = [];
  let allFieldsDict = {};
  for (const [key, value] of Object.entries(definition["data"])) {
    rechartsData.push({"x": formatIfDate(key, definition["xVar"]), ...expandData(definition["y-label"], value, allFieldsDict)});
  }

  let allFields = Object.keys(allFieldsDict);
  if (definition["splitVar"]) {
    // Reorder according to splitVar's order
    allFields = Object.values(definition["splitVar"])
      // Only grab answer options
      .filter((field) => field["jcr:primaryType"] == "cards:AnswerOption"
      // Furthermore, filter it to only fields that exist in the data
        && field["label"] in allFieldsDict)
      // Sort according to defaultOrder (if they exist)
      .sort((option1, option2) => (option1.defaultOrder - option2.defaultOrder))
      .map((field) => field["label"]);
    // If there are any fields in our data that aren't in the splitVar's answerOptions, include
    // them at the end
    allFields = allFields.concat(
      Object.keys(allFieldsDict)
        .filter((field) => !allFields.includes(field))
      );
  }

  let isBar = definition["type"] == "bar";

  let icon = isBar ? <BarChartIcon /> : <LineChartIcon />;
  let iconColor = isBar ? deepPurple[400] : indigo[400];

  let ChartType = isBar ? BarChart : LineChart;
  // Generate a palette via google-palette, and append the necessary # for a hex value
  let chartColours = DEFAULT_PALETTE.slice();
  let numKeys = Object.keys(allFields).length;
  if (numKeys > DEFAULT_PALETTE.length) {
    chartColours = chartColours.concat(palette("rainbow", numKeys-DEFAULT_PALETTE.length).map((col) => "#" + col));
  }

  let yAxisLabel = `${definition["y-label"]}s`;
  let widgetHeight = 250; // TODO: make mobile friendly

  return <Grid item md={12} lg={6}>
    <Card>
      <CardHeader
        disableTypography
        avatar={<Avatar style={{background: iconColor }}>{icon}</Avatar>}
        title={<Typography variant="h6">{definition["name"]}</Typography>}
        />
      <CardContent>
      { allFields.length == 0 ?
        <Grid container direction="row" justify="center" alignItems="center" style={{height: widgetHeight}}>
          <Grid item>
            <Typography color="textSecondary" variant="caption">No data available for this statistic</Typography>
          </Grid>
        </Grid>
        :
        <ResponsiveContainer width="100%" height={widgetHeight}>
          <ChartType data={rechartsData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="x">
              <Label value={definition["x-label"]} offset={0} position="insideBottom" />
            </XAxis>
            <YAxis allowDecimals={false} label={{ value: yAxisLabel, angle: -90, position: 'insideLeft' }} />
            <Tooltip />
            {isSplit && <Legend align="right" verticalAlign="top" />}
            {allFields.map((field, idx) =>
              isBar ?
                <Bar dataKey={field} fill={chartColours[idx]} key={idx}/>
              :
                <Line dataKey={field} type="monotone" stroke={chartColours[idx]} key={idx} />
            )}
            </ChartType>
          </ResponsiveContainer>
      }
      </CardContent>
    </Card>
  </Grid>
}

export default withStyles(statisticsStyle, {withTheme: true})(Statistic);