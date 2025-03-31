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

import React, { useReducer, useEffect } from "react";
import PropTypes from "prop-types";

import {
  Button,
  Dialog, DialogTitle, DialogContent,
  IconButton,
} from "@mui/material";

import makeStyles from '@mui/styles/makeStyles';

import CloseIcon from '@mui/icons-material/Close';

import ResponsiveDialog from "./ResponsiveDialog";

const useStyles = makeStyles(theme => ({
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


// Define reducer for error component
const initialState = {
  // error: null,
  openDialog: false,
  expandDetails: false,
};
const RESET = 'RESET';
// const SET_ERROR = 'SET_ERROR';
const TOGGLE_DIALOG = 'TOGGLE_DIALOG';
const TOGGLE_DETAILS = 'TOGGLE_DETAILS';
const errorReducer = (state, action) => {
  const payload = action.payload;
  switch (action.type) {
    case RESET:
      return initialState;
    // case SET_ERROR:
    //   const { error } = payload;
    //   return { ...state, error };
    case TOGGLE_DIALOG:
      const { toggle } = payload;
      return { ...state, openDialog: toggle };
    case TOGGLE_DETAILS:
      return { ...state, expandDetails: !state.expandDetails };
    default:
      console.warn('Invalid error dialog state')
      return state;
  }
}

function useErrorDialog() {
  const [state, dispatch] = useReducer(errorReducer, initialState);

  useEffect(() => {
    if (state.error) {
      dispatch({ type: TOGGLE_DIALOG, payload: { toggle: true } });
    } else {
      dispatch({ type: TOGGLE_DIALOG, payload: { toggle: false } });
    }
  }, [state.error]);

  return {
    state,
    dispatch: {
      reset: () => dispatch({ type: RESET }),
      // setError: (error) => dispatch({ type: SET_ERROR, payload: { error } }),
      toggleDialog: (toggle) => dispatch({ type: TOGGLE_DIALOG, payload: { toggle } }),
      toggleDetails: () => dispatch({ type: TOGGLE_DETAILS }),
    }
  }
}


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
  const {
    title,
    children, //remove
    message, details, actions = [], refreshable = false,
    onClose,
    ...rest } = props;
  const { state, dispatch } = useErrorDialog();


  const { error, openDialog, expandDetails } = state;

  // Can destructure error code here
  console.log(error)

  const handleOnClose = () => {
    dispatch.reset();
    onClose();
  };

  return (
    <ResponsiveDialog
      title={title}
      onClose={handleOnClose}
      withCloseButton={actions.length == 0}
      {...rest}
    >
      <DialogContent>
        {children || (
          <>
            {message}
            {/* Add divider and show details button as text */}
            {(!!details) && (
              <>
                <Button variant="text" onClick={() => { dispatch.toggleDetails() }}>
                  See {expandDetails ? ' less ' : ' more '} details...
                </Button>
                {expandDetails && (
                  <>
                    <Divider />
                    {details}
                  </>
                )}
              </>
            )}
          </>
        )}

      </DialogContent>

      {actions.length > 0 && (
        <DialogActions>
          {actions}
          {refreshable && (
            <Button onClick={() => { window.location.reload(); }}>
              Refresh
            </Button>
          )}
        </DialogActions>
      )}
    </ResponsiveDialog>
  )

  const classes = useStyles();

  return (
    <Dialog onClose={onClose} {...rest}>
      <DialogTitle className={classes.titleBar}>
        {title}
        <IconButton onClick={onClose} className={classes.closeButton} size="large">
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        {children}
        {!!details && (
          <>
            {details}
          </>
        )}
      </DialogContent>
      {actions.length > 0 && (
        <DialogActions>
          {actions}
        </DialogActions>
      )}
    </Dialog>
  );
}

ErrorDialog.propTypes = {
  title: PropTypes.string.isRequired,
  maxWidth: PropTypes.oneOf(["xs", "sm", "md", "lg", "xl"]),
  fullWidth: PropTypes.bool.isRequired,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  actions: PropTypes.arrayOf(PropTypes.node),
  onClose: PropTypes.func,
}

ErrorDialog.defaultProps = {
  title: "Error",
  maxWidth: "xs",
  fullWidth: true,
};

export default ErrorDialog;
