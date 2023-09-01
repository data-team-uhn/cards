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
import React, { useState, useEffect }  from 'react';

import {
  Button,
  CircularProgress,
  DialogContent,
  FormControl,
  FormHelperText,
  Grid,
  Input,
  InputLabel,
  List,
  ListItem,
  Link,
  TextField,
  Typography,
} from '@mui/material';
import makeStyles from '@mui/styles/makeStyles';
import AppointmentIcon from '@mui/icons-material/Event';

import Logo from "../components/Logo.jsx";
import ErrorPage from "../components/ErrorPage.jsx";
import ResponsiveDialog from "../components/ResponsiveDialog.jsx";
import ToUDialog from "./ToUDialog.jsx";

import DateTimeUtilities from "../questionnaire/DateTimeUtilities";

import { DateTime } from "luxon";
import { AdapterLuxon } from "@mui/x-date-pickers/AdapterLuxon";
import { LocalizationProvider } from '@mui/x-date-pickers';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import FormattedText from "../components/FormattedText.jsx";

const useStyles = makeStyles(theme => ({
  form : {
    maxWidth: "500px",
    margin: "auto",
    padding: theme.spacing(2),
  },
  description : {
    "& > *" : {
      textAlign: "center",
      marginBottom: "16px",
      "@media (max-width: 400px)" : {
        fontSize: "x-small",
      }
    },
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
  mrnHelperImage: {
    maxWidth: '100%',
  },
  mrnHelperLink: {
    cursor: 'pointer',
  },
  appointmentEntry: {
    "& .MuiButton-root" : {
      justifyContent: "flex-start",
      textTransform: "none",
    },
  }
}));

// The patient is already authenticated via the token.
// This just makes sure that the correct person accessed the application.
function PatientIdentification(props) {
  // Callback for reporting successful authentication
  const { config, onSuccess, displayText } = props;

  // The authentication token from the personalized link
  const [ authToken, setAuthToken ] = useState();

  // The values entered by the user
  const [ dob, setDob ] = useState(null);
  const [ mrn, setMrn ] = useState();
  const [ hc, setHc ] = useState();

  // Internal state
  // Flag specifying that the required authentication token is missing, so the user cannot be identified
  const [ canAuthenticate, setCanAuthenticate ] = useState();
  // Flag specifying if the identification form needs to be displayed
  const [ showIdentificationForm, setShowIdentificationForm ] = useState();

  // Holds an error message for display
  const [ error, setError ] = useState();
  // Returned from the server after successful validation of the authentication,
  // and will be returned back to the rest of the Patient Portal UI through the onSuccess callback
  const [ patientDetails, setPatientDetails ] = useState();
  const [ visit, setVisit ] = useState();
  // Returned from the server after partial validation of the authentication.
  const [ visitList, setVisitList ] = useState();
  const [ visitListShown, setVisitListShown ] = useState(false);
  // Whether the patient user has accepted the latest version of the Terms of Use
  const [ touCleared, setTouCleared ] = useState();
  // Whether the Terms of Use dialog can be displayed after patient identification
  const [ showTou, setShowTou ] = useState(false);

  const [ mrnHelperOpen, setMrnHelperOpen ] = useState(false);

  const dateFormat = DateTimeUtilities.defaultDateFormat;
  const views = DateTimeUtilities.getPickerViews(dateFormat);

  const classes = useStyles();

  const sanitizeHC = (str) => {
    return str?.toUpperCase().replaceAll(/[^A-Z0-9]*/g, "") || "";
  }

  const validateCredentials = (requestData, onError) => {
    fetch("/Survey.validateCredentials", {
      "method": "POST",
      "body": requestData
      })
      .then((response) => response.json())
      .then((json) => {
        if(json.status == "needsVisit") {
          setVisitList(json.visits);
        } else if (json.status != "success") {
          return Promise.reject(json.error);
        } else {
          setPatientDetails(json.patientInformation);
          setVisit(json.sessionSubject);
        }
        setShowTou(true);
      })
      .catch((error) => {
        onError(error);
      });
  }

  const identify = () => {
    if (!dob.isValid || !mrn && !hc) {
      return null;
    }
    let requestData = new FormData();
    authToken && requestData.append("auth_token", authToken);
    dob && requestData.append("date_of_birth", dob.toFormat(dateFormat));
    mrn && requestData.append("mrn", mrn);
    hc && requestData.append("health_card", hc);
    visit && requestData.append("visit", visit);

    validateCredentials(requestData, (error) => setError(error.statusText || "Invalid credentials"));
  }

  // On submitting the patient login form, make a request to identify the patient
  const onSubmit = (event) => {
    event?.preventDefault();
    if (!dob.isValid || !mrn && !hc) {
      setError("Date of birth and either MRN or Health Card Number are required for patient identification");
      return;
    }
    setError("");
    setPatientDetails(null);
    setVisit(null);
    identify();
  }

  // ------------------------------------------------------------------------------------
  // After loading the patient access configuration that specifies if the auth token and/or
  // PII identification are required, decide how we will proceed with the authentication
  useEffect(() => {
    // If the configuration is still empty, don't make any decisions yet
    if (Object.keys(config).length == 0) return;

    let auth_token = new URLSearchParams(window.location.search).get("auth_token");
    setAuthToken(authToken);
    setCanAuthenticate(!!(config?.tokenlessAuthEnabled || auth_token));

    // If an auth token is provided and no further identification is required,
    // initiate the authentication process
    if (auth_token && !(config?.PIIAuthRequired)) {
      let requestData = new FormData();
      requestData.append("auth_token", auth_token);
      validateCredentials(requestData, (error) => { window.location = "/Expired.html"; });
    }

    // The identification form should be made available whenever tokenless auth is enabled,
    // as well as when specifically required according to the patient access config
    setShowIdentificationForm(config?.tokenlessAuthEnabled || config?.PIIAuthRequired);
  }, [config]);

  // Once a visit is selected, finalize the identification
  useEffect(() => {
    if (!visit || !visitList) return;
    // When the user selects a visit from the visit list, clear the list and
    // send the selected visit back to obtain a token
    setVisitList(null);
    setVisitListShown(false);
    identify();
  }, [visit,visitList]);

  // After the user has accepted the TOU (if TOU are enabled), if they need to select from a list of visits present said
  useEffect(() => {
    if (!visitListShown && touCleared && visitList && visitList.length > 1 ) {
      setVisitListShown(true);
    }
  }, [visitList, touCleared]);

  // When the visit is successfully obtained and the latest version of Terms of Use accepted, pass it along with the identification data
  // to the parent component
  useEffect(() => {
    visit && patientDetails && touCleared && authenticate();
  }, [visit, touCleared, !!patientDetails]);

  let authenticate = () => {
    onSuccess && onSuccess(Object.assign({subject: visit}, patientDetails));
  }

  // -----------------------------------------------------------------------------------------------------
  // Rendering

  let appName = document.querySelector('meta[name="title"]')?.content;
  let welcomeMessage = displayText('welcomeMessage')?.replaceAll("APP_NAME", appName);

  if (canAuthenticate === false) {
    let message = `${welcomeMessage || ""}\n\n### To fill out surveys, please follow the personalized link that was emailed to you.`;
    return (
      <ErrorPage
        sx={{maxWidth: 500, margin: "0 auto"}}
        title=""
        message={message}
        messageColor="textPrimary"
      />
    )
  }

  return (<>
    <ToUDialog
      open={showTou}
      actionRequired={true}
      onClose={(withError) => withError ? window.location.href = "/system/sling/logout?resource=" + encodeURIComponent(window.location.pathname) : setShowTou(false)}
      onCleared={() => {
        setShowTou(false);
        setTouCleared(true);
      }}
      onDecline={() => {
        setShowTou(false)
        setPatientDetails(null);
        setTouCleared(false);
        setVisit(null);
        window.location.href = "/system/sling/logout?resource=" + encodeURIComponent(window.location.pathname);
      }}
    />

    {/* MRN hint dialog*/}

    <ResponsiveDialog
      title="Where can I find my MRN?"
      withCloseButton
      open={mrnHelperOpen}
      onClose={() => {setMrnHelperOpen(false)}}
    >
      <DialogContent>
        <Typography paragraph>
          1. Check the top right-hand corner of your Patient Itinerary.
        </Typography>
        <img src="/libs/cards/resources/media/patient-portal/mrn_helper_1.png" alt="MRN location within the Appointment Itinerary" className={classes.mrnHelperImage} />
        <Typography paragraph>
          2. Check your account page on the myUHN PatientPortal.
        </Typography>
        <img src="/libs/cards/resources/media/patient-portal/mrn_helper_2.png" alt="MRN location within the Patient Portal side bar" className={classes.mrnHelperImage} />
      </DialogContent>
    </ResponsiveDialog>

    {/* Patient identification form */}

    <form className={classes.form} onSubmit={onSubmit} >
      <Grid container direction="column" spacing={4} alignItems="center" justifyContent="center">
         <Logo component={Grid} item xs={12} />

         { /* If we don't have the authentication token yet or we don't need the identification form,
             display a circular progress while we wait for the next step */ }

         { (typeof(canAuthenticate) == "undefined" || !showIdentificationForm) ?

         <Grid item xs={12} className={classes.description}>
            <CircularProgress />
         </Grid>

         : (!visitList || showTou || touCleared === false) ?

         /* If we haven't authenticated and retrieved the visit list for this patient yet,
             or if the Terms of Use were declined after identification,
             display the identification form */
         <>
         { welcomeMessage &&
           <Grid item xs={12} className={classes.description}>
             <FormattedText>{welcomeMessage}</FormattedText>
           </Grid>
         }
         <Grid item xs={12} className={classes.formFields}>
            <div className={classes.description}>
            { error ?
              <Typography color="error">{error}</Typography>
              :
              <Typography>Enter the following information for identification</Typography>
            }
            </div>
            <LocalizationProvider dateAdapter={AdapterLuxon}>
              <DatePicker
                views={views}
                openTo="year"
                format={dateFormat}
                label="Date of birth"
                value={dob}
                onChange={(value) => {
                  setError(false);
                  setDob(value);
                }}
                componentsProps={{ textField: {
                                     variant: 'standard',
                                     autoFocus: true,
                                     fullWidth: true,
                                     className: classes.textField,
                                     helperText: null,
                                     onBlur: (event) => { if (dob?.invalid) {
                                          setError(true);
                                          setErrorMessage("Invalid date: "  + dob.invalid.explanation);
                                        }
                                     },
                                     inputProps: {
				                       ...params.inputProps,
				                       placeholder: `${dateFormat}, for example ${DateTime.fromISO("1970-12-31").toFormat(dateFormat)}`
				                     }
                                 }
                }}
              />
            </LocalizationProvider>
            <Grid container direction="row" alignItems="flex-end" spacing={3} wrap="nowrap" justifyContent="space-between" className={classes.identifierContainer}>
              <Grid item>
                <FormControl variant="standard" margin="normal" fullWidth>
                  <InputLabel htmlFor="j_mrn" shrink={true}>MRN</InputLabel>
                  <Input id="j_mrn" name="j_mrn" autoComplete="off" type="number" placeholder="1234567" className={classes.mrnInput} onChange={event => setMrn(event.target.value)}/>
                  <FormHelperText id="mrn_helper">
                  <Link
                    color="primary"
                    variant="caption"
                    underline="hover"
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
                <FormControl variant="standard" margin="normal" fullWidth>
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
              Continue
            </Button>
          </Grid>
          <Input id="j_visitSelection" name="j_visitSelection" autoComplete="off" style={{display: "none"}} value={visit || ""}/>
          </>

          :

          <>

          {/* If we retrieved the visit list and there's more than one option, display the options for the patient */}

          { visitListShown ?
            <>
            <Grid item className={classes.description}>
              {displayText("eventSelectionMessage", Typography)}
            </Grid>
            <Grid item>
              <List>{ visitList.map((v,i) =>
                <ListItem className={classes.appointmentEntry} key={`appointmentEntry-${i}`}>
                  <Button
                    fullWidth
                    variant="outlined"
                    color="primary"
                    onClick={() => setVisit(v.subject)}
                    startIcon={<AppointmentIcon />}
                  >
                    { v.location }
                  </Button>
                </ListItem>
              )}</List>
            </Grid>
            <Grid item className={classes.description}>
              <Typography variant="body2" color="textSecondary">
                If you prefer not to proceed with filling out your surveys at this time, you can <Link href="/system/sling/logout" underline="hover">close this page</Link>.
              </Typography>
            </Grid>
            </>

            :

            <>
            {/* Otherwise inform the user there are no known upcoming appointments that need survery responses */}
            <Grid item className={classes.description}>
              {displayText("noEventsMessage", Typography, {variant: "h6", color: "textSecondary"})}
            </Grid>
            <Grid item>
              <Button
                color="primary"
                variant="contained" onClick={() => window.location = "/system/sling/logout"}
                >
                Close
              </Button>
            </Grid>
            </>
          }
          </>
        }
       </Grid>
    </form>
  </>)
}
export default PatientIdentification;
