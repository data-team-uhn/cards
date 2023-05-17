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
import React from 'react';
import PropTypes from 'prop-types';

import { Box } from '@mui/material';
import makeStyles from '@mui/styles/makeStyles';

const useStyles = makeStyles(theme => ({
  logo : {
    "& > img" : {
      maxWidth: "240px",
      width: "100%",
    },
    "@media (max-height: 725px)" : {
      "& > img" : {
        maxHeight: "70px",
      },
    },
  },
  doubleLogo : {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    flexWrap: "wrap-reverse",
    maxWidth: "600px !important",
    "& > img" : {
      width: `calc(50% - ${theme.spacing(4)})`,
      minWidth: "100px",
      margin: theme.spacing(1, 2),
    },
  },
}));

export default function Logo(props) {
  const { component, mode, className, maxWidth, disableAffiliation, ...rest } = props;
  const classes = useStyles();

  const appName = document.querySelector('meta[name="title"]')?.content;
  const logo = document.querySelector(`meta[name="logo${mode}"]`).content;
  const affiliationLogo = document.querySelector(`meta[name="affiliationLogo${mode}"]`)?.content;
  const withAffiliation = !disableAffiliation && !!affiliationLogo;

  const Component = component;
  const style = typeof(maxWidth) != "undefined" ? {maxWidth: maxWidth} : {};
  let classNames = withAffiliation ? [classes.doubleLogo] : [classes.logo];
  if (className) classNames.push(className);

  return (
    <Component className={classNames.join(' ')} {...rest} >
      <img src={logo} alt={appName} style={style} />
      {withAffiliation && <img src={affiliationLogo} alt="" style={style} />}
    </Component>
  );
}

Logo.propTypes = {
  component: PropTypes.object,
  mode: PropTypes.oneOf(["Light", "Dark"]),
  className: PropTypes.string,
  maxWidth: PropTypes.string,
  disableAffiliation: PropTypes.bool,
}

Logo.defaultProps = {
  component: Box,
  mode: "Light",
}
