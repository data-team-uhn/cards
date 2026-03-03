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

import { useState, useEffect } from "react";

import EmojiObjectsIcon from "@mui/icons-material/EmojiObjects";
import WarningIcon from "@mui/icons-material/Warning";
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
} from "@mui/material";
import PropTypes from "prop-types";
import { makeStyles } from 'tss-react/mui';

import { checkPropTypes } from "../propTypes";
const useStyles = makeStyles()(theme => ({
  userInputAssistant: {
    "& .MuiCard-root" : {
      maxWidth: "375px",
      border: "2px solid " + theme.palette.primary.main,
      "&.Uia-placement-right": {
        marginLeft: theme.spacing(2),
        "&:before" : {
          content: "''",
          display: "block",
          borderTop: theme.spacing(2) + " solid transparent",
          borderBottom: theme.spacing(2) + " solid transparent",
          borderRight: theme.spacing(2) + " solid " + theme.palette.primary.main,
          position: "absolute",
          left: 0,
          top: "50%",
          marginTop: theme.spacing(-2),
        },
      },
      "&.Uia-placement-bottom": {
        margin: theme.spacing(3,4,0),
      },
      "& .MuiAvatar-root" : {
        background: theme.palette.primary.main,
      },
      "&.Uia-hint-secondary" : {
        borderColor: theme.palette.secondary.main,
        "&.Uia-placement-right:before" : {
          borderRightColor: theme.palette.secondary.main,
        },
        "& .MuiAvatar-root" : {
          background: theme.palette.secondary.main,
        },
      },
      "&.Uia-success" : {
        borderColor: theme.palette.success.main,
        "&.Uia-placement-right:before" : {
          borderRightColor: theme.palette.success.main,
        },
        "& .MuiAvatar-root" : {
          background: theme.palette.success.main,
        },
      },
      "&.Uia-info" : {
        borderColor: theme.palette.info.main,
        "&.Uia-placement-right:before" : {
          borderRightColor: theme.palette.info.main,
        },
        "& .MuiAvatar-root" : {
          background: theme.palette.info.main,
        },
      },
      "&.Uia-warning" : {
        borderColor: theme.palette.warning.main,
        "&.Uia-placement-right:before" : {
          borderRightColor: theme.palette.warning.main,
        },
        "& .MuiAvatar-root" : {
          background: theme.palette.warning.main,
        },
      },
      "&.Uia-error" : {
        borderColor: theme.palette.error.main,
        "&.Uia-placement-right:before" : {
          borderRightColor: theme.palette.error.main,
        },
        "& .MuiAvatar-root" : {
          background: theme.palette.error.main,
        },
      },
    },
  },
}));

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
//  onAction={separateAndAdd}
//  onIgnore={disableSeparatorDetection}
//  >
//  Don't use comma, press ENTER!
//</UserInputAssistant>
//

function UserInputAssistant (props) {
  checkPropTypes(UserInputAssistant, props);
  const {
    anchorEl,
    variant = 'hint',
    title,
    children,
    actionLabel,
    onAction,
    onIgnore,
    onClickAway
  } = props;

  let [ enabled, setEnabled ] = useState(true);

  const { classes } = useStyles();

  let [ placement, setPlacement ] = useState("right");

  useEffect(() => {
    function handleResize() {
      setPlacement(window.innerWidth > 750 ? "right" : "bottom");
    }
    handleResize();
    window.addEventListener('resize', handleResize);
    return (() => {
      window.removeEventListener('resize', handleResize);
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
        modifiers={[
          {
            name: 'flip',
            enabled: true
          }
        ]}
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
                slotProps={{ title: { variant: "h6" } }}
              />
              <CardContent>
                { children }
              </CardContent>
              <CardActions>
                { actionLabel && onAction &&
            <Button variant="outlined" onClick={onAction}>{actionLabel}</Button>
                }
                { onIgnore ?
                  <Button variant="outlined" onClick={() => {setEnabled(false); onIgnore();}}>Ignore for now</Button>
                  :
                  <Button variant="outlined" onClick={() => setEnabled(false)}>Got it!</Button>
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

export default UserInputAssistant;
