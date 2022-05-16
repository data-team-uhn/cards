// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

/*
 * This program provides a mock Slack API server for testing
 * Slack performance data output from CARDS.
 */

const LISTEN_HOST = '0.0.0.0';
const LISTEN_PORT = 8000;

const bodyParser = require('body-parser');
const express = require('express');
const webApp = express();
webApp.use(bodyParser.text({type: '*/*'}));
const webServer = require('http').createServer(webApp);

webApp.post(/.*/, (req, res) => {
  console.log("Slack API Request => " + req.body);
  console.log("");
  res.json({"success": true});
});

webServer.listen(LISTEN_PORT, LISTEN_HOST, (err) => {
  if (err) {
    console.log("Mock Slack server failed to start");
  } else {
    console.log("Mock Slack server listening on port " + LISTEN_PORT);
    console.log("==========================================");
    console.log("");
  }
});
