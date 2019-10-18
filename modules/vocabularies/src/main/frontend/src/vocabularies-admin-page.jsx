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

import { Grid, Typography } from "@material-ui/core";
import React from "react";

function VocabulariesAdminPage(props) {
  return (
    <Grid container direction="column" spacing={5} justify="space-between">

      <Grid item>
        <Typography variant="h4" gutterBottom>
          Installed
        </Typography>
      </Grid>

      <Grid item>
        <Typography variant="h4">
          Find on <a href="https://www.bioontology.org/" target="_blank">BioPortal</a>
        </Typography>
      </Grid>

    </Grid>
  );
}

export default VocabulariesAdminPage;
