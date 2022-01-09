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
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  Grid,
  IconButton,
  Input,
  InputLabel,
  Link,
  Typography,
  makeStyles
} from '@material-ui/core';
import CloseIcon from '@material-ui/icons/Close';

import ToUDialog from "./ToUDialog.jsx";

import { identifyPatient } from "./patientLookup.jsx";

import DropdownsDatePicker from "./components/DropdownsDatePicker.jsx";

const useStyles = makeStyles(theme => ({
  form : {
    maxWidth: "500px",
    margin: "auto",
    padding: theme.spacing(2),
  },
  logo : {
    maxWidth: "240px",
    "@media (max-height: 725px)" : {
      maxHeight: "70px",
    }
  },
  description : {
    "& > *" : {
      textAlign: "center",
      marginBottom: "16px",
      "@media (max-width: 400px)" : {
        fontSize: "x-small",
      }
    }
  },
  formFields : {
    marginTop: 0,
    width : "100%",
  },
  mrnInput : {
    '& input[type=number]': {
        '-moz-appearance': 'textfield'
    },
    '& input[type=number]::-webkit-outer-spin-button': {
        '-webkit-appearance': 'none',
        margin: 0
    },
    '& input[type=number]::-webkit-inner-spin-button': {
        '-webkit-appearance': 'none',
        margin: 0
    }
  },
  dateLabel : {
      paddingTop: theme.spacing(1),
  },
  identifierDivider : {
    marginTop: '35px',
  },
  identifierContainer : {
    alignItems: "start",
  },
  closeButton: {
    float: 'right',
  },
  mrnHelperImage: {
    maxWidth: '100%',
  },
  mrnHelperLink: {
    cursor: 'pointer',
  },
}));


