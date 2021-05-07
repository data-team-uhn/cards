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
import { withStyles, Avatar, Button, Card, CardActions, CardContent, CardHeader, ClickAwayListener, Grow, IconButton, Link, Popper, Tooltip, Typography } from "@material-ui/core";
import CloseIcon from '@material-ui/icons/Close';

import QueryStyle from "./queryStyle.jsx";

// Component that renders a dialog with term info for a single vocabulary term.
//
function InfoBox(props) {
  const { termInfoVisible, anchorEl, infoRef, menuPopperRef, vocabulary, closeInfo,
    term, openDialog, browserOpened, classes } = props;

  let clickAwayInfo = (event) => {
    if (menuPopperRef?.current?.contains(event.target)
      || infoRef?.current?.contains(event.target)) {
      return;
    }

    closeInfo();
  }

  return (
    <Popper
      placement="right"
      open={termInfoVisible}
      anchorEl={anchorEl}
      transition
      className={
        classNames({ [classes.popperClose]: !open })
        + " " + classes.popperNav
        + " " + (browserOpened ? classes.infoAboveBackdrop : classes.popperInfoOnTop)
      }
      ref={infoRef}
      modifiers={{
        keepTogether: {
          enabled: true
        },
        preventOverflow: {
          boundariesElement: 'viewport',
          padding: '8',
          escapeWithReference: false,
          enabled: true
        },
        arrow: {
          enabled: false
        }
      }}
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
            <ClickAwayListener onClickAway={clickAwayInfo}><div>
               <CardHeader
                 avatar={
                  <Link color="textSecondary"
                    href={vocabulary.url || ""}  target="_blank"
                    component={vocabulary.url ? 'a' : 'span'}
                    underline="none"
                    >
                    <Tooltip title={vocabulary.description}>
                      <Avatar aria-label="source" className={classes.vocabularyAvatar}>
                          {vocabulary.acronym}
                      </Avatar>
                    </Tooltip>
                  </Link>
                }
                action={
                  <IconButton aria-label="close" onClick={closeInfo}>
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
                  {!browserOpened &&
                    <CardActions className={classes.infoPaper}>
                      <Button size="small" onClick={openDialog} variant='contained' color='primary'>Learn more</Button>
                    </CardActions>
                  }
             </div></ClickAwayListener>
          </Card>
        </Grow>
      )}
    </Popper>
  );
}

export default withStyles(QueryStyle)(InfoBox);
