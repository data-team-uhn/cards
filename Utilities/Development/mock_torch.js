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

const process = require('process');
const bodyParser = require('body-parser');
const express = require('express');
const webApp = express();
webApp.use(bodyParser.text({type: '*/*'}));
const webServer = require('http').createServer(webApp);

const dateToTorchString = (date) => {
  let dateStr = "";
  dateStr += date.getFullYear().toString().padStart(4, '0');
  dateStr += '-';
  dateStr += (date.getMonth()+1).toString().padStart(2, '0');
  dateStr += '-';
  dateStr += date.getDate().toString().padStart(2, '0');
  dateStr += 'T';
  dateStr += date.getHours().toString().padStart(2, '0');
  dateStr += ':';
  dateStr += date.getMinutes().toString().padStart(2, '0');
  dateStr += ':';
  dateStr += date.getSeconds().toString().padStart(2, '0');
  return dateStr;
};

const getMockAppointmentTime = () => {
  for (let i = 0; i < process.argv.length; i++) {
    if (process.argv[i].startsWith("--appointment-time-hours-from-now=")) {
      let timeOffset = parseInt(process.argv[i].split("=")[1]);
      let nowTime = new Date();
      let apptTime = new Date(nowTime.getTime() + (1000*60*60*timeOffset));
      return dateToTorchString(apptTime);
    }
  }
  return "2022-02-20T10:00:00";
};

const getMockAppointmentStatus = () => {
  for (let i = 0; i < process.argv.length; i++) {
    if (process.argv[i].startsWith("--appointment-status=")) {
      return process.argv[i].split("=")[1];
    }
  }
  return "planned";
};

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
            status: {toJSON: getMockAppointmentStatus},
            fhirID: 'AppointmentOneFhirID',
            participants: [
              {
                role: 'ATND',
                physician: {
                  eID: 'SomeParticipantEID',
                  fhirID: 'SomeParticipantFhirID',
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
                }
              }
            ],
            time: {toJSON: getMockAppointmentTime}
          },
          {
            location: ['6012-HC-Congenital Cardiac'],
            status: {toJSON: getMockAppointmentStatus},
            fhirID: 'AppointmentTwoFhirID',
            participants: [
              {
                role: 'ABCD',
                physician: {
                  eID: 'SomeParticipantEID',
                  fhirID: 'SomeParticipantFhirID',
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
                }
              }
            ],
            time: {toJSON: getMockAppointmentTime}
          },
          {
            location: ['6012-HC-Congenital Cardiac'],
            status: {toJSON: getMockAppointmentStatus},
            fhirID: 'AppointmentThreeFhirID',
            participants: [
              {
                physician: {
                  eID: 'SomeParticipantEID',
                  fhirID: 'SomeParticipantFhirID',
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
                }
              }
            ],
            time: {toJSON: getMockAppointmentTime}
          }
        ]
      }
    ]
  }
};

webApp.post(/.*/, (req, res) => {
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
