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
  CircularProgress,
  IconButton,
  Tooltip,
} from "@mui/material";

import ErrorIcon from '@mui/icons-material/Error';
import DoneIcon from '@mui/icons-material/Done';
import ShareIcon from '@mui/icons-material/Share';

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

function SurveyLinkButton(props) {
  const { visitURL, size } = props;

  const [ surveyLink, setSurveyLink ] = useState();
  const [ fetchingLink, setFetchingLink ] = useState();
  const [ copied, setCopied ] = useState();
  const [ error, setError ] = useState();

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const copy = (text) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(function() {
      setCopied(false);
    }, 5_000);
  };

  const fetchToken = () => {
    setFetchingLink(true);
    fetchWithReLogin(globalLoginDisplay, `${visitURL}.token.html`)
      .then((response) => response.ok ? response.text() : Promise.reject(response))
      .then((text) => { setSurveyLink(window.location.origin + "/Survey.html?auth_token=" + text.trim()); copy(window.location.origin + "/Survey.html?auth_token=" + text.trim()); })
      .catch(() => setError("Could not generate survey link"))
      .finally(() => setFetchingLink(false));
  };

  const onClick = () => {
    surveyLink ? copy(surveyLink) : fetchToken();
  };

  if (error) {
    return (<Tooltip title={error}>
      <IconButton size={size || "large"}>
        <ErrorIcon />
      </IconButton>
    </Tooltip>);
  }
  if (fetchingLink) {
    return (<Tooltip title={"Generating survey link..."}>
      <IconButton size={size || "large"}>
        <CircularProgress size={24}/>
      </IconButton>
    </Tooltip>);
  }
  return (<Tooltip title={copied ? "Copied" : `Copy patient survey link to clipboard`}>
    <IconButton size={size || "large"} onClick={onClick}>
      { copied ? <DoneIcon/> : <ShareIcon/> }
    </IconButton>
  </Tooltip>);
}

SurveyLinkButton.propTypes = {
  visitURL: PropTypes.string.isRequired,
  size: PropTypes.oneOf(["small", "medium", "large"]),
}

SurveyLinkButton.defaultProps = {
  size: "large",
}

export default SurveyLinkButton;
