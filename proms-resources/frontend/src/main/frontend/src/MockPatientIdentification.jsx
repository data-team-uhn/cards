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
import React, { useState, useEffect, useContext }  from 'react';
import { v4 as uuidv4 } from 'uuid';

import {
  Grid,
  Typography,
  makeStyles
} from '@material-ui/core';
import {
    Button,
    FormControl,
    IconButton,
    Input,
    InputLabel,
    Tooltip,
} from '@material-ui/core';

const TEST_PATIENTS = [{
    last_name: "Addison",
    first_name: "John",
    date_of_birth: "1970-01-01",
    mrn: "1234567",
    health_card: "1234567890AB",
    subject: "/Subjects/SamplePatient"
  }, {
    last_name: "Bennet",
    first_name: "Mary",
    date_of_birth: "1971-02-27",
    mrn: "2345678",
    health_card: "2345678901CD",
    subject: "/Subjects/SamplePatient"
  }, {
    last_name: "Coleman",
    first_name: "Paul",
    date_of_birth: "1972-03-31",
    mrn: "3456789",
    health_card: "3456789012EF",
    subject: "/Subjects/SamplePatient"
  }];

const useStyles = makeStyles(theme => ({
  form : {
    margin: theme.spacing(6, 3, 3),
  },
  logo : {
    maxWidth: "240px",
  },
  instructions : {
    textAlign: "center",
  },
}));

function MockPatientIdentification(props) {
  const { onSuccess } = props;

  const [ dob, setDob ] = useState();
  const [ mrn, setMrn ] = useState();
  const [ hc, setHc ] = useState();
  const [ error, setError ] = useState();

  const classes = useStyles();

  const sanitizeHC = (str) => {
    return str?.toUpperCase().replaceAll(/[^A-Z0-9]*/g, "") || "";
  }

  const identify = () => {
    // TO DO: replace mock with authentication call
    return TEST_PATIENTS.filter(p => (p.date_of_birth == dob && (!mrn || p.mrn == mrn) && (!hc || p.health_card == hc)))[0];
  }

  const onSubmit = (event) => {
    event?.preventDefault();
    if (!dob || !mrn && !hc) {
      setError("Date of birth and either MRN or Health Card Number are required for patient identification");
      return;
    }
    setError("");
    let patient = identify();
    if (patient) {
      onSuccess(patient);
    } else {
      setError("No records match the submitted information");
    }
  }

  return (
    <form className={classes.form} onSubmit={onSubmit} >
      <Grid container direction="column" spacing={4} alignItems="center" justify="center">
         <Grid item xs={12}>
           <img src="/libs/cards/resources/logo_light_bg.png" className={classes.logo} alt="logo" />
         </Grid>
         <Grid item xs={12} className={classes.instructions}>
         { error ?
           <Typography color="error">{error}</Typography>
           :
           <Typography>Please enter the following information for identification</Typography>
         }
         </Grid>
         <Grid item xs={12}>
            <FormControl margin="normal" required fullWidth>
              <InputLabel htmlFor="j_dob">Date of birth</InputLabel>
              <Input id="j_dob" name="j_dob" autoComplete="off" type="date" autoFocus onChange={event => setDob(event.target.value)}/>
            </FormControl>
            <Grid container direction="row" alignItems="flex-end" spacing={3} wrap="nowrap">
              <Grid item>
                <FormControl margin="normal" fullWidth>
                  <InputLabel htmlFor="j_mrn">MRN</InputLabel>
                  <Input id="j_mrn" name="j_mrn" autoComplete="off" type="number" onChange={event => setMrn(event.target.value)}/>
                 </FormControl>
              </Grid>
              <Grid item>or</Grid>
              <Grid item>
                <FormControl margin="normal" fullWidth>
                  <InputLabel htmlFor="j_hc">Health card number</InputLabel>
                  <Input id="j_hc" name="j_hc" autoComplete="off" onChange={event => setHc(sanitizeHC(event.target.value))}/>
                 </FormControl>
              </Grid>
            </Grid>
          </Grid>
          <Grid item>
            <Button
              type="submit"
              variant="contained"
              color="primary"
              className={classes.submit}
              >
              Submit
            </Button>
          </Grid>
       </Grid>
    </form>
  )
}

export default MockPatientIdentification;
