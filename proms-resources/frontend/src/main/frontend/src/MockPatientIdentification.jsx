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

import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

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
  }
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

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // At startup, load subjectTypes
  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, "/query?query=" + encodeURIComponent("SELECT * FROM [cards:SubjectType]"))
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
    fetchWithReLogin(globalLoginDisplay, "/query?query=" + encodeURIComponent(query))
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
         let results = json.rows;
         if (results.length == 0) {
           // Subject not found: create it
           createSubject(subjectType, pathPrefix, subjectId, successCallback);
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

  // Create a new subject if it's the first time we receive this identifier
  const createSubject = (type, path, id, successCallback) => {
    // Make a POST request to create a new subject
    let requestData = new FormData();
    requestData.append('jcr:primaryType', 'cards:Subject');
    requestData.append('identifier', id);
    requestData.append('type', subjectTypes[type]);
    requestData.append('type@TypeHint', 'Reference');

    let subjectPath = `${path}/` + uuidv4();
    fetchWithReLogin(globalLoginDisplay, subjectPath, { method: 'POST', body: requestData })
      .then((response) => response.ok ? successCallback(subjectPath) : Promise.reject(response))
      .catch((response) => {
        setError(`Data recording failed with error code ${response.status}: ${response.statusText}`);
      });
  }

  // ---------------------------------------------------------------------------------------------------
  // Keep the patient information up to date

  // The definition of the Patient information questionnaire
  // Info about each patient is stored in a Patient information form
  const [ piDefinition, setPiDefinition ] = useState();

  // Load the Patient Information questionnaire
  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, "/Questionnaires/Patient information.deep.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => setPiDefinition(json))
      .catch((response) => {
         setError(`Initializing information sync failed with error code ${response.status}: ${response.statusText}`);
       });
  }, []);

  // When the patient is successfully identified, sync their information
  useEffect(() => {
    patient && piDefinition && syncPatientInfo();
  }, [patient, piDefinition]);

  // Now the Terms of Use can be shown if applicable
  useEffect(() => {
    patientData && setShowTou(true);
  }, [patientData]);

  const syncPatientInfo = () => {
    // Fetch the patient subject and forms associated with it
    fetchWithReLogin(globalLoginDisplay, `${patient}.data.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {

        // Check if the data includes a patient information form
        let piForm = json?.[piDefinition['title']]?.[0];
        setPatientData(piForm);
        if (piForm?.["jcr:primaryType"] == "cards:Form") {
          // The form already exists, get its path for the update request
          updatePatientInfo(piForm['@path']);
        } else {
          // The form doesn't exist, generate the path and populate the request data for form creation
          let request_data = new FormData();
          let formPath = "/Forms/" + uuidv4();
          request_data.append('jcr:primaryType', 'cards:Form');
          request_data.append('questionnaire', piDefinition["@path"]);
          request_data.append('questionnaire@TypeHint', 'Reference');
          request_data.append('subject', patient);
          request_data.append('subject@TypeHint', 'Reference');

          // Create or update the Patient information form
          fetchWithReLogin(globalLoginDisplay, formPath, { method: 'POST', body: request_data })
            .then( (response) => response.ok ? updatePatientInfo(formPath) : Promise.reject(response))
            .catch((response) => {
              setError(`Information sync failed with error code ${response.status}: ${response.statusText}`);
            });
        }
      })
      .catch((response) => {
        setError(`Local record retrieval failed with error code ${response.status}: ${response.statusText}`);
        console.log(response);
      });
  }

  const updatePatientInfo = (formPath) => {
     let request_data = new FormData();
     // Populate the request data with the values obtained when identifying the patient
     let fields = Object.keys(piDefinition).filter(k => piDefinition[k]?.["jcr:primaryType"] == "cards:Question");
     fields.forEach(f => {
       if (!idData[f]) return;
       let qDef = piDefinition[f];
       let type = (qDef.dataType || 'text');
       // Capitalize the type:
       type = type[0].toUpperCase() + type.substring(1);

       // Add each field to the request
       request_data.append(`./${f}/jcr:primaryType`, `cards:${type}Answer`);
       request_data.append(`./${f}/question`, qDef['jcr:uuid']);
       request_data.append(`./${f}/question@TypeHint`, "Reference");
       request_data.append(`./${f}/value`, idData[f]);
       request_data.append(`./${f}/value@TypeHint`, type == 'Text' ? 'String' : type);
     })
     // Update the Patient information form
     fetchWithReLogin(globalLoginDisplay, formPath, { method: 'POST', body: request_data })
       .then( (response) => response.ok ? null : Promise.reject(response))
       .catch((response) => {
         let errMsg = "Information sync failed";
         setError(errMsg + (response.status ? ` with error code ${response.status}: ${response.statusText}` : ''));
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
        <span className={classes.dialogTitle}>Where can I find my MRN?</span>
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
                    color="inherit"
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
