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
  Card,
  CardContent,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
  makeStyles,
  useMediaQuery
} from "@material-ui/core";

import { useTheme, styled } from '@material-ui/core/styles';

import FormattedText from "../components/FormattedText.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import { loadExtensions } from "../uiextension/extensionManager";
import { useReactToPrint } from 'react-to-print';

const PREFIX = 'PrintPreview';

const classes = {
  printPreview: `${PREFIX}-printPreview`,
  header: `${PREFIX}-header`,
  printTarget: `${PREFIX}-printTarget`
};

// TODO jss-to-styled codemod: The Fragment root was replaced by div. Change the tag if needed.
const Root = styled('div')((
  {
    theme
  }
) => ({
  [`& .${classes.printPreview}`]: {
    "& .wmde-markdown h1, .wmde-markdown h2" : {
      borderBottom: "0 none",
    },
    "& .MuiDialogContent-dividers" : {
      borderBottomColor: theme.palette.primary.main,
    },
    "& .MuiDialogActions-root" : {
      padding: theme.spacing(2),
    },
  },

  [`& .${classes.header}`]: {
    display: "flex",
    justifyContent: "space-between",
    borderBottom: "1px solid " + theme.palette.divider,
    marginBottom: theme.spacing(3),
  },

  [`& .${classes.printTarget}`]: {
    display: "none",
    "@media print" : {
      display: "block",
      padding: theme.spacing(2),
      "& table" : {
        width: "100%",
      },
      "& h1:not(:first-of-type)" : {
        breakBefore: "page",
      }
    }
  }
}));

async function getHeaderExtensions(name) {
  return loadExtensions("PrintHeader")
    .then(extensions => extensions.slice()
      .sort((a, b) => a["cards:defaultOrder"] - b["cards:defaultOrder"])
    )
}

function PrintPreview(props) {
  const { open, resourcePath, resourceData, title, breadcrumb, date, subtitle, disablePreview, fullScreen, onClose, ...rest } = props;

  const [ headerExtensions, setHeaderExtensions ] = useState();

  const [ content, setContent ] = useState();
  const [ error, setError ] = useState();



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

  useEffect(() => {
    if (disablePreview && typeof(content) != undefined) {
      handlePrint();
      // onClose && onClose();
    }
  }, [content]);

  useEffect(() => {
    getHeaderExtensions()
      .then(extensions => setHeaderExtensions(extensions || []))
      .catch(err => {
        console.log("Something went wrong loading the print header extensions", err);
        setHeaderExtensions([]);
      })
  }, [])

  let header = (
    headerExtensions?.length ? <Root>{ headerExtensions.map((extension, index) => {
              let Extension = extension["cards:extensionRender"];
              return <Extension key={`extension-${index}`} resourceData={resourceData} />;
            })}</Root>
    :
    (breadcrumb || date) ?
      <div className={classes.header}>
        <Typography variant="overline" color="textSecondary">{breadcrumb}</Typography>
        <Typography variant="overline" color="textSecondary">{date}</Typography>
      </div>
    : ""
  );

  return (<>
    { open && content &&
      <Card
        ref={ref}
        elevation={0}
        className={classes.printPreview + " " + classes.printTarget}
        >
        <CardContent>
          <table>
            <thead>
              <tr><td>
                { header }
              </td></tr>
            </thead>
            <tbody>
              <tr><td>
                <FormattedText>{content}</FormattedText>
              </td></tr>
            </tbody>
          </table>
        </CardContent>
      </Card>
    }
    { !disablePreview &&
      <Dialog
        open={open}
        className={classes.printPreview}
        maxWidth={width}
        fullWidth
        fullScreen={isFullScreen}
        onClose={onClose}
        {...rest}
      >
        { (title || subtitle) &&
        <DialogTitle>
          { title && <Typography variant="h4">{title}</Typography> }
          { subtitle && <Typography variant="overline" color="textSecondary">{subtitle}</Typography> }
        </DialogTitle>
        }
        <DialogContent dividers>
        { content ?
          <>
            { header }
            <FormattedText>{content}</FormattedText>
          </>
          :
          error ?
          <Typography color="error">Print preview cannot be loaded</Typography>
          :
          <CircularProgress />
        }
        </DialogContent>
        <DialogActions>
          <Button variant="contained" onClick={onClose}>Close</Button>
          <Button variant="contained" color="primary" onClick={handlePrint} disabled={!!!content}>Print</Button>
        </DialogActions>
      </Dialog>
    }
  </>);
}

PrintPreview.propTypes = {
  resourcePath: PropTypes.string.isRequired,
  resourceData: PropTypes.object,
  open: PropTypes.bool,
  disablePreview: PropTypes.bool,
  fullScreen: PropTypes.bool,
  title: PropTypes.string,
  breadcrumb: PropTypes.string,
  date: PropTypes.string,
  subtitle: PropTypes.string,
  onClose: PropTypes.func,
}

export default PrintPreview;
