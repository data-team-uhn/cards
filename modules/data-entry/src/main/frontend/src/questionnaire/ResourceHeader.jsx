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
import PropTypes from "prop-types";

import {
  Breadcrumbs,
  Collapse,
  Grid,
  Typography,
  useScrollTrigger
} from "@mui/material";

import makeStyles from '@mui/styles/makeStyles';

import { grey } from '@mui/material/colors';

import { GRID_SPACE_UNIT } from "./QuestionnaireStyle";
import { useMediaQuery } from "@mui/material";
import { useTheme } from '@mui/material/styles';

const useStyles = makeStyles(theme => ({
    resourceHeader: {
      position: "sticky",
      top: 0,
      padding: `${theme.spacing(GRID_SPACE_UNIT, GRID_SPACE_UNIT, GRID_SPACE_UNIT)} !important`,
      margin: theme.spacing(3*GRID_SPACE_UNIT, 0, 0, 2*GRID_SPACE_UNIT),
      backgroundColor: grey[100],
      zIndex: "1010",
      "& .MuiBreadcrumbs-root" : {
        width: "fit-content",
      },
      "& .MuiBreadcrumbs-li" : {
        color: theme.palette.text.primary,
      },
    },
    breadcrumbAction: {
      margin: theme.spacing(-1.25, 0, -2.25),
    },
    resourceTitle: {
      backgroundColor: grey[100],
      margin: theme.spacing(0, 0, 0, 2*GRID_SPACE_UNIT),
      padding: `${theme.spacing(0, GRID_SPACE_UNIT, GRID_SPACE_UNIT)} !important`,
      zIndex: 2,
    }
}))

/**
 * Component that displays the header of a resource (e.g. a form, a subject, a questionnaire)
 * with breadcrumbs and title, actions. The breadcrumbs are sticky and will include the title
 * and actions once the actual title is scrolled out of view.
 *
 * @example
 * <ResourceHEader
 *  title="..."
 *  breadcrumbs={}
 *  action={<DeleteButton .../>}
 *  >
 *    Subtitle or description
 * </ResourceHeader>
 *
 * Output when fully displayed:
 * --------------------------------------------------------------------
 * | Breadcrumbs / Breadcrumbs /                                      |
 * | Title                                                     action |
 * | Other (children)                                                 |
 * --------------------------------------------------------------------
 * Output when scrolling up:
 * --------------------------------------------------------------------
 * | Breadcrumbs / Breadcrumbs / Title                         action |
 * --------------------------------------------------------------------
 *
 *
 * @param {string} title  - the title of the resource, to be displayed in the header; required
 * @param {Array.<node>} breadcrumbs - the breadcrumb items displayed above the title; this line
 *  will be sticky when scrolling up, and will include the title once the title line is scrolled
 *  out of view
 * @param {string} separator - the breadcrumbs separator, defaults to /
 * @param {node} action - a React node specifying a button or menu associated with the
 *   resource and displayed at the right side of the title
 * @param {Array.<node>} children - any other content that will be displayed in the header, under
 *   the title and titleAction line
 */
function ResourceHeader (props) {
  let { title, breadcrumbs, separator, tags, action, children } = props;

  const classes = useStyles();
  const theme = useTheme();
  const appbarExpanded = useMediaQuery(theme.breakpoints.up('md'));

  // Scroll trigger for collapsing the Title and action into the breadcrumbs
  const fullBreadcrumbTrigger = useScrollTrigger({
    target: window,
    disableHysteresis: true,
    threshold: 120,
  });

  return (
    <>
    <Grid item xs={12} className={classes.resourceHeader} style={ { top: appbarExpanded ? props.contentOffset: 0 }} id="cards-resource-header">
      <Grid container direction="row" justifyContent="space-between" alignItems="center" wrap="nowrap">
        <Grid item>
          <Breadcrumbs separator={separator}>
            {Array.from(breadcrumbs || []).map(item => <Typography variant="overline" key={item}>{item}</Typography>)}
            <Collapse in={fullBreadcrumbTrigger}>
              <Typography variant="subtitle2">{title}</Typography>
            </Collapse>
          </Breadcrumbs>
        </Grid>
        <Collapse in={!!action &&  fullBreadcrumbTrigger} component={Grid} item>
          { fullBreadcrumbTrigger && <div className={classes.breadcrumbAction}>{action}</div> }
        </Collapse>
      </Grid>
    </Grid>
    <Grid item xs={12} className={classes.resourceTitle}>
       <Grid container direction="row" justifyContent="space-between" alignItems="start" spacing={1}>
          <Grid item>
            <Grid container direction="row" spacing={1} alignItems="center">
              <Grid item><Typography component="h2" variant="h4">{title}</Typography></Grid>
              {tags?.map((t, i) => <Grid item key={`resource-tag-${i}`}>{t}</Grid>)}
            </Grid>
          </Grid>
          {action && !fullBreadcrumbTrigger && <Grid item>{action}</Grid>}
       </Grid>
       {children}
    </Grid>
    </>
  )
};

ResourceHeader.propTypes = {
  title: PropTypes.string.isRequired,
  breadcrumbs: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  separator: PropTypes.string,
  tags: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  action: PropTypes.node,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  contentOffset: PropTypes.number,
}

ResourceHeader.defaultProps = {
  separator: "/",
  contentOffset: 0,
}

export default ResourceHeader;
