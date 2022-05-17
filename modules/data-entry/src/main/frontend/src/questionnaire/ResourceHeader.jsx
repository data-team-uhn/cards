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
import { styled } from '@mui/material/styles';
import PropTypes from "prop-types";

import { Breadcrumbs, Collapse, Grid, Typography, useScrollTrigger } from "@material-ui/core";

import makeStyles from '@material-ui/styles/makeStyles';

import { grey } from '@material-ui/core/colors';

import { GRID_SPACE_UNIT } from "./QuestionnaireStyle";

const PREFIX = 'ResourceHeader';

const classes = {
  resourceHeader: `${PREFIX}-resourceHeader`,
  breadcrumbAction: `${PREFIX}-breadcrumbAction`,
  headerSeparator: `${PREFIX}-headerSeparator`,
  resourceTitle: `${PREFIX}-resourceTitle`
};

// TODO jss-to-styled codemod: The Fragment root was replaced by div. Change the tag if needed.
const Root = styled('div')((
  {
    theme
  }
) => ({
  [`& .${classes.resourceHeader}`]: {
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

  [`& .${classes.breadcrumbAction}`]: {
    margin: theme.spacing(-1.25, 0),
  },

  [`& .${classes.headerSeparator}`]: {
    visibility: "hidden",
    border: "0 none",
    height: theme.spacing(2),
    margin: 0,
  },

  [`& .${classes.resourceTitle}`]: {
    backgroundColor: grey[100],
    margin: theme.spacing(0, GRID_SPACE_UNIT, GRID_SPACE_UNIT),
    paddingTop: "0 !important",
    zIndex: 2,
  }
}));

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
  let { title, breadcrumbs, separator, action, children } = props;



  const fullBreadcrumbTrigger = useScrollTrigger({
    target: window,
    disableHysteresis: true,
    threshold: 60,
  });

  return (
    (<Root>
      <Grid item xs={12} className={classes.resourceHeader} style={{top: props.contentOffset}} id="cards-resource-header">
        <Grid container direction="row" justify="space-between" alignItems="center" wrap="nowrap">
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
            {action && !fullBreadcrumbTrigger && <Grid item>{action}</Grid>}
          </Grid>
          {children}
      </Collapse>
    </Root>)
  );
}

ResourceHeader.propTypes = {
  title: PropTypes.string.isRequired,
  breadcrumbs: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  separator: PropTypes.string,
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
