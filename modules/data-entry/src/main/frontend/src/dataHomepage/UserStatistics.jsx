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
import uuid from "uuid/v4";
import { Button, Card, CardContent, CardHeader, Grid, withStyles, Select, MenuItem, Typography } from "@material-ui/core";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

function UserStatistics(props) {
  const { classes } = props;
  let [statistics, setStatistics] = useState([1, 2, 3]); // each statistic becomes one chart

  // get request --> statistics

  return (
    <React.Fragment>
      <Grid container spacing={3}>
        {statistics.map((statistic) => {
          return(
            <Grid item lg={12} xl={6}>
              <Card>
                <CardContent>
                  <Grid container alignItems="flex-end" spacing={2} className={classes.filterTable}>
                    {/* use select from filters! */}
                    <Typography>Name:</Typography>
                    <Typography>x-axis:</Typography>
                    <Typography>y-axis:</Typography>
                    <Typography>Split by:</Typography>
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

export default withStyles(QuestionnaireStyle)(UserStatistics);
