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
 * This program provides a mock Torch GraphQL server for testing
 * Torch data import into CARDS.
 */

const LISTEN_HOST = '0.0.0.0';
const LISTEN_PORT = 8011;

const bodyParser = require('body-parser');
const express = require('express');
const webApp = express();
webApp.use(bodyParser.text({type: '*/*'}));
const webServer = require('http').createServer(webApp);

const graphQlResponse = {
  data: {
    patientsByDateAndClinic: [
      {
        mrn: '12345',
        ohip: '980173',
        fhirID: 'Fh1R1D',
        dob: '1970-01-01',
        sex: 'F',
        name: {
          given: [
            'Alice'
          ],
          family: 'Robertson'
        },
        emailOk: true,
        com: {
          email: {
            personal: 'alice.bob@mail.com'
          }
        },
        appointments: [
          {
            location: ['6012-HC-Congenital Cardiac'],
            status: 'upcoming',
            fhirID: 'AppointmentFhirID',
            attending: {
              name: {
                prefix: [
                  'Dr.'
                ],
                given: [
                  'Robert'
                ],
                family: 'Smith',
                suffix: [
                  'M.D.'
                ]
              }
            },
            time: '2022-02-20T10:00:00'
          }
        ]
      }
    ]
  }
};

webApp.post('*', (req, res) => {
  console.log("GraphQL QUERY => " + req.body);
  console.log("");
  console.log("RESPONSE <= " + JSON.stringify(graphQlResponse));
  console.log("");
  console.log("");
  res.json(graphQlResponse);
});

webServer.listen(LISTEN_PORT, LISTEN_HOST, (err) => {
  if (err) {
    console.log("Mock Torch GraphQL server failed to start");
  } else {
    console.log("Mock Torch GraphQL server listening on port " + LISTEN_PORT);
    console.log("====================================================");
    console.log("");
  }
});
