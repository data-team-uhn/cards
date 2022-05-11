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

import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

import {
  Avatar,
  Button,
  Card,
  CardActions,
  CardContent,
  CardHeader,
  ClickAwayListener,
  Fade,
  Popper,
} from "@material-ui/core";
import withStyles from '@material-ui/styles/withStyles';
import EmojiObjectsIcon from "@material-ui/icons/EmojiObjects";
import WarningIcon from "@material-ui/icons/Warning";

import style from "./style.jsx";

// Component that renders a hint/tooltip/suggested action to be
// displayed to the user as they do data entry
//
// Example use case: Inform the user entering coma-separated values
// in an input that they should press enter after each value instead
//
// Required props:
// title: The title of the hint
//
// Optional props:
// anchorEl: the element this assistant is associated with, if absent, the message will
//   not be displayed
// variant: dictates the accent color and icon; one of:
//   *  hint (primary color, lightbulb icon),
//   *  hint-secondary (secondary color, lightbulb icon),
//   *  success (success color, lightbulb icon),
//   *  info (info color, lightbulb icon),
//   *  warning (warning color, warning icon),
//   *  error (error color, warning icon),
// actionLabel: the label of the button that will trigger the action suggested by the
//   assistant; if absent, no button is displayed
// onAction: the function to perform the action suggested by the assistant; if absent,
//   no button is displayed
// onIgnore: if present, an 'Ignore for now' button is displayed, that disables the
//   assistant for the current interaction with anchorEl and runs the callback function
// onClickAway: function called when the user clicks away from the assistant element
// children: message to display
//
// Sample usage:
//<UserInputAssistant
//  anchorEl={input}
//  variant={hint-secondary}
//  title="Separator detected"
//  actionLabel="Separate and add"
//  onAction={() => separateAndAdd()}
//  onIgnore={() => disableSeparatorDetection()}
//  >
//  Don't use comma, press ENTER!
//</UserInputAssistant>
//

function UserInputAssistant (props) {
  const { anchorEl, variant, title, children, actionLabel, onAction, onIgnore, onClickAway, classes } = {...props};
  let [ enabled, setEnabled ] = useState(true);

  let [ placement, setPlacement ] = useState("right");

  useEffect(() => {
    function handleResize() {
      setPlacement(window.innerWidth > 750 ? "right" : "bottom");
    }
    handleResize();
    window.addEventListener('resize', handleResize)
    return (() => {
      window.removeEventListener('resize', handleResize)
    });
  }, []);

  return (enabled ?
  <ClickAwayListener onClickAway={onClickAway}>
    <Popper
      className={classes.userInputAssistant}
      open={!!anchorEl}
      anchorEl={anchorEl}
      placement={placement}
      transition
      modifiers={{
        flip: {
          enabled: false,
        }
      }}
      >
    {({ TransitionProps }) => (
      <Fade {...TransitionProps} timeout={350}>
        <Card className={`Uia-${variant} Uia-placement-${placement}`}>
          <CardHeader
            avatar={<Avatar>
            {
              ['warning', 'error'].includes(variant) ?
              <WarningIcon/> : <EmojiObjectsIcon />
            }
            </Avatar>}
            title={title}
            titleTypographyProps={{variant: "h6"}}
            />
          <CardContent>
            { children }
          </CardContent>
          <CardActions>
          { actionLabel && onAction &&
            <Button onClick={onAction}>{actionLabel}</Button>
          }
          { onIgnore ?
            <Button onClick={() => {setEnabled(false); onIgnore();}}>Ignore for now</Button>
            :
            <Button onClick={() => {setEnabled(false)}}>Got it!</Button>
          }
          </CardActions>
        </Card>
      </Fade>
    )}
    </Popper>
  </ClickAwayListener>
  : null);
}
UserInputAssistant.propTypes = {
  anchorEl: PropTypes.object,
  title: PropTypes.string.isRequired,
  variant: PropTypes.oneOf(['hint', 'hint-secondary', 'success', 'info', 'warning', 'error']),
  actionLabel: PropTypes.string,
  onAction: PropTypes.func,
  onIgnore: PropTypes.func,
  onClickAway: PropTypes.func,
};
UserInputAssistant.defaultProps = {
  variant: 'hint',
}
export default withStyles(style)(UserInputAssistant);