function MockPatientIdentification(props) {
  const { onSuccess } = props;

  const [ dob, setDob ] = useState();
  const [ mrn, setMrn ] = useState();
  const [ hc, setHc ] = useState();
  const [ error, setError ] = useState();
  const [ idData, setIdData ] = useState();
  const [ patient, setPatient ] = useState();
  const [ visit, setVisit ] = useState();
  const [ subjectTypes, setSubjectTypes ] = useState();
  // Whether the patient user has accepted the latest version of the Terms of Use
  const [ touAccepted, setTouAccepted ] = useState(false);
  // Whether the Terms of Use dialog can be displayed after patient identification
  const [ showTou, setShowTou ] = useState(false);
  // Info about each patient is stored in a Patient information form
  const [ patientData, setPatientData ] = useState();

  const [ mrnHelperOpen, setMrnHelperOpen ] = useState(false);

  const classes = useStyles();

  // At startup, load subjectTypes
  useEffect(() => {
    fetch("/query?query=" + encodeURIComponent("SELECT * FROM [cards:SubjectType]"))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        let types = {};
        json.rows.forEach(r => types[r['@name']] = r['jcr:uuid']);
        setSubjectTypes(types);
      })
      .catch((response) => {
        setError(`Subject type retrieval failed with error code ${response.status}: ${response.statusText}`);
      });
  }, []);

  const sanitizeHC = (str) => {
    return str?.toUpperCase().replaceAll(/[^A-Z0-9]*/g, "") || "";
  }

  const identify = () => {
    return identifyPatient(dob, mrn, hc);
  }

  // On submitting the patient login form, make a request to identify the patient
  // If identification is successful, store the returned identification data (idData)
  const onSubmit = (event) => {
    event?.preventDefault();
    if (!dob || !mrn && !hc) {
      setError("Date of birth and either MRN or Health Card Number are required for patient identification");
      return;
    }
    setError("");
    setIdData(null);
    setPatient(null);
    setVisit(null);
    let data = identify();
    if (data) {
      setIdData(data);
    } else {
      setError("No records match the submitted information");
    }
  }

  // When the identification data is successfully obtained, get the patient subject's path
  useEffect(() => {
    idData && getPatient();
  }, [idData]);

  const getPatient = () => {
    getSubject(
      "Patient",   /* subject type */
      "/Subjects", /* path prefix*/
      idData.mrn,  /* id */
      'MRN',       /* id label */
      setPatient   /* successCallback */
    );
  }

  // When the patient subject path is successfully obtained, get the visit "subject"
  useEffect(() => {
    patient && getVisit();
  }, [patient]);

  const getVisit = () => {
    getSubject(
      "Visit",              /* subject type */
      patient,              /* path prefix*/
      idData.visit_number,  /* id */
      'visit number',       /* id label */
      setVisit              /* successCallback */
    );
  }

  // When the visit is successfully obtained and the latest version of Terms of Use accepted, pass it along with the identification data
  // to the parent component
  useEffect(() => {
    visit && touAccepted && onSuccess && onSuccess(Object.assign({subject: visit}, idData));
  }, [visit, touAccepted]);

  // Get the path of a subject with a specific identifier
  // if the subject doesn't exist, create it
  const getSubject = (subjectType, pathPrefix, subjectId, subjectIdLabel, successCallback) => {
    // If the patient doesn't yet have an MRN, or the visit doesn't yet have a number, abort mission
    // TODO: after we find out if the MRN is not always assigned in the DHP,
    // in which case implement a different logic for finding the patient
    if (!subjectId) {
      setError(`The record was found but no ${subjectIdLabel} has been assigned yet. Please try again later or contact your care team for next steps.`);
      return;
    }

    // Look for the subject identified by subjectId
    let query=`SELECT * FROM [cards:Subject] as s WHERE ischildnode(s, "${pathPrefix}") AND s.identifier="${subjectId}"`;
    fetch("/query?query=" + encodeURIComponent(query))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
         let results = json.rows;
         if (results.length == 0) {
           // Subject not found
           // Note: This should never actually happen
           setError("No matching records found. Please inform the technical administrator.");
         } else if (results.length == 1) {
           // Subject found: return its path
           successCallback(results[0]['@path']);
         } else {
           // More than one subject found, not sure which one to pick: display error
           // Note: This should never actually happen
           setError("More than one matching record found. Please inform the technical administrator.");
         }
      })
      .catch((response) => {
        setError(`Record identification failed with error code ${response.status}: ${response.statusText}`);
      });
  }

  // -----------------------------------------------------------------------------------------------------
  // Rendering

  if (!subjectTypes) {
    return null;
  }

  let appName = document.querySelector('meta[name="title"]')?.content;

  return (<>
    <ToUDialog
      open={showTou}
      patientData={patientData}
      actionRequired={true}
      onClose={() => setShowTou(false)}
      onAccept={() => {
        setShowTou(false);
        setTouAccepted(true);
      }}
      onDecline={() => {
        setShowTou(false)
        setIdData(null);
        setPatient(null);
      }}
    />
    <Dialog onClose={() => {setMrnHelperOpen(false)}} open={mrnHelperOpen}>
      <DialogTitle>
        Where can I find my MRN?
        <IconButton onClick={() => setMrnHelperOpen(false)} className={classes.closeButton}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Typography paragraph>
          1. Check the top right-hand corner of your Patient Itinerary.
        </Typography>
        <img src="/libs/cards/resources/mrn_helper_1.png" alt="MRN location within the Appointment Itinerary" className={classes.mrnHelperImage} />
        <Typography paragraph>
          2. Check your account page on the myUHN PatientPortal.
        </Typography>
        <img src="/libs/cards/resources/mrn_helper_2.png" alt="MRN location within the Patient Portal side bar" className={classes.mrnHelperImage} />
      </DialogContent>
    </Dialog>
    <form className={classes.form} onSubmit={onSubmit} >
      <Grid container direction="column" spacing={4} alignItems="center" justify="center">
         <Grid item xs={12}>
           <img src="/libs/cards/resources/logo_light_bg.png" className={classes.logo} alt="logo" />
         </Grid>
         <Grid item xs={12} className={classes.description}>
           <Typography variant="h6">
             Welcome to {appName}
           </Typography>
           <Typography paragraph>
             {appName} is designed to ask you the most important questions about your health and well being. Your responses will remain confidential and will help your provider determine how we can best help you.
           </Typography>
           <Typography paragraph>
             Completing the questionnaire is voluntary, so if you would rather not complete it, you do not have to.
           </Typography>
           <Typography paragraph>
             If routine service evaluations or research projects are undertaken, your responses may be analyzed in a completely anonymous way.
           </Typography>
         </Grid>
         <Grid item xs={12} className={classes.formFields}>
            <div className={classes.description}>
            { error ?
              <Typography color="error">{error}</Typography>
              :
              <Typography variant="h6">Enter the following information for identification</Typography>
            }
            </div>
            <InputLabel htmlFor="j_dob" shrink={true} className={classes.dateLabel}>Date of birth</InputLabel>
            <DropdownsDatePicker id="j_dob" name="j_dob" formatDate onDateChange={setDob} autoFocus fullWidth/>
            <Grid container direction="row" alignItems="flex-end" spacing={3} wrap="nowrap" justify="space-between" className={classes.identifierContainer}>
              <Grid item>
                <FormControl margin="normal" fullWidth>
                  <InputLabel htmlFor="j_mrn" shrink={true}>MRN</InputLabel>
                  <Input id="j_mrn" name="j_mrn" autoComplete="off" type="number" placeholder="1234567" className={classes.mrnInput} onChange={event => setMrn(event.target.value)}/>
                  <FormHelperText id="mrn_helper">
                  <Link
                    color="primary"
                    variant="caption"
                    onClick={() => {setMrnHelperOpen(true)}}
                    className={classes.mrnHelperLink}
                    >
                    Where can I find my MRN?
                    </Link>
                  </FormHelperText>
                 </FormControl>
              </Grid>
              <Grid item className={classes.identifierDivider}>or</Grid>
              <Grid item>
                <FormControl margin="normal" fullWidth>
                  <InputLabel htmlFor="j_hc" shrink={true}>Health card number</InputLabel>
                  <Input id="j_hc" name="j_hc" autoComplete="off" placeholder="2345 678 901 XY" onChange={event => setHc(sanitizeHC(event.target.value))}/>
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
  </>)
}

export default MockPatientIdentification;
