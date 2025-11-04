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

import CloseIcon from '@mui/icons-material/Close';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  IconButton,
} from "@mui/material";
import PropTypes from "prop-types";
import { makeStyles } from 'tss-react/mui';

import { checkPropTypes } from "../propTypes";

const useStyles = makeStyles()(theme => ({
  titleBar: {
    color: theme.palette.error.main,
    paddingRight: theme.spacing(5),
  },
  closeButton: {
    position: 'absolute',
    right: theme.spacing(1),
    top: theme.spacing(1),
  },
}));

// Component that renders an Error Dialog with a red title and a close button
//
// Props:
// title: String specifying the title of the dialog. Defaults to "Error".
// children: the dialog contents
// onClose: Callback for closing the dialog
//
// Sample usage:
// <ErrorDialog
//   title="Failed to save data"
//   open={open}
//   onClose={handleClose}
//  >
//    Saving failed due to an unknown error.
// </ErrorDialog>
//
const ErrorDialog = (props) => {
  checkPropTypes(ErrorDialog, props);
  const {
    title = "Error",
    children,
    onClose,
    maxWidth = "xs",
    fullWidth = true,
    ...rest
  } = props;

  const { classes } = useStyles();

  return (
    <Dialog onClose={onClose} maxWidth={maxWidth} fullWidth={fullWidth} {...rest}>
      <DialogTitle className={classes.titleBar}>
        {title}
        <IconButton onClick={onClose} className={classes.closeButton} size="large">
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        {children}
      </DialogContent>
    </Dialog>
  );
}

ErrorDialog.propTypes = {
  title: PropTypes.string,
  maxWidth: PropTypes.oneOf(["xs", "sm", "md", "lg", "xl"]),
  fullWidth: PropTypes.bool,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  onClose: PropTypes.func,
}

export default ErrorDialog;
