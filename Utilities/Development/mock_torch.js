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

const fs = require('fs');
const uuidv4 = require('uuid').v4;
const process = require('process');
const bodyParser = require('body-parser');
const express = require('express');
const webApp = express();
webApp.use(bodyParser.text({type: '*/*'}));
const webServer = require('http').createServer(webApp);

const RANDOM_NAME_LIST = JSON.parse(fs.readFileSync("com.github.dominictarr.random-name.names.json", "utf-8"));

const randomInt = (min, max) => {
  return Math.floor(Math.random() * (max - min) + min);
}

const getRandomName = () => {
  return RANDOM_NAME_LIST[randomInt(0, RANDOM_NAME_LIST.length)];
}

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

const readCliParam = (paramName) => {
  for (let i = 0; i < process.argv.length; i++) {
    if (process.argv[i].startsWith("--" + paramName + "=")) {
      return process.argv[i].split("=")[1];
    }
  }
  return undefined;
};

const getMockAppointmentTime = () => {
  let appointmentTimeHoursFromNow = readCliParam("appointment-time-hours-from-now");
  if (appointmentTimeHoursFromNow === undefined) {
    return "2022-02-20T10:00:00";
  } else {
    let timeOffset = parseInt(appointmentTimeHoursFromNow);
    let nowTime = new Date();
    let apptTime = new Date(nowTime.getTime() + (1000*60*60*timeOffset));
    return dateToTorchString(apptTime);
  }
};

const getMockAppointmentStatus = () => {
  let appointmentStatus = readCliParam("appointment-status");
  if (appointmentStatus === undefined) {
    return "planned";
  } else {
    return appointmentStatus;
  }
};

const generateRandomPatient = () => {
  let givenName = getRandomName();
  let familyName = getRandomName();
  return {
    mrn: 'MRN-' + uuidv4(),
    ohip: 'OHIP-' + uuidv4(),
    fhirID: 'FHIR-' + uuidv4(),
    dob: randomInt(1940, 2000) + '-' + randomInt(1, 12).toString().padStart(2, 0) + '-' + randomInt(1, 28).toString().padStart(2, 0),
    sex: (Math.random() < 0.5) ? 'F' : 'M',
    name: {
      given: [
        givenName
      ],
      family: familyName
    },
    emailOk: true,
    com: {
      email: {
        personal: givenName + '.' + familyName + '@mail.com'
      }
    }
  };
}

const generateRandomAppointment = () => {
   let physicianGivenName = getRandomName();
   let physicianFamilyName = getRandomName();
   return {
    location: ['6012-HC-Congenital Cardiac'],
    status: {toJSON: getMockAppointmentStatus},
    fhirID: 'FHIR-' + uuidv4(),
    participants: [
      {
        role: 'ATND',
        physician: {
          eID: 'eID-' + uuidv4(),
          fhirID: 'FHIR-' + uuidv4(),
          name: {
            prefix: [
              'Dr.'
            ],
            given: [
              physicianGivenName
            ],
            family: physicianFamilyName,
            suffix: [
              'M.D.'
            ]
          }
        }
      }
    ],
    time: {toJSON: getMockAppointmentTime}
  };
}

const generateRandomDataPatientsByDateAndClinic = (nDataPoints) => {
  let patientsByDateAndClinic = [];
  for (let i = 0; i < nDataPoints; i++) {
    let thisPatient = generateRandomPatient();
    let thisAppointment = generateRandomAppointment();
    thisPatient.appointments = [thisAppointment];
    patientsByDateAndClinic.push(thisPatient);
  }

  return {
    data: {
      patientsByDateAndClinic: patientsByDateAndClinic
    }
  };
}

const defaultGraphQlResponse = {
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

const selectGraphQlResponse = () => {
  let nRandomPatients = readCliParam("nRandomPatients");
  if (nRandomPatients !== undefined) {
    return generateRandomDataPatientsByDateAndClinic(parseInt(nRandomPatients));
  } else {
    return defaultGraphQlResponse;
  }
};

webApp.post(/.*/, (req, res) => {
  console.log("GraphQL QUERY => " + req.body);
  console.log("");
  console.log("RESPONSE <= " + JSON.stringify(selectGraphQlResponse()));
  console.log("");
  console.log("");
  res.json(selectGraphQlResponse());
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
