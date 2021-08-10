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
  useScrollTrigger,
  makeStyles,
} from "@material-ui/core";

import { grey } from '@material-ui/core/colors';

import { GRID_SPACE_UNIT } from "./QuestionnaireStyle";

const useStyles = makeStyles(theme => ({
    resourceHeader: {
      position: "sticky",
      top: 0,
      paddingBottom: "0 !important",
      margin: theme.spacing(2*GRID_SPACE_UNIT, GRID_SPACE_UNIT, 0),
      backgroundColor: grey[100],
      zIndex: "1010",
      "& .MuiBreadcrumbs-root" : {
        width: "fit-content",
      },
      "& .MuiBreadcrumbs-li" : {
        color: theme.palette.text.primary,
      },
    },
    headerSeparator: {
      visibility: "hidden",
      border: "0 none",
    },
    resourceTitle: {
      backgroundColor: grey[100],
      margin: theme.spacing(0, GRID_SPACE_UNIT, GRID_SPACE_UNIT),
      paddingTop: "0 !important",
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
 *  titleAction={<DeleteButton .../>}
 *  >
 *    Subtitle or description
 * </ResourceHeader>
 *
 * Output when fully displayed:
 * --------------------------------------------------------------------
 * | Breadcrumbs / Breadcrumbs /                                      |
 * | Title                                                titleAction |
 * | Other (children)                                                 |
 * --------------------------------------------------------------------
 * Output when scrolling up:
 * --------------------------------------------------------------------
 * | Breadcrumbs / Breadcrumbs / Title               breadcrumbAction |
 * --------------------------------------------------------------------
 *
 *
 * @param {string} title  - the title of the resource, to be displayed in the header; required
 * @param {Array.<node>} breadcrumbs - the breadcrumb items displayed above the title; this line
 *  will be sticky when scrolling up, and will include the title once the title line is scrolled
 *  out of view
 * @param {string} separator - the breadcrumbs separator, defaults to /
 * @param {node} titleAction - a React node specifying a button or menu associated with the
 *   resource and displayed at the right side of the title
 * @param {node} breadcrumbAction - a React node specifying a button or menu associated with the
 *   resource and displayed at the right side of the breadcrumbs + title when the page is scrolled to
 *   hide the main title and titleAction line; it is usually a more compact version of titleAction
 * @param {Array.<node>} children - any other content that will be displayed in the header, under
 *   the title and titleAction line
 */
function ResourceHeader (props) {
  let { title, breadcrumbs, separator, titleAction, breadcrumbAction, children } = props;

  const classes = useStyles();

  const fullBreadcrumbTrigger = useScrollTrigger({
    target: window,
    disableHysteresis: true,
    threshold: 60,
  });

  return (
    <>
    <Grid item xs={12} className={classes.resourceHeader} style={{top: props.contentOffset}}>
      <Grid container direction="row" justify="space-between" alignItems="center">
        <Grid item>
          <Breadcrumbs separator={separator}>
            {Array.from(breadcrumbs || []).map(item => <Typography variant="overline" key={item}>{item}</Typography>)}
            <Collapse in={fullBreadcrumbTrigger}>
              <Typography variant="subtitle2">{title}</Typography>
            </Collapse>
          </Breadcrumbs>
        </Grid>
        <Collapse in={!!breadcrumbAction &&  fullBreadcrumbTrigger} component={Grid}>
          <div className={classes.breadcrumbAction}>{breadcrumbAction}</div>
        </Collapse>
      </Grid>
      <Collapse in={fullBreadcrumbTrigger}>
        <hr className={classes.headerSeparator} />
      </Collapse>
    </Grid>
    <Collapse in={!(fullBreadcrumbTrigger)}
      component={Grid} item xs={12} className={classes.resourceTitle}>
        <Grid container direction="row" justify="space-between" alignItems="center">
          <Grid item>
            <Typography component="h2" variant="h4">{title}</Typography>
          </Grid>
          <Grid item>{titleAction}</Grid>
        </Grid>
        {children}
    </Collapse>
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
  titleAction: PropTypes.node,
  breadcrumbAction: PropTypes.node,
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
