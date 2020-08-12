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
import { Card, CardContent, CardHeader, Grid, withStyles, Typography } from "@material-ui/core";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

function UserStatistics(props) {
  const { classes } = props;
  // Store information about each Statistic and whether or not we have initialized
  let [statistics, setStatistics] = useState([]);
  let [initialized, setInitialized] = useState(false);
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  // Obtain information about the Statistics available to the user
  let initialize = () => {
    setInitialized(true);

    // Fetch the statistics
    fetch("/query?query=select * from [lfs:Statistics]")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((response) => {
        if (response.totalrows == 0) {
          setError("No statistics have been added yet.");
        }
        setStatistics(response["rows"]);
      })
      .catch(handleError);
  }

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response.statusText ? response.statusText : response.toString());
    setStatistics([]);  // Prevent an infinite loop if data was not set
  };

  // If no forms can be obtained, we do not want to keep on re-obtaining statistics
  if (!initialized) {
    initialize();
  }

  // If an error was returned, report the error
  if (error) {
    return (
      <Card>
        <CardHeader title="Error"/>
        <CardContent>
          <Typography>{error}</Typography>
        </CardContent>
      </Card>
    );
  }

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
