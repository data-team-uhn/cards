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

import React, {
  useRef,
  useState,
  useEffect,
  useContext
} from "react";

import PropTypes from "prop-types";

import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
  makeStyles,
  useMediaQuery
} from "@material-ui/core";

import { useTheme } from '@material-ui/core/styles';

import FormattedText from "../components/FormattedText.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import { useReactToPrint } from 'react-to-print';

// Component that renders a form in a format/style ready for printing.
// Internally, it queries and renders the markdown (.md) export of the form.
//
// Required props:
// resourcePath: String specifying the path of the form to print
//
// Optional props:
// title: String specifying the title to associate with the rendered content.
//   Note: the .md export doesn't generate a title.
// breadcrumb: String displayed in small fonts above the title, providing some context for the printed resource.
//   Example usage: the formatted identifier of the subject for this resource.
// date: String displayed with breadcrumb in the header above the title, providing the time context for the printed resource
// subtitle: String displayed in small fonts under the title, expected to be a relevant date or description 
// fullScreen: Boolean specifying if the preview is full screen or displayed as a modal
// onClose: Callback for closing the dialog
//
// Any other props are forwarded to the <Dialog> component used to display the preview.
//
// Sample usage:
//<PrintPreview
//  resourcePath="/Forms/UUID-1235"
//  title="..."
//  open={open}
//  fullScreen
//  onClose={() => {...}}
//  />
//

const useStyles = makeStyles(theme => ({
  printPreview : {
    "& .wmde-markdown h1, .wmde-markdown h2" : {
      borderBottom: "0 none",
    },
    "@media print" : {
      "& .MuiDialogContent-root" : {
        paddingTop: theme.spacing(3),
      },
      "& .MuiDialogActions-root" : {
        display: "none",
      },
    },
    "& .MuiDialogActions-root" : {
        padding: theme.spacing(2),
      },
  },
  header : {
    display: "flex",
    justifyContent: "space-between",
  },
}));

function PrintPreview(props) {
  const { open, resourcePath, title, breadcrumb, date, subtitle, fullScreen, onClose, ...rest } = props;

  const [ content, setContent ] = useState(null);
  const [ error, setError ] = useState();

  const classes = useStyles();

  const width = "sm";
  const theme = useTheme();
  const isFullScreen = fullScreen || useMediaQuery(theme.breakpoints.down(width));

  let globalLoginDisplay = useContext(GlobalLoginContext);

  const ref = useRef();

  const handlePrint = useReactToPrint({
    content: () => ref.current,
  });

  useEffect(() => {
    open && fetchWithReLogin(globalLoginDisplay, resourcePath + '.md')
      .then((response) => response.ok ? response.text() : Promise.reject(response))
      .then(setContent)
      .catch(response => setError(true));
  }, [open]);

  return (
    <Dialog
      ref={ref}
      open={open && !!content}
      className={classes.printPreview}
      maxWidth={width}
      fullWidth
      fullScreen={isFullScreen}
      onClose={onClose}
      {...rest}
    >
      { (breadcrumb || date || title || subtitle) &&
      <DialogTitle>
        { (breadcrumb || date) &&
          <div className={classes.header}>
            <Typography variant="overline" color="textSecondary">{breadcrumb}</Typography>
            <Typography variant="overline" color="textSecondary">{date}</Typography>
          </div>
        }
        { title && <Typography variant="h4">{title}</Typography> }
        { subtitle && <Typography variant="overline" color="textSecondary">{subtitle}</Typography> }
      </DialogTitle>
      }
      <DialogContent dividers>
        { content ?
          <FormattedText>{content}</FormattedText>
          :
          error ?
          <Typography color="error">Print preview cannot be loaded</Typography>
          :
          <CircularProgress />
        }
      </DialogContent>
      <DialogActions>
        <Button variant="contained" onClick={onClose}>Close</Button>
        <Button variant="contained" onClick={handlePrint} disabled={!!!content}>Print</Button>
      </DialogActions>
    </Dialog>
  );
}

PrintPreview.propTypes = {
  resourcePath: PropTypes.string.isRequired,
  open: PropTypes.bool,
  fullScreen: PropTypes.bool,
  title: PropTypes.string,
  breadcrumb: PropTypes.string,
  date: PropTypes.string,
  subtitle: PropTypes.string,
  onClose: PropTypes.func,
}

export default PrintPreview;
