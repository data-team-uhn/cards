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
// A "Where can I find my MRN?" hint for the patient identification form: a link
// shown under the MRN input that opens a dialog explaining where to find the MRN.
//
// This component is currently DORMANT: it is not rendered anywhere. The dialog
// content is institution specific (UHN) and the screenshots are out of date. To
// reuse it, update the screenshots and copy for the target institution, then drop
// <MRNHelper /> in place of the MRN input's FormHelperText in PatientIdentification.
// To make it opt-in per deployment, expose it as a PatientPortal UI extension,
// following the pattern used by the page footer (see Footer.jsx).
//
import { useState } from 'react';

import {
  DialogContent,
  FormHelperText,
  Link,
  Typography,
} from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import ResponsiveDialog from "../components/ResponsiveDialog.jsx";

const useStyles = makeStyles()(theme => ({
  mrnHelperImage: {
    maxWidth: '100%',
  },
  mrnHelperLink: {
    cursor: 'pointer',
  },
}));

function MRNHelper(props) {
  const [ open, setOpen ] = useState(false);

  const { classes } = useStyles();

  return (<>
    <FormHelperText id="mrn_helper">
      <Link
        variant="caption"
        underline="hover"
        onClick={() => setOpen(true)}
        className={classes.mrnHelperLink}
      >
        Where can I find my MRN?
      </Link>
    </FormHelperText>

    <ResponsiveDialog
      title="Where can I find my MRN?"
      withCloseButton
      open={open}
      onClose={() => setOpen(false)}
    >
      <DialogContent>
        <Typography component="p">
          1. Check the top right-hand corner of your Patient Itinerary.
        </Typography>
        <img src="/libs/cards/resources/media/patient-portal/mrn_helper_1.png" alt="MRN location within the Appointment Itinerary" className={classes.mrnHelperImage} />
        <Typography component="p">
          2. Check your account page on the myUHN PatientPortal.
        </Typography>
        <img src="/libs/cards/resources/media/patient-portal/mrn_helper_2.png" alt="MRN location within the Patient Portal side bar" className={classes.mrnHelperImage} />
      </DialogContent>
    </ResponsiveDialog>
  </>);
}

export default MRNHelper;
