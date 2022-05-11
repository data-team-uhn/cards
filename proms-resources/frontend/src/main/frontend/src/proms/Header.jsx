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
import React, { useState } from "react";
import { styled } from '@mui/material/styles';
import {
  AppBar,
  Breadcrumbs,
  Collapse,
  Fade,
  LinearProgress,
  Link,
  Toolbar,
  Typography,
  makeStyles,
  useScrollTrigger,
} from "@material-ui/core";

const PREFIX = 'PromsHeader';

const classes = {
  appbar: `${PREFIX}-appbar`,
  toolbar: `${PREFIX}-toolbar`,
  titleLine: `${PREFIX}-titleLine`,
  logo: `${PREFIX}-logo`,
  greeting: `${PREFIX}-greeting`,
  fullSize: `${PREFIX}-fullSize`,
  collapsed: `${PREFIX}-collapsed`
};

// TODO jss-to-styled codemod: The Fragment root was replaced by div. Change the tag if needed.
const Root = styled('div')((
  {
    theme
  }
) => ({
  [`& .${classes.appbar}`]: {
    margin: theme.spacing(-1, -1, 4),
    padding: theme.spacing(0, 1),
    boxSizing: "content-box",
    background: "transparent",
    color: theme.palette.text.primary,
    boxShadow: "none",
  },

  [`& .${classes.toolbar}`]: {
    display: "flex",
    justifyContent: "space-between",
    background: theme.palette.background.paper,
    paddingTop: theme.spacing(1),
    paddingBottom: theme.spacing(1),
  },

  [`& .${classes.titleLine}`]: {
    display: "flex",
    alignItems: "center",
  },

  [`& .${classes.logo}`]: {
    maxHeight: theme.spacing(6),
    marginRight: theme.spacing(2),
    "@media (max-width: 400px)" : {
      maxHeight: theme.spacing(4),
    }
  },

  [`& .${classes.greeting}`]: {
    "@media (max-width: 600px)" : {
      display: "none",
    },
  },

  [`& .${classes.fullSize}`]: {
    paddingTop: theme.spacing(5),
    "&.MuiToolbar-root > .MuiTypography-root" : {
      zoom: 1.2,
    }
  },

  [`& .${classes.collapsed}`]: {
    display: "none",
  }
}));

function PromsHeader (props) {
  const { title, greeting, progress, subtitle, step } = props;



  const scrollTrigger = useScrollTrigger({
    target: window,
    disableHysteresis: true,
    threshold: 250,
  });

  let subtitleBar = subtitle ?
    <Toolbar variant="dense" className={classes.toolbar}>
      <Typography variant="h6" color="textPrimary">{ subtitle }</Typography>
      { step }
    </Toolbar>
    : <Root></Root>;

  return (
    <>
    <AppBar position="sticky" className={classes.appbar}>
      <Collapse in={!subtitle || !(scrollTrigger)}>
        <Toolbar variant="dense" className={classes.toolbar}>
          <div className={classes.titleLine}>
            <img src={document.querySelector('meta[name="logoLight"]').content} alt="logo" className={classes.logo} />
            { title &&
              <Typography variant="overline" color="textPrimary">{ title }</Typography>
            }
          </div>
          <Breadcrumbs separator = "·">
            {greeting && <span className={classes.greeting}>{ greeting }</span>}
            <Link href="/system/sling/logout">Sign out</Link>
          </Breadcrumbs>
        </Toolbar>
      </Collapse>
      { subtitle && <Collapse in={scrollTrigger}>{subtitleBar}</Collapse> }
      <LinearProgress variant="determinate" value={progress} />
      { subtitle && <Fade in={!scrollTrigger} className={(scrollTrigger ? classes.collapsed : '') + ' ' + classes.fullSize + ' ' + classes.toolbar}>{subtitleBar}</Fade> }
    </AppBar>
    {/* We render another copy of the full size subtitle to maintain the same content height when the first one
        disappears and thus prevent the subtitle from "jumping" between full size and compact when scrollTrigger
        becomes true. */}
    { subtitle && scrollTrigger && <div className={classes.fullSize + ' ' + classes.toolbar}>{subtitleBar}</div> }
    </>
  );
}

export default PromsHeader;
