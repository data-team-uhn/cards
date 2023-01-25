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

import React from "react";
import PropTypes from "prop-types";
import { CircularProgress, Fab, Tooltip } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import style from './style.jsx';

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
//  onClick={() => openComposeDialog()}
//  inProgress={dialogIsLoading}
//  />
//

function MainActionButton(props) {
  const { icon, label, title, ariaLabel, onClick, inProgress, disabled, style, classes } = props;

  let extended = !!label;

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
  classes: PropTypes.object.isRequired,
  style: PropTypes.object,
}

MainActionButton.defaultProps = {
  inProgress: false,
  disabled: false,
  style: {},
};

export default withStyles(style)(MainActionButton);
