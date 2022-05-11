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

import React, { forwardRef } from "react";
import PropTypes from "prop-types";

import { Dialog, DialogTitle, IconButton, useMediaQuery } from "@material-ui/core";

import makeStyles from '@material-ui/styles/makeStyles';

import CloseIcon from '@material-ui/icons/Close';
import { useTheme } from '@material-ui/core/styles';

// Component that renders the Dialog containers that expand to full screen once
// the screen becomes more narrow than the specified width
//
// Optional props:
// title: String specifying the title of the dialog
// withCloseButton: Boolean specifying whether the dialog should have a Close button (x)
//   at the top-right corner
// width: the default size of the dialog, and the screen size that makes it go
//   fullScreen. One of xs, sm, md, lg, xl. Defaults to sm.
// children: the dialog contents
// onClose: Callback for closing the dialog
//
// Sample usage:
//<ResponsiveDialog
//  title="Select a subject"
//  withCloseButton
//  open={open}
//  onClose={selectSubject}
//  >
//  <DialogContent dividers>
//    {...}
//  </DialogContent>
//  <DialogActions>
//    {...}
//  </DialogActions>
//</ResponsiveDialog>
//

const useStyles = makeStyles(theme => ({
  withCloseButton: {
    "& .MuiDialogTitle-root" : {
      paddingRight: theme.spacing(5),
    }
  },
  closeButton: {
    position: 'absolute',
    right: theme.spacing(1),
    top: theme.spacing(1),
  },
}));

const ResponsiveDialog = forwardRef((props, ref) => {
  const { title, width, children, withCloseButton, className, onClose, ...rest } = props;

  const classes = useStyles();

  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down(width));

  let closeButton = withCloseButton ?
    <IconButton aria-label="close" className={classes.closeButton} onClick={onClose} size="large">
      <CloseIcon />
    </IconButton>
    : null;

  let classNames = [];
  if (className) classNames.push(className);
  if (withCloseButton) classNames.push(classes.withCloseButton);
  classNames = classNames.length ? classNames.join(' ') : undefined;

  return (
    <Dialog
      ref={ref}
      className={classNames}
      maxWidth={width}
      fullWidth
      fullScreen={fullScreen}
      onClose={onClose}
      {...rest}
    >
      { title && <DialogTitle>{title}{closeButton}</DialogTitle>}
      { children }
    </Dialog>
  );
})

ResponsiveDialog.propTypes = {
  title: PropTypes.string,
  width: PropTypes.oneOf(["xs", "sm", "md", "lg", "xl"]),
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  withCloseButton: PropTypes.bool,
  onClose: PropTypes.func,
}

ResponsiveDialog.defaultProps = {
  width: "sm",
};

export default ResponsiveDialog;
