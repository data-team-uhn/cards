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
import React from "react";
import {
  Avatar,
  Card,
  CardContent,
  CardHeader,
  Grid,
  Typography
} from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import BarChartIcon from '@mui/icons-material/BarChart';
import LineChartIcon from '@mui/icons-material/ShowChart';
import { deepPurple, indigo } from '@mui/material/colors';

import { useHistory } from 'react-router-dom';

import palette from "google-palette";
import {
   BarChart, Bar, CartesianGrid, Line, LineChart, Label, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis
} from "recharts";

import statisticsStyle from "./statisticsStyle.jsx";

// A single statistic, displayed as a chart
function Statistic(props) {
  const { classes, theme, definition, disableClick } = props;
  // Colours to be used before relying on the google palette
  const DEFAULT_PALETTE = [
    "#f94900",
    "#ff9900",
    "#36b37e",
    "#00b8d9",
    "#3c78d8",
    "#974efd",
    "#9e4973",
  ];

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
    rechartsData.push({"x": key, ...expandData(definition["y-label"], value, allFieldsDict)});
  }

  // Sort the data we provide to recharts according to its x value
  let xVar = definition["xVar"];

  // If there are any answer options, use their defaultOrder for sorting
  let xOptionLabels = Object.values(xVar)
    .filter(o => o["jcr:primaryType"] == "cards:AnswerOption")
    .sort((o1, o2) => (o1.defaultOrder - o2.defaultOrder));

  if (xOptionLabels.length) {
    rechartsData.sort((a, b) => {
      let aIdx = xOptionLabels.findIndex(i => i.label == a.x);
      let bIdx = xOptionLabels.findIndex(i => i.label == b.x);
      if (aIdx >= 0 && bIdx >= 0) {
        return aIdx - bIdx;
      } else if (aIdx >= 0) {
        return -1;
      } else if (bIdx >= 0) {
        return 1;
      } else if (["long", "double", "decimal"].includes(definition["xVar"]["dataType"])) {
        // Numeric sort
        return a["x"] - b["x"];
      } else {
        // Rely on the default (string-coerced) sort
        return a["x"].localeCompare(b["x"]);
      }
    });
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
      .map((field) => ({value: field["value"], label: field["label"]}))
    // If there are any fields in our data that aren't in the splitVar's answerOptions, include
    // them at the end
    allFields = allFields.concat(
      Object.keys(allFieldsDict)
        .filter((field) => !allFields.some(f => (f.label == field)))
        .map(field => ({value : field == "Not specified" ? undefined : field, label: field}))
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

  let widgetHeight = 250; // TODO: make mobile friendly
  let legendHeight = 40;


  // When clicking on a bar or line, list the questionnaires that correspond to it
  let handleClick = (data, field) => {
    if (disableClick) return;
    let xVal = data?.payload?.x;
    let splitVal = field.value;
    navigateToDataset(xVal, splitVal);
  }

  let history = useHistory();

  let navigateToDataset = (xVal, splitVal) => {;
    history.push(
      "/content.html/Subjects#subjects:activeTab=" + definition?.meta?.yVar?.["@name"] +
      "&subjects:filters=" + window.btoa(encodeURIComponent(JSON.stringify(generateFilters(xVal, splitVal))))
    );
  }

  let generateFilters = (xVal, splitVal) => {
    let result = [];
    let xVarDef = definition?.meta?.xVar;
    let xValueDictionary = definition?.xValueDictionary;
    let xVarFilter = generateFilter(xVarDef, xValueDictionary ? xValueDictionary[xVal] : xVal)
    result.push(xVarFilter);
    if (isSplit) {
      let splitDef = definition?.meta?.splitVar;
      let splitValueDictionary = definition?.splitValueDictionary;
      let splitFilter = generateFilter(splitDef, splitValueDictionary ? splitValueDictionary[splitVal] : splitVal);
      result.push(splitFilter);
    }
    return result;
  }

  let generateFilter = (varDef, value) => {
    return varDef && {
      comparator: typeof(value) == 'undefined' ? "is empty" : "=",
      name: varDef?.["@path"]?.replace(/^\/[^/]+\//, ""),
      title: varDef?.["text"],
      type: varDef?.["dataType"],
      uuid: varDef?.["jcr:uuid"],
      value: value
    };
  }

  let customStyle = disableClick ? {} : {cursor: "pointer"};

  return <Grid item xs={12} lg={6}>
    <Card className={classes.statsCard}>
      <CardHeader
        disableTypography
        avatar={<Avatar style={{background: iconColor }}>{icon}</Avatar>}
        title={<Typography variant="h6">{definition["name"]}</Typography>}
        />
      <CardContent>
      { allFields.length == 0 ?
        <Grid container direction="row" justifyContent="center" alignItems="center" style={{height: widgetHeight}}>
          <Grid item>
            <Typography color="textSecondary" variant="caption">No data available for this statistic</Typography>
          </Grid>
        </Grid>
        :
        <ResponsiveContainer width="100%" height={widgetHeight}>
          <ChartType
            data={rechartsData}
            margin={{
              top: 20 + (isSplit ? 0 : legendHeight),
              right: 80,
              bottom: 20,
              left: 20
          }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="x">
              <Label value={definition["x-label"]} offset={-10} position="insideBottom" />
            </XAxis>
            <YAxis allowDecimals={false} label={{ value: definition["y-label"], angle: -90, position: 'insideLeft', offset: 10 }} />
            <Tooltip />
            {isSplit && <Legend align="right" verticalAlign="top" height={legendHeight} />}
            {allFields.map((field, idx) =>
              isBar ?
                <Bar dataKey={field.label || field} fill={chartColours[idx]} key={idx} onClick={(data, index) => handleClick(data, field)} style={customStyle}/>
              :
                <Line dataKey={field.label || field} type="monotone" stroke={chartColours[idx]} key={idx} />
            )}
            </ChartType>
          </ResponsiveContainer>
      }
      </CardContent>
    </Card>
  </Grid>
}

export default withStyles(statisticsStyle, {withTheme: true})(Statistic);
