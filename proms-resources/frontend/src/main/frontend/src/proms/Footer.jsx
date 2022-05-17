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
import React, { useState, useEffect } from "react";
import { styled } from '@mui/material/styles';
import { Toolbar, Typography } from "@mui/material";

import makeStyles from '@mui/styles/makeStyles';

import { loadExtensions } from "../uiextension/extensionManager";

const PREFIX = 'PromsFooter';

const classes = {
  footer: `${PREFIX}-footer`
};

const StyledToolbar = styled(Toolbar)((
  {
    theme
  }
) => ({
  [`&.${classes.footer}`]: {
    color: theme.palette.text.secondary,
    justifyContent: "center",
    minHeight: theme.spacing(2),
    "& > *" : {
      margin: theme.spacing(0, 2),
    },
  }
}));

async function getFooterExtensions() {
  return loadExtensions("Footer")
    .then(extensions => extensions.slice()
      .sort((a, b) => a["cards:defaultOrder"] - b["cards:defaultOrder"])
    )
}

function PromsFooter (props) {
  let [ footerExtensions, setFooterExtensions ] = useState([]);



  useEffect(() => {
    getFooterExtensions()
      .then(extensions => setFooterExtensions(extensions))
      .catch(err => console.log("Something went wrong loading the page footer", err))
  }, [])

  return (
    <StyledToolbar className={classes.footer}>
    {
      footerExtensions.map((extension, index) => {
        let Extension = extension["cards:extensionRender"];
        return <Typography variant="caption" key={index}><Extension /></Typography>
      })
    }
    </StyledToolbar>
  );
}

export default PromsFooter;
