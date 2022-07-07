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

import { Link } from 'react-router-dom';
import { Breadcrumbs, Card, CardContent, CardHeader, Typography } from "@mui/material";
import { makeStyles } from '@mui/styles';

import LiveTable from "../dataHomepage/LiveTable.jsx";
import NewItemButton from "../components/NewItemButton.jsx";

const useStyles = makeStyles(theme => ({
  root: {
    marginTop: theme.spacing(2),
  },
  withMainAction: {
    marginBottom: theme.spacing(4),
  },
  title: {
    fontWeight: "normal",
    marginTop: theme.spacing(1.5),
  },
}));

function AdminScreen(props) {
  const { title, action, disableBreadcrumb, className, children } = props;

  const classes = useStyles();
  const appName = document.querySelector('meta[name="title"]')?.content;

  const heading = <Typography className={classes.title} variant="h4">{title}</Typography>;
  const breadcrumb = (
    <Breadcrumbs separator="/">
      <Typography variant="overline"><Link to="/">{appName}</Link></Typography>
      <Typography variant="overline"><Link to="/content.html/admin/">Administration</Link></Typography>
    </Breadcrumbs>
  );

  let classNames = [classes.root];
  if (className) {
    classNames.push(className);
  }
  if (!!action) {
    classNames.push(classes.withMainAction);
  }

  return (
    <Card className={classNames.join(' ')}>
      <CardHeader
        disableTypography
        title={disableBreadcrumb ? heading : breadcrumb}
        subheader={disableBreadcrumb ? undefined : heading}
        action={action}
      />
      <CardContent>
	  { children }
      </CardContent>
    </Card>
  );
}

AdminScreen.propTypes = {
  title: PropTypes.string,
  action: PropTypes.node,
  disableBreadcrumb: PropTypes.bool,
};

AdminScreen.defaultProps = {
  title: "Administration",
};

export default AdminScreen;
