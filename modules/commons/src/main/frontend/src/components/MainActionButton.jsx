/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/

import { CircularProgress, Fab, Tooltip } from "@mui/material";
import PropTypes from "prop-types";
import { makeStyles } from 'tss-react/mui';

import { checkPropTypes } from "../propTypes";

const useStyles = makeStyles()(theme => ({
  mainActionButton: {
    margin: theme.spacing(1),
    position: "fixed",
    bottom: theme.spacing(2),
    right: theme.spacing(4),
    zIndex: 100,
    "& .MuiCircularProgress-root" : {
      position: 'absolute',
      top: "50%",
      left: "50%",
      marginTop: "-28px",
      marginLeft: "-28px",
    },
    "& .MuiFab-extended + .MuiCircularProgress-root" : {
      marginTop: "-16px",
      marginLeft: "-16px",
    },
    "& .MuiFab-extended .MuiSvgIcon-root" : {
      marginRight: theme.spacing(1),
    },
    "& .MuiFab-extended .MuiFab-label" : {
      marginRight: theme.spacing(1),
    },
  },
}));

// Component that renders a floating action button (Fab) at the bottom right of the screen,
// to be used as the main action for a specific page.
//
// Example use case: "Save" button when editing a form
//
// Required props:
// icon: the icon displayed on the button
// onClick: the "action" of the button
//
// Optional props:
// label: the button text; if label is missing, a round Fab is rendered; if label is provided,
//   an extended Fab is rendered
// title: the title (tooltip) displayed when hovering the button
// inProgress: whether the action is currently in progress, in which case the button is
//   disabled and a CircularProgress is displayed on top of it.
// ariaLabel: defines a string that labels the current element, useful in cases where a
//   text label is not visible on the screen
//
// Sample usage:
//<MainActionButton
//  icon={<CreateIcon />}
//  title="Compose a new message"
//  label="Compose"
//  onClick={openComposeDialog}
//  inProgress={dialogIsLoading}
//  />
//

function MainActionButton(props) {
  checkPropTypes(MainActionButton, props);
  const {
    icon,
    label,
    title,
    ariaLabel,
    inProgress,
    disabled,
    style,
    onClick
  } = props;

  let extended = !!label;

  const { classes } = useStyles();

  let button = (
    <div className={classes.mainActionButton} style={style}>
      <Fab
        variant={extended ? "extended" : "round"}
        color="primary"
        onClick={onClick}
        disabled={inProgress || disabled}
        aria-label={ariaLabel}
      >
        {icon}{label}
      </Fab>
      {inProgress && <CircularProgress size={extended ? 32 : 56} />}
    </div>
  );

  return (
    <>
      { title ?
        <Tooltip title={title}>
          {button}
        </Tooltip>
        :
        button
      }
    </>
  );
}

MainActionButton.propTypes = {
  icon: PropTypes.element.isRequired,
  label: PropTypes.string,
  title: PropTypes.string,
  ariaLabel: PropTypes.string,
  onClick: PropTypes.func.isRequired,
  inProgress: PropTypes.bool,
  disabled: PropTypes.bool,
  style: PropTypes.object,
}

export default MainActionButton;
