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
import React, { useState, useEffect, useContext } from "react";
import PropTypes from "prop-types";

import {
  Badge,
  CircularProgress,
  IconButton,
  Tooltip,
} from "@mui/material";

import ErrorIcon from '@mui/icons-material/Error';
import LinkIcon from '@mui/icons-material/Link';
import SurveyIcon from '@mui/icons-material/Assignment';
import SurveyCopiedIcon from '@mui/icons-material/AssignmentTurnedIn';

import { CopyToClipboard } from 'react-copy-to-clipboard';

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

function SurveyLinkButton(props) {
  const { visitURL, size } = props;

  const [ token, setToken ] = useState();
  const [ surveyLink, setSurveyLink ] = useState();
  const [ copied, setCopied ] = useState();
  const [ error, setError ] = useState();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  useEffect(() => {
    fetchWithReLogin(globalLoginDisplay, `${visitURL}.token.html`)
      .then((response) => response.ok ? response.text() : Promise.reject(response))
      .then((text) => setToken(text.trim()))
      .catch(() => setError("Could not generate survey link"));
  }, [visitURL]);

  useEffect(() => {
    token && setSurveyLink(window.location.origin + "/Survey.html?auth_token=" + token);
  }, [token]);

  const onCopy = () => {
    setCopied(true);
    setTimeout(function() {
      setCopied(false);
    }, 3000);
  }

  return (
    surveyLink ?
      <CopyToClipboard text={surveyLink} onCopy={onCopy}>
        <Tooltip title={
          copied ? "Copied"
          : `Copy patient survey link to clipboard`
        }>
          <IconButton size={size ?? "large"}>
            <Badge
              color="default"
              badgeContent={<LinkIcon fontSize="small" sx={{zoom: .75}}/>}
              anchorOrigin={{vertical: 'bottom', horizontal: 'right'}}
            >
              { copied ? <SurveyCopiedIcon/> : <SurveyIcon /> }
            </Badge>
          </IconButton>
        </Tooltip>
    </CopyToClipboard>
   : <Tooltip title={error ? error : "Generating survey link..."}>
       <IconButton size={size || large} disabled>
         {error ? <ErrorIcon /> : <CircularProgress size={24}/>}
       </IconButton>
     </Tooltip>
  );
}

SurveyLinkButton.propTypes = {
  visitURL: PropTypes.string.isRequired,
  size: PropTypes.oneOf(["small", "medium", "large"]),
}

SurveyLinkButton.defaultProps = {
  size: "large",
}

export default SurveyLinkButton;
