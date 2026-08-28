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
import { useContext } from "react";

import {
  AppBar,
  Collapse,
  Fade,
  LinearProgress,
  Link,
  Toolbar,
  Typography,
  useScrollTrigger,
} from "@mui/material";
import { makeStyles } from 'tss-react/mui';

import Logo from "../components/Logo";
import { PageStartContext } from "../PageStartWrapper";

const useStyles = makeStyles()(theme => ({
  appbar : {
    margin: theme.spacing(-1, -1, 4),
    [theme.breakpoints.down('md')]: {
      margin: theme.spacing(0, -1),
    },
    padding: theme.spacing(0, 1),
    boxSizing: "content-box",
    background: theme.palette.background.paper,
    color: theme.palette.text.primary,
    boxShadow: "none",
  },
  utility : {
    maxWidth: theme.width.main,
    margin: "auto",
    display: "flex",
    justifyContent: "flex-end",
    paddingRight: theme.spacing(2),
    [theme.breakpoints.up('sm')]: {
      paddingRight: theme.spacing(3),
    },
  },
  userMenu : {
    display: "flex",
    alignItems: "center",
    gap: theme.spacing(1.5),
    minWidth: 0,
    maxWidth: "100%",
    marginRight: theme.spacing(-1.5),
    padding: theme.spacing(0.5, 1.5),
    background: theme.palette.action.selected,
    borderBottomLeftRadius: theme.spacing(1),
    borderBottomRightRadius: theme.spacing(1),
    color: theme.palette.text.secondary,
    fontSize: theme.typography.body2.fontSize,
  },
  toolbar : {
    maxWidth: theme.width.main,
    margin: "auto",
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    gap: theme.spacing(2),
    background: theme.palette.background.paper,
    paddingTop: theme.spacing(1),
    paddingBottom: theme.spacing(1),
  },
  brand : {
    display: "flex",
    alignItems: "center",
    gap: theme.spacing(1.5),
    minWidth: 0,
    maxWidth: "50%",
  },
  logo : {
    flexShrink: 0,
    "& > img" : {
      [`@media (max-width: ${theme.width.compact}px)`] : {
        maxHeight: theme.spacing(4),
      }
    }
  },
  bar : {
    alignSelf: "center",
    flexShrink: 0,
    width: "1px",
    height: theme.spacing(4),
    backgroundColor: theme.palette.divider,
  },
  surveyTitle : {
    minWidth: 0,
    fontWeight: "bold",
    lineHeight: 1.2,
  },
  affiliation : {
    flexShrink: 0,
    maxWidth: "160px",
    [`@media (max-width: ${theme.width.compact}px)`] : {
      display: "none",
    },
  },
  greeting: {
    minWidth: 0,
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
  },
  signout: {
    flexShrink: 0,
  },
  fullSize : {
    paddingTop: theme.spacing(5),
    width: `calc(100% - ${theme.spacing(5)})`,
    margin: "auto",
    "&.MuiToolbar-root > .MuiTypography-root" : {
      zoom: 1.2,
    }
  },
  collapsed : {
    display: "none !important",
  },
}));

function Header (props) {
  const { title, greeting, withSignout, progress, subtitle, step } = props;

  const { classes } = useStyles();

  const scrollTrigger = useScrollTrigger({
    target: window,
    disableHysteresis: true,
    threshold: 200,
  });

  const contentOffset = useContext(PageStartContext);

  const affiliationLogo = document.querySelector('meta[name="affiliationLogoLight"]')?.content;

  let subtitleBar = subtitle ?
    <Toolbar variant="dense" className={classes.toolbar}>
      <Typography variant="h6" color="textPrimary">{ subtitle }</Typography>
      { step }
    </Toolbar>
    : <></>;

  return (
    <>
      <AppBar position="sticky" className={classes.appbar} id="patient-portal-header" style={{ top: contentOffset }}>
        <Collapse in={!subtitle || !(scrollTrigger)}>
          { (greeting || withSignout) &&
            <div className={classes.utility}>
              <div className={classes.userMenu}>
                { greeting && <span className={classes.greeting}>{ greeting }</span> }
                { withSignout &&
                  <Link
                    className={classes.signout}
                    href="/system/sling/logout"
                    underline="hover"
                    onClick={(event) => {
                      event.preventDefault();
                      window.location = "/system/sling/logout?resource=" + encodeURIComponent(window.location.pathname);
                    }}
                  >
                    Sign out
                  </Link>
                }
              </div>
            </div>
          }
          <Toolbar variant="dense" className={classes.toolbar}>
            <div className={classes.brand}>
              <Logo disableAffiliation className={classes.logo} maxWidth="160px" />
              { title &&
                <>
                  <span className={classes.bar} />
                  <Typography
                    variant="overline"
                    color="textSecondary"
                    component="div"
                    className={`cards-patientPortal-surveyTitle ${classes.surveyTitle}`}
                  >
                    { title }
                  </Typography>
                </>
              }
            </div>
            { affiliationLogo &&
              <img src={affiliationLogo} alt="" className={classes.affiliation} />
            }
          </Toolbar>
        </Collapse>
        { subtitle && <Collapse in={scrollTrigger}>{subtitleBar}</Collapse> }
        <LinearProgress variant="determinate" value={progress} />
        { subtitle &&
          <Fade
            in={!scrollTrigger}
            className={(scrollTrigger ? classes.collapsed : '') + ' ' + classes.fullSize + ' ' + classes.toolbar}
          >
            {subtitleBar}
          </Fade>
        }
      </AppBar>
      {/* We render another copy of the full size subtitle to maintain the same content height when the first one
        disappears and thus prevent the subtitle from "jumping" between full size and compact when scrollTrigger
        becomes true. */}
      { subtitle && scrollTrigger && <div className={classes.fullSize + ' ' + classes.toolbar}>{subtitleBar}</div> }
    </>
  );
}

export default Header;
