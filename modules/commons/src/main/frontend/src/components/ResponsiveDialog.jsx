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
import { useTheme, styled } from '@material-ui/core/styles';

const PREFIX = 'ResponsiveDialog';

const classes = {
  withCloseButton: `${PREFIX}-withCloseButton`,
  closeButton: `${PREFIX}-closeButton`
};

const StyledDialog = styled(Dialog)((
  {
    theme
  }
) => ({
  [`& .${classes.withCloseButton}`]: {
    "& .MuiDialogTitle-root" : {
      paddingRight: theme.spacing(5),
    }
  },

  [`& .${classes.closeButton}`]: {
    position: 'absolute',
    right: theme.spacing(1),
    top: theme.spacing(1),
  }
}));

const ResponsiveDialog = forwardRef((props, ref) => {
  const { title, width, children, withCloseButton, className, onClose, ...rest } = props;



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
    <StyledDialog
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
    </StyledDialog>
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
