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
import {
  AppBar,
  Breadcrumbs,
  Collapse,
  Fade,
  LinearProgress,
  Link,
  Toolbar,
  Typography,
  useScrollTrigger,
} from "@mui/material";

import { useMediaQuery } from "@mui/material";
import { useTheme } from '@mui/material/styles';
import makeStyles from '@mui/styles/makeStyles';

const useStyles = makeStyles(theme => ({
  appbar : {
    margin: theme.spacing(-1, -1, 4),
    [theme.breakpoints.down('md')]: {
      margin: theme.spacing(0, -1),
    },
    padding: theme.spacing(0, 1),
    boxSizing: "content-box",
    background: "transparent",
    color: theme.palette.text.primary,
    boxShadow: "none",
  },
  toolbar : {
    display: "flex",
    justifyContent: "space-between",
    background: theme.palette.background.paper,
    paddingTop: theme.spacing(1),
    paddingBottom: theme.spacing(1),
  },
  titleLine : {
    display: "flex",
    alignItems: "center",
  },
  logo : {
    maxHeight: theme.spacing(6),
    marginRight: theme.spacing(2),
    "@media (max-width: 400px)" : {
      maxHeight: theme.spacing(4),
    }
  },
  greeting: {
    "@media (max-width: 600px)" : {
      display: "none",
    },
  },
  fullSize : {
    paddingTop: theme.spacing(5),
    maxWidth: "780px",
    margin: "auto",
    "&.MuiToolbar-root > .MuiTypography-root" : {
      zoom: 1.2,
    }
  },
  collapsed : {
    display: "none",
  },
}));

function Header (props) {
  const { title, greeting, progress, subtitle, step } = props;

  const classes = useStyles();

  const scrollTrigger = useScrollTrigger({
    target: window,
    disableHysteresis: true,
    threshold: 250,
  });

  const theme = useTheme();
  const appbarExpanded = useMediaQuery(theme.breakpoints.up('md'));
  const contentOffset = document?.getElementById('page-start-wrapper-content')?.style.top || 0;

  let subtitleBar = subtitle ?
    <Toolbar variant="dense" className={classes.toolbar}>
      <Typography variant="h6" color="textPrimary">{ subtitle }</Typography>
      { step }
    </Toolbar>
    : <></>;

  return (
    <>
    <AppBar position="sticky" className={classes.appbar} style={ { top: appbarExpanded ? contentOffset: 0 }}>
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
            <Link href="/system/sling/logout" underline="hover" onClick={(event) => {event.preventDefault(); window.location = "/system/sling/logout?resource=" + encodeURIComponent(window.location.pathname);}}>Sign out</Link>
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

export default Header;
