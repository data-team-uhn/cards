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
  Grid,
  Typography,
  Zoom,
  useScrollTrigger,
  makeStyles,
} from "@material-ui/core";

import { grey } from '@material-ui/core/colors';

import { GRID_SPACE_UNIT } from "./QuestionnaireStyle";

const useStyles = makeStyles(theme => ({
    resourceHeader: {
      position: "sticky",
      top: 0,
      margin: theme.spacing(2*GRID_SPACE_UNIT, GRID_SPACE_UNIT, 0),
      backgroundColor: grey[100],
      zIndex: "1010",
      "& .MuiBreadcrumbs-li" : {
        color: theme.palette.text.primary,
      },
    },
    currentItem : {
      display: "flex",
      alignItems: "center",
    },
    breadcrumbAction : {
      marginLeft: theme.spacing(2),
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
 *  breadcrumb={}
 *  titleAction={<DeleteButton .../>}
 *  >
 *    Subtitle or description
 * </ResourceHeader>
 *
 */
function ResourceHeader (props) {
  let { title, breadcrumbs, separator, titleAction, breadcrumbAction, children } = props;

  const classes = useStyles();

  const fullBreadcrumbTrigger = useScrollTrigger({
    target: window,
    disableHysteresis: true,
    threshold: 120,
  });

  return (
    <>
    <Grid item xs={12} className={classes.resourceHeader} style={{top: props.contentOffset}}>
      <Breadcrumbs separator={separator}>
        {Array.from(breadcrumbs || []).map(item => <Typography variant="overline" key={item}>{item}</Typography>)}
        <Zoom in={fullBreadcrumbTrigger}>
          <div className={classes.currentItem}>
            <Typography variant="subtitle2">{title}</Typography>
            {breadcrumbAction && <div className={classes.breadcrumbAction}>{breadcrumbAction}</div>}
          </div>
        </Zoom>
      </Breadcrumbs>
    </Grid>
    <Grid item xs={12} className={classes.resourceTitle}>
      <Grid container direction="row" justify="space-between" alignItems="center">
        <Grid item>
          <Typography component="h2" variant="h4">{title}</Typography>
        </Grid>
        <Grid item>{titleAction}</Grid>
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
