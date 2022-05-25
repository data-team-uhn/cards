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
import React from "react";
import classNames from "classnames";
import PropTypes from "prop-types";
import {
  Avatar,
  Button,
  Card,
  CardActions,
  CardContent,
  CardHeader,
  ClickAwayListener,
  Grow,
  IconButton,
  Link,
  Popper,
  Tooltip,
  Typography,
} from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import CloseIcon from '@mui/icons-material/Close';

import QueryStyle from "./queryStyle.jsx";

// Component that renders a dialog with term info for a single vocabulary term.
//
// Required arguments:
// open: Boolean representing whether or not the info dialog is open
// infoboxRef: Reference to the info box node
// vocabulary: Vocabulary source info
// term: Vocabulary term being displayed in the info box
// infoAboveBackground: Boolean representing whether or not the term info box is placed above the vocabulary tree dialog
// browserOpened: Boolean representing whether or not the vocabulary tree dialog is open
// onActionClick: Callback for the action button click event
// onClickAway: Callback for the click away event
// onClose: Callback for the close event
//
function InfoBox(props) {
  const { open, infoboxRef, vocabulary, term, infoAboveBackground, browserOpened, onActionClick, onClickAway, onClose, classes } = props;

  if (!term?.infoAnchor) {
    return null;
  }

  return (
    <Popper
      ref={infoboxRef}
      placement="right"
      open={open}
      anchorEl={term.infoAnchor}
      transition
      className={
        classNames({ [classes.popperClose]: !open })
        + " " + classes.popperNav
        + " " + (infoAboveBackground ? classes.infoAboveBackdrop : classes.popperInfoOnTop)
      }
      modifiers={[{
        name: 'preventOverflow',
        enabled: true,
        options: {
          rootBoundary: 'viewport',
          padding: 8,
          tether: true,
          }
        },
	    {
	      name: 'arrow',
	      enabled: true
	    }
      ]}
    >
      {({ TransitionProps }) => (
        <Grow
          {...TransitionProps}
          id="info-grow"
          style={{
            transformOrigin: "center left",
          }}
        >
          <Card className={classes.infoCard}>
            <ClickAwayListener onClickAway={onClickAway}><div>
               <CardHeader
                 avatar={
                  <Link color="textSecondary"
                    href={vocabulary.url || ""}  target="_blank"
                    component={vocabulary.url ? 'a' : 'span'}
                    underline="none"
                    >
                    <Tooltip title={vocabulary.description || ""}>
                      <Avatar aria-label="source" className={classes.vocabularyAvatar}>
                          {vocabulary.acronym}
                      </Avatar>
                    </Tooltip>
                  </Link>
                }
                action={
                  <IconButton aria-label="close" onClick={onClose} size="large">
                    <CloseIcon />
                  </IconButton>
                }
                title={term.name}
                subheader={term.id}
                titleTypographyProps={{variant: 'h5'}}
              />
              <CardContent className={classes.infoPaper}>
                <div className={classes.infoSection}>
                  <Typography className={classes.infoDefinition}>{term.definition}</Typography>
                </div>
                  {term.alsoKnownAs.length > 0 && (
                    <div className={classes.infoSection}>
                      <Typography variant="h6" className={classes.infoHeader}>Also known as</Typography>
                      {term.alsoKnownAs.map((name, index) => {
                        return (<Typography className={classes.infoAlsoKnownAs} key={index}>
                                  {name}
                                </Typography>
                        );
                      })}
                    </div>
                  )}
                  {term.typeOf.length > 0 && (
                    <div className={classes.infoSection}>
                      <Typography variant="h6" className={classes.infoHeader}>Is a type of</Typography>
                      {term.typeOf.map((name, index) => {
                        return (<Typography className={classes.typeOf} key={index}>
                                  {name}
                                </Typography>
                        );
                      })}
                    </div>
                  )}
                  </CardContent>
                  { !(browserOpened && infoAboveBackground) &&
                    <CardActions className={classes.infoPaper}>
                      <Button size="small" onClick={onActionClick} variant='contained' color='primary'>Learn more</Button>
                    </CardActions>
                  }
             </div></ClickAwayListener>
          </Card>
        </Grow>
      )}
    </Popper>
  );
}

InfoBox.propTypes = {
  open: PropTypes.bool.isRequired,
  infoboxRef: PropTypes.object.isRequired,
  vocabulary: PropTypes.object.isRequired,
  term: PropTypes.object.isRequired,
  infoAboveBackground: PropTypes.bool.isRequired,
  browserOpened: PropTypes.bool.isRequired,
  onActionClick: PropTypes.func.isRequired,
  onClickAway: PropTypes.func.isRequired,
  onClose: PropTypes.func.isRequired,
  classes: PropTypes.object.isRequired
};

export default withStyles(QueryStyle)(InfoBox);
